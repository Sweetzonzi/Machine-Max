#version 150

// Machine-Max: 屏幕空间光照后处理 - 片段着色器
//
// 每像素遍历光源数组：重建本像素的视空间表面点，按世界空间距离求辐照度，求和后**一次**映射回显示值；
// 再叠加沿视线积分出的空气散射光晕。两项都是线性量，各自钳制后相加。
//
// 算子（设计文档 docs/plan/已实现/渲染-屏幕空间光照物理化设计.md §3）：
//   1. 逐光源  E_i = c_i · I_i · o_i · v_i · w(d) / max(d², d_min²)
//      c_i 是线性光色（光源表提交时已从 sRGB 解码）、I_i 是光源在 1 m 处的辐照度，
//      w(d) = (1 − (d/R)⁴)² 在 d = R 处值与一阶导同时为 0，包围剔除不会在边界留硬边；
//      d_min 是 1/d² 的奇点截断，逐来源上传；
//      o_i = max((1 − NormalWeight) + NormalWeight·dot(N, L̂), 0) 是表面朝向响应（S4）：
//      N 是视空间表面法线、L̂ 是表面点指向光心的单位向量。NormalWeight = 1 时即完整 Lambert 项，
//      = 0 时 o_i 恒为 1（不乘法线项），中间值让掠射面保留 (1 − NormalWeight) 的底，并允许部分背光响应。
//      v_i 是 S3 的柔和遮挡响应：在 P→C 的屏幕线段上取32个中点，将单边软深度响应平均。
//      采样覆盖线段参数[0.06,0.88]，偏置0.08m+0.002z、过渡宽度0.25m；没有有限后界。
//      平均遮挡按受光点到光源的距离加权；范围末端25%平滑淡出，范围外跳过查询。
//      它是稳定的局部遮光近似，只作用于表面项，不参与光晕与星芒。
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
//      **光晕不接受光源级遮挡判定**（LightOcclusion）：那是在光心投影处采一次深度，太粗；
//      t_hit 已经逐像素给出"这段空气可不可见"，套上光源级门禁会把半露出来的光晕整片抹掉。
//      光晕加在合成曲线**之后**：它不乘表面颜色、不受曲线压缩。天空的线性值接近 0、q ≈ β，
//      并进 e 会被曲线吃掉。
//      半径 Rg 与倍率 k 由 Java 侧按 Rg = GlowScale·sqrt(I_base)·k 算好上传，着色器不做 sqrt；
//      I_base 是内容强度（不含时间包络），因此尺寸在闪烁期间稳定，变的是亮度。
//   5. 星芒：glare_i = GLARE_GAIN · s · g_i · c_i · armX，屏幕空间的**单轴**拉丝（anamorphic）。
//      s = L/(L + GLARE_HALF) 是"相机处表观照度" L = I/max(|C|², d_min²) 的饱和曲线；臂长随 s 从
//      GLARE_MIN_DEG 长到 GLARE_MAX_DEG，臂根亮度同时按 s 缩放。
//      **只有一轴**：真实镜头里成对出现的 90° 十字来自副镜支架，光圈叶片给的是 6/8 条，圆孔只给艾里斑；
//      一轴拉丝才是柱面镜/传感器溢出那种"横向亮条"，也才不会被读成画面中央的准星。
//      横向剖面是**两级**高斯：亮芯（根部按屏高取宽，1080p 上 2.4 px）加一层 2.6 倍宽、权重 0.25 的裙边，
//      峰值归一化为 1；两层宽度都向臂尖收窄（GLARE_TAPER），所以根粗尖细，不是等宽长条。
//      收窄方向不能反：亮度沿臂下降本来就会让可见宽度变窄，σ 若再外扩，两者相抵就成了等宽长条。
//      沿臂用 `(1 − u)^1.5`，缓慢衰减、臂尖平滑归零，不依赖窗函数。
//      **它不按像素深度截断**：墙挡住光源时整条消失（光源自身的可见性判定仍生效），但光源可见时
//      星芒会压在墙与车体上——这是真实镜头的行为，也是它与第 4 项最本质的差别。第 4 项的遮挡来自
//      逐像素的 t_hit，第 5 项只接受光源级的可见性门禁，两者不要互相套用。
//      **光源自身的投影必须落在画面内**：出画的光心没有镜头成像可言，整条跳过；光源级遮挡判定因此
//      只在光心落于画面内时执行，不存在"出画跳过遮挡、只剩一条亮臂从边缘伸进来"的路径。
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
// 本文件是通用光照链的当前编码：光源表里的条目按球光（POINT）打包，表面项按 S3 做逐像素
// 屏幕空间遮挡；LightOcclusion 逐盏选择 NONE 或 SOURCE_DEPTH（S2），只门禁星芒。锥光（SPOT）
// 与逐盏 S3 模式（SCREEN_RAY）在光源表提交处被报告为不支持，不会走到这里——锥光的字段与分支在 P5 增加。
//
// S3 见 averagedLightVisibility()；traceLightRay()及深度层级仅供诊断硬命中算法。
//
// 两条包围剔除缺一不可：
//   perp2 > Rc²  视线到光心的垂距超过 Rc —— 必要，视线完全不穿过球时球内任何点都不在此视线上
//   d >= R       表面点到光心的距离超过 R —— 充分，真正决定表面项是否为零
// 只留前者会漏（视线穿过球但表面点远在球外），只留后者则失去提前退出的收益。
// Rc = max(R, Rg)：光晕球可能比照明球大，垂距剔除处不能把光晕一起丢掉。
//
// 光源循环上限为 LightCount（≤16）；每盏参与表面遮挡的光源至多读取32次深度。

