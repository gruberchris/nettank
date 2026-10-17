package org.chrisgruber.nettank.client.engine.audio;

import org.chrisgruber.nettank.common.entities.TankType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.AL11.AL_SAMPLE_OFFSET;
import static org.lwjgl.openal.EXTEfx.*;

/**
 * One tank's engine and track sound: a looping OpenAL source per engine RPM loop plus
 * slow/fast track loops, all positioned at the tank. Every loop plays continuously and
 * {@link EngineAudioModel} decides which ones are audible, so crossfades never restart a
 * loop (no clicks). Gains are eased per frame so nothing jumps.
 */
final class TankAudioEmitter {

    private static final Logger logger = LoggerFactory.getLogger(TankAudioEmitter.class);

    private static final float GAIN_SMOOTHING_SECONDS = 0.06f;
    private static final float AUDIBILITY_SMOOTHING_SECONDS = 0.25f;
    // Mix levels relative to the one-shot effects (shots, hits, explosions)
    private static final float ENGINE_MASTER_GAIN = 0.85f;
    private static final float TRACK_MASTER_GAIN = 0.75f;
    private static final float START_STOP_GAIN = 0.9f;
    // EFX air absorption: distant tanks lose their high end (world units treated as meters)
    private static final float AIR_ABSORPTION_FACTOR = 0.5f;
    // Track squeaks only play near the listener so they don't eat the shared one-shot pool
    private static final float TRACK_ONE_SHOT_RANGE = 700.0f;

    interface OneShotPlayer {
        void play(String name, float x, float y, float gain, float pitch);
    }

    private final TankType tankType;
    private final EngineSoundProfile profile;
    private final EngineAudioModel model;
    private final OneShotPlayer oneShots;
    private final List<String> trackOneShots;
    private final int[] engineSources;
    private final float[] engineGains;
    private final int trackSlowSource;
    private final int trackFastSource;
    private float trackSlowGain;
    private float trackFastGain;
    private final int lowpassFilter;
    private float appliedBrightness = -1.0f;
    private float audibility;

    private TankAudioEmitter(TankType tankType, EngineSoundProfile profile, EngineAudioModel model,
                             OneShotPlayer oneShots, List<String> trackOneShots, int[] engineSources,
                             int trackSlowSource, int trackFastSource, int lowpassFilter, float audibility) {
        this.tankType = tankType;
        this.profile = profile;
        this.model = model;
        this.oneShots = oneShots;
        this.trackOneShots = trackOneShots;
        this.engineSources = engineSources;
        this.engineGains = new float[engineSources.length];
        this.trackSlowSource = trackSlowSource;
        this.trackFastSource = trackFastSource;
        this.lowpassFilter = lowpassFilter;
        this.audibility = audibility;
    }

    /** Creates the emitter's sources, or returns null if OpenAL is out of sources. */
    static TankAudioEmitter create(TankType tankType, EngineSoundProfile profile, Map<String, Integer> buffers,
                                   List<String> trackOneShots, OneShotPlayer oneShots, boolean efx,
                                   boolean startRunning, float audibility, Random random) {
        List<EngineSoundProfile.EngineLoop> loops = profile.sounds().loops();
        int[] engineSources = new int[loops.size()];
        int trackSlow = 0;
        int trackFast = 0;
        int filter = 0;
        try {
            alGetError(); // clear stale errors so failures below are ours
            for (int i = 0; i < loops.size(); i++) {
                engineSources[i] = createLoopSource(buffers.get(loops.get(i).path()), efx, random);
            }
            trackSlow = createLoopSource(buffers.get(EngineSoundProfiles.TRACK_SLOW), efx, random);
            trackFast = createLoopSource(buffers.get(EngineSoundProfiles.TRACK_FAST), efx, random);
            if (efx) {
                filter = alGenFilters();
                alFilteri(filter, AL_FILTER_TYPE, AL_FILTER_LOWPASS);
                alFilterf(filter, AL_LOWPASS_GAIN, 1.0f);
            }
            if (alGetError() != AL_NO_ERROR) {
                throw new IllegalStateException("OpenAL error while creating tank audio sources");
            }
        } catch (RuntimeException e) {
            logger.warn("Could not create tank audio emitter: {}", e.getMessage());
            deleteSources(engineSources);
            deleteSource(trackSlow);
            deleteSource(trackFast);
            if (filter != 0) alDeleteFilters(filter);
            return null;
        }

        EngineAudioModel model = new EngineAudioModel(profile, trackOneShots.size(), startRunning, random);
        return new TankAudioEmitter(tankType, profile, model, oneShots, trackOneShots,
                engineSources, trackSlow, trackFast, filter, audibility);
    }

