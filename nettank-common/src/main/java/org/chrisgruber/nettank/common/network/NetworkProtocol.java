package org.chrisgruber.nettank.common.network;

// Define constants for network messages
public class NetworkProtocol {

    // Client to Server Messages
    public static final String CONNECT = "CON";      // CON;<playerName>
    public static final String INPUT = "INP";        // INP;<W_down>;<S_down>;<A_down>;<D_down>
    public static final String SHOOT_CMD = "SHT";    // SHT (Command to shoot)
    public static final String PING = "PIN";         // PIN (Optional)

    // Server to Client Messages
    public static final String ASSIGN_ID = "AID";    // AID;<yourId>;<colorR>;<colorG>;<colorB> // REMOVED isHost
    public static final String NEW_PLAYER = "NEW";   // NEW;<id>;<x>;<y>;<rot>;<name>;<r>;<g>;<b> // Lives sent separately
    public static final String PLAYER_UPDATE = "UPD"; // UPD;<id>;<x>;<y>;<rot>
    public static final String PLAYER_LEFT = "LEF";  // LEF;<id>
    public static final String SHOOT = "SHO";        // SHO;<ownerId>;<x>;<y>;<dirX>;<dirY>
    public static final String HIT = "HIT";          // HIT;<targetId>;<shooterId>
    public static final String DESTROYED = "DES";    // DES;<targetId>;<shooterId>
    public static final String RESPAWN = "RSP";      // RSP;<id>;<x>;<y>
    public static final String PLAYER_LIVES = "LIV"; // LIV;<id>;<lives>
    public static final String GAME_STATE = "GST";   // GST;<stateName>;<timeData>
    public static final String ANNOUNCE = "ANN";     // ANN;<messageText>
    public static final String ROUND_OVER = "ROV";   // ROV;<winnerId>;<winnerName>;<finalTimeMillis> (-1 ID for draw)
    public static final String PONG = "PON";         // PON (Optional response to PING)
    public static final String ERROR_MSG = "ERR";    // ERR;<errorMessage>
    public static final String SPECTATE_START = "SPECTATE";         // SPECTATE;<playerId>;<respawnTimeMillis>
    public static final String SPECTATE_END = "SPEC_END";           // SPECTATE_END;<playerId>
    public static final String SPECTATE_PERMANENT = "SPEC_PERM";    // SPECTATE_PERM;<playerId>
    public static final String MAP_INFO = "MAP";     // MAP;<widthTiles>;<heightTiles>;<tileSize>
}