package org.chrisgruber.nettank.client.util;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class NativeLibraryLoader {

    public static void loadNativeLibraries() {
        try {
            // Create a unique temp directory for this run
            Path tempDir = Files.createTempDirectory("nettank-natives");
            String nativePath = tempDir.toAbsolutePath().toString();

            // Extract all native library files
            extractNativeLibraries(tempDir);

            // Set system properties for LWJGL before any LWJGL classes are loaded
            System.setProperty("org.lwjgl.librarypath", nativePath);

            // Set the java.library.path
            System.setProperty("java.library.path",
                    nativePath + File.pathSeparator + System.getProperty("java.library.path"));

            // Hack to force java.library.path to reload
            resetJavaLibraryPath();

            // Make sure the directory is deleted on exit
            tempDir.toFile().deleteOnExit();

        } catch (Exception e) {
            System.err.println("Failed to extract native libraries: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void resetJavaLibraryPath() {
        try {
            Field field = ClassLoader.class.getDeclaredField("sys_paths");
            field.setAccessible(true);
            field.set(null, null);
        } catch (Exception e) {
            System.err.println("Failed to reset java.library.path: " + e.getMessage());
        }
    }

    private static void extractNativeLibraries(Path tempDir) throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();

        // Determine the file extension based on OS
        String extension;
        if (os.contains("win")) {
            extension = ".dll";
        } else if (os.contains("mac")) {
            extension = ".dylib";
        } else {
            extension = ".so";
        }

        // Get the path to our JAR file
        String jarPath = NativeLibraryLoader.class.getProtectionDomain()
                .getCodeSource().getLocation().getPath();

        // Handle URL encoding in path
        if (jarPath.startsWith("file:")) {
            jarPath = jarPath.substring(5);
        }
        if (jarPath.contains("%20")) {
            jarPath = jarPath.replace("%20", " ");
        }

        // If we're on Windows and path starts with /, remove it
        if (os.contains("win") && jarPath.startsWith("/")) {
            jarPath = jarPath.substring(1);
        }

        System.out.println("Extracting native libraries from: " + jarPath);
        System.out.println("To directory: " + tempDir);

        try (JarFile jar = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                // Extract only native libraries for the current platform
                if (name.endsWith(extension) &&
                        (name.contains("/" + arch + "/") || name.contains("natives"))) {

                    Path outFile = tempDir.resolve(Paths.get(name).getFileName());
                    System.out.println("Extracting: " + name + " to " + outFile);

                    try (InputStream is = jar.getInputStream(entry);
                         OutputStream os2 = Files.newOutputStream(outFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os2.write(buffer, 0, bytesRead);
                        }
                    }

                    // Make the file executable (important for Linux/macOS)
                    outFile.toFile().setExecutable(true);
                }
            }
        }
    }
}