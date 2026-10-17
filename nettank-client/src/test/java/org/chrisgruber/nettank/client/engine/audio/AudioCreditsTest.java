package org.chrisgruber.nettank.client.engine.audio;

import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every sound shipped in the client JAR (and so in the public repo and GitHub Releases)
 * must have a recorded source under a license that allows public redistribution.
 * See docs/audio/engine_audio.md for the rules and how to add an entry.
 */
class AudioCreditsTest {

    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path SOUNDS = RESOURCES.resolve("sounds");
    private static final Path CREDITS = SOUNDS.resolve("credits.json");

    // Licenses that permit redistributing the raw files publicly. NC, ND, "Sampling+" and
    // store EULAs (Sonniss, Pixabay, ZapSplat, paid packs) are deliberately absent.
    private static final Set<String> ALLOWED_LICENSES = Set.of(
            "nettank-original", "CC0-1.0", "PD-USGov",
            "CC-BY-3.0", "CC-BY-4.0", "OGA-BY-3.0", "CC-BY-SA-3.0", "CC-BY-SA-4.0");

    record Source(String title, String author, String url, String license, String licenseUrl, String retrieved) {}
    record Entry(String file, List<Source> sources, String modifications) {}
    record Credits(List<Entry> sounds) {}

    private static Map<String, Entry> entries;

    @BeforeAll
    static void loadCredits() throws IOException {
        assertTrue(Files.isRegularFile(CREDITS), "missing " + CREDITS);
        Credits credits = new Gson().fromJson(Files.readString(CREDITS), Credits.class);
        assertNotNull(credits.sounds(), "credits.json needs a \"sounds\" array");
        entries = new HashMap<>();
        for (Entry entry : credits.sounds()) {
            assertNull(entries.put(entry.file(), entry), "duplicate credits entry for " + entry.file());
        }
    }

    private static List<String> shippedSounds() throws IOException {
        try (Stream<Path> files = Files.walk(SOUNDS)) {
            return files.filter(Files::isRegularFile)
                    .map(path -> RESOURCES.relativize(path).toString().replace('\\', '/'))
                    .filter(name -> name.endsWith(".ogg") || name.endsWith(".wav"))
                    .sorted()
                    .toList();
        }
    }

    @Test
    void everyShippedSoundHasACreditsEntry() throws IOException {
        List<String> missing = new ArrayList<>();
        for (String sound : shippedSounds()) {
            if (!entries.containsKey(sound)) missing.add(sound);
        }
        assertTrue(missing.isEmpty(), "No credits entry for " + missing + ". Add each file's source and license to "
                + CREDITS + ", or delete it if its origin is unknown (docs/audio/engine_audio.md).");
    }

    @Test
    void everyCreditsEntryPointsAtAShippedFile() {
        for (String file : entries.keySet()) {
            assertTrue(Files.isRegularFile(RESOURCES.resolve(file)), "credits.json lists missing file " + file);
        }
    }

    @Test
    void everySourceUsesALicenseThatAllowsPublicRedistribution() {
        for (Entry entry : entries.values()) {
            assertNotNull(entry.sources(), entry.file() + " has no sources");
            assertFalse(entry.sources().isEmpty(), entry.file() + " has no sources");
            for (Source source : entry.sources()) {
                assertTrue(ALLOWED_LICENSES.contains(source.license()),
                        entry.file() + " uses license '" + source.license() + "', allowed: " + ALLOWED_LICENSES);
                assertFalse(isBlank(source.title()) || isBlank(source.author()) || isBlank(source.url()),
                        entry.file() + ": every source needs a title, author and url");
                if (!source.license().equals("nettank-original")) {
                    assertFalse(isBlank(source.licenseUrl()) || isBlank(source.retrieved()),
                            entry.file() + ": third-party sources need licenseUrl and retrieved date");
                }
            }
        }
    }

    // A sourced .ogg replaces a placeholder .wav; leaving both would ship the stale one
    @Test
    void noSoundShipsInTwoFormats() throws IOException {
        List<String> sounds = shippedSounds();
        for (String sound : sounds) {
            if (sound.endsWith(".ogg")) {
                String wav = sound.substring(0, sound.length() - 4) + ".wav";
                assertFalse(sounds.contains(wav), "both " + sound + " and " + wav + " exist; delete the placeholder");
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
