#version 150

// Machine-Max: CRT 显像管后处理 - 片段着色器
// 依据物理原理复刻 CRT 显示特性：
//   1. 高斯光束光斑：像素无硬边、相邻混色，高亮区域电子束发散导致光斑变大（blooming）
//   2. 扫描线：行间消隐期产生暗缝
//   3. 荫罩荧光点阵：RGB 三色荧光点相位错开，叠加规则网点纹理
//   4. 荧光粉余晖辉光：高亮像素向四周洇开，色温偏暖
//   5. 屏面渐晕与三枪汇聚误差：边缘压暗 + RGB 彩边
//   6. CRT 固有伽马与 60Hz 刷新率的轻微亮度脉动

uniform sampler2D DiffuseSampler;
uniform float Time;          // 渲染时间（秒），PostChain 自动注入
uniform vec2 OutSize;        // 目标尺寸（像素），PostChain 自动注入

// 物理真实向参数，由 Java 侧 CrtMonitorEffect 每帧写入
uniform float Intensity;        // 效果总强度
uniform float ScanlineAmount;   // 扫描线暗缝强度
uniform float MaskAmount;       // 荫罩荧光点阵强度
uniform float BloomAmount;      // 荧光粉辉光强度

in vec2 texCoord;

out vec4 fragColor;

// 亮度权重（NTSC 标准：人眼对绿色最敏感、蓝色最不敏感）
float luminance(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

void main() {
    vec2 px = texCoord * OutSize;

    // 1) 高斯光束光斑：横向 3 tap 高斯卷积，sigma 随总强度增大（模拟高亮时电子束发散 = blooming）
    float sigma = 0.6 + 0.4 * Intensity;
    float w1 = exp(-1.0 / (2.0 * sigma * sigma));
    float wsum = 1.0 + 2.0 * w1;
    vec3 c = texture(DiffuseSampler, texCoord).rgb;
    c += texture(DiffuseSampler, texCoord + vec2(1.0, 0.0) / OutSize).rgb * w1;
    c += texture(DiffuseSampler, texCoord - vec2(1.0, 0.0) / OutSize).rgb * w1;
    c /= wsum;

    // 2) 三枪汇聚误差：RGB 三个电子枪轻微未对齐，R/B 从原始纹理错位采样。
    //    必须放在扫描线/荫罩/辉光/渐晕之前，让 R/B 与 G 一同经历后续所有处理——
    //    否则 R/B 绕过扫描线暗缝与荫罩调制，会与压暗的 G 叠加出整体偏紫红的通道偏差。
    //    量级收小并仅从屏面中部向边缘渐入，偏移量加上限防止四角过度错位。
    vec2 centered = texCoord * 2.0 - 1.0;
    centered.x *= OutSize.x / max(OutSize.y, 1.0);
    float convergenceIn = smoothstep(0.35, 0.9, length(centered));
    vec2 mis = centered * centered * sign(centered) * 0.0008 * convergenceIn * Intensity;
    mis = clamp(mis, vec2(-0.0015), vec2(0.0015));
    c.r = texture(DiffuseSampler, texCoord + mis).r;
    c.b = texture(DiffuseSampler, texCoord - mis).b;

    // 3) 扫描线：CRT 逐行扫描，行间回扫消隐形成真正的暗缝（黑条）。
    //    按模拟扫描行数决定行高与黑缝宽度，暗缝处亮度趋近 0（选择性变黑），而非半透明压暗。
    float scanRows = 300.0;                     // 模拟扫描行数（对应 CRT 低分辨率观感）
    float lineH = OutSize.y / scanRows;         // 每行像素高度
    float scanPos = fract(px.y / lineH);        // 行内位置 0..1
    float scanline = pow(sin(scanPos * 3.14159265), 6.0);  // 行中心亮、行边界黑
    c *= mix(1.0, scanline, ScanlineAmount);

    // 4) 荫罩荧光点阵：RGB 三通道错开相位形成网点。加粗点距（每 4 像素一点）并
    //    降低对比度，避免 RGB 分离过大导致画面整体偏紫/偏绿
    vec2 m = px * 0.25;
    vec3 mask = vec3(
        0.92 + 0.08 * sin(m.x * 3.14159265) * sin(m.y * 3.14159265),
        0.92 + 0.08 * sin((m.x + 0.5) * 3.14159265) * sin(m.y * 3.14159265),
        0.92 + 0.08 * sin(m.x * 3.14159265) * sin((m.y + 0.5) * 3.14159265));
    c *= mix(vec3(1.0), mask, MaskAmount);

    // 5) 荧光粉余晖辉光：对 3x3 邻域求平均亮度并二次方，高亮区域向四周洇开，色温偏暖
    float glow = 0.0;
    for (int dy = -2; dy <= 2; dy++) {
        for (int dx = -2; dx <= 2; dx++) {
            glow += luminance(texture(DiffuseSampler, texCoord + vec2(float(dx), float(dy)) / OutSize).rgb);
        }
    }
    glow /= 25.0;
    c += glow * glow * BloomAmount * vec3(1.0, 0.95, 0.85);

    // 6) 屏面渐晕：玻璃面板边缘压暗
    float vig = smoothstep(1.4, 0.6, length(centered));
    c *= mix(1.0, vig, 0.4 * Intensity);

    // 7) CRT 固有伽马（约 2.2，暗部更沉）与 60Hz 刷新率的轻微亮度脉动
    c = pow(c, vec3(1.0 / 1.15));
    c *= 1.0 - 0.02 * sin(Time * 6.28318 * 60.0);

    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
