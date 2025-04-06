package org.chrisgruber.nettank.client.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URLDecoder;
import java.nio.file.*;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Handles extracting native libraries bundled within the JAR file
 * to a temporary directory and configures LWJGL to use them.
 */
public class NativeLibraryLoader {

    private static final Logger logger = LoggerFactory.getLogger(NativeLibraryLoader.class);
    private static boolean loaded = false; // Ensure loading happens only once
    private static Path tempDir = null; // Store the temp directory path

    /**
     * Loads native libraries required by the application (specifically LWJGL).
     * <p>
     * This method performs the following steps:
     * 1. Checks if libraries have already been loaded.
     * 2. Determines the path to the running JAR file.
     * 3. Creates a unique temporary directory.
     * 4. Identifies the required native library file extension (.dll, .so, .dylib) based on the OS.
     * 5. Extracts all files with the matching extension from the JAR into the temporary directory.
     * 6. Sets the `org.lwjgl.librarypath` system property to the temporary directory path.
     * 7. Registers a shutdown hook to attempt cleanup of the temporary directory on JVM exit.
     * <p>
     * This method should be called **early** in the application startup, before any LWJGL classes are initialized.
     *
     * @throws RuntimeException if loading fails (e.g., cannot create temp dir, read JAR, extract files).
     */
    public static synchronized void loadNativeLibraries() {
        if (loaded) {
            logger.debug("Native libraries already loaded or loading skipped.");
            return;
        }

        try {
            // --- Step 1: Determine Jar Path ---
            File jarFile = getJarFile();
            if (jarFile == null) {
                // Not running from a JAR (e.g., in IDE) - Natives expected on classpath/library path
                logger.warn("Not running from a JAR file. Assuming natives are available via system library path or classpath.");
                // Mark as loaded to prevent repeated attempts, although nothing was extracted.
                // LWJGL will try loading from default paths.
                loaded = true;
                return; // Skip extraction
            }
            logger.info("Running from JAR: {}", jarFile.getAbsolutePath());

            // --- Step 2: Create Temp Directory ---
            // Using nanoTime for potentially better uniqueness across quick restarts
            tempDir = Files.createTempDirectory("nettank-natives-" + System.nanoTime());
            String nativePath = tempDir.toAbsolutePath().toString();
            logger.info("Created temporary directory for natives: {}", nativePath);

            // --- Step 3: Extract Natives ---
            extractNativesFromJar(jarFile, tempDir);

            // --- Step 4: Configure LWJGL Library Path ---
            // CRITICAL: Set this BEFORE any LWJGL classes that need natives are loaded.
            System.setProperty("org.lwjgl.librarypath", nativePath);
            logger.info("Set org.lwjgl.librarypath to: {}", nativePath);

            // --- Step 5: Register Cleanup Hook ---
            registerShutdownHook(tempDir);

            loaded = true; // Mark as loaded successfully
            logger.info("Native library loading process completed successfully.");

        } catch (Exception e) {
            logger.error("!!! CRITICAL FAILURE: Failed to extract or set up native libraries !!!", e);
            // Clean up partially created temp dir if possible
            if (tempDir != null) {
                deleteDirectoryQuietly(tempDir);
            }
            // Re-throw as a runtime exception to halt initialization clearly
            throw new RuntimeException("Failed to load native libraries", e);
        }
    }

    /**
     * Attempts to find the JAR file from which this class was loaded.
     * @return The File object representing the JAR, or null if not running from a JAR.
     */
    private static File getJarFile() {
        try {
            String path = NativeLibraryLoader.class.getProtectionDomain()
                    .getCodeSource().getLocation().getPath();
            // Decode URL encoding (e.g., %20 for spaces)
            String decodedPath = URLDecoder.decode(path, "UTF-8");

            // Handle Windows paths starting with / (e.g., /C:/Users/...)
            if (System.getProperty("os.name").toLowerCase().contains("win") && decodedPath.startsWith("/")) {
                decodedPath = decodedPath.substring(1);
            }

            File candidate = new File(decodedPath);
            // Check if it's actually a file and ends with .jar
            if (candidate.isFile() && candidate.getName().toLowerCase().endsWith(".jar")) {
                return candidate;
            } else {
                logger.debug("Application does not appear to be running from a JAR file (path: {}).", candidate.getAbsolutePath());
                return null;
            }
        } catch (Exception e) {
            logger.error("Could not determine JAR file path", e);
            return null;
        }
    }

