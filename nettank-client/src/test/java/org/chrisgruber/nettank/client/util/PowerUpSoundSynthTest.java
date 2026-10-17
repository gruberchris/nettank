package org.chrisgruber.nettank.client.util;

import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class PowerUpSoundSynthTest {

    @Test
    void everySoundIsShortAudibleAndFinite() {
        @SuppressWarnings("unchecked")
        Supplier<float[]>[] sounds = new Supplier[] {
                PowerUpSoundSynth::spawn, PowerUpSoundSynth::pickupDamage, PowerUpSoundSynth::pickupReload,
                PowerUpSoundSynth::pickupSpeed, PowerUpSoundSynth::pickupAmmo, PowerUpSoundSynth::pickupArmor,
                PowerUpSoundSynth::pickupHealth, PowerUpSoundSynth::tier3Sparkle, PowerUpSoundSynth::expire,
                PowerUpSoundSynth::buffEnd, PowerUpSoundSynth::buffWarning};
        for (Supplier<float[]> sound : sounds) {
            float[] clip = sound.get();
            assertTrue(clip.length > 0 && clip.length < EngineSoundSynth.SAMPLE_RATE, "under a second");
            for (float s : clip) assertTrue(Float.isFinite(s));
            assertTrue(PowerUpSoundSynth.rms(clip) > 0.01f, "audible");
        }
    }

    // Arcade sounds should be over quickly: the last 10% of each clip is near-silent
    @Test
    void soundsDecayBeforeTheirEnd() {
        float[] clip = PowerUpSoundSynth.pickupDamage();
        float peakTail = 0.0f;
        float peak = 0.0f;
        for (int i = 0; i < clip.length; i++) {
            peak = Math.max(peak, Math.abs(clip[i]));
            if (i > clip.length * 0.9) peakTail = Math.max(peakTail, Math.abs(clip[i]));
        }
        assertTrue(peakTail < peak * 0.1f, "tail " + peakTail + " vs peak " + peak);
    }
}
