#version 150

// Machine-Max: 失色后处理 - 片段着色器
// 通过 Desaturation uniform 控制 0.0（原色）→ 1.0（完全灰度）

uniform sampler2D DiffuseSampler;
uniform float Desaturation;   // 失色程度，Java 侧每帧更新

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 color = texture(DiffuseSampler, texCoord);
    // NTSC 亮度权重：人眼对绿色最敏感，对蓝色最不敏感
    float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    fragColor = vec4(mix(color.rgb, vec3(gray), Desaturation), 1.0);
}
