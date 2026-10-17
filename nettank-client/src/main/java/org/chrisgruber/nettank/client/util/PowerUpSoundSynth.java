package org.chrisgruber.nettank.client.util;

import org.chrisgruber.nettank.client.game.PowerUpStyle;
import org.chrisgruber.nettank.client.util.EngineSoundSynth.Biquad;
import org.chrisgruber.nettank.client.util.EngineSoundSynth.Resonator;
import org.chrisgruber.nettank.common.entities.PowerUpType;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * Generates the arcade-style power-up sounds under sounds/powerups/: a spawn chime, one
 * pickup per category, an extra sparkle for x3 pickups, an expiry puff, the boost
 * power-down and its countdown tick. Synthesized from scratch, so original nettank work.
 * Like EngineSoundSynth it never replaces an existing .ogg and keeps existing .wav files
 * unless run with --force. Run from the repository root:
 *
 *   java -cp nettank-client/target/classes:nettank-common/target/classes:<joml jar> \
 *       org.chrisgruber.nettank.client.util.PowerUpSoundSynth [--force]
 */
public final class PowerUpSoundSynth {

    private static final int SR = EngineSoundSynth.SAMPLE_RATE;
    private static final float PEAK = 0.89f;

    private PowerUpSoundSynth() {}

    public static void main(String[] args) throws IOException {
        boolean force = Arrays.asList(args).contains("--force");

        // Pickups are matched in loudness (RMS) and normalized together, so no category is quieter
        Map<String, float[]> pickups = new LinkedHashMap<>();
        pickups.put(PowerUpStyle.pickupSound(PowerUpType.DAMAGE_2X), pickupDamage());
        pickups.put(PowerUpStyle.pickupSound(PowerUpType.RELOAD_2X), pickupReload());
        pickups.put(PowerUpStyle.pickupSound(PowerUpType.SPEED_2X), pickupSpeed());
        pickups.put(PowerUpStyle.pickupSound(PowerUpType.UNLIMITED_AMMO), pickupAmmo());
        pickups.put(PowerUpStyle.pickupSound(PowerUpType.REPAIR_ARMOR), pickupArmor());
        pickups.put(PowerUpStyle.pickupSound(PowerUpType.REPAIR_HP), pickupHealth());
        for (float[] clip : pickups.values()) {
            scale(clip, 0.1f / rms(clip));
        }
        EngineSoundSynth.writeGroup(pickups, PEAK, force);

        Map<String, float[]> clips = new LinkedHashMap<>();
        clips.put(PowerUpStyle.SOUND_SPAWN, spawn());
        clips.put(PowerUpStyle.SOUND_TIER3_SPARKLE, tier3Sparkle());
        clips.put(PowerUpStyle.SOUND_EXPIRE, expire());
        clips.put(PowerUpStyle.SOUND_BUFF_END, buffEnd());
        clips.put(PowerUpStyle.SOUND_BUFF_WARNING, buffWarning());
        // Each clip is normalized on its own; the game sets their relative levels
        for (Map.Entry<String, float[]> clip : clips.entrySet()) {
            EngineSoundSynth.writeGroup(Map.of(clip.getKey(), clip.getValue()), PEAK, force);
        }
        System.out.println("Power-up sounds done.");
    }

    // Two-note bell "bling" with a little shimmer
    static float[] spawn() {
        float[] out = buffer(0.45);
        bell(out, 0.0, midi(88), 0.12, 0.5);
        bell(out, 0.07, midi(95), 0.16, 0.6);
        shimmer(out, 0.0, 0.3, 0.12, 51L);
        return out;
    }

    // Low punch, then a fast rising square arpeggio that rings on its top note
    static float[] pickupDamage() {
        float[] out = buffer(0.55);
        thump(out, 0.0, 95.0, 45.0, 0.07, 0.9);
        int[] notes = {67, 71, 74, 79};
        for (int i = 0; i < notes.length; i++) {
            boolean last = i == notes.length - 1;
            square(out, 0.02 + i * 0.045, midi(notes[i]), last ? 0.22 : 0.05, 0.32, 9);
        }
        return out;
    }

