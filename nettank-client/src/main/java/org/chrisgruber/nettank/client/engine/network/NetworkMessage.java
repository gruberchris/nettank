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
    
    record GameStateMessage(
        String stateName,
        long timeData
    ) implements NetworkMessage {
        public static GameStateMessage parse(String[] parts) {
            if (parts.length < 3) {
                throw new IllegalArgumentException("Invalid GameStateMessage message: insufficient parts");
            }
            return new GameStateMessage(parts[1], Long.parseLong(parts[2]));
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
    
    record TerrainInit(
        long seed,
        String profileName
    ) implements NetworkMessage {
        public static TerrainInit parse(String[] parts) {
            if (parts.length < 3) {
                throw new IllegalArgumentException("Invalid TerrainInit message: insufficient parts");
            }
            return new TerrainInit(
                Long.parseLong(parts[1]),
                parts[2]
            );
        }
    }
    
    record TerrainData(
        int width,
        int height,
        String encodedData
    ) implements NetworkMessage {
        public static TerrainData parse(String[] parts) {
            if (parts.length < 4) {
                throw new IllegalArgumentException("Invalid TerrainData message: insufficient parts");
            }
            return new TerrainData(
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                parts[3]
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
    
    record NewPlayer(
        int id,
        float x,
        float y,
        float rotation,
        String name,
        float colorR,
        float colorG,
        float colorB
    ) implements NetworkMessage {
        public static NewPlayer parse(String[] parts) {
            if (parts.length < 9) {
                throw new IllegalArgumentException("Invalid NewPlayer message: insufficient parts");
            }
            return new NewPlayer(
                Integer.parseInt(parts[1]),
                Float.parseFloat(parts[2]),
                Float.parseFloat(parts[3]),
                Float.parseFloat(parts[4]),
                parts[5],
                Float.parseFloat(parts[6]),
                Float.parseFloat(parts[7]),
                Float.parseFloat(parts[8])
            );
        }
    }
    
    record PlayerUpdate(
        int id,
        float x,
        float y,
        float rotation
    ) implements NetworkMessage {
        public static PlayerUpdate parse(String[] parts) {
            if (parts.length < 5) {
                throw new IllegalArgumentException("Invalid PlayerUpdate message: insufficient parts");
            }
            return new PlayerUpdate(
                Integer.parseInt(parts[1]),
                Float.parseFloat(parts[2]),
                Float.parseFloat(parts[3]),
                Float.parseFloat(parts[4])
            );
        }
    }
    
    record PlayerLeft(
        int id
    ) implements NetworkMessage {
        public static PlayerLeft parse(String[] parts) {
            if (parts.length < 2) {
                throw new IllegalArgumentException("Invalid PlayerLeft message: insufficient parts");
            }
            return new PlayerLeft(Integer.parseInt(parts[1]));
        }
    }
    
    record Shoot(
        java.util.UUID bulletId,
        int ownerId,
        float x,
        float y,
        float dirX,
        float dirY
    ) implements NetworkMessage {
        public static Shoot parse(String[] parts) {
            if (parts.length < 7) {
                throw new IllegalArgumentException("Invalid Shoot message: insufficient parts");
            }
            return new Shoot(
                java.util.UUID.fromString(parts[1]),
                Integer.parseInt(parts[2]),
                Float.parseFloat(parts[3]),
                Float.parseFloat(parts[4]),
                Float.parseFloat(parts[5]),
                Float.parseFloat(parts[6])
            );
        }
    }
    
    record Hit(
        int targetId,
        int shooterId,
        java.util.UUID bulletId,
        int damage
    ) implements NetworkMessage {
        public static Hit parse(String[] parts) {
            if (parts.length < 5) {
                throw new IllegalArgumentException("Invalid Hit message: insufficient parts");
            }
            return new Hit(
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                java.util.UUID.fromString(parts[3]),
                Integer.parseInt(parts[4])
            );
        }
    }
    
    record Destroyed(
        int targetId,
        int shooterId
    ) implements NetworkMessage {
        public static Destroyed parse(String[] parts) {
            if (parts.length < 3) {
                throw new IllegalArgumentException("Invalid Destroyed message: insufficient parts");
            }
            return new Destroyed(
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])
            );
        }
    }
    
    record Respawn(
        int id,
        float x,
        float y,
        float rotation
    ) implements NetworkMessage {
        public static Respawn parse(String[] parts) {
            if (parts.length < 5) {
                throw new IllegalArgumentException("Invalid Respawn message: insufficient parts");
            }
            return new Respawn(
                Integer.parseInt(parts[1]),
                Float.parseFloat(parts[2]),
                Float.parseFloat(parts[3]),
                Float.parseFloat(parts[4])
            );
        }
    }
    
    record RoundOver(
        int winnerId,
        String winnerName,
        long finalTimeMillis
    ) implements NetworkMessage {
        public static RoundOver parse(String[] parts) {
            if (parts.length < 4) {
                throw new IllegalArgumentException("Invalid RoundOver message: insufficient parts");
            }
            return new RoundOver(
                Integer.parseInt(parts[1]),
                parts[2],
                Long.parseLong(parts[3])
            );
        }
    }
    
    record ErrorMessage(
        String errorText
    ) implements NetworkMessage {
        public static ErrorMessage parse(String[] parts) {
            if (parts.length < 2) {
                throw new IllegalArgumentException("Invalid ErrorMessage message: insufficient parts");
            }
            return new ErrorMessage(parts[1]);
        }
    }
}
