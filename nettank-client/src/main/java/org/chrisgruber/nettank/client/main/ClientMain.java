package org.chrisgruber.nettank.client.main;

import ch.qos.logback.classic.LoggerContext;
import org.chrisgruber.nettank.client.config.GameConfig;
import org.chrisgruber.nettank.client.game.TankBattleGame; // Import the game implementation
import org.chrisgruber.nettank.client.util.NativeLibraryLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class ClientMain {

    private static final Logger logger = LoggerFactory.getLogger(ClientMain.class);
    private static final Random random = new Random();

    // Default connection settings
    private static final String DEFAULT_HOST = "127.0.0.1"; // Connect to localhost by default
    private static final int DEFAULT_PORT = 5555;

    // Window settings
    private static final String WINDOW_TITLE = "Nettank Client";


    public static void main(String[] args) {
        // Load game configuration
        GameConfig config = GameConfig.load();
        
        String hostIp = DEFAULT_HOST;
        int port = DEFAULT_PORT;
        
        // Use player name from config, but if it's the default "Player", generate a random name
        String playerName = config.playerName;
        if ("Player".equals(playerName)) {
            playerName = "Player" + random.nextInt(1000);
        }

        // Command line arguments override config
        if (args.length >= 1) { hostIp = args[0]; }
        if (args.length >= 2) {
            try { port = Integer.parseInt(args[1]); }
            catch (NumberFormatException e) {
                logger.error("Invalid port number: {}, using default port : {}", args[1], port, e);
            }
        }
        if (args.length >= 3) { 
            playerName = args[2]; // Command line overrides config
        }

        NativeLibraryLoader.loadNativeLibraries();

        logger.info("Starting Nettank Client for {} connecting to {}:{}", playerName, hostIp, port);
        logger.info("Window resolution: {}x{}, Fullscreen: {}, VSync: {}", 
                   config.display.width, config.display.height, 
                   config.display.fullscreen, config.display.vsync);

        // Create and run the game instance with config settings
        TankBattleGame game = null; // Declare outside try

        try {
            game = new TankBattleGame(hostIp, port, playerName, WINDOW_TITLE, 
                                     config.display.width, config.display.height);
            game.run(); // Blocks until the game loop finishes and cleanup runs
        } catch (Exception e) {
            logger.error("!!! Unhandled Critical Error in ClientMain !!!", e);

            if (game != null) {
                try {
                    logger.error("Attempting emergency cleanup...");
                    game.cleanupGame();
                } catch (Exception e2) {
                    logger.error("!!! Error during emergency cleanup !!!", e2);
                }
            }

            logger.error(e.getMessage(), e);

            System.exit(1); // Explicit exit with error code
        } finally {
            shutdownLogging();
        }

        logger.info("ClientMain finished gracefully.");

        System.exit(0); // Explicit exit with success code
    }


    private static void shutdownLogging() {
        logger.info("Shutting down logging framework...");

        try {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            loggerContext.stop();

            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {}

            System.out.println("Logback stopped.");
        } catch (Exception e) {
            System.err.println("Error shutting down logging framework: " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }
}