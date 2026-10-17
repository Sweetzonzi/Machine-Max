#version 150

// Machine-Max: 屏幕空间光照后处理 - 片段着色器
//
// 每像素遍历光源数组：重建本像素的视空间表面点，按世界空间距离求辐照度，求和后**一次**映射回显示值。
//
// 算子（设计文档 docs/plan/进行中/渲染-屏幕空间光照物理化设计.md §3）：
//   1. 逐光源  E_i = c_i · I_i · o_i · w(d) / max(d², d_min²)
//      c_i 是线性光色（光源表提交时已从 sRGB 解码）、I_i 是光源在 1 m 处的辐照度，
//      w(d) = (1 − (d/R)⁴)² 在 d = R 处值与一阶导同时为 0，包围剔除不会在边界留硬边；
//      d_min 是 1/d² 的奇点截断，逐来源上传；
//      o_i = max((1 − NormalWeight) + NormalWeight·dot(N, L̂), 0) 是表面朝向响应（S4）：
//      N 是视空间表面法线、L̂ 是表面点指向光心的单位向量。NormalWeight = 1 时即完整 Lambert 项，
//      = 0 时 o_i 恒为 1（不乘法线项），中间值让掠射面保留 (1 − NormalWeight) 的底、背光面仍为 0。
//   2. 求和得到逐通道响应 e = Σ E_i：先求和再合成，因此结果与光源遍历顺序无关。
//   3. C_out = 1 − (1 − C)/(1 + e·q)，q = C + β(1 − C)：输出恒在 [0,1]，渐近饱和、不硬截断。
//
// 未受光像素逐位不变：e 全零时直接返回 src，连解码/编码的浮点往返都不参与；某个通道 e 为零时
// 该通道同样保持原值，因此纯蓝光不会抬红绿通道。
//
// 法线由邻域深度重建（S4）：每像素在横竖两条轴上各取一个相邻像素，每条轴只用与中心深度更近的
// 那一侧作切向，因此轮廓处不会把跨物体的深度跳变当成平面斜率。任一轴取不到可信邻居（天空、
// 近裁面、退化三角形）时该像素回落为 o = 1——宁可少乘法线项，也不让物体轮廓变黑。
// 法线方向统一朝相机：叉积的符号随切向取侧而变，靠 dot(N, -P) 校正。
//
// 本文件是通用光照链的当前编码：光源表里的条目按球光（POINT）打包，遮挡策略由 LightOcclusion
// 逐盏选择 NONE 或 SOURCE_DEPTH（S2）。锥光（SPOT）与 S3（SCREEN_RAY）在光源表提交处被报告为
// 不支持，不会走到这里——它们的字段与分支在 P5 增加。
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
uniform float LightColor[64];       // 每槽 4 个：线性颜色 rgb、强度（1 m 处辐照度）
uniform float LightTol[16];         // 每槽 1 个：遮挡容差的绝对分量(m)，由来源折算
uniform float LightOcclusion[16];   // 每槽 1 个：遮挡策略，0 = 不做遮挡判定，1 = 在光心投影处采一次深度（S2）
uniform float LightMinDist[16];     // 每槽 1 个：1/d² 的奇点截断 d_min(m)，逐来源取值
uniform float NormalWeight;         // S4 朝向响应权重：0 = 不乘法线项，1 = 完整 Lambert 项；0.5 即 wrap（0.5 + 0.5·cos）

in  vec2 texCoord;
out vec4 fragColor;

const float NEAR_Z     = 0.05;      // 原版近平面（m）：用于剔除相机内部与背后的像素
const float DARK_BOOST = 0.03;      // β：暗部补偿常数，越大越容易把黑色材质抬灰
const float E_MAX      = 1024.0;    // 单光源辐照度上限：兜住异常内容，避免 inf/NaN 进入求和
const float D_MIN_FLOOR = 0.001;    // d_min 的下限（m）：d_min 配 0 且表面点正好落在光心时不留 0/0

// sRGB 编码的显示值 → 线性辐射度（分段函数，与光源表提交侧的换算同源）
vec3 srgbToLinear(vec3 c) {
    vec3 low  = c / 12.92;
    vec3 high = pow((c + 0.055) / 1.055, vec3(2.4));
    return mix(high, low, lessThanEqual(c, vec3(0.04045)));
}

