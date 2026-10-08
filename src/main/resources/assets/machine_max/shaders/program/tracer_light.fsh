#version 150

// Machine-Max: 投射物曳光光照后处理 - 片段着色器
//
// 每像素遍历光源数组：重建本像素的视空间表面点，按世界空间距离做有限支撑衰减，
// 再用 screen 混合叠加到已画好的画面上。
//
// 两条包围剔除缺一不可：
//   perp2 > R*R  视线到光心的垂距超过 R —— 必要，视线完全不穿过球时球内任何点都不在此视线上
//   d >= R       表面点到光心的距离超过 R —— 充分，真正决定贡献是否为零
// 只留前者会漏（视线穿过球但表面点远在球外），只留后者则失去提前退出的收益。
//
// 天空必须排除（深度取到端点值就直接返回）：背景没有表面，被照亮就会把整片天空染色。
// 注意这条与爆炸波前折射的语义**方向相反**——折射把背景视为"永不遮挡"（zScene = 1e9），
// 因为没有几何体就不该发生折射。两侧不要互相照抄。
//
// 代价上限是常量：循环上界为槽位数 LightCount（≤ 16），与"同屏多少发"无关，
// 多发只是让每像素的循环变长，不会造成帧率随发数崩塌。

uniform sampler2D DiffuseSampler;   // 上一 pass 的画面
uniform sampler2D MCDepthSampler;   // 链内世界深度快照（AFTER_LEVEL 从主目标拷入）
uniform vec2  OutSize;              // 输出尺寸（px），由 PostPass 逐 pass 写入
uniform float FocalPx;              // 像素焦距 f
uniform float DepthA;               // d_tex = DepthB / z - DepthA
uniform float DepthB;
uniform float LightCount;           // 有效光源数；必须是 float（int uniform 无法经 set(float) 写入）
uniform float OcclusionTolRel;      // 遮挡容差的相对分量
uniform float LightView[64];        // 每槽 4 个：视空间光心 xyz、世界半径 R(m)
uniform float LightColor[64];       // 每槽 4 个：颜色 rgb、强度
uniform float LightTol[16];         // 每槽 1 个：遮挡容差的绝对分量(m)，由弹种口径折算

in  vec2 texCoord;
out vec4 fragColor;

const float NEAR_Z = 0.05;          // 原版近平面（m）：用于剔除相机内部与背后的像素

void main() {
    vec4 src = texture(DiffuseSampler, texCoord);
    if (int(LightCount) <= 0) { fragColor = vec4(src.rgb, 1.0); return; }

    vec2 px = texCoord * OutSize;

    // 本像素的视空间视线方向（未归一化时 z 分量为 -1）
    vec2  q   = vec2((px.x - OutSize.x * 0.5) / FocalPx,
                     (OutSize.y * 0.5 - px.y) / FocalPx);
    float L   = sqrt(1.0 + dot(q, q));
    vec3  dir = vec3(q, -1.0) / L;

    // 深度快照 → 前向深度（m）
    float zBuf = texture(MCDepthSampler, texCoord).r;
    // 天空/远平面：不照亮。两个端点都判，因此不假设深度方向——标准约定下远平面是 1.0，
    // 反向深度（reversed-Z）约定下是 0.0；两种约定下可见几何都取不到这两个值
    // （标准约定里 0.0 只出现在近平面上，而那里的几何已被裁剪掉）。
    if (zBuf >= 1.0 || zBuf <= 0.0) { fragColor = vec4(src.rgb, 1.0); return; }
    float zfwd = DepthB / (zBuf + DepthA);
    if (zfwd <= NEAR_Z) { fragColor = vec4(src.rgb, 1.0); return; }

    // 本像素的视空间表面点：沿射线 t·vec3(q, -1)，前向深度恰为 t
    vec3 P = vec3(q, -1.0) * zfwd;

    vec3 light = vec3(0.0);
    for (int i = 0; i < int(LightCount); i++) {
        vec3  C = vec3(LightView[i * 4], LightView[i * 4 + 1], LightView[i * 4 + 2]);
        float R = LightView[i * 4 + 3];
        if (R <= 0.0) continue;

        // 必要性：视线到光心的垂距超过 R，球内任何点都不在此视线上。
        // 用垂距而不是"屏幕圆"，屏幕角落处同样正确。
        float tc    = dot(C, dir);
        float perp2 = max(dot(C, C) - tc * tc, 0.0);
        if (perp2 > R * R) continue;

        // 光源级遮挡：在光源自身的屏幕位置采一次深度，显著更近即判定光源在几何体之后。
        // 容差按弹种口径折算（绝对分量）+ 前向深度（相对分量）取较大者：容差太小则投射物
        // 自身的模型会把光源剔掉、光照凭空消失，太大则近处遮挡物漏光。
        float zc = -C.z;
        if (zc > NEAR_Z) {
            vec2 lightPx = vec2(OutSize.x * 0.5 + FocalPx * C.x / zc,
                                OutSize.y * 0.5 - FocalPx * C.y / zc);
            if (all(greaterThanEqual(lightPx, vec2(0.0))) &&
                all(lessThan(lightPx, OutSize))) {                  // 越界按可见处理
                float tol   = max(LightTol[i], zc * OcclusionTolRel);
                float zHere = DepthB / (texture(MCDepthSampler, lightPx / OutSize).r + DepthA);
                if (zHere < zc - tol) continue;                     // 光源在几何体之后
            }
        }

        // 充分性：真正决定贡献是否为零的量
        float d = length(P - C);
        if (d >= R) continue;

        // 有限支撑衰减：必须在 d = R 处严格归零，否则包围剔除会在阈值处留下硬边。
        // 二次窗中心平坦、边缘 C^0，是代价最低的严格归零函数。
        float att = 1.0 - d / R;
        att *= att;
        light += vec3(LightColor[i * 4], LightColor[i * 4 + 1], LightColor[i * 4 + 2])
               * (att * LightColor[i * 4 + 3]);
    }

    // LDR 帧没有 HDR 缓冲可溢出：纯加法在雪地、白色车体、明亮天空边缘会削顶成一片纯白。
    // screen 混合 out = light + src·(1 - light) 使暗表面吃到近乎全部贡献、亮表面几乎不变，
    // 且数学上永不削顶。累加后对 light 做一次 min(·, 1) 作为保险。
    light = min(light, vec3(1.0));
    // alpha 必须强制为 1.0：链尾的 blit 会做 alpha 混合，把 alpha 留给画面内容会把天空清成黑色
    fragColor = vec4(src.rgb + light * (1.0 - src.rgb), 1.0);
}
