package io.github.sweetzonzi.machine_max.common.vehicle.subsystem;

import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.dynamic_attr.BasicSubsystemDynamicAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.event.subpart.SubPartDamageEvent;
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
        if (getLevel().isClientSide()) {
            Vector3f pos = getSubPart().getPosition();
            getLevel().playLocalSound(
                    pos.x, pos.y, pos.z,
                    attr.staticAttribute.getSoundAttr().onDestroyed(),
                    SoundSource.NEUTRAL,
                    0.5f,
                    1.2f,
                    false);
        }
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        return Map.of(); // 不涉及信号传输，无目标
    }
}