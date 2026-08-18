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

    public enum EngineState {
        OFF,
        IDLE,
        FORWARD,
        REVERSE
    }

    private int engineSource = 0;
    private int engineIdleSource = 0;
    private int engineForwardSource = 0;
    private int engineReverseSource = 0;
    private int turretRotateSource = 0;
    private boolean engineLoaded = false;
    private boolean turretRotateLoaded = false;
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

            // Dedicated looping engine sources
            engineSource = alGenSources();
            configureDistanceModel(engineSource);
            alSourcei(engineSource, AL_LOOPING, AL_TRUE);

            engineIdleSource = alGenSources();
            configureDistanceModel(engineIdleSource);
            alSourcei(engineIdleSource, AL_LOOPING, AL_TRUE);

            engineForwardSource = alGenSources();
            configureDistanceModel(engineForwardSource);
            alSourcei(engineForwardSource, AL_LOOPING, AL_TRUE);

            engineReverseSource = alGenSources();
            configureDistanceModel(engineReverseSource);
            alSourcei(engineReverseSource, AL_LOOPING, AL_TRUE);

            // Dedicated looping turret traverse source
            turretRotateSource = alGenSources();
            configureDistanceModel(turretRotateSource);
            alSourcei(turretRotateSource, AL_LOOPING, AL_TRUE);

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

    /** Decodes a classpath/filesystem OGG or WAV resource and registers it under the given name. */
    public void loadSound(String name, String classpathOgg) {
        if (!initialized) return;
        try {
            ByteBuffer audioData = ioResourceToByteBuffer(classpathOgg);
            
            // Check if file is RIFF/WAVE
            if (audioData.remaining() >= 12 && 
                audioData.get(0) == 'R' && audioData.get(1) == 'I' && 
                audioData.get(2) == 'F' && audioData.get(3) == 'F') {
                
                int channels = audioData.getShort(22) & 0xFFFF;
                int sampleRate = audioData.getInt(24);
                int bitsPerSample = audioData.getShort(34) & 0xFFFF;
                
                // Find "data" chunk
                int dataOffset = 12;
                int dataLength = 0;
                while (dataOffset + 8 <= audioData.remaining()) {
                    if (audioData.get(dataOffset) == 'd' && audioData.get(dataOffset + 1) == 'a' &&
                        audioData.get(dataOffset + 2) == 't' && audioData.get(dataOffset + 3) == 'a') {
                        dataLength = audioData.getInt(dataOffset + 4);
                        dataOffset += 8;
                        break;
                    }
                    int chunkSize = audioData.getInt(dataOffset + 4);
                    dataOffset += 8 + chunkSize;
                }
                
                if (dataLength > 0 && dataOffset + dataLength <= audioData.remaining()) {
                    audioData.position(dataOffset);
                    audioData.limit(dataOffset + dataLength);
                    ByteBuffer pcm = audioData.slice();
                    
                    int format = channels == 1 ? 
                        (bitsPerSample == 8 ? AL_FORMAT_MONO8 : AL_FORMAT_MONO16) :
                        (bitsPerSample == 8 ? AL_FORMAT_STEREO8 : AL_FORMAT_STEREO16);
                        
                    int bufferId = alGenBuffers();
                    alBufferData(bufferId, format, pcm, sampleRate);
                    soundBuffers.put(name, bufferId);
                    logger.debug("Loaded WAV sound '{}' from {} ({} Hz, {} ch)", name, classpathOgg, sampleRate, channels);
                    return;
                }
            }

            // Fallback to STB Vorbis OGG decoder
            try (MemoryStack stack = stackPush()) {
                IntBuffer channelsBuffer = stack.mallocInt(1);
                IntBuffer sampleRateBuffer = stack.mallocInt(1);

                ShortBuffer rawAudio = stb_vorbis_decode_memory(audioData, channelsBuffer, sampleRateBuffer);
                if (rawAudio == null) {
                    logger.error("Failed to decode OGG Vorbis sound from {}", classpathOgg);
                    return;
                }

                int channels = channelsBuffer.get(0);
                int sampleRate = sampleRateBuffer.get(0);
                int format = channels == 1 ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16;

                int bufferId = alGenBuffers();
                alBufferData(bufferId, format, rawAudio, sampleRate);
                MemoryUtil.nmemFree(MemoryUtil.memAddress(rawAudio));

                soundBuffers.put(name, bufferId);
                logger.debug("Loaded OGG sound '{}' from {} ({} Hz, {} ch)", name, classpathOgg, sampleRate, channels);
            }
        } catch (Exception e) {
            logger.error("Failed to load sound resource: {}", classpathOgg, e);
        }
    }

    /** Plays a registered sound at a 2D world position. Distance attenuation applies. */
    public void playSound(String name, float worldX, float worldY, float gain, float pitch) {
        if (!initialized) return;
        Integer bufferId = soundBuffers.get(name);
        if (bufferId == null) return;

        int source = sourcePool[nextSource];
        nextSource = (nextSource + 1) % SOURCE_POOL_SIZE;

        alSourceStop(source);
        alSourcei(source, AL_BUFFER, bufferId);
        alSourcei(source, AL_LOOPING, AL_FALSE);
        alSourcef(source, AL_GAIN, gain);
        alSourcef(source, AL_PITCH, pitch);
        alSource3f(source, AL_POSITION, worldX, worldY, 0f);
        alSourcePlay(source);
    }

    public void playSoundAt(String name, float x, float y) {
        playSound(name, x, y, 1.0f, 1.0f);
    }

    public void playSoundAt(String name, float x, float y, float gain, float pitch) {
        playSound(name, x, y, gain, pitch);
    }

    /** Plays a UI / non-spatial sound at the listener's position (no attenuation). */
    public void playSound(String name, float gain, float pitch) {
        playSound(name, listenerX, listenerY, gain, pitch);
    }

    /** Convenience overload matching default volume and pitch. */
    public void playSound(String name) {
        playSound(name, 1.0f, 1.0f);
    }

    /** Updates the listener (camera/player) position for spatial attenuation. */
    public void setListenerPosition(float x, float y) {
        if (!initialized) return;
        this.listenerX = x;
        this.listenerY = y;
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

    /** Loads the 3 modern military engine loops: idle, forward high-power, reverse gear. */
    public void loadEngineLoops(String idlePath, String forwardPath, String reversePath) {
        if (!initialized) return;
        loadSound("__engine_idle", idlePath);
        loadSound("__engine_forward", forwardPath);
        loadSound("__engine_reverse", reversePath);

        Integer idleBuf = soundBuffers.get("__engine_idle");
        Integer fwdBuf = soundBuffers.get("__engine_forward");
        Integer revBuf = soundBuffers.get("__engine_reverse");

        if (idleBuf != null) alSourcei(engineIdleSource, AL_BUFFER, idleBuf);
        if (fwdBuf != null) alSourcei(engineForwardSource, AL_BUFFER, fwdBuf);
        if (revBuf != null) alSourcei(engineReverseSource, AL_BUFFER, revBuf);

        if (idleBuf != null || fwdBuf != null || revBuf != null) {
            engineLoaded = true;
        }
    }

    /** Updates the modern military engine state: OFF, IDLE, FORWARD, or REVERSE. */
    public void setEngineState(EngineState state, float speedFactor) {
        if (!initialized || !engineLoaded) return;

        alSource3f(engineIdleSource, AL_POSITION, listenerX, listenerY, 0f);
        alSource3f(engineForwardSource, AL_POSITION, listenerX, listenerY, 0f);
        alSource3f(engineReverseSource, AL_POSITION, listenerX, listenerY, 0f);

        switch (state) {
            case OFF -> {
                alSourcef(engineIdleSource, AL_GAIN, 0f);
                alSourcef(engineForwardSource, AL_GAIN, 0f);
                alSourcef(engineReverseSource, AL_GAIN, 0f);
                if (alGetSourcei(engineIdleSource, AL_SOURCE_STATE) == AL_PLAYING) alSourceStop(engineIdleSource);
                if (alGetSourcei(engineForwardSource, AL_SOURCE_STATE) == AL_PLAYING) alSourceStop(engineForwardSource);
                if (alGetSourcei(engineReverseSource, AL_SOURCE_STATE) == AL_PLAYING) alSourceStop(engineReverseSource);
            }
            case IDLE -> {
                ensurePlaying(engineIdleSource);
                alSourcef(engineIdleSource, AL_GAIN, 0.52f);
                alSourcef(engineIdleSource, AL_PITCH, 1.0f);

                alSourcef(engineForwardSource, AL_GAIN, 0f);
                alSourcef(engineReverseSource, AL_GAIN, 0f);
            }
            case FORWARD -> {
                ensurePlaying(engineForwardSource);
                ensurePlaying(engineIdleSource);

                float pitch = Math.max(0.88f, Math.min(1.30f, 1.0f + (speedFactor - 1.0f) * 0.20f));
                alSourcef(engineForwardSource, AL_GAIN, 0.82f);
                alSourcef(engineForwardSource, AL_PITCH, pitch);

                alSourcef(engineIdleSource, AL_GAIN, 0.12f);
                alSourcef(engineReverseSource, AL_GAIN, 0f);
            }
            case REVERSE -> {
                ensurePlaying(engineReverseSource);
                ensurePlaying(engineIdleSource);

                alSourcef(engineReverseSource, AL_GAIN, 0.78f);
                alSourcef(engineReverseSource, AL_PITCH, 0.95f);

                alSourcef(engineIdleSource, AL_GAIN, 0.12f);
                alSourcef(engineForwardSource, AL_GAIN, 0f);
            }
        }
    }

    /** Loads the looping motorized turret traverse sound effect. */
    public void loadTurretRotateLoop(String classpathOgg) {
        if (!initialized) return;
        loadSound("__turret_rotate", classpathOgg);
        Integer bufferId = soundBuffers.get("__turret_rotate");
        if (bufferId != null && turretRotateSource != 0) {
            alSourcei(turretRotateSource, AL_BUFFER, bufferId);
            turretRotateLoaded = true;
        }
    }

    /** Updates the turret rotation audio state (active when rotating). */
    public void setTurretRotateState(boolean rotating, float pitch, float gain) {
        if (!initialized || !turretRotateLoaded || turretRotateSource == 0) return;
        alSource3f(turretRotateSource, AL_POSITION, listenerX, listenerY, 0f);
        if (rotating) {
            alSourcef(turretRotateSource, AL_GAIN, gain);
            alSourcef(turretRotateSource, AL_PITCH, pitch);
            ensurePlaying(turretRotateSource);
        } else {
            int state = alGetSourcei(turretRotateSource, AL_SOURCE_STATE);
            if (state == AL_PLAYING) {
                alSourceStop(turretRotateSource);
            }
        }
    }

    private void ensurePlaying(int source) {
        if (source != 0 && alGetSourcei(source, AL_SOURCE_STATE) != AL_PLAYING) {
            alSourcePlay(source);
        }
    }

    public void cleanup() {
        if (!initialized) return;
        for (int source : sourcePool) {
            alSourceStop(source);
            alDeleteSources(source);
        }
        if (engineSource != 0) {
            alSourceStop(engineSource);
            alDeleteSources(engineSource);
        }
        if (engineIdleSource != 0) {
            alSourceStop(engineIdleSource);
            alDeleteSources(engineIdleSource);
        }
        if (engineForwardSource != 0) {
            alSourceStop(engineForwardSource);
            alDeleteSources(engineForwardSource);
        }
        if (engineReverseSource != 0) {
            alSourceStop(engineReverseSource);
            alDeleteSources(engineReverseSource);
        }
        if (turretRotateSource != 0) {
            alSourceStop(turretRotateSource);
            alDeleteSources(turretRotateSource);
        }
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
