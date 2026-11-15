#version 150

// 每格记录相机深度倒数的最小/最大值；x<0表示格内混有缺深度，(0,0)表示全无深度。
// SourceLevel=-1时将2×2原始像素归并；其余层将上一层2×2格归并。
uniform sampler2D InputSampler;
uniform int SourceLevel;
uniform float DepthA;
uniform float DepthB;
out vec2 depthRange;

void main() {
    ivec2 base = ivec2(gl_FragCoord.xy) * 2;
    ivec2 size = textureSize(InputSampler, max(SourceLevel, 0));
    float lo = 1.0e30;
    float hi = 0.0;
    bool missing = false;
    for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) {
        ivec2 p = base + ivec2(x, y);
        if (any(greaterThanEqual(p, size))) {
            if (SourceLevel < 0) missing = true;
            continue;
        }
        vec2 range;
        if (SourceLevel < 0) {
            float raw = texelFetch(InputSampler, p, 0).r;
            float invZ = (raw + DepthA) / DepthB;
            bool valid = raw > 0.0 && raw < 1.0 && invZ > 0.0 && invZ < 20.0;
            range = valid ? vec2(invZ) : vec2(0.0);
        } else {
            range = texelFetch(InputSampler, p, SourceLevel).rg;
        }
        missing = missing || range.x <= 0.0;
        if (range.y > 0.0) {
            lo = min(lo, abs(range.x));
            hi = max(hi, range.y);
        }
    }
    depthRange = hi > 0.0 ? vec2(missing ? -lo : lo, hi) : vec2(0.0);
}
