package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.BasicSubsystemDynamicAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.AbstractModuleAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.MMDamageExtensions;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.event.subpart.SubPartDamageEvent;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.HitBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模块化子系统的抽象基类。
 * <p>
 * 将子系统的耐久度拆分为多个可独立受损的 {@link SubModule}。
 * 不覆写 {@link #onTick()}，仅覆写 {@link #shouldDestroy()} / {@link #shouldRecover()} / {@link #onRecovered()}，
 * 这三个方法由父类 {@link AbstractSubsystem#onTick()} 框架层统一调用。
 * </p>
 */
public abstract class ModularSubsystem extends BasicSubsystem implements IModularSubsystem {

    /** 模块映射表，按名称索引。初始化后不可变。 */
    protected final Map<String, SubModule> modules = new LinkedHashMap<>();

    private static final String TAG_MODULES = "modules";

    /** 模块状态同步用 EntityDataAccessor，通过 NBT CompoundTag 批量同步所有模块。 */
    protected static final EntityDataAccessor<CompoundTag> DATA_MODULES_ID =
            SynchedEntityData.defineId(ModularSubsystem.class, EntityDataSerializers.COMPOUND_TAG);

    /** 模块是否已初始化（延迟初始化标记，避免在构造函数中调用抽象方法） */
    private boolean modulesInitialized = false;

    public ModularSubsystem(ISubsystemHost owner, String name, BasicSubsystemDynamicAttr attr) {
        super(owner, name, attr);
    }

    // ==================== 模块初始化（延迟初始化） ====================

    /** 子类实现：声明此子系统的模块定义（name → AbstractModuleAttr）。 */
    protected abstract Map<String, ? extends AbstractModuleAttr> defineModules();

    /**
     * 确保模块已初始化。从 defineModules() 获取定义并按权重分配耐久度。
     * 在首次访问模块前自动调用。
     */
    protected final void tryInitModules() {
        if (modulesInitialized) return;
        modulesInitialized = true;
        var defs = defineModules();
        if (defs == null || defs.isEmpty()) return;

        float totalWeight = (float) defs.values().stream()
                .mapToDouble(AbstractModuleAttr::durabilityWeight).sum();
        float baseDurability = attr.getStaticAttribute().getBasicAttr().basicDurability();

        defs.forEach((name, modAttr) -> {
            float modMaxDurability = baseDurability * (modAttr.durabilityWeight() / totalWeight);
            modules.put(name, new SubModule(modAttr, modMaxDurability));
        });
    }

    // ==================== 摧毁/恢复覆写 ====================

    @Override
    protected boolean shouldDestroy() {
        tryInitModules();
        if (modules.isEmpty()) return super.shouldDestroy();
        return isModularDestroyed();
    }

    @Override
    protected boolean shouldRecover() {
        tryInitModules();
        if (modules.isEmpty()) return super.shouldRecover();
        // 模块自己按 RECOVERY_RATIO 决定是否脱离摧毁，因此这里只需确认关键模块都已恢复；
        // 非关键模块坏着不阻挡整机恢复（关键模块才是整机能力的必要条件）
        return modules.values().stream()
                .filter(m -> m.getAttr().critical())
                .noneMatch(SubModule::isDestroyed);
    }

    /**
     * 恢复回调：重新注册能源网。子类可覆写以播放修复音效等。
     */
    @Override
    protected void onRecovered() {
        if (getOwner() != null) {
            var grid = getOwner().getEnergyGrid();
            if (grid != null) {
                if (this instanceof io.github.sweetzonzi.machine_max.common.mech.energy.IEnergyConsumer consumer) {
                    grid.registerConsumer(consumer);
                }
                if (this instanceof io.github.sweetzonzi.machine_max.common.mech.energy.IEnergyProducer producer) {
                    grid.registerProducer(producer);
                }
            }
        }
    }

    // ==================== 聚合覆写 ====================

    @Override
    public float getDurability() {
        tryInitModules();
        return modules.isEmpty() ? super.getDurability() : getAggregatedDurability();
    }

    /**
     * 按比例把整机耐久分摊到各模块，模块之和因此恒等于请求值（超出上限时按上限）。
     * <p>
     * 这是模块耐久的唯一整机写入入口：{@link AbstractSubsystem#loadData} 恢复 {@code durability_ratio}
     * 就经过它，因此旧存档（只有单一耐久、没有 {@code modules} 标签）读进来时，两个模块按缺失容量
     * 分到同一比例，聚合仍等于旧比例，而不是两个满耐久模块。
     * <p>
     * 分摊方向决定权重：向上修按缺失容量（已满的模块拿不到份额，已摧毁的模块才能被补回来），
     * 向下修按现有耐久（零耐久的模块不再挨刀）。这与 {@link #routeRepair} / {@link #routeDamage}
     * 的分摊口径一致。摧毁标志由 {@link SubModule#setDurability} 按恢复门槛归一。
     */
    @Override
    public void setDurability(float durability) {
        tryInitModules();
        if (modules.isEmpty()) {
            super.setDurability(durability);
            return;
        }
        float target = Math.clamp(durability, 0f, getMaxDurability());
        float delta = target - getAggregatedDurability();
        if (delta > 0f) {
            float missing = 0f;
            for (SubModule m : modules.values()) missing += m.getMaxDurability() - m.getDurability();
            if (missing <= 0f) return;
            for (SubModule m : modules.values()) {
                float share = m.getMaxDurability() - m.getDurability();
                if (share > 0f) m.setDurability(m.getDurability() + delta * (share / missing));
            }
        } else if (delta < 0f) {
            float present = getAggregatedDurability();
            if (present <= 0f) return;
            for (SubModule m : modules.values()) {
                if (m.getDurability() > 0f) m.setDurability(m.getDurability() + delta * (m.getDurability() / present));
            }
        }
        refreshModuleSync();
    }

    // getMaxDurability() 无需覆写：模块最大耐久按权重从 basicDurability 分配，聚合后数学上恒等于 basicDurability

    // ==================== onHurt ====================

    /**
     * 伤害处理：先按 {@code pass_damage} / {@code limit_damage} 处理事件，然后将伤害路由到模块。
     * 不调用 super.onHurt()——伤害已由子模块消费，避免二次扣耐久。
     * <p>
     * {@code pass_damage=false} 表达的是“取消向下传递、伤害由本子系统承担”，与非模块化基类的口径一致：
     * 事件照常取消（零件与载具不扣耐久），但取消之后仍要执行模块路由，否则模块化子系统命中时
     * 既不伤零件也不伤模块。{@code pass_damage=true} 时事件不取消，剩余金额按既有规则传给零件。
     * <p>
     * 模块化子系统的耐久是各模块的聚合值，因此最近有效受击由本方法自行记录：
     * 在 {@link #routeDamage} 前后比较聚合耐久，实际下降时才保存定稿事件与命中的命中箱。
     * 降低到零的那一次自然成为最后一条记录，零耐久后的追加命中不再降低聚合耐久，不会覆盖它。
     */
    @Override
    public void onHurt(SubPartDamageEvent.Pre event) {
        tryInitModules();
        if (!attr.getStaticAttribute().getBasicAttr().passDamage()) {
            event.setCanceled(true);
        }
        if (event.getDamageAmount() > getDurability()
                && attr.getStaticAttribute().getBasicAttr().limitDamage()) {
            event.setDamageAmount(getDurability());
        }

        // 从伤害上下文中获取命中的 HitBox，提取模块名进行精确路由
        HitBox hitBox = event.getCtx().extensions().get(MMDamageExtensions.HIT_BOX);
        String moduleName = (hitBox != null) ? hitBox.getAttr().getModule() : null;
        float durabilityBefore = getDurability();
        routeDamage(moduleName, event.getDamageAmount(), event);
        if (getDurability() < durabilityBefore) {
            recordLastDamage(event);
        }
        // 不调用 super.onHurt() —— 伤害已由子模块消费
    }

    // ==================== IModularSubsystem 实现 ====================

    @Override
    public void routeDamage(@Nullable String moduleName, float amount) {
        routeDamage(moduleName, amount, null);
    }

    /**
     * 伤害路由的完整形态。{@code event} 非空时，把这一击记录到耐久确实下降的模块上，
     * 供该模块自己的起爆定位使用（装弹机读弹仓模块的来源）；直接调用两参版本的
     * 无上下文伤害没有事件可记，模块的来源保持原值。
     */
    private void routeDamage(@Nullable String moduleName, float amount, @Nullable SubPartDamageEvent.Pre event) {
        tryInitModules();
        if (modules.isEmpty()) return;
        if (moduleName != null && !moduleName.isEmpty()) {
            SubModule target = modules.get(moduleName);
            if (target != null) {
                if (target.applyDamage(amount) > 0f && event != null) target.recordHit(event);
                refreshModuleSync();
                return;
            }
            MachineMax.LOGGER.warn("模块 '{}' 不存在于子系统 '{}'，回退按比例分摊", moduleName, getName());
        }
        // 未指定或不存在：按最大耐久比例分摊
        // 分母为所有模块（含已摧毁）——已摧毁模块仍占据体积、吸收动能
        float totalMax = getMaxDurability();
        if (totalMax <= 0) return;
        for (SubModule m : modules.values()) {
            if (!m.isDestroyed()) {
                float share = amount * (m.getMaxDurability() / totalMax);
                if (m.applyDamage(share) > 0f && event != null) m.recordHit(event);
            }
        }
        refreshModuleSync();
    }

    @Override
    public void routeRepair(@Nullable String moduleName, float amount) {
        tryInitModules();
        if (modules.isEmpty()) return;
        if (moduleName != null && !moduleName.isEmpty()) {
            SubModule target = modules.get(moduleName);
            if (target != null) {
                target.applyRepair(amount);
                refreshModuleSync();
                return;
            }
        }
        // 未指定：按受损比例分摊到所有模块（含已摧毁）
        float totalLost = 0;
        for (SubModule m : modules.values())
            totalLost += m.getMaxDurability() - m.getDurability();
        if (totalLost <= 0) return;
        for (SubModule m : modules.values()) {
            float lost = m.getMaxDurability() - m.getDurability();
            if (lost > 0) m.applyRepair(amount * (lost / totalLost));
        }
        refreshModuleSync();
    }

    @Override
    @Nullable
    public SubModule getModule(String name) {
        tryInitModules();
        return modules.get(name);
    }

    @Override
    public float getAggregatedDurability() {
        float sum = 0;
        for (SubModule m : modules.values()) sum += m.getDurability();
        return sum;
    }

    @Override
    public float getAggregatedMaxDurability() {
        // 模块最大耐久按权重从 basicDurability 分配，聚合后恒等于 basicDurability
        return getMaxDurability();
    }

    @Override
    public boolean isModularDestroyed() {
        return modules.values().stream().anyMatch(m -> m.getAttr().critical() && m.isDestroyed());
    }

    // ==================== SynchedEntityData（注册模块访问器） ====================

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_MODULES_ID, new CompoundTag());
    }

    // ==================== NBT 持久化 ====================

    /**
     * 保存模块状态到 NBT。格式为 {@code { "modules": { "barrel": { durability, destroyed }, ... } }}。
     * <p>
     * 整机的 {@code durability_ratio}、{@code destroyed} 由父类写出，其中 {@code destroyed} 在这里删掉：
     * 模块化子系统的摧毁状态是各模块 {@code destroyed} 的汇总，{@code modules} 已经完整表达了它，
     * 再存一份整机标志只会多出一个可能与模块分叉的来源。
     */
    @Override
    public CompoundTag saveData(CompoundTag data) {
        tryInitModules();
        data.put(TAG_MODULES, serializeModules());
        super.saveData(data);
        data.remove(TAG_DESTROYED);
        return data;
    }

    /**
     * 从 NBT 恢复模块状态。先调用父类 loadData 恢复基础字段，
     * 然后遍历 modules 标签反序列化到各模块实例。
     * <p>
     * 耐久值被钳制到 [0, maxDurability] 范围（由 SubModule.load 内部处理）。
     * 恢复完成后把最终模块状态写进同步快照：载入的损伤不会自己变成脏值，
     * 不写这一笔的话客户端只能收到访问器的空初值（{@code defineSynchedData} 里的空 CompoundTag），
     * 于是"载入后不再变化的损坏子系统"在客户端显示为满耐久。
     * <p>
     * 摧毁状态在模块读到之后再算一次：基类执行 {@link #restoreDestroyed} 时模块还是满耐久，
     * 判断不出"弹仓已毁"。
     */
    @Override
    public void loadData(CompoundTag data) {
        super.loadData(data);
        tryInitModules();
        CompoundTag modulesTag = data.getCompound(TAG_MODULES);
        for (String key : modulesTag.getAllKeys()) {
            SubModule module = modules.get(key);
            if (module != null) {
                module.load(modulesTag.getCompound(key));
            } else {
                MachineMax.LOGGER.warn("子系统 '{}' 的 NBT 包含未知模块 '{}'，已忽略", getName(), key);
            }
        }
        if (modules.isEmpty()) return;
        // 缺失的模块条目保留父类按 durability_ratio 分摊出的耐久（旧存档与部分模块标签都走这条路）
        for (String key : modules.keySet()) {
            if (!modulesTag.contains(key)) {
                MachineMax.LOGGER.warn("子系统 '{}' 的存档缺少模块 '{}'，按整机耐久比例分摊其耐久", getName(), key);
            }
        }
        refreshModuleSync();
        restoreDestroyed(data);
    }

    /**
     * 模块化子系统的摧毁状态完全由模块推导：任一关键模块处于摧毁（含修到 0%—30% 之间的恢复锁存）即整机摧毁。
     * <p>
     * 子系统的存档里没有整机摧毁键可供读取，模块状态是唯一来源；没有声明模块时退回基类规则。
     * 两个调用点：基类 {@link #loadData} 在模块读入前调用一次（模块此时满耐久，结果为未摧毁），
     * 本类 {@link #loadData} 在模块落地后再算一次，覆盖前一次。
     */
    @Override
    protected void restoreDestroyed(CompoundTag data) {
        tryInitModules();
        if (modules.isEmpty()) {
            super.restoreDestroyed(data);
            return;
        }
        synchedData.set(DATA_DESTROYED_ID, shouldDestroy());
    }

    // ==================== 网络同步 ====================

    /** 全部模块状态打包成 {@code { 模块名: { durability, destroyed }, ... }}，供存档与同步共用。 */
    private CompoundTag serializeModules() {
        CompoundTag tag = new CompoundTag();
        for (var entry : modules.entrySet()) {
            CompoundTag moduleTag = new CompoundTag();
            entry.getValue().save(moduleTag);
            tag.put(entry.getKey(), moduleTag);
        }
        return tag;
    }

    /**
     * 模块状态变更后刷新两个同步值：模块快照，以及基类耐久访问器上的总耐久镜像。<br>
     * 调用点覆盖 {@link #routeDamage}、{@link #routeRepair} 与 {@link #loadData}，每条模块写入路径都经过它。
     * <p>
     * 总耐久的事实来源是模块之和，{@code DATA_DURABILITY_ID} 只是它的镜像：基类的
     * {@code AbstractSubsystem#getDurability} 被覆写为聚合值，因此这里不扣总耐久，只把
     * {@code getAggregatedDurability()} 的当前结果写进去，让直接读该访问器的代码
     * （全量补推快照、只认同步字段的集成方）看到与 {@code getDurability()} 相同的数字。
     * 不刷新时它永远停在构造初值，两类读法会给出互相矛盾的结果。
     * <p>
     * 数据仍经 {@link #syncToClient()} → {@code SubsystemSyncPayload} 广播；同一 tick 的多次模块变更
     * 只产生一份脏值，因此不会因为多写一个访问器而多发包。
     */
    protected void refreshModuleSync() {
        if (modules.isEmpty()) return;
        synchedData.set(DATA_MODULES_ID, serializeModules());
        synchedData.set(DATA_DURABILITY_ID, getAggregatedDurability());
    }

    /**
     * 客户端收到模块状态更新时，反序列化到本地模块实例。
     * <p>
     * 覆盖父类实现以处理 DATA_MODULES_ID 的更新。
     * 基础字段（耐久度/激活/摧毁）的更新仍由父类处理。
     */
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> dataAccessor) {
        if (dataAccessor.equals(DATA_MODULES_ID)) {
            if (!getLevel().isClientSide()) return;
            tryInitModules();
            CompoundTag tag = synchedData.get(DATA_MODULES_ID);
            if (tag == null || tag.isEmpty()) return;
            for (String key : tag.getAllKeys()) {
                SubModule module = modules.get(key);
                if (module != null) {
                    module.load(tag.getCompound(key));
                }
            }
            return;
        }
        super.onSyncedDataUpdated(dataAccessor);
    }

}
