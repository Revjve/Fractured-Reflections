#version 150

// Separable 9-tap gaussian. BlurDir carries the step in texels (1 = tight, 3 = wide).

uniform sampler2D DiffuseSampler;
uniform vec2 BlurDir;

in vec2 texCoord;
in vec2 oneTexel;
out vec4 fragColor;

void main() {
    vec2 stepUv = oneTexel * BlurDir;
    vec3 sum = texture(DiffuseSampler, texCoord).rgb * 0.2270270270;
    sum += texture(DiffuseSampler, texCoord + stepUv * 1.3846153846).rgb * 0.3162162162;
    sum += texture(DiffuseSampler, texCoord - stepUv * 1.3846153846).rgb * 0.3162162162;
    sum += texture(DiffuseSampler, texCoord + stepUv * 3.2307692308).rgb * 0.0702702703;
    sum += texture(DiffuseSampler, texCoord - stepUv * 3.2307692308).rgb * 0.0702702703;
    fragColor = vec4(sum, 1.0);
}
