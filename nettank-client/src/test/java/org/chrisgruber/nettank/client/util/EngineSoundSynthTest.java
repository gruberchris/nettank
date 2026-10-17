package org.chrisgruber.nettank.client.util;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class EngineSoundSynthTest {

    private static final int SR = EngineSoundSynth.SAMPLE_RATE;

    @Test
    void dieselLoopsSpanWholeEngineCycles() {
        for (float rpm : new float[] {800f, 1500f, 2600f}) {
            float[] loop = EngineSoundSynth.dieselLoop(rpm);
            double cycleSamples = 120.0 / rpm * SR;
            double cycles = loop.length / cycleSamples;
            assertEquals(Math.round(cycles), cycles, 1.0 / cycleSamples + 1e-6, "rpm " + rpm);
        }
    }

    @Test
    void loopsWrapWithoutAClick() {
        assertSeamless("diesel 800", EngineSoundSynth.dieselLoop(800f));
        assertSeamless("diesel 2600", EngineSoundSynth.dieselLoop(2600f));
        assertSeamless("turbine 60%", EngineSoundSynth.turbineLoop(0.6));
        assertSeamless("tracks slow", EngineSoundSynth.trackLoop(false));
        assertSeamless("tracks fast", EngineSoundSynth.trackLoop(true));
    }

    // The jump from the last sample back to the first must look like any other step in the clip
    private static void assertSeamless(String label, float[] loop) {
        float[] steps = new float[loop.length - 1];
        for (int i = 1; i < loop.length; i++) steps[i - 1] = Math.abs(loop[i] - loop[i - 1]);
        Arrays.sort(steps);
        float typicalMax = steps[(int) (steps.length * 0.999)];
        float seam = Math.abs(loop[0] - loop[loop.length - 1]);
        assertTrue(seam <= typicalMax, label + ": seam step " + seam + " exceeds 99.9th percentile " + typicalMax);
    }

    @Test
    void clipsAreFiniteAndWithinFullScale() {
        float[][] clips = {
                EngineSoundSynth.dieselStart(), EngineSoundSynth.dieselStop(),
                EngineSoundSynth.turbineStart(), EngineSoundSynth.turbineStop(),
                EngineSoundSynth.squeak(1), EngineSoundSynth.clank(2)};
        for (float[] clip : clips) {
            assertTrue(clip.length > SR / 10);
            for (float s : clip) {
                assertTrue(Float.isFinite(s) && Math.abs(s) <= 1.0f);
            }
        }
    }

    @Test
    void wavBytesAreMono16BitPcm() {
        float[] samples = {0.0f, 0.5f, -0.5f, 1.0f};
        ByteBuffer wav = ByteBuffer.wrap(EngineSoundSynth.wavBytes(samples, 1.0f)).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(44 + samples.length * 2, wav.capacity());
        assertEquals("RIFF", new String(wav.array(), 0, 4));
        assertEquals("WAVE", new String(wav.array(), 8, 4));
        assertEquals(1, wav.getShort(22));      // channels
        assertEquals(SR, wav.getInt(24));       // sample rate
        assertEquals(16, wav.getShort(34));     // bits per sample
        assertEquals(32767, wav.getShort(44 + 3 * 2));
    }
}
