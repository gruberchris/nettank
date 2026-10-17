#version 410 core

in vec2 v_texCoord;
out vec4 f_color;

uniform sampler2D u_texture;
uniform vec4 u_shadowColor = vec4(0.02, 0.03, 0.05, 0.42);

void main() {
    vec4 tex = texture(u_texture, v_texCoord);
    if (tex.a < 0.05) {
        discard;
    }
    f_color = vec4(u_shadowColor.rgb, tex.a * u_shadowColor.a);
}
