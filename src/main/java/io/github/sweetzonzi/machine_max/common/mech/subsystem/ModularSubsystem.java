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
        return modules.values().stream()
                .filter(m -> m.getAttr().critical())
                .noneMatch(m -> m.isDestroyed() || m.getDurabilityRatio() < 0.3f);
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

    // getMaxDurability() 无需覆写：模块最大耐久按权重从 basicDurability 分配，聚合后数学上恒等于 basicDurability

    // ==================== onHurt ====================

    /**
     * 伤害处理：先检查 passDamage/limitDamage，然后将伤害路由到模块。
     * 不调用 super.onHurt()——伤害已由子模块消费，避免二次扣耐久。
     */
    @Override
    public void onHurt(SubPartDamageEvent.Pre event) {
        tryInitModules();
        if (!attr.getStaticAttribute().getBasicAttr().passDamage()) {
            event.setCanceled(true);
            return;
        }
        if (event.getDamageAmount() > getDurability()
                && attr.getStaticAttribute().getBasicAttr().limitDamage()) {
            event.setDamageAmount(getDurability());
        }

        // 从伤害上下文中获取命中的 HitBox，提取模块名进行精确路由
        HitBox hitBox = event.getCtx().extensions().get(MMDamageExtensions.HIT_BOX);
        String moduleName = (hitBox != null) ? hitBox.getAttr().getModule() : null;
        routeDamage(moduleName, event.getDamageAmount());
        // 不调用 super.onHurt() —— 伤害已由子模块消费
    }

    // ==================== IModularSubsystem 实现 ====================

    @Override
    public void routeDamage(@Nullable String moduleName, float amount) {
        tryInitModules();
        if (modules.isEmpty()) return;
        if (moduleName != null && !moduleName.isEmpty()) {
            SubModule target = modules.get(moduleName);
            if (target != null) {
                target.applyDamage(amount);
                markModulesDirty();
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
                m.applyDamage(share);
            }
        }
        markModulesDirty();
    }

    @Override
    public void routeRepair(@Nullable String moduleName, float amount) {
        tryInitModules();
        if (modules.isEmpty()) return;
        if (moduleName != null && !moduleName.isEmpty()) {
            SubModule target = modules.get(moduleName);
            if (target != null) {
                target.applyRepair(amount);
                markModulesDirty();
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
        markModulesDirty();
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
     * 调用父类 saveData 以保存基础字段（durability、active、destroyed）。
     */
    @Override
    public CompoundTag saveData(CompoundTag data) {
        tryInitModules();
        CompoundTag modulesTag = new CompoundTag();
        for (var entry : modules.entrySet()) {
            CompoundTag moduleTag = new CompoundTag();
            entry.getValue().save(moduleTag);
            modulesTag.put(entry.getKey(), moduleTag);
        }
        data.put(TAG_MODULES, modulesTag);
        return super.saveData(data);
    }

    /**
     * 从 NBT 恢复模块状态。先调用父类 loadData 恢复基础字段，
     * 然后遍历 modules 标签反序列化到各模块实例。
     * <p>
     * 耐久值被钳制到 [0, maxDurability] 范围（由 SubModule.load 内部处理）。
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
    }

    // ==================== 网络同步 ====================

    /**
     * 将模块状态打包为 CompoundTag 并标记同步。
     * <p>
     * 在 routeDamage / routeRepair 等模块状态变更后调用，
     * 数据将通过 {@link #syncToClient()} → {@code SubsystemSyncPayload} 广播到客户端。
     */
    protected void markModulesDirty() {
        if (modules.isEmpty()) return;
        CompoundTag tag = new CompoundTag();
        for (var entry : modules.entrySet()) {
            CompoundTag moduleTag = new CompoundTag();
            entry.getValue().save(moduleTag);
            tag.put(entry.getKey(), moduleTag);
        }
        synchedData.set(DATA_MODULES_ID, tag);
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