uniform sampler2D DiffuseSampler;   // 上一 pass 的画面
uniform sampler2D MCDepthSampler;   // 链内世界深度快照（AFTER_LEVEL 从主目标拷入）
uniform sampler2D DepthHierarchySampler; // 最细格覆盖2×2屏幕像素的RG32F深度范围层级
uniform float HierarchyLevels;
uniform vec2  OutSize;              // 输出尺寸（px），由 PostPass 逐 pass 写入
uniform float FocalPx;              // 像素焦距 f
uniform float DepthA;               // d_tex = DepthB / z - DepthA
uniform float DepthB;
uniform float LightCount;           // 有效光源数；必须是 float（int uniform 无法经 set(float) 写入）
uniform float OcclusionTolRel;      // S2 遮挡容差的相对分量（只用于星芒的光源级判定）
uniform float LightView[64];        // 每槽 4 个：视空间光心 xyz、世界半径 R(m)
uniform float LightColor[64];       // 每槽 4 个：线性颜色 rgb、强度（1 m 处辐照度）
uniform float LightTol[16];         // 每槽 1 个：遮挡容差的绝对分量(m)，由来源折算
uniform float LightOcclusion[16];   // 每槽 1 个：星芒的光源级遮挡策略，0 = 不做判定，1 = 在光心投影处采一次深度（S2）
uniform float LightMinDist[16];     // 每槽 1 个：1/d² 的奇点截断 d_min(m)，逐来源取值
uniform float LightGlow[32];        // 每槽 2 个：光晕阈值半径 Rg(m)、倍率 k；k = 0 关闭该光源的光晕
uniform float LightGlare[16];       // 每槽 1 个：星芒（单轴拉丝）的倍率 g；g = 0 关闭该光源的星芒
uniform float NormalWeight;         // S4 朝向响应权重：0 = 不乘法线项，1 = 完整 Lambert 项；0.5 即 wrap（0.5 + 0.5·cos）
uniform float ShadowRange;          // S3受光点到光源的最大距离（m）；0关闭，范围末端25%平滑淡出
// 仅独立单帧对照程序定义 MM_LIGHT_CAPTURE；常规程序以常量0裁剪诊断分支。
#ifdef MM_LIGHT_CAPTURE
uniform float CaptureRayBudget;
uniform float CapturePixelBudget;
uniform float CaptureSamples;       // >0：等距点覆盖完整屏幕线段；0：主轴像素 DDA
uniform float CaptureThickness;
uniform float CaptureOutput;        // >0：按光源聚合显示未命中的原因
uniform float CaptureLegacy;        // >0：重放 §10 定义的 7 点、单边深度、命中平均基线
uniform float CaptureTraversal;     // -1默认32点平均；0分层硬命中；1主轴像素DDA；2完整像素格
uniform float CaptureNormalThickness; // 只用于完整像素参考：按局部法线厚度换算前向后界
uniform float CaptureOcclusionOff;
uniform float CaptureOnlyLight;     // 0所有光源，i+1只保留槽i
uniform float CaptureEndExclusion;
#else
const float CaptureRayBudget = 0.0;
const float CapturePixelBudget = 0.0;
const float CaptureSamples = 0.0;
const float CaptureThickness = 0.0;
const float CaptureOutput = 0.0;
const float CaptureLegacy = 0.0;
const float CaptureTraversal = -1.0;
const float CaptureNormalThickness = 0.0;
const float CaptureOcclusionOff = 0.0;
const float CaptureOnlyLight = 0.0;
const float CaptureEndExclusion = 0.0;
#endif

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
const float GLARE_MAX_DEG = 12.0;   // 星芒最长臂长（度）：单轴拉丝到这个长度已能压过光晕核心，再长就横贯画面
const float GLARE_GAIN = 0.3;       // 臂根亮度（曲线之后的线性值）：与光晕同量级，只把亮度抬到中灰而不追白
const float GLARE_SIGMA_REL = 0.0022;   // 亮芯的横向标准差 = 屏高 × 它（屏高 1080 时 2.4 px）：按屏高取，改分辨率不改观感
const float GLARE_TAPER = 0.55;     // 收窄系数：臂尖处横向标准差 = (1 − TAPER)·根部，臂因此根粗尖细
const float GLARE_SKIRT_REL = 2.6;  // 裙边标准差 / 亮芯标准差：亮芯之外的宽而弱的一层，压住"一根等宽线"的读感
const float GLARE_SKIRT_GAIN = 0.25; // 裙边峰值权重：与亮芯权重之和为 1，根部峰值因此仍等于臂根亮度
const float GLARE_MAX_FRACTION = 0.45; // 臂长占屏高的比例上限：兜住小视场（炮镜倍率）把星芒放大到铺满画面
const float DEG_TO_RAD = 0.01745329252; // 度 → 弧度

