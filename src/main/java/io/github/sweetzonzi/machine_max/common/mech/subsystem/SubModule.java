package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.AbstractModuleAttr;
import net.minecraft.nbt.CompoundTag;

/**
 * 子模块——子系统内部可独立受损的功能单元。
 * <p>
 * 纯状态容器，管理运行时耐久度。功能衰减逻辑由各子系统根据
 * {@link #getDurabilityRatio()} + 对应 ModuleAttr 的专有字段自行计算。
 * <p>
 * 模块的 maxDurability 由 ModularSubsystem 在初始化时根据 durabilityWeight 计算：
 * {@code maxDurability = basicDurability × (weight / totalWeight)}。
 */
public class SubModule {
    private static final String TAG_DURABILITY = "durability";
    private static final String TAG_DESTROYED = "destroyed";

    private final AbstractModuleAttr attr;
    private final float maxDurability;   // 由 ModularSubsystem 初始化时计算
    private float durability;
    private boolean destroyed;

    public SubModule(AbstractModuleAttr attr, float maxDurability) {
        this.attr = attr;
        this.maxDurability = maxDurability;
        this.durability = maxDurability;
    }

    /**
     * 当前耐久度比例 [0, 1]。调用方应配合 {@link #isDestroyed()} 区分摧毁与战损。
     */
    public float getDurabilityRatio() {
        return Math.clamp(durability / maxDurability, 0f, 1f);
    }

    /**
     * 通用衰减插值工具方法。
     * <p>
     * 耐久 = 0 → 返回 destroyedValue（质变悬崖）。<br>
     * 耐久 > 0 → damagedValue + (1.0 - damagedValue) × ratio^decayPower。<br>
     * 满耐久隐式值为 1.0（乘数恒等式）。
     * <p>
     * 各 ModuleAttr 子类按需调用。
     *
     * @param ratio          耐久比例 [0, 1]
     * @param damagedValue   战损极限乘数（耐久→0 但未归零）
     * @param destroyedValue 摧毁乘数（耐久归零）
     * @param decayPower     衰减曲线指数（>1 先硬后脆）
     */
    public static float computeDecayMultiplier(float ratio, float damagedValue, float destroyedValue, float decayPower) {
        if (ratio <= 0f) return destroyedValue;
        return damagedValue + (1.0f - damagedValue) * (float) Math.pow(ratio, decayPower);
    }

    /**
     * 受到伤害。返回实际扣血量。
     * 摧毁仅设置 destroyed 标志，不触发回调——由 shouldDestroy() 在 onTick 中统一判定。
     */
    public float applyDamage(float amount) {
        if (destroyed || amount <= 0f) return 0f;
        float actual = Math.min(amount, durability);
        durability -= actual;
        if (durability <= 0f) {
            durability = 0f;
            destroyed = true;
        }
        return actual;
    }

    /**
     * 接受修复。返回实际修复量。
     * 内部正确处理已摧毁状态：若耐久从 0 恢复则清除 destroyed 标志。
     */
    public float applyRepair(float amount) {
        if (amount <= 0f) return 0f;
        float capacity = maxDurability - durability;
        if (capacity <= 0f) return 0f;
        float actual = Math.min(amount, capacity);
        durability += actual;
        if (destroyed && durability > 0f) destroyed = false;
        return actual;
    }

    // ==================== NBT 持久化 ====================

    /**
     * 将模块运行时状态保存到 NBT。
     * <p>
     * 存储绝对耐久值与摧毁标志。不保存 maxDurability——它由 ModularSubsystem
     * 在 {@code tryInitModules()} 中根据 JSON 权重计算，NBT 中不应固化。
     */
    public CompoundTag save(CompoundTag tag) {
        tag.putFloat(TAG_DURABILITY, durability);
        tag.putBoolean(TAG_DESTROYED, destroyed);
        return tag;
    }

    /**
     * 从 NBT 恢复模块运行时状态。
     * <p>
     * 耐久值被钳制到 [0, maxDurability] 范围，防止存档损坏导致越界。
     */
    public void load(CompoundTag tag) {
        durability = Math.clamp(tag.getFloat(TAG_DURABILITY), 0f, maxDurability);
        destroyed = tag.getBoolean(TAG_DESTROYED);
    }

    // ==================== 访问器 ====================

    public boolean isDestroyed() { return destroyed; }
    public AbstractModuleAttr getAttr() { return attr; }
    public float getDurability() { return durability; }
    public float getMaxDurability() { return maxDurability; }
}
