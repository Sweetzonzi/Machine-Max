package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

/**
 * 模块静态属性基类——定义所有模块共有的字段。
 * <p>
 * 各模块类型继承此类，添加自己的专有字段。
 * 不再使用 Codec dispatch，改用各子系统静态属性中的显式 {@code optionalFieldOf}。
 * </p>
 *
 * @param durabilityWeight  耐久度权重。模块的 maxDurability = basicDurability × (weight / totalWeight)
 * @param critical          是否关键模块。归零 → 子系统整体摧毁，触发 onDestroyed。
 *                          由各子类硬编码，不接受 JSON 配置。
 */
public abstract class AbstractModuleAttr {
    private final float durabilityWeight;
    private final boolean critical;

    protected AbstractModuleAttr(float durabilityWeight, boolean critical) {
        this.durabilityWeight = durabilityWeight;
        this.critical = critical;
    }

    public float durabilityWeight() { return durabilityWeight; }
    public boolean critical() { return critical; }
}
