package org.chrisgruber.nettank.client.engine.network;

/**
 * Record types for type-safe network message parsing
 */
public sealed interface NetworkMessage {
    
    record TankUpdate(
        int id, 
        float x, 
        float y, 
        float rotation, 
        boolean respawn
    ) implements NetworkMessage {
        public static TankUpdate parse(String[] parts) {
            if (parts.length < 6) {
                throw new IllegalArgumentException("Invalid TankUpdate message: insufficient parts");
            }
            return new TankUpdate(
                Integer.parseInt(parts[1]),
                Float.parseFloat(parts[2]),
                Float.parseFloat(parts[3]),
                Float.parseFloat(parts[4]),
                Boolean.parseBoolean(parts[5])
            );
        }
    }
    
    record BulletFired(
        int bulletId,
        float x,
        float y,
        float vx,
        float vy,
        int ownerId
    ) implements NetworkMessage {
        public static BulletFired parse(String[] parts) {
            if (parts.length < 7) {
                throw new IllegalArgumentException("Invalid BulletFired message: insufficient parts");
            }
            return new BulletFired(
                Integer.parseInt(parts[1]),
                Float.parseFloat(parts[2]),
                Float.parseFloat(parts[3]),
                Float.parseFloat(parts[4]),
                Float.parseFloat(parts[5]),
                Integer.parseInt(parts[6])
            );
        }
    }
    
    record PlayerHit(
        int targetId,
        int shooterId,
        int bulletId,
        int damage
    ) implements NetworkMessage {
        public static PlayerHit parse(String[] parts) {
            if (parts.length < 5) {
                throw new IllegalArgumentException("Invalid PlayerHit message: insufficient parts");
            }
            return new PlayerHit(
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3]),
                Integer.parseInt(parts[4])
            );
        }
    }
    
    record PlayerDestroyed(
        int targetId,
        int shooterId
    ) implements NetworkMessage {
        public static PlayerDestroyed parse(String[] parts) {
            if (parts.length < 3) {
                throw new IllegalArgumentException("Invalid PlayerDestroyed message: insufficient parts");
            }
            return new PlayerDestroyed(
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])
            );
        }
    }
    
    record PlayerDeath(
        int playerId,
        int killerId,
        String killerName,
        String victimName
    ) implements NetworkMessage {
        public static PlayerDeath parse(String[] parts) {
            if (parts.length < 5) {
                throw new IllegalArgumentException("Invalid PlayerDeath message: insufficient parts");
            }
            return new PlayerDeath(
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                parts[3],
                parts[4]
            );
        }
    }
    
    record GameState(
        String state
    ) implements NetworkMessage {
        public static GameState parse(String[] parts) {
            if (parts.length < 2) {
                throw new IllegalArgumentException("Invalid GameState message: insufficient parts");
            }
            return new GameState(parts[1]);
        }
    }
    
    record SpectatorMode(
        long durationMs
    ) implements NetworkMessage {
        public static SpectatorMode parse(String[] parts) {
            if (parts.length < 2) {
                throw new IllegalArgumentException("Invalid SpectatorMode message: insufficient parts");
            }
            return new SpectatorMode(Long.parseLong(parts[1]));
        }
    }
    
    record MapInfo(
        int width,
        int height
    ) implements NetworkMessage {
        public static MapInfo parse(String[] parts) {
            if (parts.length < 3) {
                throw new IllegalArgumentException("Invalid MapInfo message: insufficient parts");
            }
            return new MapInfo(
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])
            );
        }
    }
    
    record PlayerLives(
        int playerId,
        int lives
    ) implements NetworkMessage {
        public static PlayerLives parse(String[] parts) {
            if (parts.length < 3) {
                throw new IllegalArgumentException("Invalid PlayerLives message: insufficient parts");
            }
            return new PlayerLives(
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])
            );
        }
    }
    
    record ShootCooldown(
        long cooldownMs
    ) implements NetworkMessage {
        public static ShootCooldown parse(String[] parts) {
            if (parts.length < 2) {
                throw new IllegalArgumentException("Invalid ShootCooldown message: insufficient parts");
            }
            return new ShootCooldown(Long.parseLong(parts[1]));
        }
    }
    
    record Announcement(
        String message
    ) implements NetworkMessage {
        public static Announcement parse(String[] parts) {
            if (parts.length < 2) {
                throw new IllegalArgumentException("Invalid Announcement message: insufficient parts");
            }
            return new Announcement(parts[1]);
        }
    }
    
    record PlayerId(
        int id
    ) implements NetworkMessage {
        public static PlayerId parse(String[] parts) {
            if (parts.length < 2) {
                throw new IllegalArgumentException("Invalid PlayerId message: insufficient parts");
            }
            return new PlayerId(Integer.parseInt(parts[1]));
        }
    }
}
