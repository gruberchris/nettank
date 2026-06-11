#version 410 core

in vec2 v_texCoord;
out vec4 f_color;

uniform sampler2D u_texture;
uniform vec4 u_texRect = vec4(0.0, 0.0, 1.0, 1.0); // Default: full texture
uniform vec4 u_tintColor = vec4(1.0, 1.0, 1.0, 1.0);

void main() {
    // Calculate the actual texture coordinate within the specified sub-rectangle
    // Map the quad's 0-1 UVs (v_texCoord) into the target rectangle defined by u_texRect
    vec2 subTexCoord = u_texRect.xy + v_texCoord * u_texRect.zw;

    // Sample the texture at the calculated coordinate
    vec4 texColor = texture(u_texture, subTexCoord);

    // Apply the tint to RGB and modulate the texture alpha by the tint alpha
    f_color.rgb = texColor.rgb * u_tintColor.rgb;
    f_color.a = texColor.a * u_tintColor.a;
}