// S3 屏幕空间遮挡（docs/plan/已实现/渲染-屏幕空间遮挡S3设计.md §4）
const int   OCCL_MAX_STEPS  = 256;   // 单条射线的层级/原始深度查询预算；耗尽后按未知放光，0 = 关闭 S3（A/B）
const int   OCCL_PIXEL_STEPS = 256;  // 全部光源共用的每像素预算，按光源表顺序分配，限制重叠光源的总成本
const float OCCL_START_M   = 0.05;  // 从受光点沿光线排除的长度（m）
const float OCCL_END_M     = 0.15;  // 光心一端排除的长度（m），与星芒的 LightTol 独立
const float OCCL_BIAS_M    = 0.03;  // 场景厚度层前的空隙（m），同深度不会命中
const float OCCL_BIAS_REL_Z = 0.001; // 空隙随前向深度的增长量（m/m）
const float OCCL_THICKNESS_REL_Z = 0.005; // 有限层厚度按场景前向深度的比例给出
const float OCCL_THICKNESS_MIN_M = 0.05;  // 有限层厚度的米制下界
const float OCCL_THICKNESS_MAX_M = 0.15;  // 有限层厚度的米制上界
const float OCCL_EDGE_REL_INV_Z = 0.02;   // 四邻域倒数深度跨度超过该比例时使用点深度
const int   RAY_HIT        = 0;
const int   RAY_CLEAR      = 1;
const int   RAY_UNKNOWN    = 2;
const int   REASON_BUDGET  = 1;
const int   REASON_CLIPPED = 2;
const int   REASON_DEPTH   = 4;
const int   REASON_SAMPLED = 8;      // 全路径点采样未命中，点间部分没有被逐像素检查
const int   OCCL_DEBUG     = 0;     // 0 正常；1 首盏受光的命中红/未命中绿/未知蓝；2 使用预算的灰度

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
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThanEqual(uv, vec2(1.0)))) return false;
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

// 屏幕线段与一条视口轴的交集。t 是屏幕参数，同时用于 1/z 的仿射插值。
bool clipRayAxis(float origin, float delta, float limit, inout float t0, inout float t1) {
    if (abs(delta) < 1.0e-6) return origin >= 0.0 && origin <= limit;
    float a = -origin / delta;
    float b = (limit - origin) / delta;
    t0 = max(t0, min(a, b));
    t1 = min(t1, max(a, b));
    return t0 <= t1;
}

// 厚度用于细节遮挡的保守近似；诊断可显式指定固定米制厚度。
float occlusionThickness(float sceneInv) {
    if (CaptureThickness > 0.0) return CaptureThickness;
    return clamp(OCCL_THICKNESS_REL_Z / sceneInv, OCCL_THICKNESS_MIN_M, OCCL_THICKNESS_MAX_M);
}

// 连续表面用透视正确的双线性深度；物体轮廓使用点深度，避免跨物体插值侵蚀细杆。
float filteredSceneInv(vec2 pixel, float invDepthB, float sceneInv) {
    vec2 tap = pixel - 0.5;
    ivec2 base = ivec2(floor(tap));
    vec2 f = fract(tap);
    ivec2 limit = ivec2(OutSize) - 1;
    float a = texelFetch(MCDepthSampler, clamp(base, ivec2(0), limit), 0).r;
    float b = texelFetch(MCDepthSampler, clamp(base + ivec2(1, 0), ivec2(0), limit), 0).r;
    float c = texelFetch(MCDepthSampler, clamp(base + ivec2(0, 1), ivec2(0), limit), 0).r;
    float d = texelFetch(MCDepthSampler, clamp(base + ivec2(1, 1), ivec2(0), limit), 0).r;
    float spread = (max(max(a, b), max(c, d)) - min(min(a, b), min(c, d))) * abs(invDepthB);
    if (spread > sceneInv * OCCL_EDGE_REL_INV_Z) return sceneInv;
    return max(0.0, (mix(mix(a, b, f.x), mix(c, d, f.x), f.y) + DepthA) * invDepthB);
}

