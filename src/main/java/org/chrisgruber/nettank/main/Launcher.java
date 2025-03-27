package org.chrisgruber.nettank.main;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;

public class Launcher {
    private static final Logger logger = LoggerFactory.getLogger(Launcher.class);
    private static final int PORT = 5555;
    private static JFrame frame;
    private static JTextField nameField;
    private static JTextField ipField;

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--game")) {
            // Extract arguments for the game
            String hostIp = args[1];
            int port = Integer.parseInt(args[2]);
            String playerName = args[3];

            Game game = new Game(hostIp, port, playerName);
            try {
                game.run();
            } catch (Exception ex) {
                logger.error("Game client crashed", ex);
                System.err.println("Game crashed: " + ex.getMessage());
                ex.printStackTrace();
                System.exit(1);
            }
        } else {
            // Start launcher UI
            SwingUtilities.invokeLater(Launcher::createAndShowGUI);
        }
    }

    private static void createAndShowGUI() {
        frame = new JFrame("Tank Battle Launcher");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(450, 300);  // Slightly larger size
        frame.setLocationRelativeTo(null);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);  // More horizontal padding
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Player Name Label
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        panel.add(new JLabel("Player Name:"), gbc);

        // Player Name Field - Full Width
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 1.0;  // Take all available space
        nameField = new JTextField("Player" + (int)(Math.random() * 1000));
        nameField.setColumns(20);  // Suggest minimum width
        panel.add(nameField, gbc);

        // Host Button
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.insets = new Insets(15, 10, 15, 10);
        JButton hostButton = new JButton("Host Game");
        hostButton.addActionListener(Launcher::hostGame);
        panel.add(hostButton, gbc);

        // Separator
        JSeparator separator = new JSeparator();
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.insets = new Insets(5, 10, 5, 10);
        panel.add(separator, gbc);

        // IP Address Label
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 0.0;
        panel.add(new JLabel("Host IP Address:"), gbc);

        // IP Address Field - Full Width
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.weightx = 1.0;  // Take all available space
        ipField = new JTextField("0.0.0.0");
        ipField.setColumns(20);  // Suggest minimum width
        panel.add(ipField, gbc);

        // Join Button
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.insets = new Insets(15, 10, 15, 10);
        JButton joinButton = new JButton("Join Game");
        joinButton.addActionListener(Launcher::joinGame);
        panel.add(joinButton, gbc);

        // Display Host IP (Optional)
        try {
            gbc.gridx = 0;
            gbc.gridy = 7;
            gbc.insets = new Insets(5, 10, 5, 10);
            String ipAddress = getLocalIpAddress();
            JLabel ipLabel = new JLabel("Your IP: " + ipAddress);
            ipLabel.setHorizontalAlignment(SwingConstants.CENTER);
            ipLabel.setForeground(Color.DARK_GRAY);
            panel.add(ipLabel, gbc);
        } catch (Exception e) {
            // Ignore if we can't get local IP
        }

        frame.getContentPane().add(panel);
        frame.setVisible(true);
    }

    private static String getLocalIpAddress() {
        try {
            for (java.net.NetworkInterface ni : java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isLoopback() && ni.isUp()) {
                    for (java.net.InterfaceAddress ia : ni.getInterfaceAddresses()) {
                        if (ia.getAddress() instanceof java.net.Inet4Address) {
                            return ia.getAddress().getHostAddress();
                        }
                    }
                }
            }
        } catch (Exception e) {
            try {
                return InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException ex) {
                return "127.0.0.1";
            }
        }
        // Fallback to localhost if no suitable interface found
        return "127.0.0.1";
    }

    private static void hostGame(ActionEvent e) {
        // Validate player name
        String playerName = nameField.getText().trim();
        if (playerName.isEmpty() || playerName.contains(";") || playerName.contains(",")) {
            JOptionPane.showMessageDialog(frame, "Invalid player name. Cannot be empty or contain ';' or ','.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Close launcher UI
        frame.setVisible(false);
        frame.dispose();

        // Get best available IP address for connections
        String serverIp = getBestLocalIpAddress();

        // Start server in a separate process
        startServerProcess(PORT);

        // Small delay to ensure server socket has time to initialize
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {}

        // Launch the game client in a new process with the macOS flag
        startGameClient(serverIp, playerName);
    }

    private static void startServerProcess(int port) {
        try {
            // Check if port is already in use
            try (ServerSocket testSocket = new ServerSocket(port)) {
                // Port is available, close test socket
                testSocket.close();
            } catch (IOException e) {
                // Port is in use, show error and reopen launcher
                JOptionPane.showMessageDialog(null,
                        "Port " + port + " is already in use. Please close any running servers or try a different port.",
                        "Port Conflict", JOptionPane.ERROR_MESSAGE);
                createAndShowGUI();
                return;
            }

            // Continue with normal server startup...
            String javaHome = System.getProperty("java.home");
            String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
            String classpath = System.getProperty("java.class.path");

            ProcessBuilder builder = new ProcessBuilder(
                    javaBin,
                    "-cp",
                    classpath,
                    "org.chrisgruber.nettank.network.GameServer",
                    String.valueOf(port)
            );

            // Redirect output and error streams to files
            File logDir = new File("logs");
            logDir.mkdirs(); // Ensure the logs directory exists
            File serverLog = new File(logDir, "server-" + System.currentTimeMillis() + ".log");
            File serverErr = new File(logDir, "server-" + System.currentTimeMillis() + ".err");
            builder.redirectOutput(ProcessBuilder.Redirect.to(serverLog));
            builder.redirectError(ProcessBuilder.Redirect.to(serverErr));

            Process process = builder.start();
            logger.info("Server process started. Logs: {} | {}", serverLog.getAbsolutePath(), serverErr.getAbsolutePath());
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null, "Failed to start server: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Helper method to find the best network-accessible IP
    private static String getBestLocalIpAddress() {
        try {
            // Look for a non-loopback IPv4 address
            for (java.net.NetworkInterface ni : java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isLoopback() && ni.isUp()) {
                    for (java.net.InterfaceAddress ia : ni.getInterfaceAddresses()) {
                        if (ia.getAddress() instanceof java.net.Inet4Address) {
                            return ia.getAddress().getHostAddress();
                        }
                    }
                }
            }

            // Fall back to getLocalHost()
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ex) {
            return "127.0.0.1"; // Ultimate fallback
        }
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
        logger.info("Launching game client process...");

        try {
            String javaHome = System.getProperty("java.home");
            String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
            String classpath = System.getProperty("java.class.path");
            String className = Launcher.class.getName();

            ProcessBuilder builder = new ProcessBuilder(
                    javaBin,
                    "-XstartOnFirstThread", // Add the required flag for LWJGL on macOS
                    "-cp",
                    classpath,
                    className,
                    "--game", // Argument to indicate we want to start the game
                    ipAddress,
                    String.valueOf(PORT),
                    playerName
            );

            // Redirect output and error streams to files
            File logDir = new File("logs");
            logDir.mkdirs(); // Ensure the logs directory exists
            String safePlayerName = playerName.replaceAll("[^a-zA-Z0-9.-]", "_"); // Sanitize name for file
            File clientLog = new File(logDir, "client-" + safePlayerName + "-" + System.currentTimeMillis() + ".log");
            File clientErr = new File(logDir, "client-" + safePlayerName + "-" + System.currentTimeMillis() + ".err");
            builder.redirectOutput(ProcessBuilder.Redirect.to(clientLog));
            builder.redirectError(ProcessBuilder.Redirect.to(clientErr));

            Process process = builder.start();

            logger.info("Game client process launch initiated. Logs: {} | {}", clientLog.getAbsolutePath(), clientErr.getAbsolutePath());
        } catch (Exception ex) {
            logger.error("Failed to start game client: {}", ex.getMessage(), ex);
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, "Failed to start game client: " + ex.getMessage(),
                        "Launch Error", JOptionPane.ERROR_MESSAGE);
                createAndShowGUI(); // Re-show launcher on failure to start process
            });
        }
    }
}
