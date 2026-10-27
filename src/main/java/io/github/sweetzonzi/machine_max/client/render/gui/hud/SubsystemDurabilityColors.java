package io.github.sweetzonzi.machine_max.client.render.gui.hud;

/**
 * 子系统耐久色带。
 *
 * <p>内构 HUD 与座位图都要给“带冲突箱的子系统”染色，两处必须同色，否则同一台车在两个界面里
 * 会给出不同的耐久读数。色带放在这里，两边都只调 {@link #colorFor}。</p>
 *
 * <p>返回值带 alpha：内构 HUD 用半透明叠加在车体上，座位图会把 alpha 换成自己的值再画。</p>
 */
public final class SubsystemDurabilityColors {

    /** 满耐久 */
    private static final int FULL = 0x4064FF64;
    /** 80% 以上 */
    private static final int ABOVE_80 = 0x40FFFF40;
    /** 60% 以上 */
    private static final int ABOVE_60 = 0x40FFFF00;
    /** 40% 以上 */
    private static final int ABOVE_40 = 0x40FF8000;
    /** 20% 以上 */
    private static final int ABOVE_20 = 0x40FF4000;
    /** 仍有耐久但低于 20% */
    private static final int ABOVE_0 = 0x40FF0000;
    /** 已毁坏 */
    private static final int DESTROYED = 0x80323232;

    private SubsystemDurabilityColors() {
    }

    /**
     * 按耐久比例取色。
     *
     * @param durability    当前耐久
     * @param maxDurability 上限；非正数按已毁坏处理
     */
    public static int colorFor(float durability, float maxDurability) {
        if (maxDurability <= 0f) return DESTROYED;
        float ratio = Math.clamp(durability / maxDurability, 0f, 1f);
        if (ratio >= 1f) return FULL;
        if (ratio >= 0.8f) return ABOVE_80;
        if (ratio >= 0.6f) return ABOVE_60;
        if (ratio >= 0.4f) return ABOVE_40;
        if (ratio >= 0.2f) return ABOVE_20;
        if (ratio > 0f) return ABOVE_0;
        return DESTROYED;
    }

    /** 换掉 alpha、保留 RGB：座位图需要比内构 HUD 更实一些的染色 */
    public static int withAlpha(int argb, int alpha) {
        return ((alpha & 0xFF) << 24) | (argb & 0x00FFFFFF);
    }
}
