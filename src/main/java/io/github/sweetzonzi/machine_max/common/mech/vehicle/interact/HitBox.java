package io.github.sweetzonzi.machine_max.common.mech.vehicle.interact;

import cn.solarmoon.spark_core.molang.MolangContextRegistry;
import cn.solarmoon.spark_core.molang.runtime.MolangExpression;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IModularSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SubModule;
import io.github.sweetzonzi.machine_max.common.mech.target.Target;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.HitBoxAttr;
import io.github.sweetzonzi.machine_max.util.mechanic.ArmorUtil;
import lombok.Getter;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

@Getter
public class HitBox implements Target {
    public final SubPart subPart;
    public final HitBoxAttr attr;
    public final AbstractSubsystem subsystem;
    /**
     * 宿主子部件 {@code hitBoxes} 表中的键，即命中框对应的骨骼名。
     * <p>
     * 与 {@link HitBoxAttr#id} 不同：后者是「同一次攻击不重复命中」的伤害分组键，可以重复、可以缺省；
     * 本字段在一个子部件内唯一，因此是碰撞箱的会话内身份。
     */
    private final String hitBoxKey;
    private boolean active = true;
    private final boolean alwaysActive;
    /**
     * 预编译的 HitBox condition MoLang 表达式
     */
    private final MolangExpression hitBoxCondition;

    /**
     * 本碰撞箱的几何代表中心，位于<b>宿主 SubPart 刚体质心坐标系</b>，不是世界坐标。
     * <p>
     * 由 {@code SubPartAttr#getCollisionShape} 在模型几何构建阶段按 cube 体积加权求出、
     * 经完整质心逆变换（含旋转）后缓存，构造时拷入本实例。起爆定位时按宿主发布位姿
     * {@code worldOrigin = hostPosition + hostRotation × localCenter} 转成世界坐标。
     * <p>
     * 单 cube、多 cube 与旋转质心差异见设计文档 §4.1.2；同一 HitBox 的多个物理代理
     * （{@code wheel} 的球体与圆柱体）只计一次。取不到几何来源时为零向量，此时定位应回退到宿主质心。
     * <p>
     * <b>只读：</b>本向量是本实例自己的副本，调用方不得修改。
     */
    private final Vector3f localCenter;

    public HitBox(SubPart subPart, HitBoxAttr attr, String hitBoxKey, @Nullable Vector3f localCenter) {
        this.subPart = subPart;
        this.attr = attr;
        this.hitBoxKey = hitBoxKey;
        this.subsystem = subPart.subsystems.getOrDefault(attr.subsystem, null);
        this.localCenter = localCenter == null ? new Vector3f() : localCenter.clone();
        alwaysActive = attr.condition.isEmpty() || attr.condition.equalsIgnoreCase("true");
        this.hitBoxCondition = alwaysActive ? null : MolangContextRegistry.compile(attr.condition, subPart.part.getSparkMolangContext());
    }

    /**
     * 根据condition脚本更新激活状态
     * 应在每物理刻（prePhysicsTick）调用
     */
    public void updateActive() {
        if (alwaysActive) {
            active = true;
            return;
        }
        try {
            active = hitBoxCondition.evaluate(subPart.part.getSparkMolangContext()) > 0;
        } catch (Exception e) {
            io.github.sweetzonzi.machine_max.MachineMax.LOGGER.warn(
                    "Failed to evaluate condition for HitBox '{}' in part '{}-{}': {}",
                    attr.id, subPart.part.name, subPart.name, e.getMessage()
            );
        }
    }

    // ==================== Target 接口实现 ====================

    /**
     * 标签取耐久单元的名字：写了 {@code module} 时取模块名，否则取子系统名。
     * <p>
     * 子系统名是内容包里的寻址名（形如 {@code subsystem.machine_max.main_weapon}），
     * 这里去掉命名空间段后展示；没有子系统（纯结构碰撞箱）时退回骨骼名。
     */
    @Override
    public Component label() {
        if (!attr.module.isEmpty()) return Component.literal(attr.module);
        if (subsystem != null) {
            String name = subsystem.getName();
            int dot = name.lastIndexOf('.');
            return Component.literal(dot >= 0 ? name.substring(dot + 1) : name);
        }
        return Component.literal(hitBoxKey);
    }

    /**
     * 真值点：构建期缓存的代表中心按宿主刚体的位姿转到世界坐标，位姿按系数在上一步与当前步之间插值。
     * <p>
     * {@code localCenter} 位于刚体质心坐标系，{@code getWorldPositionMatrix(n)} 正是该坐标系到世界空间的变换。
     */
    @Override
    public Vec3 point(Number partialTick) {
        org.joml.Vector3f world = subPart.getWorldPositionMatrix(partialTick)
                .transformPosition(SparkMathKt.toVector3f(localCenter), new org.joml.Vector3f());
        return new Vec3(world.x, world.y, world.z);
    }

