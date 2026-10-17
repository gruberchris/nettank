package org.chrisgruber.nettank.client.engine.audio;

import java.util.Random;

/**
 * Engine sound state for one tank, free of OpenAL calls so it can be unit tested.
 *
 * Each update turns throttle and measured movement into a smoothed engine RPM, then into
 * per-loop gains (an equal-power crossfade between the two loops recorded nearest that
 * RPM) and pitches (rpm / recordedRpm), so no single recording is ever pitched far from
 * the speed it was made at. It also drives the track layer, the start/stop phases and
 * random track one-shots, which the OpenAL emitter reads back.
 */
public final class EngineAudioModel {

    public enum Phase { OFF, STARTING, RUNNING, STOPPING }

    // Fraction of the idle-to-max range a tank cruises at under full throttle
    static final float CRUISE_FRACTION = 0.85f;
    // Exponential approach rate toward the target RPM (1/s); spool rates cap the step
    static final float RPM_RESPONSE = 6.0f;
    // Portion of each log-RPM gap spent crossfading; the rest plays one loop alone
    static final float CROSSFADE_WIDTH = 0.6f;
    static final float MIN_PITCH = 0.5f;
    static final float MAX_PITCH = 2.0f;
    private static final float LOAD_RESPONSE = 8.0f;
    // Engine speed (fraction of idle) the start begins at and the rundown falls toward
    private static final float CRANK_RPM_FRACTION = 0.75f;
    private static final float RUNDOWN_RPM_FRACTION = 0.6f;
    // Normalized ground speeds the track loops were recorded at
    static final float TRACK_SLOW_SPEED = 0.35f;
    static final float TRACK_FAST_SPEED = 1.0f;

    private final EngineSoundProfile profile;
    private final float[] recordedRpms;
    private final float[] weights;
    private final float[] loopGains;
    private final float[] loopPitches;
    private final int trackOneShotCount;
    private final Random random;

    private Phase phase;
    private float phaseTime;
    private float rpm;
    private float load;
    private float engineFade;
    private float fadeAtStop;

    private float trackSlowGain;
    private float trackFastGain;
    private float trackSlowPitch = 1.0f;
    private float trackFastPitch = 1.0f;
    private float brightness = 1.0f;

    private float oneShotTimer;
    private boolean startEvent;
    private boolean stopEvent;
    private int trackOneShotEvent = -1;
    private float trackOneShotGain;

    /**
     * @param startRunning true when the tank is first heard with its engine already on
     *                     (joining mid-round, coming into view): skip the start sequence
     */
    public EngineAudioModel(EngineSoundProfile profile, int trackOneShotCount, boolean startRunning, Random random) {
        this.profile = profile;
        this.recordedRpms = profile.sounds().recordedRpms();
        this.weights = new float[recordedRpms.length];
        this.loopGains = new float[recordedRpms.length];
        this.loopPitches = new float[recordedRpms.length];
        this.trackOneShotCount = trackOneShotCount;
        this.random = random;
        this.oneShotTimer = 0.5f + random.nextFloat();

        float idle = profile.sounds().idleRpm();
        if (startRunning) {
            phase = Phase.RUNNING;
            rpm = idle;
            engineFade = 1.0f;
        } else {
            phase = Phase.OFF;
            rpm = idle * RUNDOWN_RPM_FRACTION;
            engineFade = 0.0f;
        }
        computeLoopOutputs();
    }

