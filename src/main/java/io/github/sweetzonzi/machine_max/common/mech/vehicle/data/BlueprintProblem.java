package io.github.sweetzonzi.machine_max.common.mech.vehicle.data;

/**
 * 蓝图校验问题条目。
 *
 * <p>由 {@link VehicleData#validate(net.minecraft.world.level.Level)} 产出，空列表表示校验通过。
 * 该方法本身不抛异常，调用方据列表决定拒绝与否。</p>
 *
 * @param kind   问题类别
 * @param detail 人类可读的细节（缺失的 id、非法值等）
 */
public record BlueprintProblem(Kind kind, String detail) {

    /** 问题类别 */
    public enum Kind {
        /** 引用的零件类型不在注册表中（多为内容包缺失） */
        MISSING_PART_TYPE,
        /** 变体不属于该零件类型 */
        MISSING_VARIANT,
        /** 结构、配方或数值非法 */
        INVALID_VALUE
    }

    public static BlueprintProblem missingPartType(String detail) {
        return new BlueprintProblem(Kind.MISSING_PART_TYPE, detail);
    }

    public static BlueprintProblem missingVariant(String detail) {
        return new BlueprintProblem(Kind.MISSING_VARIANT, detail);
    }

    public static BlueprintProblem invalidValue(String detail) {
        return new BlueprintProblem(Kind.INVALID_VALUE, detail);
    }
}