// 原始像素的有限厚度判据。单层相机深度只提供近似几何，未命中不能补回隐藏几何。
// 分层遍历、完整像素参考和全程点诊断共用此函数；rayInv端点相等时检测一个点。
int sampleLightRay(vec2 cell, vec2 rayInv, vec2 samplePixel, vec4 plane, bool ignorePlane, float invDepthB) {
    float sceneRaw = texelFetch(MCDepthSampler, ivec2(cell), 0).r;
    if (sceneRaw <= 0.0 || sceneRaw >= 1.0) return RAY_UNKNOWN;
    float sceneInv = (sceneRaw + DepthA) * invDepthB;
    if (!(sceneInv > 0.0 && sceneInv < 1.0 / NEAR_Z)) return RAY_UNKNOWN;
    float thickness = occlusionThickness(sceneInv);
    if (CaptureNormalThickness > 0.5) {
        vec3 normal = vec3(0.0, 0.0, 1.0);
        if (viewNormal((cell + 0.5) / OutSize, 1.0 / sceneInv, normal)) {
            vec3 ray = vec3((cell + 0.5 - OutSize * 0.5) / FocalPx, -1.0);
            thickness /= max(abs(dot(normal, ray)), 0.2);
        }
    }
    float frontScale = 1.0 + OCCL_BIAS_REL_Z + OCCL_BIAS_M * sceneInv;
    float backScale = frontScale + thickness * sceneInv;
    if (min(rayInv.x, rayInv.y) * frontScale <= sceneInv
            && max(rayInv.x, rayInv.y) * backScale >= sceneInv) {
        float planeError = abs(dot(plane.xy, cell + 0.5) + plane.z - plane.w * sceneInv);
        if (ignorePlane && planeError <= OCCL_BIAS_M * sceneInv + OCCL_BIAS_REL_Z) return RAY_CLEAR;
        // 只有点深度候选命中才读取插值邻域；光线须同时进入两种深度的有限层。
        float smoothInv = filteredSceneInv(samplePixel, invDepthB, sceneInv);
        float frontInv = min(sceneInv, smoothInv);
        float backInv = max(sceneInv, smoothInv);
        frontScale = 1.0 + OCCL_BIAS_REL_Z + OCCL_BIAS_M * frontInv;
        backScale = 1.0 + OCCL_BIAS_REL_Z + (OCCL_BIAS_M + thickness) * backInv;
        if (min(rayInv.x, rayInv.y) * frontScale <= frontInv
                && max(rayInv.x, rayInv.y) * backScale >= backInv) return RAY_HIT;
    }
    return RAY_CLEAR;
}

// 粗格只用于排除：范围相交便下钻，只有原始像素的有限厚度检测能够确认遮挡。
int traceHierarchy(vec2 origin, vec2 travel, vec2 invZ, vec4 plane, bool ignorePlane,
                   float invDepthB, int budget, inout int steps, inout int reason) {
    if (HierarchyLevels < 1.0) { reason |= REASON_DEPTH; return RAY_UNKNOWN; }
    float majorLength = max(abs(travel.x), abs(travel.y));
    int top = int(HierarchyLevels) - 1;
    bool pixelReference = CaptureTraversal > 1.5;
    int level = pixelReference ? -1 : clamp(int(floor(log2(max(majorLength / 32.0, 1.0)))) - 1, 0, top);
    float enterT = 0.0;
    vec2 inverseTravel = vec2(0.0);
    if (abs(travel.x) > 1.0e-6) inverseTravel.x = 1.0 / travel.x;
    if (abs(travel.y) > 1.0e-6) inverseTravel.y = 1.0 / travel.y;
    for (int k = 0; k < budget; k++) {
        float size = exp2(float(level + 1)); // level=-1表示原始像素。
        // 沿前进方向选边界内侧；偏移0.001像素只用于格索引，不改变相交深度区间。
        vec2 position = origin + travel * enterT;
        vec2 cell = floor((position + sign(travel) * 0.001) / size);
        vec2 boundary = (cell + step(vec2(0.0), travel)) * size;
        vec2 crossing = vec2(1.0);
        if (inverseTravel.x != 0.0) crossing.x = (boundary.x - origin.x) * inverseTravel.x;
        if (inverseTravel.y != 0.0) crossing.y = (boundary.y - origin.y) * inverseTravel.y;
        float exitT = min(1.0, min(crossing.x, crossing.y));
        if (exitT <= enterT) { reason |= REASON_DEPTH; return RAY_UNKNOWN; }
        vec2 rayInv = mix(vec2(invZ.x), vec2(invZ.y), vec2(enterT, exitT));
        steps++;
        if (level < 0) {
            cell = clamp(cell, vec2(0.0), OutSize - 1.0);
            int result = sampleLightRay(cell, rayInv, origin + travel * ((enterT + exitT) * 0.5), plane, ignorePlane, invDepthB);
            if (result == RAY_HIT) { reason = 0; return RAY_HIT; }
            if (result == RAY_UNKNOWN) reason |= REASON_DEPTH;
        } else {
            ivec2 index = clamp(ivec2(cell), ivec2(0), textureSize(DepthHierarchySampler, level) - 1);
            vec2 range = texelFetch(DepthHierarchySampler, index, level).rg;
            bool overlap = false;
            if (range.y > 0.0) {
                float sceneLo = abs(range.x), sceneHi = range.y;
                float backScale = 1.0 + OCCL_BIAS_REL_Z + (OCCL_BIAS_M + occlusionThickness(sceneLo)) * sceneLo;
                float frontScale = 1.0 + OCCL_BIAS_REL_Z + OCCL_BIAS_M * sceneHi;
                overlap = max(rayInv.x, rayInv.y) * backScale >= sceneLo
                        && min(rayInv.x, rayInv.y) * frontScale <= sceneHi;
            }
            if (overlap) { level--; continue; }
            if (range.x <= 0.0) reason |= REASON_DEPTH;
        }
        enterT = exitT;
        if (enterT >= 1.0) return reason != 0 ? RAY_UNKNOWN : RAY_CLEAR;
        level = pixelReference ? -1 : min(level + 1, top);
    }
    reason |= REASON_BUDGET;
    return RAY_UNKNOWN;
}

