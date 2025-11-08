package org.chrisgruber.nettank.client.engine.network;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class NetworkMessageTest {

    // ========== TankUpdate Tests ==========
    
    @Test
    void testTankUpdateParse_Valid() {
        String[] parts = {"UPD", "42", "150.5", "200.3", "1.57", "true"};
        var msg = NetworkMessage.TankUpdate.parse(parts);
        
        assertEquals(42, msg.id());
        assertEquals(150.5f, msg.x(), 0.001f);
        assertEquals(200.3f, msg.y(), 0.001f);
        assertEquals(1.57f, msg.rotation(), 0.001f);
        assertTrue(msg.respawn());
    }
    
    @Test
    void testTankUpdateParse_InsufficientParts() {
        String[] parts = {"UPD", "42", "150.5"};
        assertThrows(IllegalArgumentException.class, 
            () -> NetworkMessage.TankUpdate.parse(parts));
    }
    
    // ========== BulletFired Tests ==========
    
    @Test
    void testBulletFiredParse_Valid() {
        String[] parts = {"BUL", "123", "10.5", "20.3", "5.0", "3.0", "42"};
        var msg = NetworkMessage.BulletFired.parse(parts);
        
        assertEquals(123, msg.bulletId());
        assertEquals(10.5f, msg.x(), 0.001f);
        assertEquals(20.3f, msg.y(), 0.001f);
        assertEquals(5.0f, msg.vx(), 0.001f);
        assertEquals(3.0f, msg.vy(), 0.001f);
        assertEquals(42, msg.ownerId());
    }
    
    // ========== PlayerHit Tests ==========
    
    @Test
    void testPlayerHitParse_Valid() {
        String[] parts = {"HIT", "10", "20", "30", "25"};
        var msg = NetworkMessage.PlayerHit.parse(parts);
        
        assertEquals(10, msg.targetId());
        assertEquals(20, msg.shooterId());
        assertEquals(30, msg.bulletId());
        assertEquals(25, msg.damage());
    }
    
    @Test
    void testPlayerHitParse_InsufficientParts() {
        String[] parts = {"HIT", "10", "20"};
        assertThrows(IllegalArgumentException.class, 
            () -> NetworkMessage.PlayerHit.parse(parts));
    }
    
    // ========== PlayerDestroyed Tests ==========
    
    @Test
    void testPlayerDestroyedParse_Valid() {
        String[] parts = {"DES", "10", "20"};
        var msg = NetworkMessage.PlayerDestroyed.parse(parts);
        
        assertEquals(10, msg.targetId());
        assertEquals(20, msg.shooterId());
    }
    
    // ========== PlayerDeath Tests ==========
    
    @Test
    void testPlayerDeathParse_Valid() {
        String[] parts = {"DEATH", "5", "8", "Killer", "Victim"};
        var msg = NetworkMessage.PlayerDeath.parse(parts);
        
        assertEquals(5, msg.playerId());
        assertEquals(8, msg.killerId());
        assertEquals("Killer", msg.killerName());
        assertEquals("Victim", msg.victimName());
    }
    
    // ========== GameStateMessage Tests ==========
    
    @Test
    void testGameStateMessageParse_Valid() {
        String[] parts = {"GST", "PLAYING", "12345"};
        var msg = NetworkMessage.GameStateMessage.parse(parts);
        
        assertEquals("PLAYING", msg.stateName());
        assertEquals(12345L, msg.timeData());
    }
    
    @Test
    void testGameStateMessageParse_InsufficientParts() {
        String[] parts = {"GST", "PLAYING"};
        assertThrows(IllegalArgumentException.class, 
            () -> NetworkMessage.GameStateMessage.parse(parts));
    }
    
    // ========== SpectatorMode Tests ==========
    
    @Test
    void testSpectatorModeParse_Valid() {
        String[] parts = {"SPEC", "5000"};
        var msg = NetworkMessage.SpectatorMode.parse(parts);
        
        assertEquals(5000L, msg.durationMs());
    }
    
    // ========== MapInfo Tests ==========
    
    @Test
    void testMapInfoParse_Valid() {
        String[] parts = {"MAP", "100", "80"};
        var msg = NetworkMessage.MapInfo.parse(parts);
        
        assertEquals(100, msg.width());
        assertEquals(80, msg.height());
    }
    
    @Test
    void testMapInfoParse_InsufficientParts() {
        String[] parts = {"MAP", "100"};
        assertThrows(IllegalArgumentException.class, 
            () -> NetworkMessage.MapInfo.parse(parts));
    }
    
    // ========== PlayerLives Tests ==========
    
    @Test
    void testPlayerLivesParse_Valid() {
        String[] parts = {"LIV", "42", "3"};
        var msg = NetworkMessage.PlayerLives.parse(parts);
        
        assertEquals(42, msg.playerId());
        assertEquals(3, msg.lives());
    }
    
    // ========== ShootCooldown Tests ==========
    
    @Test
    void testShootCooldownParse_Valid() {
        String[] parts = {"CDN", "1500"};
        var msg = NetworkMessage.ShootCooldown.parse(parts);
        
        assertEquals(1500L, msg.cooldownMs());
    }
    
    // ========== Announcement Tests ==========
    
    @Test
    void testAnnouncementParse_Valid() {
        String[] parts = {"ANN", "Welcome to the game!"};
        var msg = NetworkMessage.Announcement.parse(parts);
        
        assertEquals("Welcome to the game!", msg.message());
    }
    
    @Test
    void testAnnouncementParse_InsufficientParts() {
        String[] parts = {"ANN"};
        assertThrows(IllegalArgumentException.class, 
            () -> NetworkMessage.Announcement.parse(parts));
    }
    
    // ========== PlayerId Tests ==========
    
    @Test
    void testPlayerIdParse_Valid() {
        String[] parts = {"AID", "99"};
        var msg = NetworkMessage.PlayerId.parse(parts);
        
        assertEquals(99, msg.id());
    }
    
    // ========== NewPlayer Tests ==========
    
    @Test
    void testNewPlayerParse_Valid() {
        String[] parts = {"NEW", "42", "100.5", "200.3", "1.57", "Player1", "1.0", "0.5", "0.0"};
        var msg = NetworkMessage.NewPlayer.parse(parts);
        
        assertEquals(42, msg.id());
        assertEquals(100.5f, msg.x(), 0.001f);
        assertEquals(200.3f, msg.y(), 0.001f);
        assertEquals(1.57f, msg.rotation(), 0.001f);
        assertEquals("Player1", msg.name());
        assertEquals(1.0f, msg.colorR(), 0.001f);
        assertEquals(0.5f, msg.colorG(), 0.001f);
        assertEquals(0.0f, msg.colorB(), 0.001f);
    }
    
    @Test
    void testNewPlayerParse_InsufficientParts() {
        String[] parts = {"NEW", "42", "100.5", "200.3"};
        assertThrows(IllegalArgumentException.class, 
            () -> NetworkMessage.NewPlayer.parse(parts));
    }
    
    // ========== PlayerUpdate Tests ==========
    
    @Test
    void testPlayerUpdateParse_Valid() {
        String[] parts = {"UPD", "42", "150.5", "200.3", "1.57"};
        var msg = NetworkMessage.PlayerUpdate.parse(parts);
        
        assertEquals(42, msg.id());
        assertEquals(150.5f, msg.x(), 0.001f);
        assertEquals(200.3f, msg.y(), 0.001f);
        assertEquals(1.57f, msg.rotation(), 0.001f);
    }
    
    // ========== PlayerLeft Tests ==========
    
    @Test
    void testPlayerLeftParse_Valid() {
        String[] parts = {"LEF", "42"};
        var msg = NetworkMessage.PlayerLeft.parse(parts);
        
        assertEquals(42, msg.id());
    }
    
    // ========== Shoot Tests ==========
    
    @Test
    void testShootParse_Valid() {
        UUID bulletId = UUID.randomUUID();
        String[] parts = {"SHO", bulletId.toString(), "42", "100.5", "200.3", "5.0", "3.0"};
        var msg = NetworkMessage.Shoot.parse(parts);
        
        assertEquals(bulletId, msg.bulletId());
        assertEquals(42, msg.ownerId());
        assertEquals(100.5f, msg.x(), 0.001f);
        assertEquals(200.3f, msg.y(), 0.001f);
        assertEquals(5.0f, msg.dirX(), 0.001f);
        assertEquals(3.0f, msg.dirY(), 0.001f);
    }
    
    @Test
    void testShootParse_InvalidUUID() {
        String[] parts = {"SHO", "not-a-uuid", "42", "100.5", "200.3", "5.0", "3.0"};
        assertThrows(IllegalArgumentException.class, 
            () -> NetworkMessage.Shoot.parse(parts));
    }
    
    // ========== Hit Tests ==========
    
    @Test
    void testHitParse_Valid() {
        UUID bulletId = UUID.randomUUID();
        String[] parts = {"HIT", "10", "20", bulletId.toString(), "25"};
        var msg = NetworkMessage.Hit.parse(parts);
        
        assertEquals(10, msg.targetId());
        assertEquals(20, msg.shooterId());
        assertEquals(bulletId, msg.bulletId());
        assertEquals(25, msg.damage());
    }
    
    // ========== Destroyed Tests ==========
    
    @Test
    void testDestroyedParse_Valid() {
        String[] parts = {"DES", "10", "20"};
        var msg = NetworkMessage.Destroyed.parse(parts);
        
        assertEquals(10, msg.targetId());
        assertEquals(20, msg.shooterId());
    }
    
    // ========== Respawn Tests ==========
    
    @Test
    void testRespawnParse_Valid() {
        String[] parts = {"RSP", "42", "100.5", "200.3", "1.57"};
        var msg = NetworkMessage.Respawn.parse(parts);
        
        assertEquals(42, msg.id());
        assertEquals(100.5f, msg.x(), 0.001f);
        assertEquals(200.3f, msg.y(), 0.001f);
        assertEquals(1.57f, msg.rotation(), 0.001f);
    }
    
    // ========== RoundOver Tests ==========
    
    @Test
    void testRoundOverParse_Valid() {
        String[] parts = {"ROV", "42", "Winner", "60000"};
        var msg = NetworkMessage.RoundOver.parse(parts);
        
        assertEquals(42, msg.winnerId());
        assertEquals("Winner", msg.winnerName());
        assertEquals(60000L, msg.finalTimeMillis());
    }
    
    // ========== ErrorMessage Tests ==========
    
    @Test
    void testErrorMessageParse_Valid() {
        String[] parts = {"ERR", "Connection failed"};
        var msg = NetworkMessage.ErrorMessage.parse(parts);
        
        assertEquals("Connection failed", msg.errorText());
    }
    
    // ========== Parameterized Tests for Number Parsing ==========
    
    @ParameterizedTest
    @MethodSource("provideInvalidNumberFormats")
    void testInvalidNumberParsing(String[] parts, Class<? extends NetworkMessage> recordClass) {
        assertThrows(NumberFormatException.class, () -> {
            if (recordClass == NetworkMessage.PlayerUpdate.class) {
                NetworkMessage.PlayerUpdate.parse(parts);
            } else if (recordClass == NetworkMessage.PlayerLives.class) {
                NetworkMessage.PlayerLives.parse(parts);
            }
        });
    }
    
    private static Stream<Arguments> provideInvalidNumberFormats() {
        return Stream.of(
            Arguments.of(new String[]{"UPD", "not-a-number", "150.5", "200.3", "1.57"}, 
                        NetworkMessage.PlayerUpdate.class),
            Arguments.of(new String[]{"LIV", "42", "not-a-number"}, 
                        NetworkMessage.PlayerLives.class)
        );
    }
    
    // ========== Record Immutability Tests ==========
    
    @Test
    void testRecordsAreImmutable() {
        String[] parts = {"UPD", "42", "150.5", "200.3", "1.57"};
        var msg1 = NetworkMessage.PlayerUpdate.parse(parts);
        var msg2 = NetworkMessage.PlayerUpdate.parse(parts);
        
        // Records with same values should be equal
        assertEquals(msg1, msg2);
        assertEquals(msg1.hashCode(), msg2.hashCode());
    }
    
    @Test
    void testRecordToString() {
        String[] parts = {"LIV", "42", "3"};
        var msg = NetworkMessage.PlayerLives.parse(parts);
        
        String toString = msg.toString();
        assertTrue(toString.contains("42"));
        assertTrue(toString.contains("3"));
    }
    
    // ========== Edge Cases ==========
    
    @Test
    void testNegativeNumbers() {
        String[] parts = {"UPD", "-1", "-150.5", "-200.3", "-1.57"};
        var msg = NetworkMessage.PlayerUpdate.parse(parts);
        
        assertEquals(-1, msg.id());
        assertEquals(-150.5f, msg.x(), 0.001f);
        assertEquals(-200.3f, msg.y(), 0.001f);
        assertEquals(-1.57f, msg.rotation(), 0.001f);
    }
    
    @Test
    void testZeroValues() {
        String[] parts = {"UPD", "0", "0.0", "0.0", "0.0"};
        var msg = NetworkMessage.PlayerUpdate.parse(parts);
        
        assertEquals(0, msg.id());
        assertEquals(0.0f, msg.x(), 0.001f);
        assertEquals(0.0f, msg.y(), 0.001f);
        assertEquals(0.0f, msg.rotation(), 0.001f);
    }
    
    @Test
    void testLargeNumbers() {
        String[] parts = {"LIV", "999999", "100"};
        var msg = NetworkMessage.PlayerLives.parse(parts);
        
        assertEquals(999999, msg.playerId());
        assertEquals(100, msg.lives());
    }
    
    @Test
    void testEmptyStringInMessage() {
        String[] parts = {"NEW", "42", "100.5", "200.3", "1.57", "", "1.0", "0.5", "0.0"};
        var msg = NetworkMessage.NewPlayer.parse(parts);
        
        assertEquals("", msg.name());
    }
    
    @Test
    void testSpecialCharactersInText() {
        String[] parts = {"ANN", "Player! @#$%^&*() won the round!"};
        var msg = NetworkMessage.Announcement.parse(parts);
        
        assertEquals("Player! @#$%^&*() won the round!", msg.message());
    }
}
