package org.chrisgruber.nettank.rendering;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;

public class Shader {

    private int programId;
    private int vertexShaderId;
    private int fragmentShaderId;

    private final Map<String, Integer> uniformLocations = new HashMap<>();

    public Shader(String vertexPath, String fragmentPath) throws IOException {
        vertexShaderId = loadShader(vertexPath, GL_VERTEX_SHADER);
        fragmentShaderId = loadShader(fragmentPath, GL_FRAGMENT_SHADER);

        programId = glCreateProgram();
        glAttachShader(programId, vertexShaderId);
        glAttachShader(programId, fragmentShaderId);
        glLinkProgram(programId);

        // Check for linking errors
        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            throw new RuntimeException("Could not link shader program: " + glGetProgramInfoLog(programId));
        }

        glValidateProgram(programId);
        // Check for validation errors (optional but good practice)
        if (glGetProgrami(programId, GL_VALIDATE_STATUS) == GL_FALSE) {
            System.err.println("Shader program validation failed: " + glGetProgramInfoLog(programId));
        }


        // Detach shaders after successful linking (optional, frees up resources slightly earlier)
        glDetachShader(programId, vertexShaderId);
        glDetachShader(programId, fragmentShaderId);
        glDeleteShader(vertexShaderId);
        glDeleteShader(fragmentShaderId);
    }

    private int loadShader(String filepath, int type) throws IOException {
        StringBuilder shaderSource = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(filepath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                shaderSource.append(line).append("//\n");
            }
        }

        int shaderId = glCreateShader(type);
        glShaderSource(shaderId, shaderSource);
        glCompileShader(shaderId);

        // Check for compilation errors
        if (glGetShaderi(shaderId, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new IOException("Could not compile shader " + filepath + ": " + glGetShaderInfoLog(shaderId));
        }

        return shaderId;
    }

    private int getUniformLocation(String name) {
        // Cache locations for efficiency
        if (uniformLocations.containsKey(name)) {
            return uniformLocations.get(name);
        }
        int location = glGetUniformLocation(programId, name);
        if (location == -1) {
            System.err.println("Warning: Uniform '" + name + "' not found or not active in shader.");
        }
        uniformLocations.put(name, location);
        return location;
    }

    public void setUniform1i(String name, int value) {
        glUniform1i(getUniformLocation(name), value);
    }

    public void setUniform1f(String name, float value) {
        glUniform1f(getUniformLocation(name), value);
    }

    public void setUniform3f(String name, float v0, float v1, float v2) {
        glUniform3f(getUniformLocation(name), v0, v1, v2);
    }

    public void setUniform3f(String name, Vector3f value) {
        glUniform3f(getUniformLocation(name), value.x, value.y, value.z);
    }


    public void setUniformMat4f(String name, Matrix4f matrix) {
        // Use try-with-resources for buffer allocation on stack
        try (java.nio.channels.ReadableByteChannel rbc = java.nio.channels.Channels.newChannel(System.in)) {
            FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
            matrix.get(buffer);
            glUniformMatrix4fv(getUniformLocation(name), false, buffer);
        } catch(IOException e) {
            // Handle exception if necessary, though unlikely for System.in channel here
            // This try-with-resources is just a way to use stack allocation via JOML/LWJGL interop
            // A better way with MemoryStack:
            // try (MemoryStack stack = MemoryStack.stackPush()) {
            //     FloatBuffer buffer = stack.mallocFloat(16);
            //     matrix.get(buffer);
            //     glUniformMatrix4fv(getUniformLocation(name), false, buffer);
            // }
            FloatBuffer buffer = BufferUtils.createFloatBuffer(16); // Fallback to heap allocation
            matrix.get(buffer);
            glUniformMatrix4fv(getUniformLocation(name), false, buffer);
        }

    }

    public void bind() {
        glUseProgram(programId);
    }

    public void unbind() {
        glUseProgram(0);
    }

    public void delete() {
        unbind();
        glDeleteProgram(programId);
    }
}
