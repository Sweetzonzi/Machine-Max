#version 150

// 参考 KubeJS (LGPL © LatvianModder) highlight.fsh
// Machine-Max: 内构查看后处理描边 - 片段着色器
// 功能：外轮廓 + 耐久色区域边界检测 + 浅填充/遮挡散点

uniform sampler2D DiffuseSampler;
uniform sampler2D DiffuseDepthSampler;
uniform sampler2D MCDepthSampler;

uniform float OutlineSize;
uniform vec2 InSize;
uniform vec2 OutSize;
uniform float OutlineCullSize;

in vec2 texCoord;

out vec4 fragColor;

const float COLOR_EPSILON = 0.5 / 255.0;
const float DEPTH_EPSILON = 0.0001;

// 判断该像素是否被剪影几何占据
bool occupied(vec4 value) {
    return value.a > 0.005;
}

// 判断两个像素的耐久色是否不同（不同区域）
bool differentRegionColor(vec3 a, vec3 b) {
    return any(greaterThan(abs(a - b), vec3(COLOR_EPSILON)));
}

// 棋盘散点渲染被遮挡区域
void renderOccludedDots(vec3 color) {
    ivec2 coord = ivec2(gl_FragCoord.xy);
    // 2x2 棋盘模式：奇数格输出，偶数格丢弃
    if (((coord.x + coord.y) & 1) == 0) {
        fragColor = vec4(color, 0.3);
    } else {
        discard;
    }
}

void main() {
    vec2 sampleStep = 1.0 / InSize;

    vec4 center = texture(DiffuseSampler, texCoord);
    bool centerOccupied = occupied(center);

    // 边缘检测：遍历 OutlineSize 邻域
    bool edge = false;
    vec3 edgeColor = center.rgb;

    for (float i = -OutlineSize; i <= OutlineSize; i += 1.0) {
        for (float j = -OutlineSize; j <= OutlineSize; j += 1.0) {
            // 跳过中心像素
            if (i == 0.0 && j == 0.0) continue;

            vec2 neighborUv = texCoord + vec2(i, j) * sampleStep;
            // 边界裁剪
            if (neighborUv.x < 0.0 || neighborUv.x > 1.0 ||
                neighborUv.y < 0.0 || neighborUv.y > 1.0) continue;

            vec4 neighbor = texture(DiffuseSampler, neighborUv);
            bool neighborOccupied = occupied(neighbor);

            if (centerOccupied != neighborOccupied) {
                // 背景 ↔ 有色区域边界：外轮廓
                edge = true;
                if (!centerOccupied) edgeColor = neighbor.rgb;
            } else if (centerOccupied && neighborOccupied
                       && differentRegionColor(center.rgb, neighbor.rgb)) {
                // 两个有色区域 RGB 不同：内部耐久色边界
                edge = true;
            }
        }
    }

    if (edge) {
        fragColor = vec4(edgeColor, 1.0);
        return;
    }

    // 非边缘像素
    if (!centerOccupied) {
        discard;  // 纯背景
        return;
    }

    // 被占据区域：区分是否被场景遮挡
    float inputDepth = texture(DiffuseDepthSampler, texCoord).r;
    float sceneDepth = texture(MCDepthSampler, texCoord).r;

    if (inputDepth - sceneDepth > DEPTH_EPSILON) {
        // 被场景遮挡 → 棋盘散点
        renderOccludedDots(center.rgb);
    } else {
        // 未被遮挡 → 浅色填充
        fragColor = vec4(center.rgb, 0.1);
    }
}
