package org.chrisgruber.nettank.client.engine.audio;

import java.util.List;

/**
 * How one tank type's engine sounds: which recorded engine family it uses and how that
 * family is tuned (spool speed, pitch, loudness, brightness, track level).
 *
 * @param sounds         the engine family's recordings (shared between variants)
 * @param spoolUpRate    max RPM gain per second (turbines lag, light diesels snap)
 * @param spoolDownRate  max RPM loss per second
 * @param pitchScale     constant pitch multiplier for this variant (heavy &lt; 1, light &gt; 1)
 * @param gain           engine loudness relative to other tank types
 * @param brightness     high-frequency gain 0..1 for the EFX low-pass (1 = unfiltered)
 * @param trackGain      track/suspension layer loudness
 * @param trackPitch     track layer pitch multiplier (lighter chassis rattle higher)
 */
public record EngineSoundProfile(
        EngineSoundSet sounds,
        float spoolUpRate,
        float spoolDownRate,
        float pitchScale,
        float gain,
        float brightness,
        float trackGain,
        float trackPitch
) {
    public EngineSoundProfile withSounds(EngineSoundSet resolved) {
        return new EngineSoundProfile(resolved, spoolUpRate, spoolDownRate, pitchScale, gain, brightness, trackGain, trackPitch);
    }

    /**
     * One seamless engine loop and the engine speed it was recorded at.
     * For diesels the unit is crankshaft RPM; turbines use percent N1 (0-100).
     * {@code path} has no extension: ".ogg" is preferred over ".wav" at load time.
     */
    public record EngineLoop(float recordedRpm, String path) {}

    /**
     * The recordings for one engine family (e.g. diesel V12, gas turbine).
     *
     * @param loops          RPM loops in ascending recordedRpm order
     * @param idleRpm        engine speed at idle, same unit as the loops
     * @param maxRpm         governed max engine speed
     * @param startPath      one-shot played when the engine starts (crank + catch / light-off)
     * @param stopPath       one-shot played when the engine shuts down (rundown)
     * @param startLoopDelay seconds into the start one-shot before the loops fade in
     * @param startFadeTime  seconds for the loops to fade in after that delay
     * @param stopFadeTime   seconds for the loops to fade out when the engine stops
     */
    public record EngineSoundSet(
            String id,
            List<EngineLoop> loops,
            float idleRpm,
            float maxRpm,
            String startPath,
            String stopPath,
            float startLoopDelay,
            float startFadeTime,
            float stopFadeTime
    ) {
        public EngineSoundSet {
            loops = List.copyOf(loops);
        }

        public EngineSoundSet withLoops(List<EngineLoop> availableLoops) {
            return new EngineSoundSet(id, availableLoops, idleRpm, maxRpm, startPath, stopPath,
                    startLoopDelay, startFadeTime, stopFadeTime);
        }

        public float[] recordedRpms() {
            float[] rpms = new float[loops.size()];
            for (int i = 0; i < rpms.length; i++) {
                rpms[i] = loops.get(i).recordedRpm();
            }
            return rpms;
        }
    }
}