    /**
     * @param running  engine should be on (tank alive, round active)
     * @param throttle 0..1 driver demand, or negative to estimate it from movement (remote tanks)
     * @param speed01  ground speed as a fraction of the tank type's move speed
     * @param turn01   hull turn rate as a fraction of the tank type's turn speed
     */
    public void update(float dt, boolean running, float throttle, float speed01, float turn01) {
        if (dt <= 0.0f) return;
        speed01 = clamp(speed01, 0.0f, 1.5f);
        turn01 = clamp(turn01, 0.0f, 1.5f);

        updatePhase(dt, running);

        boolean engineOn = phase == Phase.STARTING || phase == Phase.RUNNING;
        float demand = throttle >= 0.0f ? clamp(throttle, 0.0f, 1.0f) : estimateDemand(speed01, turn01);
        if (phase != Phase.RUNNING) demand = 0.0f; // starting settles at idle; stopping runs down

        EngineSoundProfile.EngineSoundSet sounds = profile.sounds();
        float idle = sounds.idleRpm();
        float targetRpm = engineOn
                ? idle + (sounds.maxRpm() - idle) * CRUISE_FRACTION * demand
                : idle * RUNDOWN_RPM_FRACTION;
        rpm = approach(rpm, targetRpm, dt, profile.spoolUpRate(), profile.spoolDownRate());

        float loadTarget = engineOn && demand > 0.05f ? 0.4f + 0.6f * demand : 0.0f;
        load += (loadTarget - load) * (1.0f - (float) Math.exp(-dt * LOAD_RESPONSE));

        engineFade = switch (phase) {
            case STARTING -> clamp((phaseTime - sounds.startLoopDelay()) / Math.max(sounds.startFadeTime(), 0.001f), 0.0f, 1.0f);
            case RUNNING -> 1.0f;
            case STOPPING -> fadeAtStop * clamp(1.0f - phaseTime / Math.max(sounds.stopFadeTime(), 0.001f), 0.0f, 1.0f);
            case OFF -> 0.0f;
        };

        computeLoopOutputs();
        float trackSpeed = clamp(Math.max(speed01, turn01 * 0.6f), 0.0f, 1.3f);
        computeTrackOutputs(trackSpeed);
        updateTrackOneShots(dt, trackSpeed);
    }

    private void updatePhase(float dt, boolean running) {
        phaseTime += dt;
        EngineSoundProfile.EngineSoundSet sounds = profile.sounds();
        switch (phase) {
            case OFF -> {
                if (running) enterStarting();
            }
            case STARTING -> {
                if (!running) enterStopping();
                else if (phaseTime >= sounds.startLoopDelay() + sounds.startFadeTime()) enter(Phase.RUNNING);
            }
            case RUNNING -> {
                if (!running) enterStopping();
            }
            case STOPPING -> {
                if (running) enterStarting();
                else if (phaseTime >= sounds.stopFadeTime()) enter(Phase.OFF);
            }
        }
    }

    private void enterStarting() {
        rpm = Math.max(rpm, profile.sounds().idleRpm() * CRANK_RPM_FRACTION);
        startEvent = true;
        enter(Phase.STARTING);
    }

    private void enterStopping() {
        fadeAtStop = engineFade;
        stopEvent = true;
        enter(Phase.STOPPING);
    }

    private void enter(Phase next) {
        phase = next;
        phaseTime = 0.0f;
    }

    // Remote tanks only report positions, so driver demand is inferred from motion
    static float estimateDemand(float speed01, float turn01) {
        return clamp(Math.max(speed01 / 0.85f, turn01 * 0.7f), 0.0f, 1.0f);
    }

    private void computeLoopOutputs() {
        crossfadeWeights(rpm, recordedRpms, CROSSFADE_WIDTH, weights);
        float level = engineFade * profile.gain() * (0.7f + 0.3f * load);
        for (int i = 0; i < recordedRpms.length; i++) {
            loopGains[i] = weights[i] * level;
            loopPitches[i] = clamp(rpm / recordedRpms[i], MIN_PITCH, MAX_PITCH) * profile.pitchScale();
        }
        // Coasting and idling sound duller than an engine under load
        brightness = clamp(profile.brightness() * (0.6f + 0.4f * load), 0.05f, 1.0f);
    }

