package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.ballistics_framework.api.BFDamageContext;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.ReactiveArmorSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.ReactiveArmorSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.MMDamageExtensions;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.event.subpart.SubPartDamageEvent;

/**
 * 爆反（反应装甲）砖：被<b>足够强</b>的命中打到即失效，不按伤害量磨损。
 *
 * <p>与父类 {@link BasicSubsystem} 的差别就是"失效判据"：父类按金额扣耐久，本类把
 * {@link #onHit(SubPartDamageEvent.Pre)} 当作唯一失效入口——进砖之前的等效穿深达到
 * {@code trigger_penetration} 就把耐久归零，当次是否击穿无关；低于阈值的命中既不引爆也不磨损。
 * {@link #onHurt(SubPartDamageEvent.Pre)} 因此不调 {@code super.onHurt}：小口径弹的钝伤不会
 * 一点点把砖磨掉，{@code basic_durability} 只作为存活位与维修读数存在。</p>
 *
 * <p>失效之后命中箱的激活状态由 {@code condition} 里的
 * {@code local.subsystem_destroyed(...)} 决定，与摧毁标记的翻转延迟同一口径：摧毁状态在
 * {@link AbstractSubsystem#onTick()} 里按耐久判定，命中箱在下一次 {@code prePhysicsTick}
 * 重算激活状态。</p>
 */
public class ReactiveArmorSubsystem extends BasicSubsystem {

    public ReactiveArmorSubsystem(ISubsystemHost owner, String name, ReactiveArmorSubsystemAttr attr) {
        super(owner, name, attr);
    }

    /** 静态属性在父类里是 {@link io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.BasicSubsystemStaticAttr} 类型，这里收窄回本类型。 */
    private ReactiveArmorSubsystemStaticAttr era() {
        return (ReactiveArmorSubsystemStaticAttr) attr.getStaticAttribute();
    }

    /**
     * 阈值引爆。{@inheritDoc}
     *
     * <p>三条判据的顺序即语义：耐久已为 0 时直接返回（同一结算批里的第二次命中不再产生副作用）；
     * 碰撞冲击不引爆（它的"穿深"取的是伤害量本身，蹭一下墙不该掀掉一块砖）；进砖之前穿深低于
     * 阈值不引爆。</p>
     */
    @Override
    public void onHit(SubPartDamageEvent.Pre event) {
        if (getDurability() <= 0f) return;
        BFDamageContext ctx = event.getCtx();
        if (Boolean.TRUE.equals(ctx.extensions().get(MMDamageExtensions.COLLISION_IMPACT))) return;
        if (ctx.penetration() < era().getTriggerPenetration()) return;
        setDurability(0f);
    }

    /**
     * 砖拦下的命中按 {@code pass_damage} 决定是否继续传到车体；本方法不扣自身耐久。{@inheritDoc}
     *
     * <p>不调 {@code super.onHurt} 正是"不磨损"的实现：耐久只由 {@link #onHit} 的阈值决定。</p>
     */
    @Override
    public void onHurt(SubPartDamageEvent.Pre event) {
        if (!era().getBasicAttr().passDamage()) event.setCanceled(true);
    }
}