    // Two breech clacks, then a rising two-note confirm
    static float[] pickupReload() {
        float[] out = buffer(0.5);
        clack(out, 0.0, 2300.0, 52L);
        clack(out, 0.08, 1900.0, 53L);
        square(out, 0.13, midi(74), 0.06, 0.3, 7);
        square(out, 0.2, midi(81), 0.2, 0.32, 7);
        return out;
    }

    // Rising whoosh with a glide tone on top
    static float[] pickupSpeed() {
        float[] out = buffer(0.6);
        Random rnd = new Random(54L);
        Biquad sweep = Biquad.bandpass(500.0, 1.2);
        double phase = 0.0;
        for (int i = 0; i < out.length; i++) {
            double t = time(i);
            double p = Math.min(1.0, t / 0.45);
            if (i % 32 == 0) sweep.setBandpass(500.0 * Math.pow(10.0, p), 1.2);
            double env = Math.sin(Math.PI * Math.min(1.0, t / 0.5));
            double whoosh = sweep.process(white(rnd)) * 1.6 * env;
            phase = wrap(phase + 400.0 * Math.pow(4.0, Math.min(1.0, t / 0.35)) / SR);
            double glide = triangle(phase) * 0.35 * env * Math.exp(-Math.max(0.0, t - 0.3) / 0.12);
            out[i] += (float) (whoosh + glide);
        }
        return out;
    }

    // Cash-register "ka-ching": a click, a bright bell, and a few coin plinks
    static float[] pickupAmmo() {
        float[] out = buffer(0.7);
        clack(out, 0.0, 1500.0, 55L);
        double[] ratios = {1.0, 2.0, 2.76, 5.4};
        double[] decays = {0.35, 0.22, 0.16, 0.08};
        for (int k = 0; k < ratios.length; k++) {
            partial(out, 0.05, midi(91) * ratios[k], decays[k], 0.3 / (k + 1));
            partial(out, 0.05, midi(91) * ratios[k] * 1.003, decays[k], 0.2 / (k + 1)); // beating shimmer
        }
        Random rnd = new Random(56L);
        for (int c = 0; c < 3; c++) {
            bell(out, 0.12 + c * 0.07 + rnd.nextDouble() * 0.02, midi(96 + c * 2), 0.06, 0.25);
        }
        return out;
    }

    // Metal plate clunk, then a rising fifth with vibrato
    static float[] pickupArmor() {
        float[] out = buffer(0.6);
        Resonator[] plate = {new Resonator(320.0, 0.07), new Resonator(870.0, 0.05), new Resonator(1650.0, 0.03)};
        double[] amps = {0.9, 0.6, 0.4};
        for (int i = 0; i < out.length; i++) {
            double hit = i == 0 ? 1.0 : 0.0;
            double s = 0.0;
            for (int m = 0; m < plate.length; m++) s += plate[m].process(hit * amps[m]);
            out[i] += (float) (s * 0.8);
        }
        vibratoTone(out, 0.1, midi(69), 0.12, 0.3);
        vibratoTone(out, 0.2, midi(76), 0.3, 0.34);
        shimmer(out, 0.2, 0.3, 0.06, 57L);
        return out;
    }

    // Warm major-seventh arpeggio with a chorused voice
    static float[] pickupHealth() {
        float[] out = buffer(0.75);
        int[] notes = {72, 76, 79, 83};
        for (int i = 0; i < notes.length; i++) {
            double start = i * 0.07;
            double hz = midi(notes[i]);
            softNote(out, start, hz, 0.28, 0.22);
            softNote(out, start, hz * 1.0023, 0.28, 0.14); // ~4 cents sharp: chorus
        }
        shimmer(out, 0.15, 0.4, 0.05, 58L);
        return out;
    }

