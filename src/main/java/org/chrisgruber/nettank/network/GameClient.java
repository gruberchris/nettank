package org.chrisgruber.nettank.network;

import org.chrisgruber.nettank.main.Game;
import org.chrisgruber.nettank.util.GameState;
import org.joml.Vector3f;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

public class GameClient implements Runnable {

    private final String serverIp;
    private final int serverPort;
    private final String playerName;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private volatile boolean running = false;
    private final Game game; // Reference to the main game class for callbacks

    private long lastInputSendTime = 0;
    private static final long INPUT_SEND_INTERVAL_MS = 50; // Send input updates roughly 20 times/sec

    public GameClient(String serverIp, int serverPort, String playerName, Game game) {
        this.serverIp = serverIp;
        this.serverPort = serverPort;
        this.playerName = playerName;
        this.game = game;
    }

    @Override
    public void run() {
        running = true;
        try {
            socket = new Socket(serverIp, serverPort);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            System.out.println("Connected to server: " + serverIp + ":" + serverPort);

            // Send initial connect message
            sendMessage(NetworkProtocol.CONNECT + ";" + playerName);

            String serverMessage;
            while (running && (serverMessage = in.readLine()) != null) {
                parseServerMessage(serverMessage);
            }

        } catch (UnknownHostException e) {
            System.err.println("Server not found: " + e.getMessage());
            if (running) game.connectionFailed("Server not found: " + serverIp);
        } catch (SocketException e) {
            // Handle cases where the server might close the connection abruptly or socket is closed locally
            if (running) {
                System.err.println("SocketException: " + e.getMessage());
                if (e.getMessage().toLowerCase().contains("connection reset")) {
                    game.disconnected();
                } else if (e.getMessage().toLowerCase().contains("socket closed")) {
                    // Expected during shutdown
                    System.out.println("Client socket closed.");
                }
                else {
                    game.connectionFailed("Network error: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("I/O error with server: " + e.getMessage());
                e.printStackTrace();
                game.disconnected(); // Treat as disconnection
            }
        } finally {
            stop(); // Ensure resources are cleaned up
            System.out.println("Client network thread finished.");
        }
    }

    private void parseServerMessage(String message) {
        //System.out.println("Received: " + message); // DEBUG
        try {
            String[] parts = message.split(";");
            if (parts.length == 0) return;

            String command = parts[0];

            switch (command) {
                case NetworkProtocol.ASSIGN_ID:
                    if (parts.length >= 5) {
                        int id = Integer.parseInt(parts[1]);
                        float r = Float.parseFloat(parts[2]);
                        float g = Float.parseFloat(parts[3]);
                        float b = Float.parseFloat(parts[4]);
                        game.setLocalPlayerId(id, r, g, b);
                    }
                    break;
                case NetworkProtocol.NEW_PLAYER:
                    if (parts.length >= 9) {
                        int id = Integer.parseInt(parts[1]);
                        float x = Float.parseFloat(parts[2]);
                        float y = Float.parseFloat(parts[3]);
                        float rot = Float.parseFloat(parts[4]);
                        String name = parts[5];
                        float r = Float.parseFloat(parts[6]);
                        float g = Float.parseFloat(parts[7]);
                        float b = Float.parseFloat(parts[8]);
                        game.addOrUpdateTank(id, x, y, rot, name, r, g, b);
                    }
                    break;
                case NetworkProtocol.PLAYER_UPDATE:
                    if (parts.length >= 5) {
                        int id = Integer.parseInt(parts[1]);
                        float x = Float.parseFloat(parts[2]);
                        float y = Float.parseFloat(parts[3]);
                        float rot = Float.parseFloat(parts[4]);
                        game.updateTankState(id, x, y, rot);
                    }
                    break;
                case NetworkProtocol.PLAYER_LEFT:
                    if (parts.length >= 2) {
                        int id = Integer.parseInt(parts[1]);
                        game.removeTank(id);
                    }
                    break;
                case NetworkProtocol.SHOOT:
                    if (parts.length >= 7) {
                        int ownerId = Integer.parseInt(parts[1]);
                        float x = Float.parseFloat(parts[2]);
                        float y = Float.parseFloat(parts[3]);
                        float dirX = Float.parseFloat(parts[4]);
                        float dirY = Float.parseFloat(parts[5]);
                        // Ignore accuracy param parts[6] for now
                        game.spawnBullet(ownerId, x, y, dirX, dirY);
                    }
                    break;
                case NetworkProtocol.HIT:
                    if (parts.length >= 3) {
                        int targetId = Integer.parseInt(parts[1]);
                        int shooterId = Integer.parseInt(parts[2]);
                        game.handlePlayerHit(targetId, shooterId);
                    }
                    break;
                case NetworkProtocol.DESTROYED:
                    if (parts.length >= 3) {
                        int targetId = Integer.parseInt(parts[1]);
                        int shooterId = Integer.parseInt(parts[2]);
                        game.handlePlayerDestroyed(targetId, shooterId);
                    }
                    break;
                case NetworkProtocol.RESPAWN:
                    if (parts.length >= 4) {
                        int id = Integer.parseInt(parts[1]);
                        float x = Float.parseFloat(parts[2]);
                        float y = Float.parseFloat(parts[3]);
                        // Handled implicitly by NEW_PLAYER or PLAYER_UPDATE in simple model
                        // game.handleRespawn(id, x, y); // Could be explicit if needed
                    }
                    break;
                case NetworkProtocol.PLAYER_LIVES:
                    if(parts.length >= 3) {
                        int id = Integer.parseInt(parts[1]);
                        int lives = Integer.parseInt(parts[2]);
                        game.updatePlayerLives(id, lives);
                    }
                    break;
                case NetworkProtocol.GAME_STATE:
                    if(parts.length >= 3) {
                        try {
                            GameState state = GameState.valueOf(parts[1]);
                            long timeData = Long.parseLong(parts[2]);
                            game.setGameState(state, timeData);
                        } catch (IllegalArgumentException e) {
                            System.err.println("Received invalid game state: " + parts[1]);
                        }
                    }
                    break;
                case NetworkProtocol.ANNOUNCE:
                    if(parts.length >= 2) {
                        game.addAnnouncement(parts[1]);
                    }
                    break;
                case NetworkProtocol.ROUND_OVER: // Sent alongside announcement, contains winner ID
                    if(parts.length >= 4) {
                        // int winnerId = Integer.parseInt(parts[1]);
                        // String winnerName = parts[2];
                        // long finalTime = Long.parseLong(parts[3]);
                        // This data is now primarily in the announcement message for simplicity
                    }
                    break;
                case NetworkProtocol.ERROR_MSG:
                    if(parts.length >= 2) {
                        game.connectionFailed(parts[1]); // Show error from server
                        stop(); // Stop the client connection
                    }
                    break;
                // Add PING/PONG later if needed for connection checking
                default:
                    System.out.println("Unknown message from server: " + message);
            }
        } catch (NumberFormatException e) {
            System.err.println("Failed to parse number in message: " + message + " - " + e.getMessage());
        } catch (ArrayIndexOutOfBoundsException e) {
            System.err.println("Malformed message received: " + message + " - " + e.getMessage());
        } catch (Exception e) { // Catch broader exceptions during parsing
            System.err.println("Error parsing server message '" + message + "': " + e.getMessage());
            e.printStackTrace();
        }
    }

    public synchronized void sendMessage(String message) {
        if (out != null && !out.checkError()) { // Check error prevents spamming logs if pipe is broken
            out.println(message);
            //System.out.println("Sent: " + message); // DEBUG
        } else if (running) {
            System.err.println("Cannot send message, PrintWriter is closed or in error state.");
            // Maybe trigger disconnection logic here?
            // stop(); game.disconnected();
        }
    }

    // Send movement input state
    public void sendInput(boolean w, boolean s, boolean a, boolean d) {
        long now = System.currentTimeMillis();
        // Throttle input sending
        if (now - lastInputSendTime >= INPUT_SEND_INTERVAL_MS) {
            sendMessage(String.format("%s;%b;%b;%b;%b", NetworkProtocol.INPUT, w, s, a, d));
            lastInputSendTime = now;
        }
    }

    // Send shoot command
    public void sendShoot() {
        sendMessage(NetworkProtocol.SHOOT_CMD); // Simple command
    }


    public void stop() {
        System.out.println("Stopping client network connection...");
        running = false;
        try {
            // Close streams first
            if (out != null) {
                out.close();
            }
            if (in != null) {
                in.close();
            }
            // Then close socket
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing client connection: " + e.getMessage());
        } finally {
            out = null;
            in = null;
            socket = null;
            System.out.println("Client connection stopped.");
        }
    }

    public boolean isConnected() {
        return running && socket != null && socket.isConnected() && !socket.isClosed();
    }
}