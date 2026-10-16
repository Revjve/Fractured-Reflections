#version 150

// Bloom source for the Mirror Dimension: keeps light sources and anything already hot-red,
// discards the rest, so lanterns and the Fractured's core bleed crimson light.

uniform sampler2D DiffuseSampler;
uniform float Threshold;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 c = texture(DiffuseSampler, texCoord).rgb;
    float peak = max(c.r, max(c.g, c.b));
    float redness = clamp(c.r - max(c.g, c.b) * 0.8, 0.0, 1.0);
    float w = smoothstep(Threshold, 1.0, peak) + redness * smoothstep(0.15, 0.6, c.r) * 0.6;
    fragColor = vec4(c * clamp(w, 0.0, 1.5), 1.0);
}
