package org.chrisgruber.nettank.client.engine.audio;

import org.chrisgruber.nettank.common.entities.TankType;
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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.stb.STBVorbis.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * Minimal OpenAL sound engine: decodes OGG Vorbis (or WAV) clips, plays them through a
 * small round-robin pool of sources (positional 2D, z=0), and runs one
 * {@link TankAudioEmitter} per audible tank for RPM-driven engine and track loops.
 * UI/one-shot sounds that aren't tied to a world position play at the listener's location.
 */
public class AudioManager {

    private static final Logger logger = LoggerFactory.getLogger(AudioManager.class);
    private static final int SOURCE_POOL_SIZE = 16;
    private static final float REFERENCE_DISTANCE = 250.0f;
    private static final float MAX_DISTANCE = 2000.0f;
    private static final float ROLLOFF_FACTOR = 1.0f;
    // The listener hovers above the battlefield so nearby sounds pan gently instead of
    // snapping hard left/right; this stays below REFERENCE_DISTANCE so close sounds keep full gain.
    private static final float LISTENER_HEIGHT = 150.0f;
    // Each emitter holds ~7 sources; OpenAL Soft defaults to 256 in total
    private static final int MAX_TANK_EMITTERS = 12;

    /** Per-frame input for one tank's engine sound. */
    public record TankAudioState(
            TankType type,
            float x,
            float y,
            boolean engineRunning,
            float throttle,      // 0..1, or negative to estimate from movement (remote tanks)
            float speed01,       // ground speed / tank type move speed
            float turn01,        // hull turn rate / tank type turn speed
            float audibility     // 0..1; 0 for tanks the player can't see
    ) {}

    private long device = 0;
    private long context = 0;
    private boolean initialized = false;
    private boolean efxAvailable = false;

    private final Map<String, Integer> soundBuffers = new HashMap<>();
    private final int[] sourcePool = new int[SOURCE_POOL_SIZE];
    private int nextSource = 0;

    private int turretRotateSource = 0;
    private boolean turretRotateLoaded = false;
    private float listenerX = 0f;
    private float listenerY = 0f;

    private final Map<TankType, EngineSoundProfile> tankProfiles = new EnumMap<>(TankType.class);
    private final List<String> trackOneShots = new ArrayList<>();
    private final Map<Integer, TankAudioEmitter> tankEmitters = new HashMap<>();
    private final Set<Integer> tanksUpdatedThisFrame = new HashSet<>();
    private final Random random = new Random();
    private boolean tankAudioLoaded = false;

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
            // EFX gives per-tank low-pass (load, stealth) and distance air absorption
            efxAvailable = alcCaps.ALC_EXT_EFX;

            for (int i = 0; i < SOURCE_POOL_SIZE; i++) {
                sourcePool[i] = alGenSources();
                configureDistanceModel(sourcePool[i]);
            }
            alListener3f(AL_POSITION, 0f, 0f, LISTENER_HEIGHT);

            // Dedicated looping turret traverse source
            turretRotateSource = alGenSources();
            configureDistanceModel(turretRotateSource);
            alSourcei(turretRotateSource, AL_LOOPING, AL_TRUE);

