package io.github.sweetzonzi.machine_max.client.render.gui.hud;

/**
 * 子系统耐久色带：健康时为低干扰灰白，受损后逐渐转黄、橙、红，毁坏时为深红。
 *
 * <p>内构 HUD、座位图与 O 键内构查看器共用 RGB 色带；各渲染面只按需调整 alpha，避免同一耐久值
 * 在不同视图里表达成不同状态。</p>
 *
 * <p>返回值带 alpha：内构 HUD 默认半透明，座位图与 O 键查看器按场景替换 alpha。</p>
 */
public final class SubsystemDurabilityColors {

    private static final float[] DURABILITY_STOPS = {0f, 0.25f, 0.5f, 0.75f, 1f};
    private static final int[] RGB_STOPS = {
            0x681E2B, // 深红：已毁坏
            0xCC493F, // 红
            0xE58A36, // 橙
            0xD7C64D, // 黄
            0xE2E6EA  // 灰白：满耐久
    };
    private static final int DEFAULT_ALPHA = 0x40;
    private static final int DESTROYED_ALPHA = 0x80;

    private SubsystemDurabilityColors() {
    }

    /**
     * 按耐久比例取色。
     *
     * @param durability    当前耐久；非有限值按已毁坏处理
     * @param maxDurability 上限；非正数或非有限值按已毁坏处理
     */
    public static int colorFor(float durability, float maxDurability) {
        if (!Float.isFinite(durability) || !Float.isFinite(maxDurability) || maxDurability <= 0f) {
            return withAlpha(RGB_STOPS[0], DESTROYED_ALPHA);
        }
        float ratio = Math.clamp(durability / maxDurability, 0f, 1f);
        return withAlpha(rgbForRatio(ratio), ratio == 0f ? DESTROYED_ALPHA : DEFAULT_ALPHA);
    }

    private static int rgbForRatio(float ratio) {
        for (int i = 1; i < DURABILITY_STOPS.length; i++) {
            if (ratio <= DURABILITY_STOPS[i]) {
                float t = (ratio - DURABILITY_STOPS[i - 1])
                        / (DURABILITY_STOPS[i] - DURABILITY_STOPS[i - 1]);
                return interpolateRgb(RGB_STOPS[i - 1], RGB_STOPS[i], t);
            }
        }
        return RGB_STOPS[RGB_STOPS.length - 1];
    }

    private static int interpolateRgb(int from, int to, float t) {
        int red = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int green = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int blue = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (red << 16) | (green << 8) | blue;
    }

    /** 换掉 alpha、保留 RGB：适配座位图与 O 键查看器的渲染透明度 */
    public static int withAlpha(int argb, int alpha) {
        return ((alpha & 0xFF) << 24) | (argb & 0x00FFFFFF);
    }
}
