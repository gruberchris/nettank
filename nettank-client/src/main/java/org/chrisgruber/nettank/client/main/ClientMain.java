package org.chrisgruber.nettank.client.main;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.util.StatusPrinter;
import org.chrisgruber.nettank.client.game.TankBattleGame; // Import the game implementation
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientMain {

    private static final Logger logger = LoggerFactory.getLogger(ClientMain.class);

    // Default connection settings
    private static final String DEFAULT_HOST = "127.0.0.1"; // Connect to localhost by default
    private static final int DEFAULT_PORT = 5555;
    private static final String DEFAULT_NAME = "Player" + (int)(Math.random() * 1000);

    // Window settings
    private static final String WINDOW_TITLE = "Nettank Client";
    private static final int WINDOW_WIDTH = 1280;
    private static final int WINDOW_HEIGHT = 720;


    public static void main(String[] args) {
        // --- Argument Parsing ---
        String hostIp = DEFAULT_HOST;
        int port = DEFAULT_PORT;
        String playerName = DEFAULT_NAME;

        // Simple arg parsing: host port name
        if (args.length >= 1) { hostIp = args[0]; }
        if (args.length >= 2) {
            try { port = Integer.parseInt(args[1]); }
            catch (NumberFormatException e) { System.err.println("Invalid port number: " + args[1] + ". Using default " + port); }
        }
        if (args.length >= 3) { playerName = args[2]; }

        // Print logback status at startup for debugging
        try {
            LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
            StatusPrinter.print(lc);
        } catch (Exception e) {
            System.err.println("Could not print Logback status: " + e.getMessage());
        }

        logger.info("Starting Nettank Client for {} connecting to {}:{}", playerName, hostIp, port);

        // Create and run the game instance
        TankBattleGame game = null; // Declare outside try
        try {
            game = new TankBattleGame(hostIp, port, playerName, WINDOW_TITLE, WINDOW_WIDTH, WINDOW_HEIGHT);
            game.run(); // Blocks until game loop finishes and cleanup runs
            logger.info("TankBattleGame run() method completed.");

        } catch (Throwable t) { // Catch everything including Errors
            logger.error("!!! Unhandled Critical Error in ClientMain !!!", t);
            // Attempt to clean up if game object exists
            if (game != null) {
                try {
                    logger.error("Attempting emergency cleanup...");
                    game.cleanupGame(); // Call game cleanup directly if run failed badly
                    // Engine cleanup might happen via run()'s finally, or might need manual call
                } catch (Throwable cleanupError) {
                    logger.error("!!! Error during emergency cleanup !!!", cleanupError);
                }
            }
            System.err.println("FATAL ERROR: " + t.getMessage());
            t.printStackTrace(System.err);
            // Force exit in case of catastrophic failure
            System.exit(1); // Exit with error code
        } finally {
            // --- Ensure logs are flushed ---
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
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            System.out.println("Logback stopped.");
        } catch (Exception e) {
            System.err.println("Error shutting down logging framework: " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }
}