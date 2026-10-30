package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.AbstractModuleAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.event.subpart.SubPartDamageEvent;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * 子模块——子系统内部可独立受损的功能单元。
 * <p>
 * 纯状态容器，管理运行时耐久度。功能衰减逻辑由各子系统根据
 * {@link #getDurabilityRatio()} + 对应 ModuleAttr 的专有字段自行计算。
 * <p>
 * 模块的 maxDurability 由 ModularSubsystem 在初始化时根据 durabilityWeight 计算：
 * {@code maxDurability = basicDurability × (weight / totalWeight)}。
 * <p>
 * 摧毁规则与一般子系统一致，只是按模块自己的最大耐久判定：归零即摧毁，修复到
 * {@link AbstractSubsystem#RECOVERY_RATIO} 才脱离摧毁，0%—30% 之间保持摧毁。
 * 关键模块何时恢复由 {@link ModularSubsystem#shouldRecover()} 汇总。
 */
public class SubModule {
    private static final String TAG_DURABILITY = "durability";
    private static final String TAG_DESTROYED = "destroyed";

    private final AbstractModuleAttr attr;
    private final float maxDurability;   // 由 ModularSubsystem 初始化时计算
    private float durability;
    private boolean destroyed;

    /**
     * 最近一次打到本模块的受击。模块状态有两种生命周期：耐久与摧毁标志随存档走，
     * 这条记录只在本次会话有效——不写 NBT、不参与同步，新实例与客户端恒为 {@code null}。
     */
    @Nullable
    private SubPartDamageEvent.Pre lastHitEvent;

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
     * <p>
     * 已摧毁的模块要修到 {@link AbstractSubsystem#RECOVERY_RATIO} 才清除 {@code destroyed}；
     * 在此之前耐久为正也仍然算摧毁，与一般子系统的恢复门槛一致。
     */
    public float applyRepair(float amount) {
        if (amount <= 0f) return 0f;
        float capacity = maxDurability - durability;
        if (capacity <= 0f) return 0f;
        float actual = Math.min(amount, capacity);
        durability += actual;
        normalizeDestroyed(destroyed);
        return actual;
    }

    // ==================== 最近有效受击 ====================

    /**
     * 记录一次打到本模块的受击。只在耐久确实下降时由
     * {@link ModularSubsystem#routeDamage} 调用，因此零耐久的追加命中不会覆盖它。
     * <p>
     * 保存定稿事件副本；命中箱不另存，读取时由 {@link AbstractSubsystem#resolveHitBox}
     * 按事件里的命中箱名回宿主查当前实例。
     */
    void recordHit(SubPartDamageEvent.Pre event) {
        this.lastHitEvent = AbstractSubsystem.finalizedCopy(event);
    }

    /** 本模块最近一次有效受击；未受击、已恢复或脱离摧毁后为 {@code null}。 */
    @Nullable
    public SubPartDamageEvent.Pre getLastHitEvent() {
        return lastHitEvent;
    }

    void clearHitRecord() {
        this.lastHitEvent = null;
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
     * 摧毁标志按 {@link #normalizeDestroyed} 与耐久归一：零耐久必为摧毁，
     * 已达到恢复门槛的必为未摧毁，两者之间才采用存档值，于是 0%—30% 的
     * 恢复锁存能跨存档保留，而"零耐久却不摧毁"这类非法组合不会进入运行期。
     */
    public void load(CompoundTag tag) {
        durability = Math.clamp(tag.getFloat(TAG_DURABILITY), 0f, maxDurability);
        normalizeDestroyed(tag.getBoolean(TAG_DESTROYED));
    }

    /**
     * 按恢复门槛归一摧毁标志，并在脱离摧毁时清空最近有效受击：
     * 零耐久必为摧毁、越过门槛必为未摧毁、中间区间沿用 {@code stored}。
     * <p>
     * 清空放在这里，是因为"脱离摧毁"就是一次生命周期的结束；修复到门槛、读档、
     * 客户端快照覆盖都经过本方法，不必在三个调用点各写一次。
     */
    private void normalizeDestroyed(boolean stored) {
        boolean next = durability <= 0f
                ? true
                : getDurabilityRatio() >= AbstractSubsystem.RECOVERY_RATIO ? false : stored;
        if (destroyed && !next) lastHitEvent = null;
        destroyed = next;
    }

    // ==================== 访问器 ====================

    /**
     * 直接写入耐久（由 {@link ModularSubsystem#setDurability} 按比例分摊调用）。
     * <p>
     * 按上限钳位，并按恢复门槛归一摧毁标志：写回零必为摧毁，越过门槛才脱离摧毁，
     * 中间区间保留原有标志——与 {@link #applyRepair} 的规则一致。
     */
    void setDurability(float value) {
        durability = Math.clamp(value, 0f, maxDurability);
        normalizeDestroyed(destroyed);
    }

    public boolean isDestroyed() { return destroyed; }
    public AbstractModuleAttr getAttr() { return attr; }
    public float getDurability() { return durability; }
    public float getMaxDurability() { return maxDurability; }
}
