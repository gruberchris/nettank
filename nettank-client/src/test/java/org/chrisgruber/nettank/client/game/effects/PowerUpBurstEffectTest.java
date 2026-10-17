package org.chrisgruber.nettank.client.game.effects;

import org.joml.Vector2f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PowerUpBurstEffectTest {

    private static final Vector2f ORIGIN = new Vector2f(100, 200);
    private static final Vector3f RED = new Vector3f(1, 0.2f, 0.2f);

    @Test
    void pickupBurstExpandsFadesAndFinishes() {
        var burst = new PowerUpBurstEffect(PowerUpBurstEffect.Kind.PICKUP, ORIGIN, RED, 1000, 3);
        assertFalse(burst.isFinished(1000));
        assertTrue(burst.ringRadius(1300) > burst.ringRadius(1050));
        assertTrue(burst.ringAlpha(1300) < burst.ringAlpha(1050));
        assertTrue(burst.flashAlpha(1000) > 0.9f);
        assertTrue(burst.isFinished(1000 + 650));
    }

    @Test
    void particlesFlyOutwardAndFade() {
        var burst = new PowerUpBurstEffect(PowerUpBurstEffect.Kind.PICKUP, ORIGIN, RED, 0, 3);
        for (int i = 0; i < burst.particleCount(); i++) {
            var early = burst.particle(i, 50);
            var late = burst.particle(i, 400);
            float earlyDistance = ORIGIN.distance(early.x(), early.y());
            float lateDistance = ORIGIN.distance(late.x(), late.y());
            assertTrue(lateDistance > earlyDistance, "particle " + i + " moves outward");
            assertTrue(late.alpha() < early.alpha());
        }
    }

    @Test
    void expiryPuffIsGreyRisesAndHasNoFlash() {
        var puff = new PowerUpBurstEffect(PowerUpBurstEffect.Kind.EXPIRE, ORIGIN, RED, 0, 3);
        assertEquals(0.0f, puff.flashAlpha(0));
        assertTrue(puff.getColor().y > 0.5f, "red is pulled toward grey");
        float averageRise = 0.0f;
        for (int i = 0; i < puff.particleCount(); i++) {
            averageRise += puff.particle(i, 500).y() - puff.particle(i, 0).y();
        }
        assertTrue(averageRise / puff.particleCount() > 5.0f, "the puff drifts upward");
    }
}
