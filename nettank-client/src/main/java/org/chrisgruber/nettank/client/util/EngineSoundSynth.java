package org.chrisgruber.nettank.client.util;

import org.chrisgruber.nettank.client.engine.audio.EngineSoundProfile.EngineLoop;
import org.chrisgruber.nettank.client.engine.audio.EngineSoundProfile.EngineSoundSet;
import org.chrisgruber.nettank.client.engine.audio.EngineSoundProfiles;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.DoubleUnaryOperator;
import java.util.function.IntFunction;

/**
 * Generates the placeholder engine and track sounds under sounds/engines/ and sounds/tracks/.
 * Everything is synthesized from scratch (no samples), so the output is original nettank work.
 *
 * These are stand-ins until real recordings are sourced (docs/audio/engine_audio.md). The
 * generator never touches a sound that already has an .ogg (a sourced recording), and skips
 * existing .wav placeholders unless run with --force. Run from the repository root:
 *
 *   java -cp nettank-client/target/classes:nettank-common/target/classes \
 *       org.chrisgruber.nettank.client.util.EngineSoundSynth [--force]
 *
 * Loops are rendered over a whole number of engine cycles (or track links) with a short
 * crossfade at the seam, so they repeat without clicks or phase jumps.
 */
public final class EngineSoundSynth {

    static final int SAMPLE_RATE = 44100;
    private static final String RESOURCES_DIR = "nettank-client/src/main/resources/";
    private static final float PEAK = 0.89f; // -1 dBFS
    private static final double PREROLL_SECONDS = 0.3; // lets filters settle before a loop starts
    private static final double LOOP_CROSSFADE_SECONDS = 0.08;

    private EngineSoundSynth() {}

    public static void main(String[] args) throws IOException {
        boolean force = Arrays.asList(args).contains("--force");

        // Each family is normalized as a group so loops and start/stop keep their relative levels
        EngineSoundSet diesel = EngineSoundProfiles.DIESEL_V12;
        Map<String, float[]> dieselClips = new LinkedHashMap<>();
        for (EngineLoop loop : diesel.loops()) {
            dieselClips.put(loop.path(), dieselLoop(loop.recordedRpm()));
        }
        dieselClips.put(diesel.startPath(), dieselStart());
        dieselClips.put(diesel.stopPath(), dieselStop());
        writeGroup(dieselClips, PEAK, force);

        EngineSoundSet turbine = EngineSoundProfiles.TURBINE;
        Map<String, float[]> turbineClips = new LinkedHashMap<>();
        for (EngineLoop loop : turbine.loops()) {
            turbineClips.put(loop.path(), turbineLoop(loop.recordedRpm() / 100.0));
        }
        turbineClips.put(turbine.startPath(), turbineStart());
        turbineClips.put(turbine.stopPath(), turbineStop());
        writeGroup(turbineClips, PEAK, force);

        Map<String, float[]> trackClips = new LinkedHashMap<>();
        trackClips.put(EngineSoundProfiles.TRACK_SLOW, trackLoop(false));
        trackClips.put(EngineSoundProfiles.TRACK_FAST, trackLoop(true));
        writeGroup(trackClips, PEAK, force);

        for (String path : EngineSoundProfiles.TRACK_ONE_SHOTS) {
            int variant = Character.getNumericValue(path.charAt(path.length() - 1));
            float[] clip = path.contains("squeak") ? squeak(variant) : clank(variant);
            writeGroup(Map.of(path, clip), PEAK, force);
        }
        System.out.println("Engine sound placeholders done.");
    }

    // --- Diesel V12 -------------------------------------------------------------------------

    static float[] dieselLoop(float rpm) {
        double cycleSeconds = 120.0 / rpm; // one four-stroke cycle = two crank revolutions
        int cycles = Math.max(4, (int) Math.round(2.6 / cycleSeconds));
        double loopSeconds = cycles * cycleSeconds;
        // Higher-RPM loops are "recorded" pulling hard, which brightens exhaust and turbo
        double load = 0.2 + 0.8 * clamp01((rpm - 800.0) / 1800.0);
        return renderLoop(loopSeconds, total -> renderDiesel(total, t -> rpm, t -> 1.0, t -> load,
                loopSeconds, 7000L + (long) rpm));
    }