// 线性辐射度 → sRGB 编码
vec3 linearToSrgb(vec3 c) {
    vec3 low  = c * 12.92;
    vec3 high = 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055;
    return mix(high, low, lessThanEqual(c, vec3(0.0031308)));
}

// 深度快照 → 前向深度（m）。天空与近裁面没有可信表面，返回 false。
bool forwardDepth(vec2 uv, out float zfwd) {
    float z = texture(MCDepthSampler, uv).r;
    if (z >= 1.0 || z <= 0.0) return false;
    float f = DepthB / (z + DepthA);
    if (f <= NEAR_Z) return false;
    zfwd = f;
    return true;
}

// 本像素的视空间表面点：沿射线 t·vec3(q, -1)，前向深度恰为 t
vec3 viewPos(vec2 uv, float zfwd) {
    vec2 q = vec2((uv.x * OutSize.x - OutSize.x * 0.5) / FocalPx,
                  (uv.y * OutSize.y - OutSize.y * 0.5) / FocalPx);
    return vec3(q, -1.0) * zfwd;
}

// 邻域深度 → 视空间法线（S4），方向朝相机。
// 每条轴取与中心深度更接近的一侧作切向：轮廓处另一侧是另一个物体，用它算出的斜率不是本平面的。
// 任一轴两侧都不可用、或切向退化时返回 false，调用方回落为 o = 1。
bool viewNormal(vec2 uv, float zc, out vec3 n) {
    vec2 texel = 1.0 / OutSize;
    vec2 uvL = uv - vec2(texel.x, 0.0);
    vec2 uvR = uv + vec2(texel.x, 0.0);
    vec2 uvD = uv - vec2(0.0, texel.y);
    vec2 uvU = uv + vec2(0.0, texel.y);

    float zL, zR, zD, zU;
    bool okL = forwardDepth(uvL, zL);
    bool okR = forwardDepth(uvR, zR);
    bool okD = forwardDepth(uvD, zD);
    bool okU = forwardDepth(uvU, zU);

    // 两个切向都取“指向 +x / +y”的方向，叉积符号才与取侧无关
    vec3 p  = viewPos(uv, zc);
    vec3 tx;
    bool okX;
    if (okL && (!okR || abs(zc - zL) < abs(zR - zc))) {
        tx = p - viewPos(uvL, zL); okX = true;
    } else if (okR) {
        tx = viewPos(uvR, zR) - p; okX = true;
    } else {
        okX = false;
    }
    vec3 ty;
    bool okY;
    if (okD && (!okU || abs(zc - zD) < abs(zU - zc))) {
        ty = p - viewPos(uvD, zD); okY = true;
    } else if (okU) {
        ty = viewPos(uvU, zU) - p; okY = true;
    } else {
        okY = false;
    }
    if (!okX || !okY) return false;

    vec3 c = cross(tx, ty);
    float len2 = dot(c, c);
    if (len2 < 1.0e-12) return false;              // 相邻像素共线：本像素没有可信平面

    n = c * inversesqrt(len2);
    if (dot(n, -p) < 0.0) n = -n;                  // 统一朝相机，背面像素不会算出背光
    return true;
}

