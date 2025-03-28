#version 410 core

layout (location = 0) in vec2 a_position;
layout (location = 1) in vec2 a_texCoord; // Input Texture Coords from VBO

uniform mat4 u_projection;
uniform mat4 u_view;
uniform mat4 u_model;

// >>>>> MUST MATCH 'in' IN FRAGMENT SHADER <<<<<
out vec2 v_texCoord; // Output to Fragment Shader

void main() {
    // Pass input texture coordinate through
    v_texCoord = a_texCoord;

    gl_Position = u_projection * u_view * u_model * vec4(a_position, 0.0, 1.0);
}