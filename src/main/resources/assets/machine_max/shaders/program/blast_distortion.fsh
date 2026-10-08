#version 150

// Machine-Max: 爆炸波前折射后处理 - 片段着色器
//
// 以投影后的爆心为圆心，在"波前所在的细环"上做双极径向偏移：
// 判据是爆心到本像素视线的垂距落在 [R-W, R+W] 附近（即这条视线掠过了球壳），
// 因此环是精确的锥截面而不是屏幕上的圆，屏幕角落处同样准确。
// 深度在米制空间比对（DepthA/DepthB 把非线性深度还原成"离相机多少米"）。

uniform sampler2D DiffuseSampler;
uniform sampler2D MCDepthSampler;
uniform vec2  OutSize;
uniform float FocalPx;          // 像素焦距 f
uniform float DepthA;           // d_tex = DepthB / z - DepthA
uniform float DepthB;
uniform float BlastCount;       // 有效发数；必须是 float（int uniform 无法经 set(float) 写入）
uniform float BlastView[32];    // 每发 4 个：视空间爆心 xyz、世界半径 R(m)
uniform float BlastShape[32];   // 每发 4 个：壳层厚度 W(m)、偏移峰值 delta(px)、色散比 chi、备用

in  vec2 texCoord;
out vec4 fragColor;

const float NEAR_Z       = 0.05;   // 原版近平面：用于剔除相机内部与背后的像素
const float DEPTH_EPS_M  = 0.05;   // 深度容差，米
const float LOBE_BALANCE = 0.35;   // 后缘相对前缘的幅度
const float T_MIN        = -1.0;   // 剖面支撑的外边界
const float T_MAX        =  2.5;   // 剖面支撑的内边界

// 双极剖面：前缘（向内）峰在 t=0，后缘（向外）峰在 t=1。
// 两个高斯衰减很快，因此剖面自带有限支撑，[T_MIN, T_MAX] 之外没有扰动。
float gradient(float t) {
    float lead  = -exp(-4.0 * t * t);
    float trail =  exp(-4.0 * (t - 1.0) * (t - 1.0));
    return lead + LOBE_BALANCE * trail;
}

void main() {
    vec4 src = texture(DiffuseSampler, texCoord);
    vec2 px  = texCoord * OutSize;

    // 本像素的视空间视线方向（未归一化时 z 分量为 -1）
    // px.y 自下而上：后处理 quad 的顶点是 (0,0),(W,0),(W,H),(0,H) 且正交矩阵为 setOrtho(0, W, 0, H, ...)，
    // 所以 y 项必须是 (px.y - H/2)；写成 (H/2 - px.y) 会把整个环上下镜像到视线轴另一侧。
    vec2  q   = vec2((px.x - OutSize.x * 0.5) / FocalPx,
                     (px.y - OutSize.y * 0.5) / FocalPx);
    float L   = sqrt(1.0 + dot(q, q));
    vec3  dir = vec3(q, -1.0) / L;

    // 深度纹理 → 视空间米制距离（任何透视投影的深度都线性于 1/z）
    float zBuf   = texture(MCDepthSampler, texCoord).r;
    float zScene = DepthB / (zBuf + DepthA);
    if (zScene <= 0.0) zScene = 1e9;          // 背景/远平面：永不遮挡

    vec2  acc  = vec2(0.0);
    float amp  = 0.0;
    float chro = 0.0;

    for (int i = 0; i < int(BlastCount); i++) {
        vec3  C     = vec3(BlastView[i * 4], BlastView[i * 4 + 1], BlastView[i * 4 + 2]);
        float R     = BlastView[i * 4 + 3];
        float W     = BlastShape[i * 4];
        float delta = BlastShape[i * 4 + 1];
        if (R < NEAR_Z || W <= 0.0) continue;

        // 爆心到本像素视线的垂距：精确值，不做"屏幕圆"近似
        float tc    = dot(C, dir);                    // 眼到垂足的距离
        float perp2 = max(dot(C, C) - tc * tc, 0.0);
        float perp  = sqrt(perp2);

        float t = (R - perp) / W;                     // 到波前的归一化距离，向内为正
        if (t < T_MIN || t > T_MAX) continue;         // 波前之外与壳体深处都不处理

        // 近侧交点：垂距 > R 时退化为"最接近点"，在 perp = R 处连续
        float zFront = (tc - sqrt(max(R * R - perp2, 0.0))) / L;
        if (zFront < NEAR_Z) continue;                // 相机在球内，或交点在相机背后
        if (zScene < zFront - DEPTH_EPS_M) continue;  // 被前景遮挡

        // 屏幕径向方向：爆心的屏幕投影 → 本像素（y 同样自下而上）
        vec2 centerPx = vec2(OutSize.x * 0.5 + FocalPx * C.x / -C.z,
                             OutSize.y * 0.5 + FocalPx * C.y / -C.z);
        vec2 dirPx    = px - centerPx;

        float g = gradient(t) * delta;
        acc  += normalize(dirPx + vec2(1e-5, 1e-5)) * g;
        amp  += abs(g);
        // 色散：按本发的偏移峰值归一，chi 即"通道间的最大相对偏移"（0.12 表示 ±12%）
        chro  = max(chro, abs(g) / max(delta, 1e-4) * BlastShape[i * 4 + 2]);
    }

    if (amp <= 1e-4) { fragColor = vec4(src.rgb, 1.0); return; }     // 无生效像素：原样输出（alpha 强制为 1，避免 blit alpha 混合将天空清为黑色）

    // 三通道独立采样：偏移量在通道间差 ±chi，贡献远大于单纯加大偏移
    vec2 uR = texCoord + acc * (1.0 + chro) / OutSize;
    vec2 uG = texCoord + acc / OutSize;
    vec2 uB = texCoord + acc * (1.0 - chro) / OutSize;
    fragColor = vec4(texture(DiffuseSampler, uR).r,
                     texture(DiffuseSampler, uG).g,
                     texture(DiffuseSampler, uB).b,
                     1.0);
}