    /**
     * 内容包显式声明可锁、条件激活、宿主存活、且所属耐久单元未摧毁时可锁。
     * <p>
     * {@code lockable} 默认 {@code false}：不是每个碰撞箱都值得标，由作者逐块标注。
     * 模块优先于子系统：炮管打坏后，即使发射器整体还在，炮管那个框也不再是候选。
     * 模块名在子系统里查不到时按「没有可摧毁的模块」处理，即仍然可锁。
     */
    @Override
    public boolean isLockable() {
        if (!attr.lockable) return false;
        if (!active || subPart.isRemoved || subPart.isDestroyed()) return false;
        if (subsystem == null) return false;
        if (attr.module.isEmpty()) return !subsystem.isDestroyed();
        SubModule module = resolveModule();
        return module == null || !module.isDestroyed();
    }

    @Nullable
    private SubModule resolveModule() {
        return subsystem instanceof IModularSubsystem modular ? modular.getModule(attr.module) : null;
    }

    public float modifyImpact(DamageSource source, float amount) {
        return attr.impactModifiers().apply(source, amount);
    }

    public float modifyPiercing(DamageSource source, float amount) {
        return attr.piercingModifiers().apply(source, amount);
    }

    /**
     * 完整伤害修正管线：条件化修正 → 线性减伤 → 百分比乘算。
     *
     * @param source 伤害来源
     * @param amount 原始伤害量
     * @return 修正后的伤害量
     */
    public float modifyDamage(DamageSource source, float amount) {
        // ① 条件化伤害修正（类型/实体特定调整）
        float dmg = attr.damageModifiers().apply(source, amount);
        // ② 全局线性减伤（材料硬度阈值）
        dmg = Math.max(0, dmg - getEffectiveDmgResist(subPart));
        // ③ 全局百分比减伤（材料能量耗散效率）
        dmg *= getEffectiveDmgMult(subPart);
        return dmg;
    }

    /**
     * 计算等效护甲厚度（RHA 毫米数），根据零部件当前耐久度动态衰减。
     * <p>
     * 公式：thickness × [decay_rha + (rha - decay_rha) × (durabilityRatio)^decay_power]
     * </p>
     *
     * @param subPart 所属零部件
     * @return 等效护甲厚度
     */
    public float getRHA(SubPart subPart) {
        return ArmorUtil.rha(attr.getEffectiveMaterial().armor(), attr.thickness, durabilityRatio(subPart));
    }

    /**
     * 计算考虑耐久衰减后的有效线性减伤值。
     * <p>
     * 公式：decay_dmg_resist + (dmg_resist - decay_dmg_resist) × durability^decay_power
     * </p>
     *
     * @param subPart 所属零部件
     * @return 有效线性减伤值
     */
    public float getEffectiveDmgResist(SubPart subPart) {
        return ArmorUtil.resist(attr.getEffectiveMaterial().armor(), durabilityRatio(subPart));
    }

    /**
     * 计算考虑耐久衰减后的有效伤害乘数。
     * <p>
     * 公式：decay_dmg_mult + (dmg_mult - decay_dmg_mult) × durability^decay_power
     * </p>
     *
     * @param subPart 所属零部件
     * @return 有效伤害乘数（1.0=无变化，0.5=一半伤害）
     */
    public float getEffectiveDmgMult(SubPart subPart) {
        return ArmorUtil.mult(attr.getEffectiveMaterial().armor(), durabilityRatio(subPart));
    }

    private static float durabilityRatio(SubPart subPart) {
        float maxDurability = subPart.getMaxDurability();
        return maxDurability > 0f ? Math.clamp(subPart.getDurability() / maxDurability, 0f, 1f) : 1f;
    }

    /**
     * 是否启用入射角导致的等效厚度效应。
     * <p>
     * 由 {@code SubPart.getRHA(BFDamageContext)} 读取：启用时等效厚度为
     * {@code 垂直厚度 / |cosθ|}，并受 {@code ArmorUtil.MIN_ANGLE_COS} 钳制。
     */
    public boolean hasAngleEffect() {
        return attr.angleEffect();
    }

    /**
     * 是否有未穿透钝伤效果
     *
     * @return true 表示未穿透时仍可造成部分伤害
     */
    public boolean hasUnpenDamage() {
        return attr.unpen_power() > 0.0f;
    }

    /**
     * 获取未穿透伤害指数（幂次）
     *
     * @return 未穿透伤害指数
     */
    public float getUnpenPower() {
        return attr.unpen_power();
    }

    /**
     * 获取碰撞箱的前向摩擦系数
     *
     * @return 前向摩擦系数
     */
    public double getMuFront() {
        return attr.friction().y();
    }

    /**
     * 获取碰撞箱的侧向摩擦系数
     *
     * @return 侧向摩擦系数
     */
    public double getMuSide() {
        return attr.friction().x();
    }

    public SoundEvent getHitPenSound() {
        return attr.sounds().onHitPen();
    }

    public SoundEvent getHitUnPenSound() {
        return attr.sounds().onHitUnPen();
    }

}