    /**
     * Extracts native library files for the current OS from the JAR into the target directory.
     */
    private static void extractNativesFromJar(File jarFile, Path targetDir) throws IOException {
        String osName = System.getProperty("os.name").toLowerCase();
        // String osArch = System.getProperty("os.arch").toLowerCase(); // Not needed for simple extension check

        String expectedExtension;
        if (osName.contains("win")) {
            expectedExtension = ".dll";
        } else if (osName.contains("mac")) {
            expectedExtension = ".dylib";
        } else { // Assuming Linux/Unix variants
            expectedExtension = ".so";
        }
        logger.debug("Expecting native libraries for OS '{}' with extension: {}", osName, expectedExtension);

        Set<String> extractedFiles = new HashSet<>(); // Track extracted base filenames to prevent duplicates

        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                // Extract only files ending with the native extension for the current OS
                if (!entry.isDirectory() && name.endsWith(expectedExtension)) {
                    // Extract the base filename (e.g., "lwjgl.dll" from "org/lwjgl/windows/lwjgl.dll")
                    String baseName = Paths.get(name).getFileName().toString();
                    Path outFile = targetDir.resolve(baseName);

                    // Avoid extracting the same library if it exists in multiple paths within the JAR
                    if (extractedFiles.contains(baseName)) {
                        logger.trace("Skipping duplicate native file entry: {}", name);
                        continue;
                    }

                    logger.debug("Extracting native file: {} -> {}", name, outFile);
                    try (InputStream is = jar.getInputStream(entry);
                         OutputStream os = Files.newOutputStream(outFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {

                        byte[] buffer = new byte[8192]; // 8KB buffer
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                    }
                    extractedFiles.add(baseName);

                    // Make executable on non-Windows systems
                    if (!osName.contains("win")) {
                        try {
                            if (outFile.toFile().setExecutable(true)) {
                                logger.trace("Set executable flag on {}", outFile);
                            } else {
                                logger.warn("Failed to set executable flag on {}", outFile);
                            }
                        } catch (SecurityException se) {
                            logger.warn("Security restriction prevented setting executable flag on {}: {}", outFile, se.getMessage());
                        }
                    }
                }
            }
            logger.info("Extraction complete. Extracted {} native file(s) for {} ({})",
                    extractedFiles.size(), osName, expectedExtension);
            if (extractedFiles.isEmpty()) {
                // This is a critical warning if running from a JAR
                logger.warn("!!! WARNING: No native files found in the JAR '{}' for the current OS ({}) with extension {} !!!",
                        jarFile.getName(), osName, expectedExtension);
                // This likely means the POM dependencies are incorrect or the JAR is corrupted.
            }

        } catch (IOException e) {
            logger.error("Error reading from JAR file: {}", jarFile.getAbsolutePath(), e);
            throw e; // Propagate the error
        }
    }

    /**
     * Registers a JVM shutdown hook to attempt deleting the temporary directory.
     */
    private static void registerShutdownHook(Path dirToDelete) {
        Thread cleanupThread = new Thread(() -> {
            logger.info("Shutdown hook triggered: Attempting to clean up natives directory: {}", dirToDelete);
            deleteDirectoryQuietly(dirToDelete);
        }, "NativeLibCleanupHook");

        Runtime.getRuntime().addShutdownHook(cleanupThread);
        logger.debug("Registered shutdown hook for cleaning up directory: {}", dirToDelete);
    }

    /**
     * Attempts to recursively delete the specified directory and its contents.
     * Logs errors but does not throw exceptions.
     */
    private static void deleteDirectoryQuietly(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try {
            Files.walk(directory)
                    .sorted(java.util.Comparator.reverseOrder()) // Delete files before directories
                    .map(Path::toFile)
                    .forEach(file -> {
                        if (!file.delete()) {
                            logger.warn("Shutdown cleanup: Could not delete temp file/dir: {}", file.getAbsolutePath());
                            // Mark for deletion on exit - might help on Windows if files are locked
                            file.deleteOnExit();
                        }
                    });
            logger.info("Shutdown cleanup: Successfully deleted or marked for deletion: {}", directory);
        } catch (IOException e) {
            logger.error("Shutdown cleanup: Error occurred while trying to delete directory: {}", directory, e);
        }
    }

    // No longer needed: remove resetJavaLibraryPath method
}