void main() {
    vec4 src = texture(DiffuseSampler, texCoord);
    if (int(LightCount) <= 0) { fragColor = vec4(src.rgb, 1.0); return; }

    vec2 px = texCoord * OutSize;

    // 本像素的视空间视线方向（未归一化时 z 分量为 -1）。
    // px.y 自下而上：后处理全屏 quad 的顶点是 (0,0),(W,0),(W,H),(0,H)、正交矩阵是 setOrtho(0, W, 0, H, ...)，
    // 因此 Position.y = 0 落在屏幕下沿，y 项必须写成 (px.y - H/2)。写成 (H/2 - px.y) 会把整个世界上下镜像：
    // 相机下方的光斑被画到天上，而天上像素会被天空早退跳过——表现是"光斑跟着视角跑、地上什么都没有"。
    vec2  q   = vec2((px.x - OutSize.x * 0.5) / FocalPx,
                     (px.y - OutSize.y * 0.5) / FocalPx);
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

    // S4 朝向响应：从邻域深度重建本像素的表面法线。NormalWeight = 0 时整段跳过，
    // 不发生任何额外深度采样，算子与不乘法线项时逐位一致。
    float nw   = clamp(NormalWeight, 0.0, 1.0);
    vec3  N    = vec3(0.0, 0.0, 1.0);
    bool  hasN = false;
    if (nw > 0.0) hasN = viewNormal(texCoord, zfwd, N);   // 写成 if 而不是 &&，采样与否不受求值顺序影响

    // 逐通道的光照响应 e（辐照度之和）；e = 0 的通道在编码后保持原值
    vec3 e = vec3(0.0);
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
        // 容差按来源折算（绝对分量）+ 前向深度（相对分量）取较大者：容差太小则光源
        // 自身的模型会把光源剔掉、光照凭空消失，太大则近处遮挡物漏光。
        // 是否执行本判定由光源表逐盏给出：爆炸火光用“不判定”（它的光心可能不在视野内，
        // 也可能被起爆自身的粒子与烟尘挡住，用光心深度判定会整盏误杀），曳光用 S2。
        float zc = -C.z;
        if (LightOcclusion[i] > 0.5 && zc > NEAR_Z) {
            vec2 lightPx = vec2(OutSize.x * 0.5 + FocalPx * C.x / zc,
                                OutSize.y * 0.5 + FocalPx * C.y / zc);   // y 同样自下而上
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

        // 有限支撑窗：必须在 d = R 处严格归零且一阶导为零，否则包围剔除会在阈值处留下硬边。
        // (1 − (d/R)⁴)² 的中心接近 1、边缘 C¹，把 1/d² 的长尾平滑截断。
        float x   = d / R;
        float x2  = x * x;
        float win = 1.0 - x2 * x2;
        win *= win;

        // 1/d² 在 d → 0 时发散：用逐来源的 d_min 截断，光源贴到表面上也不会出现无穷大
        float dmin  = max(LightMinDist[i], D_MIN_FLOOR);
        float dEff2 = max(d * d, dmin * dmin);

        // 朝向响应（S4）：背向光源的表面 cos ≤ 0（连同墙后漏光一并消失），斜射按 Lambert 折减。
        // 权重 nw 把结果在"不乘法线项"与"完整 Lambert"之间插值：(1 − nw) + nw·cos，再钳到 0。
        // 钳零放在插值之后：nw < 1 时掠射面拿到 (1 − nw) 的底，但真正背光的面仍然为 0，
        // 不会因为权重而凭空照亮墙的背面。法线不可信时 orient 保持 1，只损失朝向信息。
        float orient = 1.0;
        if (hasN && d > 1.0e-4) {
            orient = max(mix(1.0, dot(N, (C - P) / d), nw), 0.0);
        }

        float falloff = win * orient / dEff2;

        // 逐光源钳制：曲线本身有界，但内容里的强度没有上界，先兜住异常输入
        vec3 radiance = vec3(LightColor[i * 4], LightColor[i * 4 + 1], LightColor[i * 4 + 2]);
        e += min(radiance * (LightColor[i * 4 + 3] * falloff), vec3(E_MAX));
    }

    // 没有任何通道受光：直接返回原画面，逐位不变（不解码、不编码）
    if (e.r <= 0.0 && e.g <= 0.0 && e.b <= 0.0) { fragColor = vec4(src.rgb, 1.0); return; }

    // 线性空间里做增量，再编码回显示值
    vec3 C   = srgbToLinear(src.rgb);
    vec3 qd  = C + DARK_BOOST * (vec3(1.0) - C);
    vec3 lit = vec3(1.0) - (vec3(1.0) - C) / (vec3(1.0) + e * qd);
    vec3 encoded = linearToSrgb(lit);

    // 逐通道旁路：该通道没有光照响应时保持原始编码值，避免解码/编码的浮点往返引入 ±1 的抖动
    encoded = mix(encoded, src.rgb, lessThanEqual(e, vec3(0.0)));

    // alpha 必须强制为 1.0：链尾的 blit 会做 alpha 混合，把 alpha 留给画面内容会把天空清成黑色
    fragColor = vec4(encoded, 1.0);
}
