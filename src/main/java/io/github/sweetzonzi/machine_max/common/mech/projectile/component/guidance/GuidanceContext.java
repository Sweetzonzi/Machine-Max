package io.github.sweetzonzi.machine_max.common.mech.projectile.component.guidance;

/**
 * 每步制导解算的展平上下文。
 * <p>
 * 只携带原始类型（无 Level / ObjectManager / 实体引用），使制导律保持纯粹且
 * 可在物理线程安全使用。由 {@code ProjectileManager} 在物理步积分前构造，
 * 每个受制导的投射物每物理步一个实例——数量是个位数量级，分配可忽略。
 * <p>
 * 单位一律采用 SI：位置/目标为米，速度 m/s，质量 kg，半径 m，
 * 空气密度为归一化值（海平面 y=62 处为 1.0）。
 *
 * @param posX       当前位置 X
 * @param posY       当前位置 Y
 * @param posZ       当前位置 Z
 * @param velX       当前速度 X（积分前的值）
 * @param velY       当前速度 Y
 * @param velZ       当前速度 Z
 * @param targetX    目标点 X（世界坐标）
 * @param targetY    目标点 Y
 * @param targetZ    目标点 Z
 * @param mass       质量（kg）
 * @param radius     弹体半径（m），用于解析缺省的参考面积
 * @param airDensity 当前位置的归一化空气密度 ρ(h)
 */
public record GuidanceContext(
    float posX, float posY, float posZ,
    float velX, float velY, float velZ,
    float targetX, float targetY, float targetZ,
    float mass, float radius, float airDensity
) {}
