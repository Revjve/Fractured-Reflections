#version 150

// Fractured Reflections screen-space composite (runs in both worlds, after the Mirror chain):
//   Distortion  0..1  low-sanity warp, breathing barrel distortion, chromatic split, vignette pulse
//   Cracks      0..1  shattered-glass overlay: every Voronoi shard refracts the image differently
//                     and the fault lines glow (red inside the Mirror Dimension)
//   Flashlight  0..1  flashlight cone: bright ellipse in the centre, darkness at the edges
//   Flicker     0..1  flashlight cutting out when The Fractured is near
//   Pulse       0..1  one-shot flash (blackout, escape, banishment)
//   MirrorAmount      tints the flash / crack glow red inside the Mirror Dimension

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
in vec2 oneTexel;

uniform vec2 InSize;
uniform float GameTime;
uniform float MirrorAmount;
uniform float Distortion;
uniform float Cracks;
uniform float Flashlight;
uniform float Flicker;
uniform float Pulse;

out vec4 fragColor;

vec2 hash2(vec2 p) {
    p = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)));
    return fract(sin(p) * 43758.5453);
}

// Inigo Quilez' Voronoi edge distance: x = distance to nearest cell border, yz = cell id
vec3 voronoi(vec2 x) {
    vec2 n = floor(x);
    vec2 f = fract(x);
    vec2 mg = vec2(0.0);
    vec2 mr = vec2(0.0);
    float md = 8.0;
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            vec2 g = vec2(float(i), float(j));
            vec2 r = g + hash2(n + g) - f;
            float d = dot(r, r);
            if (d < md) { md = d; mr = r; mg = g; }
        }
    }
    md = 8.0;
    for (int j = -2; j <= 2; j++) {
        for (int i = -2; i <= 2; i++) {
            vec2 g = mg + vec2(float(i), float(j));
            vec2 r = g + hash2(n + g) - f;
            if (dot(mr - r, mr - r) > 0.00001) md = min(md, dot(0.5 * (mr + r), normalize(r - mr)));
        }
    }
    return vec3(md, n + mg);
}

void main() {
    vec2 uv = texCoord;
    vec2 centered = uv - 0.5;
    vec2 aspect = vec2(InSize.x / max(InSize.y, 1.0), 1.0);
    float radius = length(centered);

    if (Distortion > 0.0) {
        float breathe = sin(GameTime * 1.7) * 0.5 + 0.5;
        uv = 0.5 + centered * (1.0 - Distortion * 0.08 * breathe * radius);
        uv += Distortion * 0.004 * vec2(sin(uv.y * 38.0 + GameTime * 3.1), cos(uv.x * 31.0 + GameTime * 2.3));
    }

    // shattered glass: the cracks hug the screen edges and spread inward as Cracks rises
    float crackLine = 0.0;
    if (Cracks > 0.01) {
        vec3 v = voronoi(texCoord * aspect * 5.0);
        float reach = mix(0.62, 0.0, clamp(Cracks, 0.0, 1.0));
        float mask = smoothstep(reach, reach + 0.25, radius * 1.25);
        uv += (hash2(v.yz) - 0.5) * 0.018 * mask * Cracks;
        crackLine = (1.0 - smoothstep(0.0, 0.02, v.x)) * mask * Cracks;
    }

    vec3 color = texture(DiffuseSampler, uv).rgb;
    float split = Distortion * 0.005 + Cracks * 0.002;
    if (split > 0.0) {
        color.r = texture(DiffuseSampler, uv + vec2(split, 0.0)).r;
        color.b = texture(DiffuseSampler, uv - vec2(split, 0.0)).b;
    }

    vec3 glow = mix(vec3(0.85, 0.92, 1.0), vec3(1.0, 0.12, 0.08), clamp(MirrorAmount, 0.0, 1.0));
    color = mix(color, glow, crackLine * 0.85);

    // flashlight cone
    float cone = 1.0 - smoothstep(0.18, 0.62, length(centered * aspect));
    float lightLevel = mix(1.0, 0.30 + 0.70 * cone, Flashlight);
    lightLevel *= 1.0 - Flicker * 0.75;
    color *= lightLevel;

    // sanity vignette, pulsing like a heartbeat
    float beat = pow(abs(sin(GameTime * 2.6)), 6.0);
    color *= 1.0 - Distortion * (0.35 + 0.25 * beat) * smoothstep(0.25, 0.85, radius);

    vec3 flash = mix(vec3(0.80, 0.90, 1.0), vec3(1.0, 0.15, 0.1), clamp(MirrorAmount, 0.0, 1.0));
    color = mix(color, flash, clamp(Pulse, 0.0, 1.0) * 0.85);
    fragColor = vec4(color, 1.0);
}
