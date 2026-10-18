#version 150

// Machine-Max: 屏幕空间光照后处理 - 片段着色器
//
// 每像素遍历光源数组：重建本像素的视空间表面点，按世界空间距离求辐照度，求和后**一次**映射回显示值；
// 再叠加沿视线积分出的空气散射光晕。两项都是线性量，各自钳制后相加。
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
//   4. 光晕：glow_i = Scatter · k_i · c_i · I_i · I0_i · win(b/Rg_i)，逐通道钳到 GLOW_MAX 后与
//      C_out 线性相加。I0 是视线穿过阈值半径 Rg 的球时 ∫1/r² dt 的解析解（§3.6）：
//        b  = 相机视线到光心的垂距（被该光源的 d_min 截断，视线正穿光心时才有界）
//        h  = sqrt(Rg² − b²)                     弦半长
//        t0 = max(0, tc − h)，t1 = min(tc + h, t_hit)
//        I0 = [atan((t1 − tc)/b) − atan((t0 − tc)/b)] / b
//      t_hit 是深度快照给出的第一个表面距离，光晕因此获得逐像素遮挡：墙前的空气没有光晕，
//      光心在墙后则区间为空。t_hit 必须用 zfwd·L 而不是 zfwd——视空间射线是 t·(q,−1)，
//      只有 z 分量等于前向深度，真实长度要乘 |(q,−1)| = L。
//      光晕加在合成曲线**之后**：它不乘表面颜色、不受曲线压缩。天空的线性值接近 0、q ≈ β，
//      并进 e 会被曲线吃掉。
//      半径 Rg 与倍率 k 由 Java 侧按 Rg = GlowScale·sqrt(I_base)·k 算好上传，着色器不做 sqrt；
//      I_base 是内容强度（不含时间包络），因此尺寸在闪烁期间稳定，变的是亮度。
//   5. 星芒：glare_i = GLARE_GAIN · s · g_i · c_i · max(armX, armY)，屏幕空间、固定角度。
//      s = L/(L + GLARE_HALF) 是"相机处表观照度" L = I/max(|C|², d_min²) 的饱和曲线；臂长随 s 从
//      GLARE_MIN_DEG 长到 GLARE_MAX_DEG，臂根亮度同时按 s 缩放，所以更近更亮的闪光是更长更亮的十字。
//      臂长必须长过光晕的可见半径（信号弹 30 m 处光晕约 23°），否则星芒会缩在光晕的饱和核心里、
//      只把中心撑成一个方块。
//      两条臂沿屏幕 ±x / ±y：星芒是镜头成像（光圈衍射 + 传感器饱和溢出），随画幅固定，不随世界转。
//      横向剖面是向臂尖收窄的楔形（根部按屏高取宽），沿臂用 `(1 − u)^1.5`，缓慢衰减、臂尖平滑归零，不依赖窗函数。
//      收窄方向不能反：亮度沿臂下降本来就会让可见宽度变窄，σ 若再外扩，两者相抵就成了等宽长条。
//      **它不按像素深度截断**：墙挡住光源时整条消失（光源自身的遮挡判定仍生效），但光源可见时
//      星芒会压在墙与车体上——这是真实镜头的行为，也是它与第 4 项最本质的差别。
//      星芒与光晕共用 GLOW_MAX 的加法预算，参数完全独立：光晕是米制的阈值半径与 1/D 律，
//      星芒是固定角度与 1/D² 律。
//
// 受光修正的旁路：e 与 glow/glare 全零时直接返回 src，连解码/编码的浮点往返都不参与；某个通道两者
// 之和为零时该通道同样保持原值，因此纯蓝光不会抬红绿通道。天空像素只在光晕也为零时逐位不变。
//
// 法线由邻域深度重建（S4）：每像素在横竖两条轴上各取一个相邻像素，每条轴只用与中心深度更近的
// 那一侧作切向，因此轮廓处不会把跨物体的深度跳变当成平面斜率。任一轴取不到可信邻居（天空、
// 近裁面、退化三角形）时该像素回落为 o = 1——宁可少乘法线项，也不让物体轮廓变黑。
// 法线方向统一朝相机：叉积的符号随切向取侧而变，靠 dot(N, -P) 校正。
//
// 天空没有表面，但没有表面不等于没有画面：天空是光晕的主要背景，因此天空像素照常进入光照循环，
// 只是表面项恒为零。爆炸波前折射把背景视为"永不遮挡"（zScene = 1e9），与这里的方向相反，
// 两侧不要互相照抄。
//
// 本文件是通用光照链的当前编码：光源表里的条目按球光（POINT）打包，遮挡策略由 LightOcclusion
// 逐盏选择 NONE 或 SOURCE_DEPTH（S2）。锥光（SPOT）与 S3（SCREEN_RAY）在光源表提交处被报告为
// 不支持，不会走到这里——它们的字段与分支在 P5 增加。
//
// 两条包围剔除缺一不可：
//   perp2 > Rc²  视线到光心的垂距超过 Rc —— 必要，视线完全不穿过球时球内任何点都不在此视线上
//   d >= R       表面点到光心的距离超过 R —— 充分，真正决定表面项是否为零
// 只留前者会漏（视线穿过球但表面点远在球外），只留后者则失去提前退出的收益。
// Rc = max(R, Rg)：光晕球可能比照明球大，垂距剔除处不能把光晕一起丢掉。
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
uniform float LightGlow[32];        // 每槽 2 个：光晕阈值半径 Rg(m)、倍率 k；k = 0 关闭该光源的光晕
uniform float LightGlare[16];       // 每槽 1 个：星芒（十字炫光）的倍率 g；g = 0 关闭该光源的星芒
uniform float NormalWeight;         // S4 朝向响应权重：0 = 不乘法线项，1 = 完整 Lambert 项；0.5 即 wrap（0.5 + 0.5·cos）

