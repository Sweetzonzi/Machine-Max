package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import com.mojang.serialization.Codec;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.control.ControlGroupSet;
import io.github.sweetzonzi.machine_max.common.mech.signal.EmptySignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.MoveInputSignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.RegularInputSignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.ViewInputSignal;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.AbstractSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.BasicSubsystemDynamicAttr;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import io.github.sweetzonzi.machine_max.util.data.KeyInputMapping;
import lombok.Getter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

abstract public class AbstractControllableSubsystem extends BasicSubsystem {
    @Getter
    protected ControlGroupSet controlGroupSet = ControlGroupSet.EMPTY;

    protected AbstractControllableSubsystem(ISubsystemHost owner, String name, BasicSubsystemDynamicAttr attr) {
        super(owner, name, attr);
    }

    /**
     * 设置控制组集合，替代旧的三 Map 初始化方式。
     */
    public void setControlGroupSet(ControlGroupSet cgs) {
        this.controlGroupSet = cgs != null ? cgs : ControlGroupSet.EMPTY;
    }

    public Map<String, List<String>> setUpTargets(Map<String, List<String>> map) {
        map.putAll(controlGroupSet.getMergedMoveTargets());
        map.putAll(controlGroupSet.getMergedRegularTargets());
        map.putAll(controlGroupSet.getMergedViewTargets());
        return map;
    }

    public void clearInputSignals() {
        Map<String, List<String>> all = new HashMap<>();
        all.putAll(controlGroupSet.getMergedMoveTargets());
        all.putAll(controlGroupSet.getMergedRegularTargets());
        all.putAll(controlGroupSet.getMergedViewTargets());
        for (String signalKey : all.keySet()) {
            this.sendSignalToAllTargets(signalKey, EmptySignal.INSTANCE);
        }
    }

    public void setMoveInputSignal(byte[] inputs, byte[] conflicts) {
        Map<String, List<String>> targets = controlGroupSet.getMergedMoveTargets();
        if (!targets.isEmpty() && this.isActive()) {
            for (String signalKey : targets.keySet()) {
                this.sendSignalToAllTargets(signalKey, new MoveInputSignal(inputs, conflicts));
            }
            this.getOwner().getSubPart().part.vehicle.activate();
        } else {
            for (String signalKey : targets.keySet()) {
                this.sendSignalToAllTargets(signalKey, EmptySignal.INSTANCE);
            }
        }
    }

    public void setRegularInputSignal(KeyInputMapping inputType, int tickCount) {
        Map<String, List<String>> targets = controlGroupSet.getMergedRegularTargets();
        if (!targets.isEmpty() && this.isActive()) {
            for (String signalKey : targets.keySet()) {
                this.sendSignalToAllTargets(signalKey, new RegularInputSignal(inputType, tickCount));
            }
            this.getOwner().getSubPart().part.vehicle.activate();
        } else {
            for (String signalKey : targets.keySet()) {
                this.sendSignalToAllTargets(signalKey, EmptySignal.INSTANCE);
            }
        }
    }

    /**
     * 设置视角输入信号（瞄准点世界坐标），发送到当前控制组的 viewTargets 频道。
     *
     * @param aimPoint 玩家瞄准点的世界坐标，null 表示无有效瞄准目标
     */
    public void setViewInputSignal(@Nullable Vec3 aimPoint) {
        Map<String, List<String>> targets = controlGroupSet.getMergedViewTargets();
        if (!targets.isEmpty() && this.isActive()) {
            for (String signalKey : targets.keySet()) {
                this.sendSignalToAllTargets(signalKey,
                        aimPoint != null ? new ViewInputSignal(aimPoint) : EmptySignal.INSTANCE);
            }
            if (aimPoint != null) {
                this.getOwner().getSubPart().part.vehicle.activate();
            }
        } else {
            for (String signalKey : targets.keySet()) {
                this.sendSignalToAllTargets(signalKey, EmptySignal.INSTANCE);
            }
        }
    }

    /**
     * 恢复至 JSON 预设的控制组配置。
     * 玩家编辑后被覆盖时调用此方法重置。
     */
    public void restoreControlGroupPreset() {
        ResourceLocation rl = this.attr.getControlGroupPresetRl();
        if (!rl.equals(AbstractSubsystemAttr.NO_CONTROL_GROUP_PRESET)) {
            ControlGroupSet preset = MMDynamicRes.CONTROL_GROUP_PRESETS.get(rl);
            if (preset != null) {
                setControlGroupSet(preset);
            }
        }
    }

    @Override
    public void loadData(CompoundTag data) {
        super.loadData(data);
        if (data.contains("control_group_set", CompoundTag.TAG_COMPOUND)) {
            ControlGroupSet.CODEC.parse(NbtOps.INSTANCE, data.get("control_group_set"))
                    .resultOrPartial(e -> MachineMax.LOGGER.warn("无法加载控制组数据: {}", e))
                    .ifPresent(cgs -> this.controlGroupSet = cgs);
        }
    }

    @Override
    public CompoundTag saveData(CompoundTag data) {
        super.saveData(data);
        ControlGroupSet.CODEC.encodeStart(NbtOps.INSTANCE, controlGroupSet)
                .resultOrPartial(e -> MachineMax.LOGGER.warn("无法保存控制组数据: {}", e))
                .ifPresent(tag -> data.put("control_group_set", tag));
        return data;
    }
}