    // Quick pentatonic run of high bell plinks, layered over x3 pickups
    static float[] tier3Sparkle() {
        float[] out = buffer(0.75);
        int[] scale = {96, 98, 100, 103, 105, 108, 110, 112};
        for (int i = 0; i < scale.length; i++) {
            bell(out, 0.06 + i * 0.035, midi(scale[i]), 0.1, 0.22);
        }
        shimmer(out, 0.05, 0.5, 0.08, 59L);
        return out;
    }

    // Puff of air closing down, with a falling blip
    static float[] expire() {
        float[] out = buffer(0.5);
        Random rnd = new Random(60L);
        Biquad lp = Biquad.lowpass(3000.0, 0.7);
        double phase = 0.0;
        for (int i = 0; i < out.length; i++) {
            double t = time(i);
            if (i % 32 == 0) lp.setLowpass(3000.0 * Math.pow(0.1, Math.min(1.0, t / 0.3)), 0.7);
            double env = Math.min(1.0, t / 0.01) * Math.exp(-t / 0.12);
            double puff = lp.process(white(rnd)) * 1.2 * env;
            phase = wrap(phase + 900.0 * Math.pow(1.0 / 3.0, Math.min(1.0, t / 0.25)) / SR);
            double blip = Math.sin(2.0 * Math.PI * phase) * 0.3 * Math.exp(-t / 0.1);
            out[i] += (float) (puff + blip);
        }
        return out;
    }

    // Falling square arpeggio whose tone closes down: the boost wearing off
    static float[] buffEnd() {
        float[] out = buffer(0.65);
        int[] notes = {79, 76, 72, 67};
        for (int i = 0; i < notes.length; i++) {
            boolean last = i == notes.length - 1;
            int harmonics = 9 - i * 2; // fewer harmonics on each step: duller as it drops
            square(out, i * 0.08, midi(notes[i]) * (last ? 0.98 : 1.0), last ? 0.22 : 0.07, 0.3, harmonics);
        }
        return out;
    }

    // Short countdown tick before a boost runs out
    static float[] buffWarning() {
        float[] out = buffer(0.08);
        partial(out, 0.0, 1760.0, 0.025, 0.6);
        partial(out, 0.0, 3520.0, 0.012, 0.25);
        return out;
    }

    // --- Building blocks --------------------------------------------------------------------

    static float rms(float[] clip) {
        double sum = 0.0;
        for (float v : clip) sum += v * v;
        return (float) Math.sqrt(sum / Math.max(1, clip.length));
    }

    private static void scale(float[] clip, float gain) {
        for (int i = 0; i < clip.length; i++) clip[i] *= gain;
    }

    private static void bell(float[] out, double start, double hz, double decay, double gain) {
        partial(out, start, hz, decay, gain);
        partial(out, start, hz * 2.76, decay * 0.5, gain * 0.3);
        partial(out, start, hz * 5.4, decay * 0.25, gain * 0.12);
    }

    private static void partial(float[] out, double start, double hz, double decay, double gain) {
        int from = (int) (start * SR);
        for (int i = from; i < out.length; i++) {
            double t = (i - from) / (double) SR;
            double env = Math.min(1.0, t / 0.002) * Math.exp(-t / decay);
            if (env < 1e-4 && t > 0.01) break;
            out[i] += (float) (Math.sin(2.0 * Math.PI * hz * t) * env * gain);
        }
    }

    // Band-limited square (odd harmonics up to the given count) with a short attack
    private static void square(float[] out, double start, double hz, double decay, double gain, int harmonics) {
        int from = (int) (start * SR);
        for (int i = from; i < out.length; i++) {
            double t = (i - from) / (double) SR;
            double env = Math.min(1.0, t / 0.003) * Math.exp(-t / decay);
            if (env < 1e-4 && t > 0.01) break;
            double s = 0.0;
            for (int k = 1; k <= harmonics * 2 - 1; k += 2) {
                if (hz * k > SR * 0.45) break;
                s += Math.sin(2.0 * Math.PI * hz * k * t) / k;
            }
            out[i] += (float) (s * env * gain);
        }
    }