// 范围以真实光心为中心；它只限制S3的受光点，不截短范围内的P→C路径。
float surfaceShadowWeight(float distancePC) {
    if (ShadowRange <= 0.0) return 0.0;
    return 1.0 - smoothstep(ShadowRange * 0.75, ShadowRange, distancePC);
}

int traceLightRay(vec3 P, vec3 C, float distancePC, vec3 receiverNormal, bool hasReceiverPlane,
                  int availableSteps, out int steps, out int reason) {
    steps = 0;
    reason = 0;
    if (ShadowRange <= 0.0 || distancePC >= ShadowRange) return RAY_CLEAR;
    int rayLimit = CaptureRayBudget > 0.0 ? int(CaptureRayBudget) : OCCL_MAX_STEPS;
    int budget = min(rayLimit, availableSteps);
    if (budget <= 0) { reason = REASON_BUDGET; return RAY_UNKNOWN; }
    float endExclusion = CaptureEndExclusion > 0.0 ? CaptureEndExclusion : OCCL_END_M;
    if (distancePC <= OCCL_START_M + endExclusion) return RAY_CLEAR;

    vec3 direction = (C - P) / distancePC;
    // 灯在受光面正侧时，同一平面本身不能挡住 P→C。用接收平面排除掠射像素的常深度近似自交。
    // 将平面方程乘 sceneInv，直接在倒数深度中比较，不为每格重建米制位置。
    bool ignoreReceiverPlane = hasReceiverPlane && dot(receiverNormal, C - P) >= 0.0;
    float planeD = dot(receiverNormal, P);
    vec2 planeSlope = receiverNormal.xy / FocalPx;
    float planeOrigin = -dot(planeSlope, OutSize * 0.5) - receiverNormal.z;
    vec3 start = P + direction * OCCL_START_M;
    vec3 end   = C - direction * endExclusion;
    float za = -start.z;
    float zb = -end.z;
    if (za < NEAR_Z && zb < NEAR_Z) { reason = REASON_CLIPPED; return RAY_UNKNOWN; }
    if (za < NEAR_Z) {
        start = mix(start, end, (NEAR_Z - za) / (zb - za));
        za = NEAR_Z;
        reason |= REASON_CLIPPED;
    }
    if (zb < NEAR_Z) {
        end = mix(start, end, (NEAR_Z - za) / (zb - za));
        zb = NEAR_Z;
        reason |= REASON_CLIPPED;
    }

    vec2 a = OutSize * 0.5 + FocalPx * start.xy / za;
    vec2 b = OutSize * 0.5 + FocalPx * end.xy / zb;
    vec2 delta = b - a;
    float t0 = 0.0;
    float t1 = 1.0;
    // 上界留在最后一个像素内部，避免 floor(W) / floor(H) 越过纹理。
    if (!clipRayAxis(a.x, delta.x, OutSize.x - 0.001, t0, t1)) { reason |= REASON_CLIPPED; return RAY_UNKNOWN; }
    if (!clipRayAxis(a.y, delta.y, OutSize.y - 0.001, t0, t1)) { reason |= REASON_CLIPPED; return RAY_UNKNOWN; }
    if (t1 < t0) { reason |= REASON_CLIPPED; return RAY_UNKNOWN; }
    if (t0 > 0.0 || t1 < 1.0) reason |= REASON_CLIPPED;

    // 直接插值 1/z，避免在 raw≈1 时再相减丢失有效位；两种深度方向共用同一条路径。
    float invA = 1.0 / za;
    float invB = 1.0 / zb;
    vec2 invZ = vec2(mix(invA, invB, t0), mix(invA, invB, t1));
    vec2 origin = a + delta * t0;
    vec2 travel = delta * (t1 - t0);
    vec4 plane = vec4(planeSlope, planeOrigin, planeD);
    float invDepthB = 1.0 / DepthB;
    if (CaptureSamples > 0.0) {
        int samples = int(CaptureSamples);
        int count = min(samples, budget);
        for (int k = 0; k < count; k++) {
            // 中点分层：64 个点分布在完整可见线段上，点与点之间没有已检测的证明。
            float t = (float(k) + 0.5) / float(samples);
            vec2 cell = clamp(floor(origin + travel * t), vec2(0.0), OutSize - 1.0);
            float rayInv = mix(invZ.x, invZ.y, t);
            int sampleResult = sampleLightRay(cell, vec2(rayInv), origin + travel * t, plane, ignoreReceiverPlane, invDepthB);
            steps++;
            if (sampleResult == RAY_HIT) { reason = 0; return RAY_HIT; }
            if (sampleResult == RAY_UNKNOWN) reason |= REASON_DEPTH;
        }
        if (count < samples) reason |= REASON_BUDGET;
        reason |= REASON_SAMPLED;
        // 点采样之间的空段无法确认无遮挡，未命中仍按未知放光。
        return RAY_UNKNOWN;
    }
    if (CaptureTraversal < 0.5 || CaptureTraversal > 1.5) {
        return traceHierarchy(origin, travel, invZ, plane, ignoreReceiverPlane, invDepthB, budget, steps, reason);
    }
    // 按主轴的像素列/行推进（透视正确 DDA）。每列覆盖完整的射线参数区间，
    // 深度取该列中点所在的像素；次轴随射线插值，不把长线段压成固定比例的孤立采样。
    bool vertical = abs(travel.y) > abs(travel.x);
    float majorOrigin = vertical ? origin.y : origin.x;
    float majorTravel = vertical ? travel.y : travel.x;
    float majorCell = floor(majorOrigin);
    if (majorTravel < 0.0 && majorOrigin == majorCell) majorCell -= 1.0;
    float nextT = 1.0;
    float deltaT = 1.0;
    if (abs(majorTravel) > 1.0e-6) {
        nextT = (majorCell + (majorTravel > 0.0 ? 1.0 : 0.0) - majorOrigin) / majorTravel;
        deltaT = 1.0 / abs(majorTravel);
    }
    float enterT = 0.0;
    float rayStartInv = invZ.x;
    for (int k = 0; k < budget; k++) {
        float exitT = min(1.0, nextT);
        vec2 cell = floor(origin + travel * ((enterT + exitT) * 0.5));
        // 裁剪已保证在视口内；钳制浮点边界误差，避免最后一列的 texelFetch 越界。
        cell = clamp(cell, vec2(0.0), OutSize - 1.0);
        float rayEndInv = mix(invZ.x, invZ.y, exitT);
        int sampleResult = sampleLightRay(cell, vec2(rayStartInv, rayEndInv), origin + travel * ((enterT + exitT) * 0.5), plane, ignoreReceiverPlane, invDepthB);
        steps++;
        if (sampleResult == RAY_HIT) { reason = 0; return RAY_HIT; }
        if (sampleResult == RAY_UNKNOWN) reason |= REASON_DEPTH;
        if (exitT >= 1.0) return reason != 0 ? RAY_UNKNOWN : RAY_CLEAR;
        nextT += deltaT;
        enterT = exitT;
        rayStartInv = rayEndInv;
    }
    reason |= REASON_BUDGET;
    return RAY_UNKNOWN;
}

