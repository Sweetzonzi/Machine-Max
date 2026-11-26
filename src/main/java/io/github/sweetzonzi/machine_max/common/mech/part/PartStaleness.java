package io.github.sweetzonzi.machine_max.common.mech.part;

/** 部件连续做旧状态及其渲染阶段映射。 */
public final class PartStaleness {
    public static final int STAGE_COUNT = 16;

    private PartStaleness() {
    }

    /** 将有限进度限制在 [0, 1]。 */
    public static float clamp(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("陈旧度必须是有限数值");
        return Math.clamp(value, 0f, 1f);
    }

    /** 校验进度位于 [0, 1]，不符合时抛出异常。 */
    public static float requireValid(float value) {
        if (!Float.isFinite(value) || value < 0f || value > 1f) {
            throw new IllegalArgumentException("陈旧度必须位于 [0, 1]");
        }
        return value;
    }

    /** 按配置的完整做旧时长推进一个服务端 tick，达到时长后精确返回 1。 */
    public static float advance(float value, int fullWeatheringTicks) {
        value = requireValid(value);
        if (fullWeatheringTicks <= 0) {
            throw new IllegalArgumentException("完全做旧时长必须大于 0 tick");
        }
        if (value >= 1f) return 1f;
        float step = 1f / fullWeatheringTicks;
        float next = value + step;
        return next >= 1f - 0.5f * step ? 1f : Math.min(next, 1f);
    }

    /** 按本次修复量占所属 Part 最大耐久的比例回退陈旧度；参数无效时抛出异常，结果不会低于 0。 */
    public static float repair(float value, float repairedDurability, float partMaxDurability) {
        value = requireValid(value);
        if (!Float.isFinite(repairedDurability) || repairedDurability < 0f) {
            throw new IllegalArgumentException("修复耐久量必须是非负有限数值");
        }
        if (!Float.isFinite(partMaxDurability) || partMaxDurability <= 0f) {
            throw new IllegalArgumentException("Part 最大耐久必须是正的有限数值");
        }
        return Math.max(0f, value - repairedDurability / partMaxDurability);
    }

    /** 将连续进度映射为 0 到 16 的最近阶段。 */
    public static int stage(float value) {
        return Math.round(clamp(value) * STAGE_COUNT);
    }
}
