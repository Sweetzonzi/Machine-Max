#version 150

// 参考 KubeJS (LGPL © LatvianModder) rendertype_highlight.fsh
// Machine-Max: 内构查看剪影着色器 - 片段着色器
// 功能：采样纹理做 alpha 遮罩（丢弃透明像素），输出不透明耐久样式色

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a < 0.005) { discard; }
    fragColor = vec4(vertexColor.rgb, 1.0);
}
