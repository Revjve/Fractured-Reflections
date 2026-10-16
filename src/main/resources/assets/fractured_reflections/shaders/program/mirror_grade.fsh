#version 150

// The Mirror Dimension: a world of soot and blood.
//  * filmic crimson ramp: blacks are crushed to true black, mids fall into deep blood red,
//    highlights burn through crimson to a pale, sickly rose
//  * anything that was already red keeps its saturation (blood stays blood)
//  * red bloom from the bright pass, so every lantern bleeds light
//  * slow "breathing" heat-shimmer, film grain, heavy vignette
//  * Tear: occasional horizontal signal tear, as if the reflection is failing to hold together

uniform sampler2D DiffuseSampler;
uniform sampler2D BloomSampler;

in vec2 texCoord;
in vec2 oneTexel;

uniform vec2 InSize;
uniform float GameTime;
uniform float Intensity;
uniform float BloomStrength;
uniform float Tear;
uniform float Pulse;

out vec4 fragColor;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

vec3 crimsonRamp(float t) {
    vec3 c = mix(vec3(0.0), vec3(0.16, 0.0, 0.012), smoothstep(0.0, 0.18, t));
    c = mix(c, vec3(0.55, 0.02, 0.035), smoothstep(0.12, 0.55, t));
    c = mix(c, vec3(0.95, 0.16, 0.12), smoothstep(0.45, 0.82, t));
    c = mix(c, vec3(1.0, 0.72, 0.64), smoothstep(0.80, 1.0, t));
    return c;
}

void main() {
    vec2 uv = texCoord;

    // heat shimmer that slowly breathes
    float breathe = 0.5 + 0.5 * sin(GameTime * 0.9);
    uv += vec2(sin(uv.y * 24.0 + GameTime * 2.1), cos(uv.x * 19.0 + GameTime * 1.7)) * 0.0011 * (0.5 + breathe) * Intensity;

    // signal tear: a band of rows slides sideways
    float band = step(abs(uv.y - fract(GameTime * 0.23)), 0.035) * Tear;
    uv.x += band * (hash(vec2(floor(GameTime * 30.0), 1.0)) - 0.5) * 0.08;

    vec3 scene = texture(DiffuseSampler, uv).rgb;
    float luma = dot(scene, vec3(0.2126, 0.7152, 0.0722));

    float t = smoothstep(0.015, 0.9, luma);
    t = pow(t, 1.25);
    vec3 graded = crimsonRamp(t);

    float redness = clamp(scene.r - max(scene.g, scene.b), 0.0, 1.0);
    graded = mix(graded, vec3(scene.r * 1.15, scene.g * 0.1, scene.b * 0.12), clamp(redness * 1.4, 0.0, 0.75));

    vec3 bloom = texture(BloomSampler, uv).rgb;
    float bloomLuma = dot(bloom, vec3(0.3333));
    graded += vec3(1.0, 0.16, 0.1) * bloomLuma * BloomStrength;

    // vignette: the world closes in around you
    vec2 centered = texCoord - 0.5;
    centered.x *= InSize.x / max(InSize.y, 1.0);
    float vig = 1.0 - smoothstep(0.35, 1.05, length(centered));
    graded *= mix(0.25, 1.0, vig);

    // grain
    float grain = hash(texCoord * InSize + fract(GameTime * 13.0) * 100.0) - 0.5;
    graded += grain * 0.05;

    // red heartbeat flash at the edges
    graded = mix(graded, vec3(0.9, 0.05, 0.05), clamp(Pulse, 0.0, 1.0) * (1.0 - vig) * 0.8);

    fragColor = vec4(mix(scene, max(graded, 0.0), clamp(Intensity, 0.0, 1.0)), 1.0);
}
