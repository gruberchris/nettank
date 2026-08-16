package org.chrisgruber.nettank.client.engine.audio;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.stb.STBVorbis.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * Minimal OpenAL sound engine: decodes OGG Vorbis clips with STB, plays them
 * through a small round-robin pool of sources (positional 2D, z=0), and drives
 * one dedicated looping source for the local tank's engine hum. UI/one-shot
 * sounds that aren't tied to a world position play at the listener's location.
 */
public class AudioManager {

    private static final Logger logger = LoggerFactory.getLogger(AudioManager.class);
    private static final int SOURCE_POOL_SIZE = 16;
    private static final float REFERENCE_DISTANCE = 250.0f;
    private static final float MAX_DISTANCE = 2000.0f;
    private static final float ROLLOFF_FACTOR = 1.0f;

    private long device = 0;
    private long context = 0;
    private boolean initialized = false;

    private final Map<String, Integer> soundBuffers = new HashMap<>();
    private final int[] sourcePool = new int[SOURCE_POOL_SIZE];
    private int nextSource = 0;

    private int engineSource = 0;
    private boolean engineLoaded = false;
    private float listenerX = 0f;
    private float listenerY = 0f;

    public void init() {
        try {
            String defaultDeviceName = alcGetString(0, ALC_DEFAULT_DEVICE_SPECIFIER);
            device = alcOpenDevice(defaultDeviceName);
            if (device == 0) {
                logger.warn("No OpenAL device found; audio will be disabled.");
                return;
            }

            ALCCapabilities alcCaps = ALC.createCapabilities(device);
            context = alcCreateContext(device, (IntBuffer) null);
            if (context == 0) {
                logger.warn("Failed to create OpenAL context; audio will be disabled.");
                alcCloseDevice(device);
                device = 0;
                return;
            }
            alcMakeContextCurrent(context);
            AL.createCapabilities(alcCaps);

            for (int i = 0; i < SOURCE_POOL_SIZE; i++) {
                sourcePool[i] = alGenSources();
                configureDistanceModel(sourcePool[i]);
            }
            engineSource = alGenSources();
            configureDistanceModel(engineSource);
            alSourcei(engineSource, AL_LOOPING, AL_TRUE);

            initialized = true;
            logger.info("AudioManager initialized (device: {})", defaultDeviceName);
        } catch (Exception e) {
            logger.error("Failed to initialize OpenAL audio; continuing without sound.", e);
            initialized = false;
        }
    }

    private void configureDistanceModel(int source) {
        alSourcef(source, AL_REFERENCE_DISTANCE, REFERENCE_DISTANCE);
        alSourcef(source, AL_MAX_DISTANCE, MAX_DISTANCE);
        alSourcef(source, AL_ROLLOFF_FACTOR, ROLLOFF_FACTOR);
        alSource3f(source, AL_POSITION, 0f, 0f, 0f);
    }

    public boolean isInitialized() {
        return initialized;
    }

    /** Decodes a classpath/filesystem OGG resource and registers it under the given name. */
    public void loadSound(String name, String classpathOgg) {
        if (!initialized) return;
        try {
            ByteBuffer vorbis = ioResourceToByteBuffer(classpathOgg);
            try (MemoryStack stack = stackPush()) {
                IntBuffer channelsBuffer = stack.mallocInt(1);
                IntBuffer sampleRateBuffer = stack.mallocInt(1);

                ShortBuffer pcm = stb_vorbis_decode_memory(vorbis, channelsBuffer, sampleRateBuffer);
                if (pcm == null) {
                    logger.error("Failed to decode OGG resource: {}", classpathOgg);
                    return;
                }
                int channels = channelsBuffer.get(0);
                int sampleRate = sampleRateBuffer.get(0);
                int format = channels == 1 ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16;

                int bufferId = alGenBuffers();
                alBufferData(bufferId, format, pcm, sampleRate);
                MemoryUtil.memFree(pcm);

                soundBuffers.put(name, bufferId);
                logger.debug("Loaded sound '{}' from {} ({} Hz, {} ch)", name, classpathOgg, sampleRate, channels);
            }
        } catch (IOException e) {
            logger.error("Failed to load sound '{}' from {}", name, classpathOgg, e);
        }
    }

    /** Plays a registered clip at a world position with distance attenuation. */
    public void playSoundAt(String name, float x, float y) {
        playSoundAt(name, x, y, 1.0f, 1.0f);
    }

