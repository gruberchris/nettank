package org.chrisgruber.nettank.client.game.effects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PowerUpAnimationTest {

    private static final long LIFETIME = 30000;

    @Test
    void spawnPopsInWithAnOvershootAndSettles() {
        assertEquals(0.0f, PowerUpAnimation.spawnScale(0));
        float peak = 0.0f;
        for (long t = 0; t <= PowerUpAnimation.SPAWN_POP_MS; t += 5) {
            peak = Math.max(peak, PowerUpAnimation.spawnScale(t));
        }
        assertTrue(peak > 1.15f && peak < 1.25f, "overshoot peak " + peak);
        assertEquals(1.0f, PowerUpAnimation.spawnScale(PowerUpAnimation.SPAWN_POP_MS));
        assertEquals(1.0f, PowerUpAnimation.spawnScale(10_000));
    }

    @Test
    void solidUntilTheWarningWindowThenBlinksFaster() {
        for (long t = 0; t < LIFETIME - PowerUpAnimation.EXPIRY_WARNING_MS; t += 250) {
            assertEquals(1.0f, PowerUpAnimation.expiryAlpha(t, LIFETIME));
        }
        long warningStart = LIFETIME - PowerUpAnimation.EXPIRY_WARNING_MS;
        int early = blinkTransitions(warningStart, warningStart + 1000);
        int late = blinkTransitions(LIFETIME - 1000, LIFETIME - 1);
        assertTrue(early >= 2, "blinks at the start of the warning: " + early);
        assertTrue(late > early * 2, "blinks speed up toward despawn: early " + early + ", late " + late);
    }

    private static int blinkTransitions(long from, long to) {
        int transitions = 0;
        float previous = PowerUpAnimation.expiryAlpha(from, LIFETIME);
        for (long t = from + 1; t <= to; t++) {
            float alpha = PowerUpAnimation.expiryAlpha(t, LIFETIME);
            if (alpha != previous) transitions++;
            previous = alpha;
        }
        return transitions;
    }

    @Test
    void coinFlipIsBriefAndNeverCollapsesToNothing() {
        int atRest = 0;
        int samples = 0;
        for (long t = 0; t < 26_000; t += 10, samples++) {
            float width = PowerUpAnimation.flipWidth(t, 42);
            assertTrue(width >= 0.08f && width <= 1.0f);
            if (width == 1.0f) atRest++;
        }
        float restFraction = atRest / (float) samples;
        float expected = 1.0f - PowerUpAnimation.FLIP_DURATION_S / PowerUpAnimation.FLIP_INTERVAL_S;
        assertEquals(expected, restFraction, 0.03f);
    }

    @Test
    void shadowShrinksAndFadesAsTheTokenRises() {
        assertTrue(PowerUpAnimation.shadowScale(8.0f) < PowerUpAnimation.shadowScale(2.0f));
        assertTrue(PowerUpAnimation.shadowAlpha(8.0f) < PowerUpAnimation.shadowAlpha(2.0f));
        for (long t = 0; t < 5000; t += 50) {
            float hover = PowerUpAnimation.hoverHeight(t);
            assertTrue(hover >= 2.0f && hover <= 8.0f);
        }
    }

    @Test
    void sparklesStayNearTheTokenAndAreDeterministic() {
        int lit = 0;
        for (long t = 0; t < 6000; t += 20) {
            for (int k = 0; k < PowerUpAnimation.SPARKLE_COUNT; k++) {
                var sparkle = PowerUpAnimation.sparkle(t, 7, k);
                assertTrue(sparkle.alpha() >= 0.0f && sparkle.alpha() <= 1.0f);
                assertTrue(Math.hypot(sparkle.dx(), sparkle.dy()) <= 17.01);
                assertEquals(sparkle, PowerUpAnimation.sparkle(t, 7, k));
                if (sparkle.alpha() > 0.5f) lit++;
            }
        }
        assertTrue(lit > 0, "sparkles twinkle at some point");
    }
}
