package io.github.sweetzonzi.machine_max.common.mech.projectile;

/**
 * 投射物物理模型类型枚举。
 * <p>
 * JSON 读写使用字符串（"point" / "rigid"），
 * 通过 {@link #fromString(String)} 转换为枚举，
 * 通过 {@link #getSerializedName()} 序列化为字符串。
 */
public enum ProjectileTypeEnum {

    /** 质点投射物：无 JME 物理刚体，运动由 SoA 批量积分驱动，JME rayTest 碰撞检测 */
    POINT("point"),

    /** 刚体投射物：有 JME PhysicsRigidBody，受 Bullet 管理，球体碰撞体积 */
    RIGID("rigid");

    private final String serializedName;

    ProjectileTypeEnum(String serializedName) {
        this.serializedName = serializedName;
    }

    /**
     * @return JSON 中使用的字符串标识
     */
    public String getSerializedName() {
        return serializedName;
    }

    /**
     * 从 JSON 字符串转换为枚举。
     *
     * @param name 序列化名（"point" / "rigid"）
     * @return 对应的枚举值
     * @throws IllegalArgumentException 不匹配时抛出
     */
    public static ProjectileTypeEnum fromString(String name) {
        return switch (name) {
            case "point" -> POINT;
            case "rigid" -> RIGID;
            default -> throw new IllegalArgumentException("未知投射物类型: " + name);
        };
    }

    /**
     * @return 是否质点类型
     */
    public boolean isPoint() {
        return this == POINT;
    }

    /**
     * @return 是否刚体类型
     */
    public boolean isRigid() {
        return this == RIGID;
    }
}
