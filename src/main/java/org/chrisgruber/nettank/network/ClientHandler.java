package org.chrisgruber.nettank.network;

import org.joml.Vector3f;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;

public class ClientHandler implements Runnable {

    private Socket socket;
    private GameServer server;
    private PrintWriter out;
    private BufferedReader in;
    private volatile boolean running = false;

    // Player specific info - set after successful registration
    private int playerId = -1;
    private String playerName = null;
    private Vector3f playerColor = null;


    public ClientHandler(Socket socket, GameServer server) {
        this.socket = socket;
        this.server = server;
    }

    // Called by GameServer after successful registration
    public void setPlayerInfo(int id, String name, Vector3f color) {
        this.playerId = id;
        this.playerName = name;
        this.playerColor = color;
    }

    @Override
    public void run() {
        running = true;
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            String clientMessage;
            while (running && (clientMessage = in.readLine()) != null) {
                parseClientMessage(clientMessage);
            }

        } catch (SocketException e) {
            if (running) {
                System.out.println("ClientHandler SocketException for Player " + playerId + ": " + e.getMessage());
                // Common causes: client disconnected abruptly, network issue
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("ClientHandler IOException for Player " + playerId + ": " + e.getMessage());
                e.printStackTrace();
            }
        } finally {
            closeConnection("Connection closed");
        }
    }

    private void parseClientMessage(String message) {
        // System.out.println("Received from client " + playerId + ": " + message); // DEBUG
        try {
            String[] parts = message.split(";");
            if (parts.length == 0) return;

            String command = parts[0];

            // Handle messages only AFTER player ID is assigned, except for CONNECT
            if (playerId == -1 && !command.equals(NetworkProtocol.CONNECT)) {
                System.err.println("Received message before registration: " + message);
                return; // Ignore messages until registered
            }


            switch (command) {
                case NetworkProtocol.CONNECT:
                    if (parts.length >= 2 && playerId == -1) { // Allow only once before registered
                        String name = parts[1];
                        // Validate name length/characters?
                        server.registerPlayer(this, name);
                    }
                    break;

                case NetworkProtocol.INPUT:
                    if (parts.length >= 5) {
                        boolean w = Boolean.parseBoolean(parts[1]);
                        boolean s = Boolean.parseBoolean(parts[2]);
                        boolean a = Boolean.parseBoolean(parts[3]);
                        boolean d = Boolean.parseBoolean(parts[4]);
                        server.handlePlayerInput(playerId, w, s, a, d);
                    }
                    break;

                case NetworkProtocol.SHOOT_CMD:
                    server.handlePlayerShoot(playerId);
                    break;

                // Handle PING later if needed
                // case NetworkProtocol.PING:
                //     sendMessage(NetworkProtocol.PONG);
                //     break;

                default:
                    System.out.println("Unknown message from client " + playerId + ": " + message);
            }
        } catch (Exception e) { // Catch parsing errors
            System.err.println("Error parsing client message from " + playerId + " ('" + message + "'): " + e.getMessage());
        }
    }

    public void sendMessage(String message) {
        if (running && out != null && !out.checkError()) {
            out.println(message);
        } else {
            // System.err.println("Cannot send message to player " + playerId + ", writer closed or error.");
            // If we can't send, the connection is likely dead, trigger cleanup
            closeConnection("Error sending message");
        }
    }

    public void closeConnection(String reason) {
        if (running) {
            running = false; // Stop the loop and prevent further sends
            System.out.println("Closing connection for player " + playerId + ". Reason: " + reason);
            try {
                // Close streams first
                if (out != null) out.close();
                if (in != null) in.close();
                // Then close socket
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                System.err.println("Error closing client handler for player " + playerId + ": " + e.getMessage());
            } finally {
                out = null;
                in = null;
                socket = null;
                // Important: Notify the server to remove the player *after* cleaning up
                if (playerId != -1) { // Only remove if registration was completed
                    server.removePlayer(playerId);
                }
                System.out.println("Finished closing connection for player " + playerId);
            }

        }
    }


    public int getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }
}
