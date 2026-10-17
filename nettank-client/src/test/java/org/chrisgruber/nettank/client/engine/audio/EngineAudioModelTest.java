package org.chrisgruber.nettank.client.engine.audio;

import org.chrisgruber.nettank.common.entities.TankType;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class EngineAudioModelTest {

    private static final float DT = 1.0f / 60.0f;
    private static final float[] DIESEL_RPMS = EngineSoundProfiles.DIESEL_V12.recordedRpms();

    private static EngineAudioModel running(TankType type) {
        return new EngineAudioModel(EngineSoundProfiles.forType(type), 5, true, new Random(1));
    }

    private static void run(EngineAudioModel model, float seconds, boolean running, float throttle, float speed01, float turn01) {
        for (float t = 0; t < seconds; t += DT) {
            model.update(DT, running, throttle, speed01, turn01);
        }
    }

    @Test
    void crossfadeWeightsKeepConstantPowerAcrossTheRange() {
        float[] w = new float[DIESEL_RPMS.length];
        for (float rpm = 500; rpm <= 3000; rpm += 7) {
            EngineAudioModel.crossfadeWeights(rpm, DIESEL_RPMS, EngineAudioModel.CROSSFADE_WIDTH, w);
            float power = 0;
            int nonZero = 0;
            int first = -1;
            for (int i = 0; i < w.length; i++) {
                power += w[i] * w[i];
                if (w[i] > 1e-6f) {
                    nonZero++;
                    if (first < 0) first = i;
                }
            }
            assertEquals(1.0f, power, 1e-4f, "power at rpm " + rpm);
            assertTrue(nonZero <= 2, "at most two loops audible at rpm " + rpm);
            if (nonZero == 2) {
                assertTrue(w[first + 1] > 1e-6f, "audible loops must be neighbours at rpm " + rpm);
            }
        }
    }

    @Test
    void edgeLoopsPlayAloneOutsideTheRecordedRange() {
        float[] w = new float[DIESEL_RPMS.length];
        EngineAudioModel.crossfadeWeights(300, DIESEL_RPMS, EngineAudioModel.CROSSFADE_WIDTH, w);
        assertEquals(1.0f, w[0]);
        EngineAudioModel.crossfadeWeights(4000, DIESEL_RPMS, EngineAudioModel.CROSSFADE_WIDTH, w);
        assertEquals(1.0f, w[w.length - 1]);
    }

    @Test
    void aLoopPlaysAloneAtItsRecordedRpm() {
        float[] w = new float[DIESEL_RPMS.length];
        EngineAudioModel.crossfadeWeights(DIESEL_RPMS[2], DIESEL_RPMS, EngineAudioModel.CROSSFADE_WIDTH, w);
        assertEquals(1.0f, w[2], 1e-6f);
    }

    @Test
    void audibleLoopsArePitchedByRpmOverRecordedRpm() {
        EngineSoundProfile profile = EngineSoundProfiles.forType(TankType.HEAVY);
        EngineAudioModel model = running(TankType.HEAVY);
        for (float throttle : new float[] {0.0f, 0.4f, 1.0f}) {
            run(model, 2.0f, true, throttle, throttle, 0);
            for (int i = 0; i < model.loopCount(); i++) {
                float pitch = model.loopPitch(i);
                assertTrue(pitch >= EngineAudioModel.MIN_PITCH * profile.pitchScale() - 1e-6f);
                assertTrue(pitch <= EngineAudioModel.MAX_PITCH * profile.pitchScale() + 1e-6f);
                if (model.loopGain(i) > 0.01f) {
                    float expected = model.rpm() / DIESEL_RPMS[i] * profile.pitchScale();
                    assertEquals(expected, pitch, 1e-4f, "loop " + i + " at throttle " + throttle);
                }
            }
        }
    }

    @Test
    void rpmNeverRisesFasterThanTheSpoolRate() {
        EngineSoundProfile profile = EngineSoundProfiles.forType(TankType.HEAVY);
        EngineAudioModel model = running(TankType.HEAVY);
        float idle = profile.sounds().idleRpm();
        model.update(0.1f, true, 1.0f, 1.0f, 0);
        assertTrue(model.rpm() - idle <= profile.spoolUpRate() * 0.1f + 1e-3f);

        run(model, 5.0f, true, 1.0f, 1.0f, 0);
        float cruise = idle + (profile.sounds().maxRpm() - idle) * EngineAudioModel.CRUISE_FRACTION;
        assertEquals(cruise, model.rpm(), cruise * 0.01f);
    }

    @Test
    void turbineSpoolsUpSlowerThanALightDiesel() {
        EngineAudioModel turbine = running(TankType.STANDARD);
        EngineAudioModel diesel = running(TankType.LIGHT);
        run(turbine, 0.5f, true, 1.0f, 1.0f, 0);
        run(diesel, 0.5f, true, 1.0f, 1.0f, 0);
        assertTrue(fractionOfRange(turbine, TankType.STANDARD) < fractionOfRange(diesel, TankType.LIGHT) * 0.6f);
    }

    private static float fractionOfRange(EngineAudioModel model, TankType type) {
        EngineSoundProfile.EngineSoundSet sounds = EngineSoundProfiles.forType(type).sounds();
        return (model.rpm() - sounds.idleRpm()) / (sounds.maxRpm() - sounds.idleRpm());
    }

    @Test
    void startingFiresOnceAndFadesLoopsInAfterTheStartDelay() {
        EngineSoundProfile.EngineSoundSet sounds = EngineSoundProfiles.DIESEL_V12;
        EngineAudioModel model = new EngineAudioModel(EngineSoundProfiles.forType(TankType.HEAVY), 0, false, new Random(1));
        assertEquals(EngineAudioModel.Phase.OFF, model.phase());

        model.update(DT, true, 0, 0, 0);
        assertTrue(model.pollStartEvent());
        assertFalse(model.pollStartEvent(), "event is consumed");
        assertEquals(EngineAudioModel.Phase.STARTING, model.phase());

        run(model, sounds.startLoopDelay() - 0.1f, true, 0, 0, 0);
        assertEquals(0.0f, totalLoopGain(model), 1e-6f, "loops stay silent while the start one-shot plays");

        run(model, sounds.startFadeTime() + 0.3f, true, 0, 0, 0);
        assertEquals(EngineAudioModel.Phase.RUNNING, model.phase());
        assertTrue(totalLoopGain(model) > 0.1f);
        assertFalse(model.pollStartEvent());
    }

    @Test
    void stoppingFiresTheRundownAndGoesSilent() {
        EngineAudioModel model = running(TankType.STANDARD);
        run(model, 0.5f, true, 0, 0, 0);
        assertTrue(totalLoopGain(model) > 0.1f);

        model.update(DT, false, 0, 0, 0);
        assertTrue(model.pollStopEvent());
        run(model, EngineSoundProfiles.TURBINE.stopFadeTime() + 0.2f, false, 0, 0, 0);
        assertEquals(EngineAudioModel.Phase.OFF, model.phase());
        assertEquals(0.0f, totalLoopGain(model), 1e-6f);
    }

    private static float totalLoopGain(EngineAudioModel model) {
        float sum = 0;
        for (int i = 0; i < model.loopCount(); i++) sum += model.loopGain(i);
        return sum;
    }

    @Test
    void remoteTanksRevFromMeasuredMovement() {
        float idle = EngineSoundProfiles.DIESEL_V12.idleRpm();
        EngineAudioModel parked = running(TankType.HEAVY);
        run(parked, 2.0f, true, -1.0f, 0, 0);
        assertEquals(idle, parked.rpm(), 1.0f);

        EngineAudioModel driving = running(TankType.HEAVY);
        run(driving, 2.0f, true, -1.0f, 1.0f, 0);
        assertTrue(driving.rpm() > idle + 800);
    }

    @Test
    void tracksAreSilentParkedAndShiftToTheFastLoopAtSpeed() {
        EngineAudioModel model = running(TankType.HEAVY);
        run(model, 1.0f, true, 0, 0, 0);
        assertEquals(0.0f, model.trackSlowGain(), 1e-6f);
        assertEquals(0.0f, model.trackFastGain(), 1e-6f);

        run(model, 1.0f, true, 1.0f, 0.2f, 0);
        assertTrue(model.trackSlowGain() > model.trackFastGain());

        run(model, 1.0f, true, 1.0f, 1.0f, 0);
        assertTrue(model.trackFastGain() > model.trackSlowGain());
    }

    @Test
    void trackOneShotsOnlyPlayWhileCrawling() {
        EngineAudioModel crawling = running(TankType.HEAVY);
        int crawlShots = countTrackShots(crawling, 0.2f);
        assertTrue(crawlShots >= 3, "expected several squeaks/clanks while crawling, got " + crawlShots);

        EngineAudioModel fast = running(TankType.HEAVY);
        assertEquals(0, countTrackShots(fast, 1.0f));
    }

    private static int countTrackShots(EngineAudioModel model, float speed01) {
        int shots = 0;
        for (float t = 0; t < 10.0f; t += DT) {
            model.update(DT, true, 1.0f, speed01, 0);
            int shot = model.pollTrackOneShot();
            if (shot >= 0) {
                shots++;
                assertTrue(shot < 5);
            }
        }
        return shots;
    }
}
