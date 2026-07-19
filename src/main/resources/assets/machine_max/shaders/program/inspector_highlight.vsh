#version 150

// 参考 KubeJS (LGPL © LatvianModder) highlight.vsh
// Machine-Max: 内构查看后处理描边 - 顶点着色器
// Position 为像素坐标，ProjMat 是正交投影矩阵，映射到 clip space

in vec4 Position;

uniform mat4 ProjMat;
uniform vec2 OutSize;

out vec2 texCoord;

void main() {
    vec4 outPos = ProjMat * vec4(Position.xy, 0.0, 1.0);
    gl_Position = vec4(outPos.xy, 0.2, 1.0);
    texCoord = Position.xy / OutSize;
}