    private static void softNote(float[] out, double start, double hz, double decay, double gain) {
        int from = (int) (start * SR);
        for (int i = from; i < out.length; i++) {
            double t = (i - from) / (double) SR;
            double env = Math.min(1.0, t / 0.012) * Math.exp(-t / decay);
            if (env < 1e-4 && t > 0.02) break;
            double s = Math.sin(2.0 * Math.PI * hz * t) + 0.25 * Math.sin(4.0 * Math.PI * hz * t);
            out[i] += (float) (s * env * gain);
        }
    }

    private static void vibratoTone(float[] out, double start, double hz, double decay, double gain) {
        int from = (int) (start * SR);
        double phase = 0.0;
        for (int i = from; i < out.length; i++) {
            double t = (i - from) / (double) SR;
            double env = Math.min(1.0, t / 0.01) * Math.exp(-t / decay);
            if (env < 1e-4 && t > 0.02) break;
            phase = wrap(phase + hz * (1.0 + 0.012 * Math.sin(2.0 * Math.PI * 7.0 * t)) / SR);
            out[i] += (float) (triangle(phase) * env * gain);
        }
    }

    // Pitch-dropping sine punch
    private static void thump(float[] out, double start, double fromHz, double toHz, double decay, double gain) {
        int from = (int) (start * SR);
        double phase = 0.0;
        for (int i = from; i < out.length; i++) {
            double t = (i - from) / (double) SR;
            double env = Math.min(1.0, t / 0.002) * Math.exp(-t / decay);
            if (env < 1e-4 && t > 0.01) break;
            phase = wrap(phase + (toHz + (fromHz - toHz) * Math.exp(-t / 0.03)) / SR);
            out[i] += (float) (Math.sin(2.0 * Math.PI * phase) * env * gain);
        }
    }

    // Short resonant noise click
    private static void clack(float[] out, double start, double hz, long seed) {
        Random rnd = new Random(seed);
        Biquad body = Biquad.bandpass(hz, 4.0);
        int from = (int) (start * SR);
        for (int i = from; i < Math.min(out.length, from + (int) (0.05 * SR)); i++) {
            double t = (i - from) / (double) SR;
            out[i] += (float) (body.process(white(rnd)) * 3.0 * Math.exp(-t / 0.008));
        }
    }

    // High airy sparkle noise
    private static void shimmer(float[] out, double start, double length, double gain, long seed) {
        Random rnd = new Random(seed);
        Biquad hp = Biquad.highpass(6000.0, 0.7);
        int from = (int) (start * SR);
        int to = Math.min(out.length, from + (int) (length * SR));
        for (int i = from; i < to; i++) {
            double t = (i - from) / (double) SR;
            double env = Math.sin(Math.PI * t / length);
            double grain = rnd.nextDouble() < 0.02 ? 1.0 : 0.25; // sparse glints
            out[i] += (float) (hp.process(white(rnd)) * env * gain * grain * 3.0);
        }
    }

    private static float[] buffer(double seconds) {
        return new float[(int) Math.round(seconds * SR)];
    }

    private static double midi(int note) {
        return 440.0 * Math.pow(2.0, (note - 69) / 12.0);
    }

    private static double triangle(double phase) {
        return 4.0 * Math.abs(phase - 0.5) - 1.0;
    }

    private static double time(int i) {
        return (double) i / SR;
    }

    private static double white(Random rnd) {
        return rnd.nextDouble() * 2.0 - 1.0;
    }

    private static double wrap(double phase) {
        return phase - Math.floor(phase);
    }
}
