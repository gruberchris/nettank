package org.chrisgruber.nettank.client.game.effects;

import org.joml.Vector2f;

/**
 * A pair of dark, semi-transparent quads left behind a moving tank's tracks,
 * fading out over a few seconds. Spawned roughly every 12 px traveled; callers
 * cap the live list and recycle the oldest entries.
 */
public class TrackMarkEffect {

    public static final long DURATION_MS = 4000;

    private final Vector2f leftTrack;
    private final Vector2f rightTrack;
    private final float rotationDegrees;
    private final long createdAtMillis;
    private final float baseAlpha;
    private final float markSize;

    public TrackMarkEffect(Vector2f tankPosition, float hullRotationDegrees, float trackSpacing,
                           float markSize, float baseAlpha) {
        // Tracks sit perpendicular to the hull facing
        float perpRad = (float) Math.toRadians(hullRotationDegrees + 90.0f);
        float dx = -(float) Math.sin(perpRad) * trackSpacing / 2.0f;
        float dy = (float) Math.cos(perpRad) * trackSpacing / 2.0f;

        this.leftTrack = new Vector2f(tankPosition.x + dx, tankPosition.y + dy);
        this.rightTrack = new Vector2f(tankPosition.x - dx, tankPosition.y - dy);
        this.rotationDegrees = hullRotationDegrees;
        this.createdAtMillis = System.currentTimeMillis();
        this.baseAlpha = baseAlpha;
        this.markSize = markSize;
    }

    public boolean isFinished() {
        return System.currentTimeMillis() - createdAtMillis >= DURATION_MS;
    }

    public float getAlpha() {
        float age = (System.currentTimeMillis() - createdAtMillis) / (float) DURATION_MS;
        return Math.max(0.0f, baseAlpha * (1.0f - age));
    }

    public Vector2f getLeftTrack() { return leftTrack; }
    public Vector2f getRightTrack() { return rightTrack; }
    public float getRotationDegrees() { return rotationDegrees; }
    public float getMarkSize() { return markSize; }
}
