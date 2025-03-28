package org.chrisgruber.nettank.network;

// Define constants for network messages
public class NetworkProtocol {

    // Client to Server Messages
    public static final String CONNECT = "CON";             // CON;<playerName>
    public static final String INPUT = "INP";               // INP;<W_down>;<S_down>;<A_down>;<D_down>
    public static final String SHOOT_CMD = "SHT";           // SHT (Command to shoot)
    public static final String PING = "PIN";                // PIN (Optional)
    public static final String SHUTDOWN_SERVER = "SSD";     // SSD (Client requests server shutdown)

    // Server to Client Messages
    public static final String ASSIGN_ID = "AID";           // AID;<yourId>;<colorR>;<colorG>;<colorB>;<isHostBoolean>
    public static final String NEW_PLAYER = "NEW";          // NEW;<id>;<x>;<y>;<rot>;<name>;<r>;<g>;<b>
    public static final String PLAYER_UPDATE = "UPD";       // UPD;<id>;<x>;<y>;<rot>
    public static final String PLAYER_LEFT = "LEF";         // LEF;<id>
    public static final String SHOOT = "SHO";               // SHO;<ownerId>;<x>;<y>;<dirX>;<dirY>
    public static final String HIT = "HIT";                 // HIT;<targetId>;<shooterId>
    public static final String DESTROYED = "DES";           // DES;<targetId>;<shooterId>
    public static final String RESPAWN = "RSP";             // RSP;<id>;<x>;<y> (Server tells client new pos after death/round start)
    public static final String PLAYER_LIVES = "LIV";        // LIV;<id>;<lives>
    public static final String GAME_STATE = "GST";          // GST;<stateName>;<timeData> (timeData depends on state)
    public static final String ANNOUNCE = "ANN";            // ANN;<messageText>
    public static final String ROUND_OVER = "ROV";          // ROV;<winnerId>;<winnerName>;<finalTimeMillis> (-1 ID for draw)
    public static final String PONG = "PON";                // PON (Optional response to PING)
    public static final String ERROR_MSG = "ERR";           // ERR;<errorMessage> (e.g., server full, name taken etc)

}
