package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import cn.solarmoon.spark_core.sound.SpreadingSoundHelper;
import cn.solarmoon.spark_core.util.PPhase;
import cn.solarmoon.spark_core.util.SparkMathKt;
import cn.solarmoon.spark_core.util.TaskSubmitOffice;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.BasicSubsystemDynamicAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.event.subpart.SubPartDamageEvent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.List;
import java.util.Map;

public class BasicSubsystem extends AbstractSubsystem {
    public final BasicSubsystemDynamicAttr attr;

    public BasicSubsystem(ISubsystemHost owner, String name, BasicSubsystemDynamicAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
    }

    @Override
    public void onHurt(SubPartDamageEvent.Pre event) {
        if (attr.getStaticAttribute().getBasicAttr().passDamage()) {
            if (event.getDamageAmount() > getDurability() && attr.getStaticAttribute().getBasicAttr().limitDamage())
                event.setDamageAmount(getDurability());
        } else {
            event.setCanceled(true);
        }
        super.onHurt(event); // 对子系统造成伤害
    }

    @Override
    public void onDestroyed() {
        super.onDestroyed();
        playLifecycleSound(attr.getStaticAttribute().getSoundAttr().onDestroyed());
    }

    @Override
    public void onActive() {
        super.onActive();
        playLifecycleSound(attr.getStaticAttribute().getSoundAttr().onActivated());
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        playLifecycleSound(attr.getStaticAttribute().getSoundAttr().onDeactivated());
    }

    protected void playLifecycleSound(SoundEvent soundEvent) {
        if (!getLevel().isClientSide()) return;
        ((TaskSubmitOffice) getLevel()).submitImmediateTask(
                PPhase.ALL,
                () -> {
                    SpreadingSoundHelper.playSpreadingSound(
                            getLevel(),
                            soundEvent,
                            SoundSource.NEUTRAL,
                            SparkMathKt.toVec3(getSubPart().getPosition()),
                            SparkMathKt.toVec3(getSubPart().getLinearVelocity()),
                            (float) 1.0,
                            (float) 1.0
                    );
                    return null;
                }
        );
    }

    public boolean isHidden() {
        return attr.isHidden();
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        return Map.of(); // 不涉及信号传输，无目标
    }
}
