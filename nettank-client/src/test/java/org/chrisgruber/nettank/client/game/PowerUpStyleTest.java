package org.chrisgruber.nettank.client.game;

import org.chrisgruber.nettank.common.entities.PowerUpType;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PowerUpStyleTest {

    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    void everyPowerUpHasAnAsciiLabelAndAnIcon() {
        for (PowerUpType type : PowerUpType.values()) {
            String label = PowerUpStyle.label(type);
            assertFalse(label.isBlank(), type.name());
            assertTrue(label.chars().allMatch(c -> c >= 32 && c < 127), type + ": the bitmap font is ASCII-only");
            assertTrue(Files.isRegularFile(RESOURCES.resolve(PowerUpStyle.iconPath(type))), "icon for " + type);
        }
    }

    @Test
    void onlyTheX3PowerUpsAreRare() {
        for (PowerUpType type : PowerUpType.values()) {
            boolean x3 = type.name().endsWith("_3X");
            assertEquals(x3 ? 3 : 1, PowerUpStyle.tier(type), type.name());
        }
    }

    @Test
    void everyPowerUpSoundShips() {
        for (PowerUpType type : PowerUpType.values()) {
            assertSoundExists(PowerUpStyle.pickupSound(type));
        }
        for (String sound : List.of(PowerUpStyle.SOUND_SPAWN, PowerUpStyle.SOUND_EXPIRE, PowerUpStyle.SOUND_BUFF_END,
                PowerUpStyle.SOUND_BUFF_WARNING, PowerUpStyle.SOUND_TIER3_SPARKLE)) {
            assertSoundExists(sound);
        }
    }

    private static void assertSoundExists(String base) {
        assertTrue(Files.isRegularFile(RESOURCES.resolve(base + ".ogg")) || Files.isRegularFile(RESOURCES.resolve(base + ".wav")),
                "missing " + base + ".ogg/.wav");
    }

    @Test
    void effectSpritesShip() {
        for (String fx : List.of("glow", "shadow", "ring", "sparkle", "rays", "particle", "tick")) {
            assertTrue(Files.isRegularFile(RESOURCES.resolve("textures/powerups/fx/" + fx + ".png")), fx);
        }
    }
}
