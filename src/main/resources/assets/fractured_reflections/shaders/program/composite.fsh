#version 150

// Fractured Reflections composite pass. One cheap full-screen pass, driven by uniforms:
//   MirrorAmount  0..1  desaturated, cold, slightly inverted grade of the Mirror Dimension
//   Distortion    0..1  low-sanity warp, breathing barrel distortion, chromatic split, vignette pulse
//   Flashlight    0..1  flashlight cone: bright ellipse in the centre, darkness at the edges
//   Flicker       0..1  flashlight cutting out when The Fractured is near
//   Pulse         0..1  one-shot white-blue flash (blackout, escape, banishment)

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
in vec2 oneTexel;

uniform vec2 InSize;
uniform float GameTime;
uniform float MirrorAmount;
uniform float Distortion;
uniform float Flashlight;
uniform float Flicker;
uniform float Pulse;

out vec4 fragColor;

void main() {
    vec2 uv = texCoord;
    vec2 centered = uv - 0.5;
    float radius = length(centered);

    if (Distortion > 0.0) {
        float breathe = sin(GameTime * 1.7) * 0.5 + 0.5;
        uv = 0.5 + centered * (1.0 - Distortion * 0.08 * breathe * radius);
        uv += Distortion * 0.004 * vec2(sin(uv.y * 38.0 + GameTime * 3.1), cos(uv.x * 31.0 + GameTime * 2.3));
    }

    vec3 color = texture(DiffuseSampler, uv).rgb;
    float split = Distortion * 0.005 + MirrorAmount * 0.0012;
    if (split > 0.0) {
        color.r = texture(DiffuseSampler, uv + vec2(split, 0.0)).r;
        color.b = texture(DiffuseSampler, uv - vec2(split, 0.0)).b;
    }

    // Mirror Dimension grade: drained colour, cold tint, crushed highlights, slight hue inversion
    float luma = dot(color, vec3(0.299, 0.587, 0.114));
    vec3 drained = mix(vec3(luma), color, 0.22);
    vec3 inverted = mix(drained, vec3(luma) + (vec3(luma) - drained) * 0.6, 0.35);
    vec3 mirrorColor = inverted * vec3(0.82, 0.94, 1.12);
    mirrorColor = mirrorColor / (1.0 + mirrorColor * 0.25);
    color = mix(color, mirrorColor, MirrorAmount);

    // Flashlight cone
    vec2 aspect = vec2(InSize.x / max(InSize.y, 1.0), 1.0);
    float cone = 1.0 - smoothstep(0.18, 0.62, length(centered * aspect));
    float lightLevel = mix(1.0, 0.30 + 0.70 * cone, Flashlight);
    lightLevel *= 1.0 - Flicker * 0.75;
    color *= lightLevel;

    // Sanity vignette, pulsing like a heartbeat
    float beat = pow(abs(sin(GameTime * 2.6)), 6.0);
    color *= 1.0 - Distortion * (0.35 + 0.25 * beat) * smoothstep(0.25, 0.85, radius);

    color = mix(color, vec3(0.80, 0.90, 1.0), clamp(Pulse, 0.0, 1.0) * 0.85);
    fragColor = vec4(color, 1.0);
}
