#version 150

// Machine-Max: 失色后处理 - 顶点着色器
// 全屏四边形顶点映射，Position 为像素坐标

in vec4 Position;

uniform mat4 ProjMat;
uniform vec2 OutSize;

out vec2 texCoord;

void main() {
    vec4 outPos = ProjMat * vec4(Position.xy, 0.0, 1.0);
    gl_Position = vec4(outPos.xy, 0.2, 1.0);
    texCoord = Position.xy / OutSize;
}