            initialized = true;
            logger.info("AudioManager initialized (device: {}, EFX: {})", defaultDeviceName, efxAvailable);
        } catch (Exception e) {
            logger.error("Failed to initialize OpenAL audio; continuing without sound.", e);
            initialized = false;
        }
    }

    static void configureDistanceModel(int source) {
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
        alListener3f(AL_POSITION, x, y, LISTENER_HEIGHT);
    }

    /**
     * Loads every engine family, the track loops and track one-shots. Each sound is looked
     * up as .ogg first, then .wav, so a sourced recording replaces a placeholder by just
     * dropping it in. Missing loops are skipped; the remaining ones still crossfade.
     */
    public void loadTankAudio() {
        if (!initialized) return;
        Map<String, EngineSoundProfile.EngineSoundSet> resolvedSets = new HashMap<>();
        for (EngineSoundProfile.EngineSoundSet set : EngineSoundProfiles.allSets()) {
            List<EngineSoundProfile.EngineLoop> loaded = new ArrayList<>();
            for (EngineSoundProfile.EngineLoop loop : set.loops()) {
                if (loadSoundAnyFormat(loop.path())) loaded.add(loop);
            }
            loadSoundAnyFormat(set.startPath());
            loadSoundAnyFormat(set.stopPath());
            resolvedSets.put(set.id(), set.withLoops(loaded));
            if (!loaded.isEmpty()) tankAudioLoaded = true;
            logger.info("Engine sound set '{}': {}/{} loops loaded", set.id(), loaded.size(), set.loops().size());
        }

        loadSoundAnyFormat(EngineSoundProfiles.TRACK_SLOW);
        loadSoundAnyFormat(EngineSoundProfiles.TRACK_FAST);
        trackOneShots.clear();
        for (String path : EngineSoundProfiles.TRACK_ONE_SHOTS) {
            if (loadSoundAnyFormat(path)) trackOneShots.add(path);
        }

        for (TankType type : TankType.values()) {
            EngineSoundProfile profile = EngineSoundProfiles.forType(type);
            tankProfiles.put(type, profile.withSounds(resolvedSets.get(profile.sounds().id())));
        }
    }

    /**
     * Updates (creating if needed) the engine sound of one tank. Call once per tank per
     * frame, then {@link #endTankAudioFrame()} to release tanks that weren't updated.
     */
    public void updateTankAudio(int tankId, TankAudioState state, float deltaTime) {
        if (!initialized || !tankAudioLoaded || state.type() == null) return;
        tanksUpdatedThisFrame.add(tankId);

        TankAudioEmitter emitter = tankEmitters.get(tankId);
        if (emitter != null && emitter.tankType() != state.type()) {
            emitter.dispose(); // tank type changed (lobby re-selection)
            tankEmitters.remove(tankId);
            emitter = null;
        }
        if (emitter == null) {
            if (state.audibility() <= 0.01f || tankEmitters.size() >= MAX_TANK_EMITTERS) return;
            emitter = TankAudioEmitter.create(state.type(), tankProfiles.get(state.type()), soundBuffers,
                    trackOneShots, this::playSound, efxAvailable, state.engineRunning(), state.audibility(), random);
            if (emitter == null) return;
            tankEmitters.put(tankId, emitter);
        }

        emitter.update(deltaTime, state, listenerX, listenerY);
        if (state.audibility() <= 0.01f && emitter.isSilent()) {
            emitter.dispose();
            tankEmitters.remove(tankId);
        }
    }

    /** Releases emitters for tanks that left or weren't updated this frame. */
    public void endTankAudioFrame() {
        if (!initialized) return;
        tankEmitters.entrySet().removeIf(entry -> {
            if (tanksUpdatedThisFrame.contains(entry.getKey())) return false;
            entry.getValue().dispose();
            return true;
        });
        tanksUpdatedThisFrame.clear();
    }

    /**
     * Loads path + ".ogg" if present, else path + ".wav", registered under {@code path}
     * (so a sourced .ogg replaces a synthesized .wav placeholder just by being dropped in).
     */
    public boolean loadSoundAnyFormat(String path) {
        if (!initialized) return false;
        for (String extension : new String[] {".ogg", ".wav"}) {
            if (resourceExists(path + extension)) {
                loadSound(path, path + extension);
                return soundBuffers.containsKey(path);
            }
        }
        logger.warn("Missing sound resource: {}.ogg / .wav", path);
        return false;
    }

    private static boolean resourceExists(String resource) {
        if (java.nio.file.Files.isReadable(java.nio.file.Paths.get(resource))) return true;
        return AudioManager.class.getClassLoader().getResource(resource) != null;
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
        // Emitter sources reference buffers, so they must go before the buffers do
        for (TankAudioEmitter emitter : tankEmitters.values()) {
            emitter.dispose();
        }
        tankEmitters.clear();
        for (int source : sourcePool) {
            alSourceStop(source);
            alDeleteSources(source);
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
