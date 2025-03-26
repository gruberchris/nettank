#version 330 core
layout (location = 0) in vec2 aPos;
layout (location = 1) in vec2 aTexCoord;

out vec2 TexCoord;

uniform mat4 u_projection;
uniform mat4 u_view;
uniform mat4 u_model;

void main()
{
    // Order: Project * View * Model * Vertex Position
    gl_Position = u_projection * u_view * u_model * vec4(aPos, 0.0, 1.0);
    TexCoord = aTexCoord;
}