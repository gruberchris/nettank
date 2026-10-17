package org.chrisgruber.nettank.client.engine.audio;

import org.chrisgruber.nettank.client.engine.audio.EngineSoundProfile.EngineLoop;
import org.chrisgruber.nettank.client.engine.audio.EngineSoundProfile.EngineSoundSet;
import org.chrisgruber.nettank.common.entities.TankType;

import java.util.List;

/**
 * Maps each tank type to an engine family and variant tuning. Two families ship:
 * a diesel V12 (Leopard 2 / T-90 class) and a gas turbine (Abrams AGT1500 class).
 *
 * When replacing a placeholder with a real recording, keep the file name's number
 * equal to the speed the recording was made at, and update {@code recordedRpm} here
 * if it differs. See docs/audio/engine_audio.md.
 */
public final class EngineSoundProfiles {

    private static final String ENGINES_DIR = "sounds/engines/";
    private static final String TRACKS_DIR = "sounds/tracks/";

    // Diesel V12: fires 6 times per crank revolution, so firing Hz = RPM / 10.
    public static final EngineSoundSet DIESEL_V12 = new EngineSoundSet(
            "diesel_v12",
            List.of(
                    new EngineLoop(800f, ENGINES_DIR + "diesel_v12/loop_0800"),
                    new EngineLoop(1100f, ENGINES_DIR + "diesel_v12/loop_1100"),
                    new EngineLoop(1500f, ENGINES_DIR + "diesel_v12/loop_1500"),
                    new EngineLoop(2000f, ENGINES_DIR + "diesel_v12/loop_2000"),
                    new EngineLoop(2600f, ENGINES_DIR + "diesel_v12/loop_2600")),
            800f, 2600f,
            ENGINES_DIR + "diesel_v12/start",
            ENGINES_DIR + "diesel_v12/stop",
            1.9f, 0.8f, 0.25f);

    // Gas turbine: speeds are percent N1 (gas generator speed), idle around 60%.
    public static final EngineSoundSet TURBINE = new EngineSoundSet(
            "turbine",
            List.of(
                    new EngineLoop(60f, ENGINES_DIR + "turbine/loop_060"),
                    new EngineLoop(73f, ENGINES_DIR + "turbine/loop_073"),
                    new EngineLoop(86f, ENGINES_DIR + "turbine/loop_086"),
                    new EngineLoop(100f, ENGINES_DIR + "turbine/loop_100")),
            60f, 100f,
            ENGINES_DIR + "turbine/start",
            ENGINES_DIR + "turbine/stop",
            2.8f, 1.4f, 0.7f);

    public static final String TRACK_SLOW = TRACKS_DIR + "track_slow";
    public static final String TRACK_FAST = TRACKS_DIR + "track_fast";
    // Random one-shots layered over the track loops at low speed
    public static final List<String> TRACK_ONE_SHOTS = List.of(
            TRACKS_DIR + "squeak_1",
            TRACKS_DIR + "squeak_2",
            TRACKS_DIR + "squeak_3",
            TRACKS_DIR + "clank_1",
            TRACKS_DIR + "clank_2");

    // Heavy diesel: low, slow to rev, loud tracks
    private static final EngineSoundProfile HEAVY =
            new EngineSoundProfile(DIESEL_V12, 1400f, 1000f, 0.88f, 1.0f, 0.9f, 1.0f, 0.9f);
    // Light diesel: higher, snappy, thinner. The LAV-25 is wheeled, so no track layer.
    private static final EngineSoundProfile LIGHT =
            new EngineSoundProfile(DIESEL_V12, 3000f, 2200f, 1.12f, 0.85f, 1.0f, 0.0f, 1.15f);
    // Standard turbine: Abrams-like whine with spool lag
    private static final EngineSoundProfile STANDARD =
            new EngineSoundProfile(TURBINE, 22f, 12f, 1.0f, 0.9f, 1.0f, 1.0f, 1.0f);
    // Stealth turbine: "whispering death" - quieter and low-passed
    private static final EngineSoundProfile STEALTH =
            new EngineSoundProfile(TURBINE, 24f, 14f, 1.05f, 0.55f, 0.35f, 0.6f, 1.05f);

    private EngineSoundProfiles() {}

    // Only the Abrams-family types (STANDARD, STEALTH) get the turbine. Any other vehicle uses
    // the diesel, e.g. a future tracked Bradley would be a diesel with the track layer on.
    public static EngineSoundProfile forType(TankType type) {
        if (type == null) return STANDARD;
        return switch (type) {
            case STANDARD -> STANDARD;
            case HEAVY -> HEAVY;
            case LIGHT -> LIGHT;
            case STEALTH -> STEALTH;
        };
    }

    public static List<EngineSoundSet> allSets() {
        return List.of(DIESEL_V12, TURBINE);
    }
}