in  vec2 texCoord;
out vec4 fragColor;

const float NEAR_Z     = 0.05;      // 原版近平面（m）：用于剔除相机内部与背后的像素
const float DARK_BOOST = 0.03;      // β：暗部补偿常数，越大越容易把黑色材质抬灰
const float E_MAX      = 1024.0;    // 单光源辐照度上限：兜住异常内容，避免 inf/NaN 进入求和
const float D_MIN_FLOOR = 0.001;    // d_min 的下限（m）：d_min 配 0 且表面点正好落在光心时不留 0/0
const float SCATTER    = 2.1e-4;    // 光晕整体强度：信号弹 30 m 处中心接近饱和的标定起点（§4.3）
const float GLOW_MAX   = 4.0;       // 光晕与星芒的逐通道上限：兜异常内容与贴脸光源
const float GLARE_HALF = 0.6;       // 星芒饱和曲线的半值照度：表观照度等于它时臂长与亮度各取一半
const float GLARE_MIN_DEG = 2.0;    // 星芒最短臂长（度）：很远的闪光只剩一个点
const float GLARE_MAX_DEG = 22.0;   // 星芒最长臂长（度）：必须长过光晕的可见半径（信号弹 30 m 约 23°）
const float GLARE_GAIN = 2.0;       // 臂根亮度（曲线之后的线性值）：2.0 即压到接近纯白
const float GLARE_SIGMA_REL = 0.0022;   // 臂根部的横向标准差 = 屏高 × 它（屏高 1080 时 2.4 px）：按屏高取，改分辨率不改观感
const float GLARE_TAPER = 0.55;     // 收窄系数：臂尖处横向标准差 = (1 − TAPER)·根部，臂因此根粗尖细
const float GLARE_MAX_FRACTION = 0.45; // 臂长占屏高的比例上限：兜住小视场（炮镜倍率）把星芒放大到铺满画面
const float DEG_TO_RAD = 0.01745329252; // 度 → 弧度

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

    // 本像素有没有表面。深度取到端点（天空/远平面）或在近裁面之内时没有表面：
    // 表面项与法线整段跳过，但光晕照算——天空是光晕的主要背景。
    // 两个端点都判，因此不假设深度方向——标准约定下远平面是 1.0，
    // 反向深度（reversed-Z）约定下是 0.0；两种约定下可见几何都取不到这两个值
    // （标准约定里 0.0 只出现在近平面上，而那里的几何已被裁剪掉）。
    float zBuf = texture(MCDepthSampler, texCoord).r;
    bool  hasSurface = !(zBuf >= 1.0 || zBuf <= 0.0);
    float zfwd = 0.0;
    vec3  P    = vec3(0.0);
    float tHit = 1.0e30;                     // 视线上第一个表面的距离；没有表面即无穷远
    if (hasSurface) {
        zfwd = DepthB / (zBuf + DepthA);
        hasSurface = zfwd > NEAR_Z;
    }
    if (hasSurface) {
        P    = vec3(q, -1.0) * zfwd;
        tHit = zfwd * L;                     // 射线参数 t 对应真实距离 t·L，不是 t
    }

    // S4 朝向响应：从邻域深度重建本像素的表面法线。没有表面、或 NormalWeight = 0 时整段跳过，
    // 前者没有可信平面，后者不发生任何额外深度采样、算子与不乘法线项时逐位一致。
    float nw   = clamp(NormalWeight, 0.0, 1.0);
    vec3  N    = vec3(0.0, 0.0, 1.0);
    bool  hasN = false;
    if (hasSurface && nw > 0.0) hasN = viewNormal(texCoord, zfwd, N);   // 写成 if 而不是 &&，采样与否不受求值顺序影响

    // 逐通道响应：e 是表面辐照度之和，glow 是空气散射、glare 是星芒，后两者都是线性、都不进合成曲线
    vec3 e     = vec3(0.0);
    vec3 glow  = vec3(0.0);
    vec3 glare = vec3(0.0);

    for (int i = 0; i < int(LightCount); i++) {
        vec3  C  = vec3(LightView[i * 4], LightView[i * 4 + 1], LightView[i * 4 + 2]);
        float R  = LightView[i * 4 + 3];
        float Rg = LightGlow[i * 2];
        float gk = LightGlow[i * 2 + 1];
        float gg = LightGlare[i];
        if (R <= 0.0) continue;

        vec3  radiance  = vec3(LightColor[i * 4], LightColor[i * 4 + 1], LightColor[i * 4 + 2]);
        float intensity = LightColor[i * 4 + 3];

        // 必要性：视线到光心的垂距超过影响半径，球内任何点都不在此视线上。
        // 用垂距而不是"屏幕圆"，屏幕角落处同样正确。光晕球可能比照明球大，取两者较大者。
        float tc     = dot(C, dir);
        float perp2  = max(dot(C, C) - tc * tc, 0.0);
        float Rc     = max(R, gk > 0.0 ? Rg : 0.0);
        bool  haloIn = perp2 <= Rc * Rc;

        // 光心的屏幕投影：遮挡判定与星芒都要用。zcSafe 只防 0 除；有效范围由 zc 判断。
        float zc      = -C.z;
        float zcSafe  = max(zc, NEAR_Z);
        vec2  lightPx = vec2(OutSize.x * 0.5 + FocalPx * C.x / zcSafe,
                             OutSize.y * 0.5 + FocalPx * C.y / zcSafe);   // y 同样自下而上

        // 星芒没有米制范围，可见性只能在像素空间判定：以光心投影为中心、边长 2·thPx 的方框。
        // 光心在相机背后时没有可用的投影，整条跳过。
        vec2  dp       = vec2(0.0);
        float thPx     = 0.0;
        float glareAmp = 0.0;
        bool  glareIn  = false;
        if (gg > 0.0 && zc > NEAR_Z) {
            float dmin  = max(LightMinDist[i], D_MIN_FLOOR);
            float lApp  = intensity / max(dot(C, C), dmin * dmin);         // 镜头处的表观照度
            float sat   = lApp / (lApp + GLARE_HALF);
            float thRad = (GLARE_MIN_DEG + (GLARE_MAX_DEG - GLARE_MIN_DEG) * sat) * DEG_TO_RAD;
            thPx     = min(thRad * sqrt(gg) * FocalPx, OutSize.y * GLARE_MAX_FRACTION);
            dp       = px - lightPx;
            glareIn  = thPx > 0.5 && abs(dp.x) <= thPx && abs(dp.y) <= thPx;
            glareAmp = GLARE_GAIN * sat * gg;
        }
        if (!haloIn && !glareIn) continue;

        // 光源级遮挡：在光源自身的屏幕位置采一次深度，显著更近即判定光源在几何体之后。
        // 容差按来源折算（绝对分量）+ 前向深度（相对分量）取较大者：容差太小则光源
        // 自身的模型会把光源剔掉、光照凭空消失，太大则近处遮挡物漏光。
        // 是否执行本判定由光源表逐盏给出：爆炸火光用“不判定”（它的光心可能不在视野内，
        // 也可能被起爆自身的粒子与烟尘挡住，用光心深度判定会整盏误杀），曳光用 S2。
        // 它同时是星芒的门禁：光源自身都不在画面上时没有星芒。
        if (LightOcclusion[i] > 0.5 && zc > NEAR_Z) {
            if (all(greaterThanEqual(lightPx, vec2(0.0))) &&
                all(lessThan(lightPx, OutSize))) {                  // 越界按可见处理
                float tol   = max(LightTol[i], zc * OcclusionTolRel);
                float zHere = DepthB / (texture(MCDepthSampler, lightPx / OutSize).r + DepthA);
                if (zHere < zc - tol) continue;                     // 光源在几何体之后
            }
        }

        // 表面项：只在本像素有表面、且表面点落在照明半径内时贡献
        if (haloIn && hasSurface) {
            float d = length(P - C);
            if (d < R) {
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

                // 逐光源钳制：曲线本身有界，但内容里的强度没有上界，先兜住异常输入
                e += min(radiance * (intensity * win * orient / dEff2), vec3(E_MAX));
            }
        }

        // 光晕项：沿视线对 1/r² 定积分。有无表面都要算——夜空里的光晕主要画在天空上。
        // 两条前置判断：b ≥ Rg 时弦长为虚数；t1 ≤ t0 时可见空气段为空。
        if (gk > 0.0 && Rg > 0.0) {
            float b = max(sqrt(perp2), max(LightMinDist[i], D_MIN_FLOOR));   // 视线正穿光心时靠 d_min 有界
            if (b < Rg) {
                float h  = sqrt(Rg * Rg - b * b);
                float t0 = max(0.0, tc - h);
                float t1 = min(tc + h, tHit);
                if (t1 > t0) {
                    float i0 = (atan((t1 - tc) / b) - atan((t0 - tc) / b)) / b;
                    float x  = b / Rg;
                    float x2 = x * x;
                    float gw = 1.0 - x2 * x2;                            // 与表面项同一条窗，边缘 C¹
                    gw *= gw;
                    glow += min(radiance * (SCATTER * gk * intensity * i0 * gw), vec3(GLOW_MAX));
                }
            }
        }

        // 星芒项：屏幕空间的固定角度十字。不查深度、不被前景截断（镜头内的成像，不是空气里的散射），
        // 只在光源自身可见（上面的光源级遮挡）时出现。
        if (glareIn) {
            float ux = abs(dp.x) / thPx;
            float uy = abs(dp.y) / thPx;
            float ax = max(1.0 - ux, 0.0);
            float ay = max(1.0 - uy, 0.0);
            ax *= sqrt(ax);                                        // (1−u)^1.5：沿臂缓慢衰减、臂尖平滑归零
            ay *= sqrt(ay);
            float sigma = max(1.2, OutSize.y * GLARE_SIGMA_REL);   // 根部宽度，按屏高取
            float sx = sigma * (1.0 - GLARE_TAPER * ux);           // 向臂尖收窄：根粗尖细，不是等宽长条
            float sy = sigma * (1.0 - GLARE_TAPER * uy);
            float armX = ax * exp(-(dp.y * dp.y) / (sx * sx));     // 沿 x 衰减、横跨 y 收窄
            float armY = ay * exp(-(dp.x * dp.x) / (sy * sy));
            // max 而不是 sum：两条臂在光心相交，相加会把那里叠成一块方块
            glare += min(radiance * (glareAmp * max(armX, armY)), vec3(GLOW_MAX));
        }
    }

    // 表面项、光晕与星芒都没有贡献：直接返回原画面，逐位不变（不解码、不编码）
    vec3 added = e + glow + glare;
    if (all(lessThanEqual(added, vec3(0.0)))) { fragColor = vec4(src.rgb, 1.0); return; }

    // 线性空间里做增量，再编码回显示值；光晕与星芒在曲线之后叠加，不乘表面颜色、不受曲线压缩。
    // 两者共用同一个加法预算：任一项再亮也只会把该像素推到 GLOW_MAX。
    vec3 C   = srgbToLinear(src.rgb);
    vec3 qd  = C + DARK_BOOST * (vec3(1.0) - C);
    vec3 lit = vec3(1.0) - (vec3(1.0) - C) / (vec3(1.0) + e * qd);
    lit += min(glow + glare, vec3(GLOW_MAX));
    vec3 encoded = linearToSrgb(lit);

    // 逐通道旁路：该通道既没有表面光照，也没有光晕与星芒时保持原始编码值，避免解码/编码的浮点往返引入 ±1 的抖动
    encoded = mix(encoded, src.rgb, lessThanEqual(added, vec3(0.0)));

    // alpha 必须强制为 1.0：链尾的 blit 会做 alpha 混合，把 alpha 留给画面内容会把天空清成黑色
    fragColor = vec4(encoded, 1.0);
}
