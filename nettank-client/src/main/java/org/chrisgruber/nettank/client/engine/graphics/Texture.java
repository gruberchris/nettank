package org.chrisgruber.nettank.client.engine.graphics;

import org.lwjgl.BufferUtils; // Explicit import for clarity
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger; // Explicit import for clarity
import org.slf4j.LoggerFactory; // Explicit import for clarity

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.lwjgl.opengl.GL11.*; // Basic OpenGL functions
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE; // Texture wrap mode
import static org.lwjgl.opengl.GL30.GL_INVALID_FRAMEBUFFER_OPERATION; // Error code
import static org.lwjgl.stb.STBImage.*; // STB Image library functions
import static org.lwjgl.system.MemoryStack.stackPush; // Stack allocation utility

/**
 * Represents an OpenGL texture loaded from an image file.
 * Handles loading from both filesystem and classpath resources.
 * Uses STBImage for image decoding and flips images vertically on load
 * to align with OpenGL's bottom-left texture coordinate origin.
 * Configured with GL_NEAREST filtering suitable for pixel art.
 */
public class Texture {

    private static final Logger logger = LoggerFactory.getLogger(Texture.class);

    // OpenGL texture object ID. 0 indicates no texture or an error.
    private int textureId = 0;
    private int width;
    private int height;

    /**
     * Loads a texture from the specified file path.
     * Tries filesystem first, then falls back to classpath resource.
     * Configures the texture with pixel-art friendly settings (NEAREST filtering).
     *
     * @param filepath Path to the texture file (relative or absolute, or classpath resource)
     * @throws IOException If the file cannot be found or loaded.
     */
    public Texture(String filepath) throws IOException {
        logger.debug("Loading texture: {}", filepath);
        ByteBuffer imageBuffer = null; // Initialize to null

        // --- Load image data into a ByteBuffer ---
        try {
            // Try loading directly from filesystem
            Path path = Paths.get(filepath);
            if (Files.isReadable(path)) {
                logger.trace("Reading texture from filesystem: {}", path.toAbsolutePath());
                try (SeekableByteChannel sbc = Files.newByteChannel(path)) {
                    // Allocate buffer precisely for file size
                    imageBuffer = BufferUtils.createByteBuffer((int) sbc.size() + 1);
                    // Read the entire file content into the buffer
                    while (sbc.read(imageBuffer) != -1) { /* Loop until EOF */ }
                }
            } else {
                // Fallback: Try loading from classpath resources
                logger.trace("Texture not found on filesystem, trying classpath: {}", filepath);
                URL url = Texture.class.getClassLoader().getResource(filepath);
                // Handle potential leading slash needed for classpath lookups
                if (url == null && !filepath.startsWith("/")) {
                    url = Texture.class.getClassLoader().getResource("/" + filepath);
                }
                if (url == null) {
                    throw new IOException("Failed to find texture resource: " + filepath);
                }
                logger.trace("Reading texture from classpath URL: {}", url);
                try (InputStream source = url.openStream();
                     ReadableByteChannel rbc = Channels.newChannel(source)) {
                    // Read from stream into a dynamically resizing buffer
                    imageBuffer = BufferUtils.createByteBuffer(1024 * 8); // Start with 8KB
                    while (true) {
                        int bytes = rbc.read(imageBuffer);
                        if (bytes == -1) {
                            break; // End of stream
                        }
                        if (!imageBuffer.hasRemaining()) {
                            // Double buffer size if full
                            ByteBuffer newBuffer = BufferUtils.createByteBuffer(imageBuffer.capacity() * 2);
                            imageBuffer.flip(); // Prepare for reading
                            newBuffer.put(imageBuffer); // Copy old data
                            imageBuffer = newBuffer; // Use new buffer
                        }
                    }
                }
            }
            // Prepare buffer for reading by STB
            if (imageBuffer != null) {
                imageBuffer.flip();
            } else {
                // This should not happen if logic above is correct, but as a safeguard:
                throw new IOException("Image buffer is null after loading attempt for: " + filepath);
            }
        } catch (IOException e) {
            logger.error("Failed to read texture file/resource: {}", filepath, e);
            throw new IOException("Failed to read texture file/resource: " + filepath, e);
        }

        // --- Decode image data using STB and upload to OpenGL ---
        try (MemoryStack stack = stackPush()) {
            IntBuffer w = stack.mallocInt(1); // Buffer to receive width
            IntBuffer h = stack.mallocInt(1); // Buffer to receive height
            IntBuffer channels = stack.mallocInt(1); // Buffer to receive number of channels

            // Configure STB to flip the image vertically upon loading.
            // This aligns the image data (usually top-left origin) with OpenGL's
            // texture coordinate system (bottom-left origin) when using standard UVs.
            stbi_set_flip_vertically_on_load(true);

            logger.debug("Decoding image data for: {}", filepath);
            // Load image from memory buffer, force 4 channels (RGBA)
            ByteBuffer decodedImage = stbi_load_from_memory(imageBuffer, w, h, channels, 4);
            if (decodedImage == null) {
                String failureReason = stbi_failure_reason();
                logger.error("STB failed to load texture image: {} - Reason: {}", filepath, failureReason);
                // Use the captured reason in the exception message
                throw new IOException("STB failed to load texture image: " + filepath + " - Reason: " + failureReason);
            }

            // Retrieve width and height from buffers
            this.width = w.get(0);
            this.height = h.get(0);
            logger.debug("Decoded image '{}': {}x{} pixels", filepath, width, height);

            // Validate dimensions
            if (width <= 0 || height <= 0) {
                stbi_image_free(decodedImage); // Free STB memory before throwing
                logger.error("Invalid texture dimensions {}x{} for {}", width, height, filepath);
                throw new IOException("Invalid texture dimensions (" + width + "x" + height + ") for " + filepath);
            }

            // --- Create and configure OpenGL texture object ---
            textureId = glGenTextures(); // Generate a texture ID
            checkGLError("glGenTextures for " + filepath);

            glBindTexture(GL_TEXTURE_2D, textureId); // Bind the texture object
            checkGLError("glBindTexture for " + filepath);

            // Set texture parameters suitable for 2D pixel art
            // GL_CLAMP_TO_EDGE prevents sampling beyond texture boundaries (avoids edge artifacts)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            // GL_NEAREST selects the single nearest pixel, preserving sharp edges (pixelated look)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST); // For minification
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST); // For magnification
            checkGLError("glTexParameteri for " + filepath);

            // Upload the decoded image data to the bound texture object on the GPU
            logger.debug("Uploading texture data to GPU for ID: {}", textureId);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, decodedImage);
            checkGLError("glTexImage2D for " + filepath);