    private void computeTrackOutputs(float trackSpeed) {
        float level = profile.trackGain() * smoothstep(0.02f, 0.2f, trackSpeed)
                * (0.55f + 0.45f * Math.min(trackSpeed, 1.0f));
        float x = clamp((trackSpeed - 0.3f) / 0.45f, 0.0f, 1.0f);
        trackSlowGain = level * (float) Math.cos(x * Math.PI * 0.5);
        trackFastGain = level * (float) Math.sin(x * Math.PI * 0.5);
        trackSlowPitch = clamp(trackSpeed / TRACK_SLOW_SPEED, 0.6f, 1.6f) * profile.trackPitch();
        trackFastPitch = clamp(trackSpeed / TRACK_FAST_SPEED, 0.7f, 1.3f) * profile.trackPitch();
    }

    // Occasional squeaks and clanks while crawling; they blur into the loop at speed
    private void updateTrackOneShots(float dt, float trackSpeed) {
        if (trackOneShotCount <= 0 || trackSpeed < 0.04f || trackSpeed > 0.8f) return;
        oneShotTimer -= dt;
        if (oneShotTimer <= 0.0f) {
            trackOneShotEvent = random.nextInt(trackOneShotCount);
            trackOneShotGain = profile.trackGain() * (0.35f + 0.25f * random.nextFloat());
            oneShotTimer = 0.9f + 2.2f * random.nextFloat();
        }
    }

    /**
     * Fills {@code out} with equal-power crossfade weights (sum of squares = 1) for the
     * loops on either side of {@code rpm}, interpolating in log-RPM so spacing follows
     * pitch ratios. Below the lowest or above the highest loop, that loop plays alone.
     */
    static void crossfadeWeights(float rpm, float[] recordedRpms, float width, float[] out) {
        int n = recordedRpms.length;
        for (int i = 0; i < n; i++) out[i] = 0.0f;
        if (n == 0) return;
        if (rpm <= recordedRpms[0]) {
            out[0] = 1.0f;
            return;
        }
        if (rpm >= recordedRpms[n - 1]) {
            out[n - 1] = 1.0f;
            return;
        }
        int lower = 0;
        while (lower < n - 2 && rpm >= recordedRpms[lower + 1]) lower++;
        double a = Math.log(recordedRpms[lower]);
        double b = Math.log(recordedRpms[lower + 1]);
        float t = (float) ((Math.log(rpm) - a) / (b - a));
        float w = clamp(width, 0.01f, 1.0f);
        float x = clamp((t - (1.0f - w) * 0.5f) / w, 0.0f, 1.0f);
        out[lower] = (float) Math.cos(x * Math.PI * 0.5);
        out[lower + 1] = (float) Math.sin(x * Math.PI * 0.5);
    }

    // Eases toward target, never faster than the engine can spool up or down
    static float approach(float current, float target, float dt, float upRate, float downRate) {
        float delta = target - current;
        float step = delta * (1.0f - (float) Math.exp(-dt * RPM_RESPONSE));
        float maxStep = (delta > 0.0f ? upRate : downRate) * dt;
        if (Math.abs(step) > maxStep) step = Math.copySign(maxStep, step);
        return current + step;
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    public boolean pollStartEvent() {
        boolean fired = startEvent;
        startEvent = false;
        return fired;
    }

    public boolean pollStopEvent() {
        boolean fired = stopEvent;
        stopEvent = false;
        return fired;
    }

    /** Index into the track one-shot list to play now, or -1. */
    public int pollTrackOneShot() {
        int fired = trackOneShotEvent;
        trackOneShotEvent = -1;
        return fired;
    }

    public float trackOneShotGain() { return trackOneShotGain; }
    public Phase phase() { return phase; }
    public float rpm() { return rpm; }
    public float load() { return load; }
    public int loopCount() { return recordedRpms.length; }
    public float loopGain(int i) { return loopGains[i]; }
    public float loopPitch(int i) { return loopPitches[i]; }
    public float trackSlowGain() { return trackSlowGain; }
    public float trackFastGain() { return trackFastGain; }
    public float trackSlowPitch() { return trackSlowPitch; }
    public float trackFastPitch() { return trackFastPitch; }
    public float brightness() { return brightness; }
}
