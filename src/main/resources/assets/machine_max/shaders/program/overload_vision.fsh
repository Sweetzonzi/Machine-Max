#version 150

uniform sampler2D DiffuseSampler;
uniform float Overload;
uniform vec2 OutSize;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 source = texture(DiffuseSampler, texCoord);
    float positiveG = clamp(Overload, 0.0, 1.0);
    float negativeG = clamp(-Overload, 0.0, 1.0);
    float luminance = dot(source.rgb, vec3(0.2126, 0.7152, 0.0722));

    // Positive G: lose saturation, darken, then close the peripheral vision.
    vec3 blackoutColor = mix(source.rgb, vec3(luminance), positiveG * 0.75);
    blackoutColor *= mix(1.0, 0.12, positiveG * positiveG);

    vec2 centered = texCoord * 2.0 - 1.0;
    centered.x *= OutSize.x / max(OutSize.y, 1.0);
    float radius = length(centered);
    float vignetteAmount = smoothstep(0.15, 1.0, positiveG);
    float innerRadius = mix(1.35, 0.22, positiveG);
    float outerRadius = mix(1.75, 0.95, positiveG);
    float peripheralVisibility = 1.0 - smoothstep(innerRadius, outerRadius, radius);
    blackoutColor *= mix(1.0, peripheralVisibility, vignetteAmount);

    // Negative G: remap luminance toward red and add a stronger red veil at the edges.
    float redLuminance = smoothstep(0.02, 0.95, luminance);
    vec3 redoutColor = vec3(redLuminance, redLuminance * 0.07, redLuminance * 0.035);
    float redEdge = smoothstep(0.25, 1.35, radius);
    redoutColor += vec3(0.16, 0.0, 0.0) * redEdge * negativeG;
    redoutColor = mix(source.rgb, redoutColor, negativeG);

    vec3 color = mix(source.rgb, blackoutColor, positiveG);
    color = mix(color, redoutColor, negativeG);
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
