package org.chrisgruber.nettank.server.world;

import org.chrisgruber.nettank.common.world.GameMapData;
import org.chrisgruber.nettank.common.world.TerrainTile;
import org.chrisgruber.nettank.common.world.VisionBlockingType;
import org.joml.Vector2f;

/**
 * Tile-based line-of-sight: a Bresenham walk over the terrain grid between viewer
 * and target. FULL-blocking tiles (MOUNTAIN, ROCKS) stop sight entirely; PARTIAL
 * tiles (FOREST, HILL) halve the view distance through them, modeled by counting
 * their traversal at double weight against the viewer's sight radius.
 */
public final class LineOfSightCalculator {

    private LineOfSightCalculator() {}

    public static boolean canSee(GameMapData mapData, Vector2f viewerPosition, Vector2f targetPosition, float sightRadius) {
        float distance = viewerPosition.distance(targetPosition);
        if (distance > sightRadius) return false;

        float tileSize = mapData.getTileSize();
        int x0 = (int) (viewerPosition.x / tileSize);
        int y0 = (int) (viewerPosition.y / tileSize);
        int x1 = (int) (targetPosition.x / tileSize);
        int y1 = (int) (targetPosition.y / tileSize);

        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int stepX = x0 < x1 ? 1 : -1;
        int stepY = y0 < y1 ? 1 : -1;
        int error = dx - dy;

        int x = x0;
        int y = y0;
        float effectiveDistance = 0.0f;

        while (x != x1 || y != y1) {
            int e2 = 2 * error;
            if (e2 > -dy) {
                error -= dy;
                x += stepX;
            }
            if (e2 < dx) {
                error += dx;
                y += stepY;
            }

            // The target's own tile never blocks: a tank at a forest edge is spottable
            if (x == x1 && y == y1) break;

            TerrainTile tile = mapData.getTile(x, y);
            if (tile == null) continue;

            VisionBlockingType blocking = tile.getEffectiveType().getVisionBlocking();
            if (blocking == VisionBlockingType.FULL) return false;

            // PARTIAL tiles consume sight range at double rate (halved view distance)
            effectiveDistance += tileSize * (blocking == VisionBlockingType.PARTIAL ? 2.0f : 1.0f);
            if (effectiveDistance > sightRadius) return false;
        }

        return true;
    }
}
