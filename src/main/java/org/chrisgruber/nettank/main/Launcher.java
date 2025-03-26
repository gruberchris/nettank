package org.chrisgruber.nettank.main;

import org.chrisgruber.nettank.network.GameServer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class Launcher {

    private static final int PORT = 8888;
    private static JFrame frame;
    private static JTextField nameField;
    private static JTextField ipField;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Launcher::createAndShowGUI);
    }

    private static void createAndShowGUI() {
        frame = new JFrame("Tank Battle Launcher");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 250);
        frame.setLocationRelativeTo(null); // Center window

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Player Name
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Player Name:"), gbc);

        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        nameField = new JTextField("Player" + (int)(Math.random() * 1000));
        panel.add(nameField, gbc);

        // Host Button
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 3;
        JButton hostButton = new JButton("Host Game");
        hostButton.addActionListener(Launcher::hostGame);
        panel.add(hostButton, gbc);

        // --- Join Section ---
        JSeparator separator = new JSeparator();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(15, 5, 15, 5);
        panel.add(separator, gbc);
        gbc.insets = new Insets(5, 5, 5, 5); // Reset insets

        // IP Address
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 1;
        panel.add(new JLabel("Host IP Address:"), gbc);

        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        ipField = new JTextField("127.0.0.1"); // Default to localhost
        panel.add(ipField, gbc);

        // Join Button
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 3;
        JButton joinButton = new JButton("Join Game");
        joinButton.addActionListener(Launcher::joinGame);
        panel.add(joinButton, gbc);


        // Display Host IP (Optional)
        try {
            gbc.gridx = 0;
            gbc.gridy = 5;
            gbc.gridwidth = 3;
            gbc.insets = new Insets(10, 5, 5, 5);
            JLabel ipLabel = new JLabel("Your IP: " + InetAddress.getLocalHost().getHostAddress());
            ipLabel.setHorizontalAlignment(SwingConstants.CENTER);
            ipLabel.setForeground(Color.DARK_GRAY);
            panel.add(ipLabel, gbc);
        } catch (UnknownHostException e) {
            // Ignore if we can't get local IP
        }


        frame.getContentPane().add(panel);
        frame.setVisible(true);
    }

    private static void hostGame(ActionEvent e) {
        String playerName = nameField.getText().trim();
        if (playerName.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Player Name cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (playerName.contains(";") || playerName.contains(",")) {
            JOptionPane.showMessageDialog(frame, "Player Name cannot contain ';' or ','.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        frame.setVisible(false);
        frame.dispose();

        // Start the server in a new thread
        new Thread(() -> {
            try {
                GameServer server = new GameServer(PORT);
                server.start(); // Should block until server stops
            } catch (IOException ex) {
                System.err.println("Failed to start server: " + ex.getMessage());
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(null, "Failed to start server: " + ex.getMessage(), "Server Error", JOptionPane.ERROR_MESSAGE);
                    createAndShowGUI(); // Re-show launcher
                });
            }
        }).start();

        // Automatically join the hosted game
        // Small delay to ensure server socket is likely open
        try {
            Thread.sleep(500);
        } catch (InterruptedException ignored) {}
        startGameClient("127.0.0.1", playerName);
    }

    private static void joinGame(ActionEvent e) {
        String playerName = nameField.getText().trim();
        String ipAddress = ipField.getText().trim();

        if (playerName.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Player Name cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (playerName.contains(";") || playerName.contains(",")) {
            JOptionPane.showMessageDialog(frame, "Player Name cannot contain ';' or ','.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (ipAddress.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "IP Address cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        frame.setVisible(false);
        frame.dispose();
        startGameClient(ipAddress, playerName);
    }

    private static void startGameClient(String ipAddress, String playerName) {
        System.out.println("Attempting to start client. Connecting to " + ipAddress + ":" + PORT + " as " + playerName);
        // Start the game client (LWJGL part)
        Game game = new Game(ipAddress, PORT, playerName);
        try {
            game.run();
        } catch (Exception ex) {
            System.err.println("Game client crashed: " + ex.getMessage());
            ex.printStackTrace();
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, "Game client crashed: " + ex.getMessage(), "Client Error", JOptionPane.ERROR_MESSAGE);
                // Optionally re-show launcher or just exit
                System.exit(1);
            });
        }
    }
}