// 柔和遮挡：每个采样响应至多贡献1/32。越界样本按无遮挡计，分母固定，避免视口边缘跳变。
// 0.25m是单边响应的过渡宽度；它不表示物体背面的有限厚度。
float averagedLightVisibility(vec3 P, vec3 C) {
    if (-C.z <= NEAR_Z) return 1.0;
    vec2 a = OutSize * 0.5 + FocalPx * P.xy / (-P.z);
    vec2 b = OutSize * 0.5 + FocalPx * C.xy / (-C.z);
    float rawA = DepthB / (-P.z) - DepthA;
    float rawB = DepthB / (-C.z) - DepthA;
    float depthSign = DepthB < 0.0 ? -1.0 : 1.0;
    float occ = 0.0;
    for (int i = 0; i < 32; i++) {
        float t = mix(0.06, 0.88, (float(i) + 0.5) / 32.0);
        vec2 uv = mix(a, b, t) / OutSize;
        if (any(lessThan(uv, vec2(0.0))) || any(greaterThanEqual(uv, vec2(1.0)))) continue;
        float ray = mix(rawA, rawB, t);
        float rel = abs(ray + DepthA);
        float perM = max(rel * rel / abs(DepthB), 1e-12);
        float diff = (texture(MCDepthSampler, uv).r - ray) * depthSign - (0.08 * perM + 0.002 * rel);
        occ += clamp(diff / (0.25 * perM) + 0.5, 0.0, 1.0);
    }
    return clamp(1.0 - occ / 32.0, 0.0, 1.0);
}