            // Mipmaps are usually not generated when using GL_NEAREST filtering for pixel art.
            // glGenerateMipmap(GL_TEXTURE_2D);

            logger.info("Successfully loaded and uploaded texture: {} (ID: {})", filepath, textureId);

            // Free the memory buffer allocated by STB now that data is on GPU
            stbi_image_free(decodedImage);

        } catch (Exception e) {
            logger.error("Exception during texture creation for {}: {}", filepath, e.getMessage(), e);
            // Attempt to clean up potentially created texture ID if an error occurred mid-process
            if (textureId != 0) {
                logger.warn("Attempting cleanup for partially created texture ID: {}", textureId);
                glDeleteTextures(textureId);
                checkGLError("glDeleteTextures in catch block for " + filepath);
                textureId = 0; // Mark as invalid
            }
            // Rethrow as IOException to signal loading failure
            throw new IOException("Exception during texture creation for " + filepath, e);
        } finally {
            // Always unbind the texture from the 2D target when done.
            glBindTexture(GL_TEXTURE_2D, 0);
        }
    }

    /**
     * Helper method to check for OpenGL errors after an operation.
     * Logs any errors found.
     *
     * @param operation A description of the OpenGL operation just performed.
     */
    private void checkGLError(String operation) {
        int errorCode;
        // Loop until all error flags are cleared
        while ((errorCode = glGetError()) != GL_NO_ERROR) {
            String errorStr;
            switch (errorCode) {
                case GL_INVALID_ENUM:                  errorStr = "INVALID_ENUM"; break;
                case GL_INVALID_VALUE:                 errorStr = "INVALID_VALUE"; break;
                case GL_INVALID_OPERATION:             errorStr = "INVALID_OPERATION"; break;
                case GL_STACK_OVERFLOW:                errorStr = "STACK_OVERFLOW"; break;
                case GL_STACK_UNDERFLOW:               errorStr = "STACK_UNDERFLOW"; break;
                case GL_OUT_OF_MEMORY:                 errorStr = "OUT_OF_MEMORY"; break;
                case GL_INVALID_FRAMEBUFFER_OPERATION: errorStr = "INVALID_FRAMEBUFFER_OPERATION"; break;
                default:                               errorStr = String.format("UNKNOWN(0x%X)", errorCode); break;
            }
            logger.error("OpenGL Error after [{}]: {} ({})", operation, errorStr, errorCode);
            // Consider throwing a RuntimeException here for critical errors if desired
            // throw new RuntimeException("OpenGL Error after [" + operation + "]: " + errorStr);
        }
    }

    /**
     * Binds this texture to the GL_TEXTURE_2D target, making it active for subsequent rendering.
     */
    public void bind() {
        if (textureId != 0) {
            glBindTexture(GL_TEXTURE_2D, textureId);
        } else {
            logger.warn("Attempted to bind an invalid texture (ID=0)");
        }
    }

    /**
     * Unbinds any texture from the GL_TEXTURE_2D target by binding texture ID 0.
     */
    public void unbind() {
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    /**
     * Deletes the OpenGL texture object, freeing GPU memory.
     * This texture instance should not be used after calling delete.
     */
    public void delete() {
        if (textureId != 0) {
            glDeleteTextures(textureId);
            logger.trace("Deleted texture ID: {}", textureId);
            textureId = 0; // Mark as deleted
        }
    }

    /** Gets the width of the loaded texture in pixels. */
    public int getWidth() {
        return width;
    }

    /** Gets the height of the loaded texture in pixels. */
    public int getHeight() {
        return height;
    }

    /** Gets the OpenGL texture ID. Returns 0 if loading failed or deleted. */
    public int getTextureId() {
        return textureId;
    }
}