    // Returns 0 (no source) when the buffer failed to load, so a missing file just goes silent
    private static int createLoopSource(Integer bufferId, boolean efx, Random random) {
        if (bufferId == null) return 0;
        int source = alGenSources();
        AudioManager.configureDistanceModel(source);
        alSourcei(source, AL_BUFFER, bufferId);
        alSourcei(source, AL_LOOPING, AL_TRUE);
        alSourcef(source, AL_GAIN, 0.0f);
        if (efx) {
            alSourcef(source, AL_AIR_ABSORPTION_FACTOR, AIR_ABSORPTION_FACTOR);
        }
        // Random start offset so several tanks using the same loops don't phase-lock
        int bytes = alGetBufferi(bufferId, AL_SIZE);
        int frameBytes = Math.max(1, alGetBufferi(bufferId, AL_CHANNELS) * alGetBufferi(bufferId, AL_BITS) / 8);
        int frames = bytes / frameBytes;
        if (frames > 1) {
            alSourcei(source, AL_SAMPLE_OFFSET, random.nextInt(frames));
        }
        alSourcePlay(source);
        return source;
    }

    void update(float dt, AudioManager.TankAudioState state, float listenerX, float listenerY) {
        model.update(dt, state.engineRunning(), state.throttle(), state.speed01(), state.turn01());

        float target = Math.max(0.0f, Math.min(1.0f, state.audibility()));
        audibility += (target - audibility) * (1.0f - (float) Math.exp(-dt / AUDIBILITY_SMOOTHING_SECONDS));
        float ease = 1.0f - (float) Math.exp(-dt / GAIN_SMOOTHING_SECONDS);

        for (int i = 0; i < engineSources.length; i++) {
            int source = engineSources[i];
            if (source == 0) continue;
            engineGains[i] += (model.loopGain(i) * audibility * ENGINE_MASTER_GAIN - engineGains[i]) * ease;
            alSourcef(source, AL_GAIN, engineGains[i]);
            alSourcef(source, AL_PITCH, model.loopPitch(i));
            alSource3f(source, AL_POSITION, state.x(), state.y(), 0.0f);
        }

        trackSlowGain += (model.trackSlowGain() * audibility * TRACK_MASTER_GAIN - trackSlowGain) * ease;
        trackFastGain += (model.trackFastGain() * audibility * TRACK_MASTER_GAIN - trackFastGain) * ease;
        updateTrackSource(trackSlowSource, trackSlowGain, model.trackSlowPitch(), state);
        updateTrackSource(trackFastSource, trackFastGain, model.trackFastPitch(), state);

        applyBrightness(model.brightness());
        playEvents(state, target, listenerX, listenerY);
    }

    private static void updateTrackSource(int source, float gain, float pitch, AudioManager.TankAudioState state) {
        if (source == 0) return;
        alSourcef(source, AL_GAIN, gain);
        alSourcef(source, AL_PITCH, pitch);
        alSource3f(source, AL_POSITION, state.x(), state.y(), 0.0f);
    }

    // The source copies filter settings when attached, so re-attach after each change
    private void applyBrightness(float brightness) {
        if (lowpassFilter == 0 || Math.abs(brightness - appliedBrightness) < 0.02f) return;
        appliedBrightness = brightness;
        alFilterf(lowpassFilter, AL_LOWPASS_GAINHF, brightness);
        for (int source : engineSources) {
            if (source != 0) alSourcei(source, AL_DIRECT_FILTER, lowpassFilter);
        }
    }

    private void playEvents(AudioManager.TankAudioState state, float audibility, float listenerX, float listenerY) {
        EngineSoundProfile.EngineSoundSet sounds = profile.sounds();
        float oneShotGain = START_STOP_GAIN * profile.gain() * audibility;
        if (model.pollStartEvent() && audibility > 0.05f) {
            oneShots.play(sounds.startPath(), state.x(), state.y(), oneShotGain, profile.pitchScale());
        }
        if (model.pollStopEvent() && audibility > 0.05f) {
            oneShots.play(sounds.stopPath(), state.x(), state.y(), oneShotGain, profile.pitchScale());
        }
        int trackShot = model.pollTrackOneShot();
        if (trackShot >= 0 && audibility > 0.05f) {
            float dx = state.x() - listenerX;
            float dy = state.y() - listenerY;
            if (dx * dx + dy * dy <= TRACK_ONE_SHOT_RANGE * TRACK_ONE_SHOT_RANGE) {
                oneShots.play(trackOneShots.get(trackShot), state.x(), state.y(),
                        model.trackOneShotGain() * audibility, profile.trackPitch());
            }
        }
    }

    TankType tankType() {
        return tankType;
    }

    /** True once the tank has faded out completely and its sources can be released. */
    boolean isSilent() {
        return audibility < 0.002f;
    }

    void dispose() {
        deleteSources(engineSources);
        deleteSource(trackSlowSource);
        deleteSource(trackFastSource);
        if (lowpassFilter != 0) alDeleteFilters(lowpassFilter);
    }

    private static void deleteSources(int[] sources) {
        for (int source : sources) deleteSource(source);
    }

    private static void deleteSource(int source) {
        if (source == 0) return;
        alSourceStop(source);
        alDeleteSources(source);
    }
}
