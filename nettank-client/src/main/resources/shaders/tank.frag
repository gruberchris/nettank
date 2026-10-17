#version 410 core

in vec2 v_texCoord;
out vec4 f_color;

uniform sampler2D u_diffuseTexture;
uniform sampler2D u_teamMaskTexture;
uniform vec4 u_teamColor = vec4(1.0, 1.0, 1.0, 1.0);
uniform vec4 u_tintColor = vec4(1.0, 1.0, 1.0, 1.0);
uniform int u_hasTeamMask = 0;

void main() {
    vec4 baseColor = texture(u_diffuseTexture, v_texCoord);
    if (baseColor.a < 0.02) {
        discard;
    }

    vec3 finalRgb = baseColor.rgb;

    if (u_hasTeamMask == 1) {
        vec4 maskColor = texture(u_teamMaskTexture, v_texCoord);
        // Team color multiplier onto the base diffuse texture highlights
        vec3 shadedTeam = baseColor.rgb * u_teamColor.rgb * 1.5;
        finalRgb = mix(baseColor.rgb, shadedTeam, maskColor.r);
    } else {
        finalRgb = baseColor.rgb * u_teamColor.rgb;
    }

    // Apply hit flash (white or gold) or global damage/wreck tint
    if (u_tintColor.rgb != vec3(1.0)) {
        finalRgb = mix(finalRgb, u_tintColor.rgb, 0.7);
    }

    f_color = vec4(finalRgb, baseColor.a * u_tintColor.a);
}
