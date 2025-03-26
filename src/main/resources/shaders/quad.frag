#version 330 core
out vec4 FragColor;

in vec2 TexCoord;

uniform sampler2D u_texture;
uniform vec3 u_tintColor = vec3(1.0, 1.0, 1.0); // Default to white (no tint)

void main()
{
    vec4 texColor = texture(u_texture, TexCoord);

    // If texture alpha is near zero, discard the fragment (optional, good for transparency)
    if(texColor.a < 0.1)
    discard;

    // Apply tint color multiplicative
    FragColor = texColor * vec4(u_tintColor, 1.0);

    // Ensure alpha blending works correctly
    FragColor.a = texColor.a;
}