    public void playSoundAt(String name, float x, float y, float gain, float pitch) {
        if (!initialized) return;
        Integer bufferId = soundBuffers.get(name);
        if (bufferId == null) {
            logger.warn("Attempted to play unregistered sound '{}'", name);
            return;
        }
        int source = sourcePool[nextSource];
        nextSource = (nextSource + 1) % SOURCE_POOL_SIZE;

        alSourceStop(source);
        alSourcei(source, AL_BUFFER, bufferId);
        alSource3f(source, AL_POSITION, x, y, 0f);
        alSourcef(source, AL_GAIN, gain);
        alSourcef(source, AL_PITCH, pitch);
        alSourcePlay(source);
    }

    /** Plays a registered clip with no world position (UI sounds, always at full volume). */
    public void playSound(String name) {
        playSound(name, 1.0f, 1.0f);
    }

    public void playSound(String name, float gain, float pitch) {
        if (!initialized) return;
        playSoundAt(name, listenerX, listenerY, gain, pitch);
    }

    /** Call once per frame with the local player's position so distance attenuation is correct. */
    public void setListenerPosition(float x, float y) {
        listenerX = x;
        listenerY = y;
        if (!initialized) return;
        alListener3f(AL_POSITION, x, y, 0f);
    }

    /** Starts/updates or stops the looping local-tank engine hum. Call every frame. */
    public void setEngineLoopActive(boolean active, float gain, float pitch) {
        if (!initialized || !engineLoaded) return;
        int state = alGetSourcei(engineSource, AL_SOURCE_STATE);
        if (active) {
            alSourcef(engineSource, AL_GAIN, gain);
            alSourcef(engineSource, AL_PITCH, pitch);
            alSource3f(engineSource, AL_POSITION, listenerX, listenerY, 0f);
            if (state != AL_PLAYING) {
                alSourcePlay(engineSource);
            }
        } else if (state == AL_PLAYING) {
            alSourceStop(engineSource);
        }
    }

    public void loadEngineLoop(String classpathOgg) {
        loadSound("__engine_loop", classpathOgg);
        Integer bufferId = soundBuffers.get("__engine_loop");
        if (bufferId != null) {
            alSourcei(engineSource, AL_BUFFER, bufferId);
            engineLoaded = true;
        }
    }

    public void cleanup() {
        if (!initialized) return;
        for (int source : sourcePool) {
            alSourceStop(source);
            alDeleteSources(source);
        }
        alSourceStop(engineSource);
        alDeleteSources(engineSource);
        for (int bufferId : soundBuffers.values()) {
            alDeleteBuffers(bufferId);
        }
        soundBuffers.clear();

        if (context != 0) {
            alcMakeContextCurrent(0);
            alcDestroyContext(context);
        }
        if (device != 0) {
            alcCloseDevice(device);
        }
        initialized = false;
        logger.info("AudioManager cleaned up.");
    }

    /** Reads a classpath or filesystem resource fully into a native ByteBuffer (mirrors Texture's loader). */
    private static ByteBuffer ioResourceToByteBuffer(String resource) throws IOException {
        java.nio.file.Path path = java.nio.file.Paths.get(resource);
        if (java.nio.file.Files.isReadable(path)) {
            try (java.nio.channels.SeekableByteChannel sbc = java.nio.file.Files.newByteChannel(path)) {
                ByteBuffer buffer = BufferUtils.createByteBuffer((int) sbc.size() + 1);
                while (sbc.read(buffer) != -1) { /* read fully */ }
                buffer.flip();
                return buffer;
            }
        }

        URL url = AudioManager.class.getClassLoader().getResource(resource);
        if (url == null && !resource.startsWith("/")) {
            url = AudioManager.class.getClassLoader().getResource("/" + resource);
        }
        if (url == null) {
            throw new IOException("Failed to find sound resource: " + resource);
        }
        try (InputStream source = url.openStream();
             ReadableByteChannel rbc = Channels.newChannel(source)) {
            ByteBuffer buffer = BufferUtils.createByteBuffer(1024 * 64);
            while (true) {
                int bytes = rbc.read(buffer);
                if (bytes == -1) break;
                if (!buffer.hasRemaining()) {
                    ByteBuffer newBuffer = BufferUtils.createByteBuffer(buffer.capacity() * 2);
                    buffer.flip();
                    newBuffer.put(buffer);
                    buffer = newBuffer;
                }
            }
            buffer.flip();
            return buffer;
        }
    }
}
