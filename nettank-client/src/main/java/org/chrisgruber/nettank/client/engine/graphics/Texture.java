package org.chrisgruber.nettank.client.engine.graphics;

import org.lwjgl.system.MemoryStack;

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

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;
import static org.lwjgl.stb.STBImage.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;

public class Texture {

    private final int textureId;
    private int width;
    private int height;

    public Texture(String filepath) throws IOException {
        ByteBuffer imageBuffer;
        Path path = Paths.get(filepath);

        if (Files.isReadable(path)) {
            try (SeekableByteChannel sbc = Files.newByteChannel(path)) {
                imageBuffer = org.lwjgl.BufferUtils.createByteBuffer((int) sbc.size() + 1);
                while (sbc.read(imageBuffer) != -1) { ; }
            }
        } else {
            URL url = Texture.class.getClassLoader().getResource(filepath);
            if (url == null) {
                throw new IOException("Failed to find texture file: " + filepath);
            }
            try (InputStream source = url.openStream();
                 ReadableByteChannel rbc = Channels.newChannel(source)) {
                imageBuffer = org.lwjgl.BufferUtils.createByteBuffer(1024 * 8); // 8KB initial buffer

                while (true) {
                    int bytes = rbc.read(imageBuffer);
                    if (bytes == -1) {
                        break;
                    }
                    if (imageBuffer.remaining() == 0) {
                        // Resize buffer
                        ByteBuffer newBuffer = org.lwjgl.BufferUtils.createByteBuffer(imageBuffer.capacity() * 2);
                        imageBuffer.flip();
                        newBuffer.put(imageBuffer);
                        imageBuffer = newBuffer;
                    }
                }
            }
        }

        imageBuffer.flip();

        try (MemoryStack stack = stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);

            // Load image using STB
            stbi_set_flip_vertically_on_load(true); // Usually needed for OpenGL
            ByteBuffer decodedImage = stbi_load_from_memory(imageBuffer, w, h, channels, 4); // Request RGBA
            if (decodedImage == null) {
                throw new IOException("Failed to load texture image: " + filepath + " - " + stbi_failure_reason());
            }

            this.width = w.get(0);
            this.height = h.get(0);

            // Create OpenGL texture
            textureId = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, textureId);

            // Set texture parameters
            // Use GL_CLAMP_TO_EDGE to prevent artifacts at sprite borders
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

            // Use linear filtering for smoother scaling
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

            // Upload texture data
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, decodedImage);

            // Generate mipmaps for better minification filtering
            glGenerateMipmap(GL_TEXTURE_2D);

            // Free STB image data
            stbi_image_free(decodedImage);
        }

        // Unbind texture
        glBindTexture(GL_TEXTURE_2D, 0);
    }


    public void bind() {
        glBindTexture(GL_TEXTURE_2D, textureId);
    }

    public void unbind() {
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public void delete() {
        glDeleteTextures(textureId);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
