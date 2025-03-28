#version 410 core

// >>>>> MUST MATCH 'out' IN VERTEX SHADER <<<<<
in vec2 v_texCoord; // Input from Vertex Shader (Quad's 0-1 UVs)

out vec4 f_color;

uniform sampler2D u_texture;
uniform vec3 u_tintColor = vec3(1.0, 1.0, 1.0);

// Uniform for texture sub-rectangle:
// u_texRect.xy = Top-Left corner offset (normalized 0-1)
// u_texRect.zw = Size (normalized 0-1)
uniform vec4 u_texRect = vec4(0.0, 0.0, 1.0, 1.0); // Default: full texture

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