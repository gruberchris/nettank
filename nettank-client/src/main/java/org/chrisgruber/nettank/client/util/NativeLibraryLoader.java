package org.chrisgruber.nettank.client.util;

import java.io.*;
import java.lang.reflect.Field;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class NativeLibraryLoader {

    public static void loadNativeLibraries() {
        try {
            String tempDir = System.getProperty("java.io.tmpdir") + "/nettank-natives-" + System.currentTimeMillis();
            new File(tempDir).mkdirs();

            // Extract native libraries from JAR
            extractNatives(NativeLibraryLoader.class, tempDir, ".dylib"); // macOS
            extractNatives(NativeLibraryLoader.class, tempDir, ".so");    // Linux
            extractNatives(NativeLibraryLoader.class, tempDir, ".dll");   // Windows

            // Set the library path
            System.setProperty("java.library.path", tempDir);
            System.setProperty("org.lwjgl.librarypath", tempDir);

            // Force java.library.path to be reloaded
            try {
                Field field = ClassLoader.class.getDeclaredField("sys_paths");
                field.setAccessible(true);
                field.set(null, null);
            } catch (Exception e) {
                e.printStackTrace();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void extractNatives(Class<?> clazz, String destDir, String extension) throws IOException {
        String jarPath = clazz.getProtectionDomain().getCodeSource().getLocation().getPath();
        if (jarPath.startsWith("/")) {
            jarPath = jarPath.substring(1);
        }

        try (JarFile jar = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(extension)) {
                    File file = new File(destDir, new File(entry.getName()).getName());
                    try (InputStream is = jar.getInputStream(entry);
                         FileOutputStream fos = new FileOutputStream(file)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                        file.setExecutable(true);
                    }
                }
            }
        }
    }
}