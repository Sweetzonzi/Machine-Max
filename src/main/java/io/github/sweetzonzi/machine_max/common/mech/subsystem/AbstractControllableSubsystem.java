package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.signal.EmptySignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.MoveInputSignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.RegularInputSignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.ViewInputSignal;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.BasicSubsystemDynamicAttr;
import io.github.sweetzonzi.machine_max.util.data.KeyInputMapping;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

abstract public class AbstractControllableSubsystem extends BasicSubsystem {

    public Map<String, List<String>> moveSignalTargets = new HashMap<>();
    public Map<String, List<String>> viewSignalTargets = new HashMap<>();
    public Map<String, List<String>> regularSignalTargets = new HashMap<>();

    protected AbstractControllableSubsystem(ISubsystemHost owner, String name, BasicSubsystemDynamicAttr attr) {
        super(owner, name, attr);
    }

    public void setUp(Map<String, List<String>> moveSignalTargets, Map<String, List<String>> viewSignalTargets, Map<String, List<String>> regularSignalTargets) {
        this.moveSignalTargets = moveSignalTargets;
        this.viewSignalTargets = viewSignalTargets;
        this.regularSignalTargets = regularSignalTargets;
    }

    public Map<String, List<String>> setUpTargets(Map<String, List<String>> map) {
        map.putAll(moveSignalTargets);
        map.putAll(regularSignalTargets);
        map.putAll(viewSignalTargets);
        return map;
    }

    public void clearInputSignals() {
        for (String signalKey : moveSignalTargets.keySet()) {
            this.sendSignalToAllTargets(signalKey, EmptySignal.INSTANCE);
        }
        for (String signalKey : regularSignalTargets.keySet()) {
            this.sendSignalToAllTargets(signalKey, EmptySignal.INSTANCE);
        }
        for (String signalKey : viewSignalTargets.keySet()) {
            this.sendSignalToAllTargets(signalKey, EmptySignal.INSTANCE);
        }
    }

    public void setMoveInputSignal(byte[] inputs, byte[] conflicts) {
        if (!moveSignalTargets.isEmpty() && this.isActive()) {
            for (String signalKey : moveSignalTargets.keySet()) {
                this.sendSignalToAllTargets(signalKey, new MoveInputSignal(inputs, conflicts));
            }
            this.getOwner().getSubPart().part.vehicle.activate();
        } else {
            for (String signalKey : moveSignalTargets.keySet()) {
                this.sendSignalToAllTargets(signalKey, EmptySignal.INSTANCE);
            }
        }
    }

    public void setRegularInputSignal(KeyInputMapping inputType, int tickCount) {
        if (!regularSignalTargets.isEmpty() && this.isActive()) {
            for (String signalKey : regularSignalTargets.keySet()) {
                this.sendSignalToAllTargets(signalKey, new RegularInputSignal(inputType, tickCount));
            }
            this.getOwner().getSubPart().part.vehicle.activate();
        } else {
            for (String signalKey : regularSignalTargets.keySet()) {
                this.sendSignalToAllTargets(signalKey, EmptySignal.INSTANCE);
            }
        }
    }

    /**
     * 设置视角输入信号（瞄准点世界坐标），发送到所有 viewSignalTargets 频道。
     * 内容包 JSON 中通过信号端口将瞄准输出 (aim_outputs) 连接到武器控制器的 target_inputs。
     *
     * @param aimPoint 玩家瞄准点的世界坐标，null 表示无有效瞄准目标
     */
    public void setViewInputSignal(@Nullable Vec3 aimPoint) {
        if (!viewSignalTargets.isEmpty() && this.isActive()) {
            for (String signalKey : viewSignalTargets.keySet()) {
                this.sendSignalToAllTargets(signalKey,
                        aimPoint != null ? new ViewInputSignal(aimPoint) : EmptySignal.INSTANCE);
            }
            if (aimPoint != null) {
                this.getOwner().getSubPart().part.vehicle.activate();
            }
        } else {
            for (String signalKey : viewSignalTargets.keySet()) {
                this.sendSignalToAllTargets(signalKey, EmptySignal.INSTANCE);
            }
        }
    }
}