// 诊断基线：固定7点t=k/8，单边软深度响应平均；与32点共享米制偏置和过渡宽度。
float legacyLightVisibility(vec3 P, vec3 C) {
    float zc = -C.z;
    if (zc <= NEAR_Z) return 1.0;
    vec2 a = OutSize * 0.5 + FocalPx * P.xy / (-P.z);
    vec2 b = OutSize * 0.5 + FocalPx * C.xy / zc;
    float rawA = DepthB / (-P.z) - DepthA;
    float rawB = DepthB / zc - DepthA;
    float depthSign = DepthB < 0.0 ? -1.0 : 1.0;
    float occ = 0.0;
    for (int k = 1; k <= 7; k++) {
        float t = float(k) / 8.0;
        vec2 uv = mix(a, b, t) / OutSize;
        if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) continue;
        float ray = mix(rawA, rawB, t);
        float rel = abs(ray + DepthA);
        float perM = rel * rel / abs(DepthB);
        float diff = (texture(MCDepthSampler, uv).r - ray) * depthSign - (0.08 * perM + 0.002 * rel);
        occ += clamp(diff / (0.25 * perM) + 0.5, 0.0, 1.0);
    }
    return 1.0 - occ / 7.0;
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

    // 四邻域法线供S4使用；诊断硬命中算法还用它排除接收平面。
    float nw   = clamp(NormalWeight, 0.0, 1.0);
    vec3  N    = vec3(0.0, 0.0, 1.0);
    bool  hasN = false;
    if (hasSurface && (nw > 0.0 || (CaptureTraversal >= 0.0 && CaptureLegacy <= 0.0))) hasN = viewNormal(texCoord, zfwd, N);

    // 逐通道响应：e 是表面辐照度之和，glow 是空气散射、glare 是星芒，后两者都是线性、都不进合成曲线
    vec3 e     = vec3(0.0);
    vec3 glow  = vec3(0.0);
    vec3 glare = vec3(0.0);
    int remainingRaySteps = CapturePixelBudget > 0.0 ? int(CapturePixelBudget) : OCCL_PIXEL_STEPS;
    int captureReasons = 0;
    bool captureHit = false;
    bool captureEvaluated = false;
    float captureShadow = 0.0;

    for (int i = 0; i < int(LightCount); i++) {
        if (CaptureOnlyLight > 0.0 && i != int(CaptureOnlyLight) - 1) continue;
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

        // 星芒没有米制范围，可见性只能在像素空间判定：光心投影必须落在画面内，且本像素落在以它为中心、
        // 边长 2·thPx 的方框内。**中心出画就整条跳过**——镜头成像要求光源在取景框里，出画之后只剩
        // 散射（光晕）而没有拉丝；下面的光源级遮挡判定因此总在画面内采样，不需要越界兜底。
        // 光心在相机背后时没有可用的投影，同样整条跳过。
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
            glareIn  = thPx > 0.5
                    && lightPx.x >= 0.0 && lightPx.x < OutSize.x
                    && lightPx.y >= 0.0 && lightPx.y < OutSize.y
                    && abs(dp.x) <= thPx && abs(dp.y) <= thPx;
            glareAmp = GLARE_GAIN * sat * gg;
        }
        if (!haloIn && !glareIn) continue;

        // 星芒的光源级可见性（S2）：在光源自身的屏幕位置采一次深度，显著更近即判定光源在几何体之后。
        // 容差按来源折算（绝对分量）+ 前向深度（相对分量）取较大者：容差太小则光源
        // 自身的模型会把光源剔掉、星芒凭空消失，太大则近处遮挡物漏光。
        // **它只门禁星芒**：表面项由S3的32点平均逐像素决定，光晕由t_hit逐像素截断。
        // glareIn 已经保证光心投影在画面内，这里的采样恒落在 [0,1]²，不需要越界兜底。
        bool sourceVisible = true;
        if (glareIn && LightOcclusion[i] > 0.5) {
            float tol   = max(LightTol[i], zc * OcclusionTolRel);
            float zHere = DepthB / (texture(MCDepthSampler, lightPx / OutSize).r + DepthA);
            sourceVisible = zHere >= zc - tol;
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

                // 朝向响应（S4）：nw=1时按完整Lambert折减，背光面归零；nw<1时使用wrap响应。
                // 权重 nw 把结果在"不乘法线项"与"完整 Lambert"之间插值：(1 − nw) + nw·cos，再钳到 0。
                // 插值后钳零：掠射面响应为1−nw，部分背光面在nw<1时也有响应；几何遮挡由S3判断。
                // 法线不可信时 orient 保持 1，只损失朝向信息。
                float orient = 1.0;
                if (nw > 0.0 && hasN && d > 1.0e-4) {
                    orient = max(mix(1.0, dot(N, (C - P) / d), nw), 0.0);
                }

                // S3默认平均软深度响应；硬命中和7点平均是独立的诊断对照。
                float visible = 1.0;
                float shadowWeight = surfaceShadowWeight(d);
                if (orient > 0.0 && intensity > 0.0 && shadowWeight > 0.0) {
                    int steps = 0;
                    int reason = 0;
                    int result = RAY_CLEAR;
                    if (CaptureTraversal >= 0.0 && CaptureLegacy <= 0.0 && CaptureOcclusionOff < 0.5) {
                        result = traceLightRay(P, C, d, N, hasN, remainingRaySteps, steps, reason);
                    }
                    remainingRaySteps -= steps;
                    if (CaptureOutput > 0.0) {
                        captureReasons |= reason;
                        captureHit = captureHit || result == RAY_HIT;
                        captureEvaluated = true;
                    }
                    if (OCCL_DEBUG == 1) {
                        vec3 diagnostic = result == RAY_HIT ? vec3(1.0, 0.0, 0.0)
                                : (result == RAY_CLEAR ? vec3(0.0, 1.0, 0.0) : vec3(0.0, 0.0, 1.0));
                        fragColor = vec4(diagnostic, 1.0);
                        return;
                    }
                    if (OCCL_DEBUG == 2) {
                        fragColor = vec4(vec3(float(steps) / float(max(OCCL_MAX_STEPS, 1))), 1.0);
                        return;
                    }
                    visible = result == RAY_HIT ? 1.0 - shadowWeight : 1.0;
                    if (CaptureOcclusionOff < 0.5) {
                        if (CaptureLegacy > 0.0) visible = mix(1.0, legacyLightVisibility(P, C), shadowWeight);
                        else if (CaptureTraversal < 0.0) visible = mix(1.0, averagedLightVisibility(P, C), shadowWeight);
                    }
                    captureShadow = max(captureShadow, 1.0 - visible);
                }

                // 逐光源钳制：曲线本身有界，但内容里的强度没有上界，先兜住异常输入
                e += min(radiance * (intensity * win * orient * visible / dEff2), vec3(E_MAX));
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

        // 星芒项：屏幕空间的固定角度**单轴**拉丝（anamorphic）。不按本像素深度截断——星芒是镜头内的
        // 成像，不是空气里的散射，压在墙与车体上是预期行为；但它要求**光源自身在画面内且未被挡住**。
        // 只有一轴：圆孔只给艾里斑，成对的 90° 十字是副镜支架的图案，一轴拉丝才是柱面镜/传感器溢出的形状。
        if (sourceVisible && glareIn) {
            float u  = abs(dp.x) / thPx;
            float a  = max(1.0 - u, 0.0);
            float ax = a * sqrt(a);                                // (1−u)^1.5：沿臂缓慢衰减、臂尖平滑归零
            float sigma  = max(1.2, OutSize.y * GLARE_SIGMA_REL);  // 亮芯宽度，按屏高取
            float sCore  = sigma * (1.0 - GLARE_TAPER * u);        // 向臂尖收窄：根粗尖细，不是等宽长条
            float sSkirt = sCore * GLARE_SKIRT_REL;                // 裙边按同一比例收窄
            float y2     = dp.y * dp.y;
            float core   = exp(-y2 / (sCore * sCore));
            float skirt  = exp(-y2 / (sSkirt * sSkirt));
            // 芯与裙的权重和为 1：根部峰值恰为臂根亮度，裙边只负责把剖面撑宽、撑软
            float armX   = ax * ((1.0 - GLARE_SKIRT_GAIN) * core + GLARE_SKIRT_GAIN * skirt);
            glare += min(radiance * (glareAmp * armX), vec3(GLOW_MAX));
        }
    }

    if (CaptureOutput > 0.0) {
        // 平均算法显示参与光源的最大加权遮挡量：黑=无遮挡，白=完全抑制表面光。
        if (CaptureTraversal < 0.0 || CaptureLegacy > 0.0) {
            fragColor = vec4(vec3(captureShadow), 1.0);
            return;
        }
        vec3 diagnostic = vec3(0.08);
        if (captureEvaluated) {
            diagnostic = (captureReasons & REASON_BUDGET) != 0 ? vec3(1.0, 0.5, 0.0)
                    : ((captureReasons & REASON_CLIPPED) != 0 ? vec3(1.0, 0.0, 1.0)
                    : ((captureReasons & REASON_DEPTH) != 0 ? vec3(0.0, 0.0, 1.0)
                    : ((captureReasons & REASON_SAMPLED) != 0 ? vec3(0.0, 1.0, 1.0)
                    : (captureHit ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0)))));
        }
        fragColor = vec4(diagnostic, 1.0);
        return;
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
