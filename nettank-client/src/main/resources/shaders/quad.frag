#version 410 core

in vec2 v_texCoord;
out vec4 f_color;

uniform sampler2D u_texture;
uniform vec4 u_texRect = vec4(0.0, 0.0, 1.0, 1.0); // Default: full texture
uniform vec3 u_tintColor = vec3(1.0, 1.0, 1.0);

void main() {
    // Calculate the actual texture coordinate within the specified sub-rectangle
    // Map the quad's 0-1 UVs (v_texCoord) into the target rectangle defined by u_texRect
    vec2 subTexCoord = u_texRect.xy + v_texCoord * u_texRect.zw;

    // Sample the texture at the calculated coordinate
    vec4 texColor = texture(u_texture, subTexCoord);

    // Apply tint
    f_color = texColor * vec4(u_tintColor, 1.0);

    // Discard transparent pixels (important for bitmap fonts)
    if (f_color.a < 0.1) { // Use a small threshold
        discard;
    }
}