    // Solenoid clunk, starter whine and cranking, then the catch, a flare and settle to idle
    static float[] dieselStart() {
        double length = 3.4;
        int n = samples(length);
        DoubleUnaryOperator rpm = t -> {
            if (t < 1.15) return 190.0 + 25.0 * Math.sin(2.0 * Math.PI * 1.7 * t);
            if (t < 1.75) return 190.0 + (1150.0 - 190.0) * smooth((t - 1.15) / 0.6);
            return 800.0 + 350.0 * Math.exp(-(t - 1.75) / 0.35);
        };
        DoubleUnaryOperator combustion = t -> clamp01((t - 1.15) / 0.2);
        DoubleUnaryOperator load = t -> t > 1.15 && t < 1.9 ? 1.0 : clamp01(1.0 - (t - 1.9) / 0.5);
        float[] engine = renderDiesel(n, rpm, combustion, load, 0.0, 811L);

        Random rnd = new Random(812L);
        Resonator solenoid = new Resonator(180.0, 0.04);
        Biquad gearNoise = Biquad.bandpass(2500.0, 1.5);
        Biquad smokeLp = Biquad.lowpass(300.0, 0.7);
        double starterPhase = 0.0;
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            double t = time(i);
            double starterEnv = smooth(clamp01((t - 0.05) / 0.15)) * clamp01((1.3 - t) / 0.15);
            starterPhase = wrap(starterPhase + (260.0 + 120.0 * clamp01(t / 1.1)) / SAMPLE_RATE);
            double starter = starterEnv * (0.06 * Math.sin(2.0 * Math.PI * starterPhase)
                    + 0.03 * Math.sin(4.0 * Math.PI * starterPhase)
                    + 0.05 * gearNoise.process(white(rnd)));
            double clunk = solenoid.process(i == samples(0.05) ? 1.2 : 0.0);
            double puff = t > 1.2 ? smokeLp.process(white(rnd)) * 0.8 * Math.exp(-(t - 1.2) / 0.3) : 0.0;
            double fade = 1.0 - smooth(clamp01((t - 2.6) / 0.8));
            out[i] = (float) ((engine[i] + Math.tanh(starter + clunk + puff)) * fade);
        }
        return out;
    }

    // Fuel cut: compression chuffs slow to a stop, fans and turbo wind down, then the hull settles
    static float[] dieselStop() {
        double length = 2.4;
        int n = samples(length);
        DoubleUnaryOperator rpm = t -> 800.0 * Math.pow(Math.max(0.0, 1.0 - t / 1.5), 0.7);
        DoubleUnaryOperator combustion = t -> clamp01(1.0 - t / 0.08);
        float[] engine = renderDiesel(n, rpm, combustion, t -> 0.0, 0.0, 821L);

        Random rnd = new Random(822L);
        Resonator shudder = new Resonator(45.0, 0.08);
        Resonator rattle = new Resonator(1250.0, 0.03);
        float[] out = new float[n];
        int shudderAt = samples(1.5);
        for (int i = 0; i < n; i++) {
            double t = time(i);
            double hit = i == shudderAt ? 1.0 : 0.0;
            double rattleHit = (i == shudderAt + samples(0.05) || i == shudderAt + samples(0.11)) ? 0.4 * rnd.nextDouble() : 0.0;
            double fade = 1.0 - smooth(clamp01((t - 2.1) / 0.3));
            out[i] = (float) ((engine[i] + Math.tanh(shudder.process(hit) * 1.2 + rattle.process(rattleHit) * 0.3)) * fade);
        }
        return out;
    }

    /**
     * Twelve cylinders fire six times per crank revolution. Each firing is a short pressure
     * pulse through exhaust/hull resonances plus an exhaust noise burst and injector clatter,
     * over engine-driven cooling-fan roar and turbo whistle (both loud on real MBTs).
     *
     * @param combustionAt 0 = cranking (compression only), 1 = firing
     * @param quantizeSeconds loop length; tones snap to whole cycles over it (0 = no snapping)
     */
    static float[] renderDiesel(int n, DoubleUnaryOperator rpmAt, DoubleUnaryOperator combustionAt,
                                DoubleUnaryOperator loadAt, double quantizeSeconds, long seed) {
        Random rnd = new Random(seed);
        // Same cylinder signature in every clip: slight timing and strength imbalance gives the lope
        Random cylinders = new Random(1234L);
        double[] cylOffset = new double[12];
        double[] cylStrength = new double[12];
        for (int c = 0; c < 12; c++) {
            cylOffset[c] = (cylinders.nextDouble() - 0.5) * 0.06;
            cylStrength[c] = 0.8 + 0.2 * cylinders.nextDouble();
        }

        Biquad body1 = Biquad.bandpass(62.0, 0.9);
        Biquad body2 = Biquad.bandpass(125.0, 1.4);
        Biquad body3 = Biquad.bandpass(330.0, 1.6);
        Biquad body4 = Biquad.bandpass(950.0, 1.2);
        Biquad exhaustHp = Biquad.highpass(180.0, 0.7);
        Biquad exhaustLp = Biquad.lowpass(1500.0, 0.7);
        Biquad clatterHp = Biquad.highpass(1800.0, 0.7);
        Biquad clatterLp = Biquad.lowpass(5500.0, 0.7);
        Biquad fanLp = Biquad.lowpass(650.0, 0.6);
        Biquad fanBody = Biquad.bandpass(300.0, 0.7);
        Biquad turboBp = Biquad.bandpass(3000.0, 6.0);
        // Distance/air roll-off: an exterior recording has little energy above ~8 kHz
        Biquad rolloff1 = Biquad.lowpass(6000.0, 0.7);
        Biquad rolloff2 = Biquad.lowpass(6000.0, 0.7);

        double pulseWidth = 0.0016;
        double noiseDecay = Math.exp(-1.0 / (SAMPLE_RATE * 0.006));
        double clatterDecay = Math.exp(-1.0 / (SAMPLE_RATE * 0.0012));

        float[] out = new float[n];
        double crank = 0.0; // four-stroke cycles elapsed
        long nextSlot = 0;
        double pulseAge = 1.0;
        double pulseAmp = 0.0;
        double noiseEnv = 0.0;
        double clatterEnv = 0.0;
        double fanPhase = 0.0;
        double turboPhase = 0.0;
        double gearPhase = 0.0;

        for (int i = 0; i < n; i++) {
            double t = time(i);
            double rpm = rpmAt.applyAsDouble(t);
            double comb = combustionAt.applyAsDouble(t);
            double load = loadAt.applyAsDouble(t);
            double rpmNorm = clamp01((rpm - 600.0) / 2000.0);

            crank += rpm / 120.0 / SAMPLE_RATE;
            double slots = crank * 12.0;
            while (slots >= nextSlot + cylOffset[(int) (nextSlot % 12)]) {
                double strength = cylStrength[(int) (nextSlot % 12)] * (0.35 + 0.65 * comb)
                        * (0.92 + 0.16 * rnd.nextDouble());
                pulseAge = 0.0;
                pulseAmp = strength;
                noiseEnv += strength * comb * (0.6 + 0.6 * load);
                clatterEnv += strength * comb;
                nextSlot++;
            }

            double excitation = pulseAge < pulseWidth ? pulseAmp * Math.sin(Math.PI * pulseAge / pulseWidth) : 0.0;
            pulseAge += 1.0 / SAMPLE_RATE;
            double body = body1.process(excitation) * 2.2 + body2.process(excitation) * 1.6
                    + body3.process(excitation) * 1.0 + body4.process(excitation) * 1.1;

            if (i % 32 == 0) exhaustLp.setLowpass(900.0 + 1400.0 * load + 700.0 * rpmNorm, 0.7);
            double exhaust = exhaustLp.process(exhaustHp.process(white(rnd))) * noiseEnv;
            noiseEnv *= noiseDecay;

            double clatter = clatterLp.process(clatterHp.process(white(rnd))) * clatterEnv;
            clatterEnv *= clatterDecay;

            double fanLevel = 0.05 + 0.45 * rpmNorm * rpmNorm;
            fanPhase = wrap(fanPhase + quantize(rpm / 60.0 * 1.35 * 9.0, quantizeSeconds) / SAMPLE_RATE);
            double fan = fanLevel * (fanLp.process(white(rnd)) + 0.6 * fanBody.process(white(rnd))
                    + 0.12 * Math.sin(2.0 * Math.PI * fanPhase));

            double turboHz = quantize(1500.0 + 4300.0 * Math.pow(rpmNorm, 1.4) * (0.65 + 0.35 * load), quantizeSeconds);
            turboPhase = wrap(turboPhase + turboHz / SAMPLE_RATE);
            if (i % 32 == 0) turboBp.setBandpass(turboHz, 6.0);
            double turboLevel = (0.003 + 0.014 * load) * (0.3 + rpmNorm);
            double turbo = turboLevel * (Math.sin(2.0 * Math.PI * turboPhase) + 2.4 * turboBp.process(white(rnd)));

            gearPhase = wrap(gearPhase + quantize(rpm / 60.0 * 29.0, quantizeSeconds) / SAMPLE_RATE);
            double gear = 0.004 * Math.min(1.0, rpm / 400.0) * Math.sin(2.0 * Math.PI * gearPhase);

            // Balanced against the firing body: exhaust and fan take over at high RPM, as on a real MBT
            double mix = body * 0.32 + exhaust * 0.55 + clatter * 0.5 + fan * 2.5 + turbo * 1.8 + gear * 1.3;
            out[i] = (float) rolloff2.process(rolloff1.process(Math.tanh(mix * 1.6)));
        }
        return out;
    }

    // --- Gas turbine ------------------------------------------------------------------------

    static float[] turbineLoop(double n1) {
        double loopSeconds = 2.5;
        double load = 0.2 + 0.8 * clamp01((n1 - 0.6) / 0.4);
        return renderLoop(loopSeconds, total -> renderTurbine(total, t -> n1, t -> 1.0, t -> load,
                loopSeconds, 9000L + Math.round(n1 * 100.0)));
    }

    // Starter spools the gas generator, igniters tick, light-off whump, then spool up to idle
    static float[] turbineStart() {
        double length = 5.0;
        int n = samples(length);
        DoubleUnaryOperator n1 = t -> t < 1.3
                ? 0.18 * Math.pow(t / 1.3, 1.4)
                : 0.18 + 0.42 * (1.0 - Math.exp(-(t - 1.3) / 0.9));
        DoubleUnaryOperator lit = t -> smooth(clamp01((t - 1.25) / 0.35));
        float[] engine = renderTurbine(n, n1, lit, t -> 0.3, 0.0, 911L);

        Random rnd = new Random(912L);
        Biquad tickHp = Biquad.highpass(3000.0, 0.7);
        Biquad whumpLp = Biquad.lowpass(300.0, 0.7);
        double tickEnv = 0.0;
        double tickDecay = Math.exp(-1.0 / (SAMPLE_RATE * 0.0015));
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            double t = time(i);
            if (t > 0.4 && t < 1.9 && i % (SAMPLE_RATE / 6) == 0) tickEnv += 1.0;
            double tick = tickHp.process(white(rnd)) * tickEnv * 0.5;
            tickEnv *= tickDecay;
            double whump = t > 1.3 ? whumpLp.process(white(rnd)) * 2.5 * Math.exp(-(t - 1.3) / 0.25) : 0.0;
            double fade = 1.0 - smooth(clamp01((t - 4.2) / 0.8));
            out[i] = (float) ((engine[i] + Math.tanh(tick + whump)) * fade);
        }
        return out;
    }

    // Flameout: the roar drops at once while the spools wind down for several seconds
    static float[] turbineStop() {
        double length = 6.0;
        int n = samples(length);
        float[] engine = renderTurbine(n, t -> 0.6 * Math.exp(-t / 1.9), t -> clamp01(1.0 - t / 0.15),
                t -> 0.0, 0.0, 921L);
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            double fade = 1.0 - smooth(clamp01((time(i) - 5.4) / 0.6));
            out[i] = (float) (engine[i] * fade);
        }
        return out;
    }

    /**
     * Compressor whine from two spools (close tones that beat, giving the shimmer), combustor
     * roar, exhaust whoosh and intake hiss; the hull and transmission add a little low end.
     *
     * @param n1At gas generator speed 0..1
     * @param litAt 0 = no combustion (starter only / flameout), 1 = burning
     */
    static float[] renderTurbine(int n, DoubleUnaryOperator n1At, DoubleUnaryOperator litAt,
                                 DoubleUnaryOperator loadAt, double quantizeSeconds, long seed) {
        Random rnd = new Random(seed);
        Biquad roarLp = Biquad.lowpass(1500.0, 0.7);
        Biquad whooshBp = Biquad.bandpass(1800.0, 0.8);
        Biquad hissHp = Biquad.highpass(6500.0, 0.7);
        Biquad rumbleLp = Biquad.lowpass(140.0, 0.7);
        Biquad turbulenceLp = Biquad.lowpass(4.0, 0.7);
        Biquad rolloff1 = Biquad.lowpass(10000.0, 0.7);
        Biquad rolloff2 = Biquad.lowpass(10000.0, 0.7);

        double hp1 = 0.0;
        double hp2 = 0.0;
        double lp = 0.0;
        double shaft = 0.0;
        double transmission = 0.0;
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            double t = time(i);
            double n1 = n1At.applyAsDouble(t);
            double lit = litAt.applyAsDouble(t);
            double load = loadAt.applyAsDouble(t);

            hp1 = wrap(hp1 + quantize(4400.0 * n1, quantizeSeconds) / SAMPLE_RATE);
            hp2 = wrap(hp2 + quantize(4400.0 * n1 * 1.0012, quantizeSeconds) / SAMPLE_RATE);
            lp = wrap(lp + quantize(1650.0 * Math.pow(n1, 1.1), quantizeSeconds) / SAMPLE_RATE);
            shaft = wrap(shaft + quantize(290.0 * n1, quantizeSeconds) / SAMPLE_RATE);
            transmission = wrap(transmission + quantize(760.0 * n1, quantizeSeconds) / SAMPLE_RATE);

            double twoPi = 2.0 * Math.PI;
            double whine = 0.10 * n1 * n1 * (Math.sin(twoPi * hp1) + 0.7 * Math.sin(twoPi * hp2)
                    + 0.3 * Math.sin(2.0 * twoPi * hp1))
                    + 0.05 * n1 * (Math.sin(twoPi * lp) + 0.4 * Math.sin(2.0 * twoPi * lp))
                    + 0.02 * n1 * Math.sin(twoPi * shaft)
                    + 0.008 * Math.min(1.0, n1 * 3.0) * Math.sin(twoPi * transmission);

            double turbulence = 1.0 + 12.0 * turbulenceLp.process(white(rnd));
            if (i % 32 == 0) roarLp.setLowpass(500.0 + 2300.0 * n1 * (0.7 + 0.3 * load), 0.7);
            double roar = roarLp.process(white(rnd)) * 0.9 * n1 * n1 * lit * turbulence;
            double whoosh = whooshBp.process(white(rnd)) * 0.45 * n1 * n1 * lit;
            double hiss = hissHp.process(white(rnd)) * 0.10 * n1 * n1;
            double rumble = rumbleLp.process(white(rnd)) * 1.2 * n1;

            // The compressor whine leads at idle, as it does on an Abrams
            out[i] = (float) rolloff2.process(rolloff1.process(Math.tanh(whine * 1.8 + roar + whoosh * 0.8 + hiss + rumble * 2.0)));
        }
        return out;
    }

    // --- Tracks -----------------------------------------------------------------------------

    // Slow: individual link clanks over road-wheel thuds. Fast: a blurred clatter and rumble.
    static float[] trackLoop(boolean fast) {
        double linkRate = fast ? 24.0 : 8.0;
        int links = fast ? 48 : 20;
        double loopSeconds = links / linkRate;
        return renderLoop(loopSeconds, total -> renderTracks(total, linkRate, fast ? 1.0 : 0.0, fast ? 77L : 55L));
    }

    static float[] renderTracks(int n, double linkRate, double intensity, long seed) {
        Random rnd = new Random(seed);
        Resonator[] pinModes = {
                new Resonator(720.0, 0.028), new Resonator(1310.0, 0.020),
                new Resonator(2380.0, 0.014), new Resonator(3900.0, 0.008)};
        double[] pinAmps = {1.0, 0.7, 0.45, 0.25};
        Resonator[] padModes = {
                new Resonator(540.0, 0.022), new Resonator(1060.0, 0.016), new Resonator(1980.0, 0.010)};
        double[] padAmps = {0.9, 0.5, 0.3};
        Resonator thud = new Resonator(72.0, 0.035);
        Biquad crunchBp = Biquad.bandpass(1800.0, 0.6);
        Biquad rumbleLp = Biquad.lowpass(90.0 + 90.0 * intensity, 0.7);
        Biquad sizzleHp = Biquad.highpass(3800.0, 0.7);
        Biquad rolloff = Biquad.lowpass(8000.0, 0.7);
        double crunchDecay = Math.exp(-1.0 / (SAMPLE_RATE * 0.015));
        double impactScale = 1.0 - 0.45 * intensity;

        double nextLink = 0.0;
        long link = 0;
        double crunchEnv = 0.0;
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            double t = time(i);
            double pinHit = 0.0;
            double padHit = 0.0;
            double thudHit = 0.0;
            if (t >= nextLink) {
                double strength = impactScale * (0.5 + 0.5 * rnd.nextDouble());
                if (link % 2 == 0) pinHit = strength;
                else padHit = strength * 0.8;
                thudHit = strength * (link % 6 == 0 ? 1.4 : 0.7);
                crunchEnv += strength * 0.8;
                link++;
                nextLink = link / linkRate + (rnd.nextDouble() - 0.5) * 0.004;
            }

            double metal = 0.0;
            for (int m = 0; m < pinModes.length; m++) metal += pinModes[m].process(pinHit * pinAmps[m]);
            for (int m = 0; m < padModes.length; m++) metal += padModes[m].process(padHit * padAmps[m]);
            double low = thud.process(thudHit);
            double crunch = crunchBp.process(white(rnd)) * (0.12 + crunchEnv);
            crunchEnv *= crunchDecay;
            double rumble = rumbleLp.process(white(rnd)) * (0.6 + 1.2 * intensity);
            double sizzle = sizzleHp.process(white(rnd)) * 0.03 * intensity;

            out[i] = (float) rolloff.process(Math.tanh(metal * 0.5 + low * 0.55 + crunch * 0.7 + rumble * 1.3 + sizzle));
        }
        return out;
    }

    // Steel-on-steel squeal from a sprocket or idler: a wavering tone with friction noise
    static float[] squeak(int variant) {
        double[] baseHz = {1750.0, 2150.0, 2600.0};
        double f0 = baseHz[Math.floorMod(variant - 1, 3)];
        double length = 0.32 + 0.1 * Math.floorMod(variant - 1, 3);
        int n = samples(length);
        Random rnd = new Random(600L + variant);
        Biquad friction = Biquad.bandpass(f0, 12.0);
        Biquad roughness = Biquad.lowpass(30.0, 0.7);
        double vibratoHz = 11.0 + 2.0 * variant;
        double phase = 0.0;
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            double t = time(i);
            double glide = 1.0 + 0.06 * t / length;
            double hz = f0 * glide * (1.0 + 0.025 * Math.sin(2.0 * Math.PI * vibratoHz * t));
            phase = wrap(phase + hz / SAMPLE_RATE);
            double tone = Math.sin(2.0 * Math.PI * phase) + 0.45 * Math.sin(4.0 * Math.PI * phase)
                    + 0.2 * Math.sin(6.0 * Math.PI * phase);
            double env = smooth(clamp01(t / 0.025)) * smooth(clamp01((length - t) / 0.12));
            double rough = 0.8 + 6.0 * roughness.process(white(rnd));
            out[i] = (float) Math.tanh((tone * 0.35 * rough + friction.process(white(rnd)) * 1.5) * env);
        }
        return out;
    }

    // A loose link or road wheel slamming the hull: metallic modes, a thud, a short bounce
    static float[] clank(int variant) {
        double length = 0.45;
        int n = samples(length);
        double tune = variant == 2 ? 0.85 : 1.0;
        Random rnd = new Random(700L + variant);
        Resonator[] modes = {
                new Resonator(380.0 * tune, 0.09), new Resonator(940.0 * tune, 0.06),
                new Resonator(1720.0 * tune, 0.04), new Resonator(3050.0 * tune, 0.02)};
        double[] amps = {1.0, 0.7, 0.5, 0.3};
        Resonator thud = new Resonator(60.0, 0.06);
        Biquad crackHp = Biquad.highpass(2000.0, 0.7);
        double crackEnv = 0.0;
        double crackDecay = Math.exp(-1.0 / (SAMPLE_RATE * 0.003));
        int bounceAt = samples(0.07);
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            double hit = i == 0 ? 1.0 : (i == bounceAt ? 0.35 : 0.0);
            if (hit > 0.0) crackEnv += hit;
            double metal = 0.0;
            for (int m = 0; m < modes.length; m++) metal += modes[m].process(hit * amps[m]);
            double crack = crackHp.process(white(rnd)) * crackEnv;
            crackEnv *= crackDecay;
            double fade = smooth(clamp01((length - time(i)) / 0.1));
            out[i] = (float) (Math.tanh(metal * 0.6 + thud.process(hit) * 0.8 + crack * 0.6) * fade);
        }
        return out;
    }

    // --- Loop building, file output and DSP helpers -----------------------------------------

    /**
     * Renders preroll + loop + crossfade, then folds the tail over the head with an
     * equal-gain crossfade: the last loop sample flows into what was rendered right after
     * it, so playback wraps seamlessly.
     */
    static float[] renderLoop(double loopSeconds, IntFunction<float[]> renderer) {
        int preroll = samples(PREROLL_SECONDS);
        int length = samples(loopSeconds);
        int crossfade = samples(LOOP_CROSSFADE_SECONDS);
        float[] rendered = renderer.apply(preroll + length + crossfade);
        float[] loop = Arrays.copyOfRange(rendered, preroll, preroll + length);
        for (int i = 0; i < crossfade; i++) {
            double w = 0.5 - 0.5 * Math.cos(Math.PI * i / crossfade); // 0 -> 1
            loop[i] = (float) (rendered[preroll + i] * w + rendered[preroll + length + i] * (1.0 - w));
        }
        return loop;
    }

    static void writeGroup(Map<String, float[]> clips, float targetPeak, boolean force) throws IOException {
        float peak = 0.0f;
        for (float[] clip : clips.values()) {
            for (float s : clip) peak = Math.max(peak, Math.abs(s));
        }
        float scale = peak > 0.0f ? targetPeak / peak : 1.0f;
        for (Map.Entry<String, float[]> entry : clips.entrySet()) {
            Path wav = Path.of(RESOURCES_DIR + entry.getKey() + ".wav");
            Path ogg = Path.of(RESOURCES_DIR + entry.getKey() + ".ogg");
            if (Files.exists(ogg)) {
                System.out.println("Keeping sourced recording " + ogg);
                continue;
            }
            if (Files.exists(wav) && !force) {
                System.out.println("Keeping existing placeholder " + wav + " (use --force to regenerate)");
                continue;
            }
            Files.createDirectories(wav.getParent());
            Files.write(wav, wavBytes(entry.getValue(), scale));
            System.out.println("Wrote " + wav);
        }
    }

    static byte[] wavBytes(float[] samples, float scale) {
        int dataSize = samples.length * 2;
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes()).putInt(36 + dataSize).put("WAVE".getBytes());
        header.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2).putShort((short) 2).putShort((short) 16);
        header.put("data".getBytes()).putInt(dataSize);

        ByteBuffer data = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN);
        for (float s : samples) {
            float v = Math.max(-1.0f, Math.min(1.0f, s * scale));
            data.putShort((short) Math.round(v * 32767.0f));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + dataSize);
        out.writeBytes(header.array());
        out.writeBytes(data.array());
        return out.toByteArray();
    }

    private static int samples(double seconds) {
        return (int) Math.round(seconds * SAMPLE_RATE);
    }

    private static double time(int sample) {
        return (double) sample / SAMPLE_RATE;
    }

    private static double white(Random rnd) {
        return rnd.nextDouble() * 2.0 - 1.0;
    }

    private static double wrap(double phase) {
        return phase - Math.floor(phase);
    }

    // Snaps a frequency to a whole number of cycles over the loop so the tone wraps cleanly
    private static double quantize(double hz, double loopSeconds) {
        if (loopSeconds <= 0.0) return hz;
        return Math.max(1.0, Math.round(hz * loopSeconds)) / loopSeconds;
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    /** RBJ-cookbook biquad, transposed direct form II. */
    static final class Biquad {
        private double b0, b1, b2, a1, a2;
        private double z1, z2;

        static Biquad lowpass(double hz, double q) {
            Biquad f = new Biquad();
            f.setLowpass(hz, q);
            return f;
        }

        static Biquad highpass(double hz, double q) {
            Biquad f = new Biquad();
            double w = 2.0 * Math.PI * hz / SAMPLE_RATE;
            double alpha = Math.sin(w) / (2.0 * q);
            double cos = Math.cos(w);
            f.set((1.0 + cos) / 2.0, -(1.0 + cos), (1.0 + cos) / 2.0, 1.0 + alpha, -2.0 * cos, 1.0 - alpha);
            return f;
        }

        // Constant 0 dB peak gain
        static Biquad bandpass(double hz, double q) {
            Biquad f = new Biquad();
            f.setBandpass(hz, q);
            return f;
        }

        void setLowpass(double hz, double q) {
            double w = 2.0 * Math.PI * Math.min(hz, SAMPLE_RATE * 0.45) / SAMPLE_RATE;
            double alpha = Math.sin(w) / (2.0 * q);
            double cos = Math.cos(w);
            set((1.0 - cos) / 2.0, 1.0 - cos, (1.0 - cos) / 2.0, 1.0 + alpha, -2.0 * cos, 1.0 - alpha);
        }

        void setBandpass(double hz, double q) {
            double w = 2.0 * Math.PI * Math.min(hz, SAMPLE_RATE * 0.45) / SAMPLE_RATE;
            double alpha = Math.sin(w) / (2.0 * q);
            set(alpha, 0.0, -alpha, 1.0 + alpha, -2.0 * Math.cos(w), 1.0 - alpha);
        }

        private void set(double nb0, double nb1, double nb2, double na0, double na1, double na2) {
            b0 = nb0 / na0;
            b1 = nb1 / na0;
            b2 = nb2 / na0;
            a1 = na1 / na0;
            a2 = na2 / na0;
        }

        double process(double x) {
            double y = b0 * x + z1;
            z1 = b1 * x - a1 * y + z2;
            z2 = b2 * x - a2 * y;
            return y;
        }
    }

    /** Damped sine oscillator: an impulse in rings at {@code hz}, decaying with time constant {@code tau}. */
    static final class Resonator {
        private final double c1;
        private final double c2;
        private final double inputGain;
        private double y1, y2;

        Resonator(double hz, double tau) {
            double w = 2.0 * Math.PI * hz / SAMPLE_RATE;
            double r = Math.exp(-1.0 / (tau * SAMPLE_RATE));
            c1 = 2.0 * r * Math.cos(w);
            c2 = -r * r;
            inputGain = Math.sin(w); // unit-amplitude ring for a unit impulse
        }

        double process(double x) {
            double y = c1 * y1 + c2 * y2 + x * inputGain;
            y2 = y1;
            y1 = y;
            return y;
        }
    }
}
