package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import com.mojang.serialization.Codec;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.control.ControlBinding;
import io.github.sweetzonzi.machine_max.common.mech.control.ControlGroupSet;
import io.github.sweetzonzi.machine_max.common.mech.signal.EmptySignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalSender;
import io.github.sweetzonzi.machine_max.common.mech.signal.MoveInputSignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.RegularInputSignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalResult;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

abstract public class AbstractControllableSubsystem extends BasicSubsystem {
    @Getter
    protected ControlGroupSet controlGroupSet = ControlGroupSet.EMPTY;

    protected AbstractControllableSubsystem(ISubsystemHost owner, String name, BasicSubsystemDynamicAttr attr) {
        super(owner, name, attr);
    }

    // ===== 摄像机发现 =====

    /** 握手发现的摄像机列表（线程安全） */
    protected final List<CameraSubsystem> discoveredCameras = new CopyOnWriteArrayList<>();

    /** 返回握手发现的摄像机列表 */
    public List<CameraSubsystem> getDiscoveredCameras() {
        return discoveredCameras;
    }

    /**
     * 摄像机发现握手配置 {频道名 → [目标接收者名列表]}。
     * 子类（SeatSubsystem 等）在构造函数中从动态属性赋值。
     */
    protected Map<String, List<String>> cameraDiscoveryTargets = Map.of();

    /**
     * 摄像机发现握手，向 cameraDiscoveryTargets 配置的每个频道发送空信号（带回调），
     * CameraSubsystem 收到后通过 callback 回复，座椅在 onSignalUpdated 中缓存。
     */
    protected void cameraDiscoveryHandshake() {
        for (String signalChannel : cameraDiscoveryTargets.keySet()) {
            sendSignalToAllTargetsWithCallback(signalChannel, EmptySignal.INSTANCE, false);
        }
    }

    @Override
    public void onAttach() {
        super.onAttach();
        cameraDiscoveryHandshake();
    }

    /**
     * 设置控制组集合，替代旧的三 Map 初始化方式。
     */
    public void setControlGroupSet(ControlGroupSet cgs) {
        this.controlGroupSet = cgs != null ? cgs : ControlGroupSet.EMPTY;
    }

    /**
     * 收集所有控制组和 GUI 元素的全部目标名称并集（baseGroup + 全部子组 + 全部绑定 + GUI 动作）。
     * 用于注册和显示所有可能的目标频道与接收者，实际发送信号时应使用 getMerged*Targets() 按激活态发送。
     */
    public Map<String, List<String>> setUpTargets(Map<String, List<String>> map) {
        map.putAll(controlGroupSet.getAllMoveTargets());
        map.putAll(controlGroupSet.getAllRegularTargets());
        map.putAll(controlGroupSet.getAllViewTargets());
        map.putAll(controlGroupSet.getAllBindingTargets());
        map.putAll(controlGroupSet.getAllGuiActionTargets());
        map.putAll(cameraDiscoveryTargets);
        return map;
    }

    @Override
    public SignalResult onSignalUpdated(String channelName, ISignalSender sender) {
        super.onSignalUpdated(channelName, sender);

        if ("callback".equals(channelName) && sender instanceof CameraSubsystem camera) {
            if (camera.getOwner().getSubPart().getPart().assembly
                    == this.getOwner().getSubPart().getPart().assembly) {
                if (!discoveredCameras.contains(camera)) {
                    discoveredCameras.add(camera);
                }
            }
        }
        return SignalResult.PASS;
    }

    @Override
    public List<String> getAcceptedChannels() {
        List<String> channels = new ArrayList<>();
        channels.add("callback");
        return channels;
    }

    @Override
    public void onVehicleStructureChanged() {
        super.onVehicleStructureChanged();
        discoveredCameras.clear();
        cameraDiscoveryHandshake();
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
            this.getOwner().getSubPart().part.assembly.activatePhysics();
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
            this.getOwner().getSubPart().part.assembly.activatePhysics();
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
                this.getOwner().getSubPart().part.assembly.activatePhysics();
            }
        } else {
            for (String signalKey : targets.keySet()) {
                this.sendSignalToAllTargets(signalKey, EmptySignal.INSTANCE);
            }
        }
    }

    /**
     * 处理来自 ControlBinding 按键事件的信号发送。
     * 根据 BindingAction 类型（PRESS/HOLD/TOGGLE）和按键事件类型（按下/松开），
     * 将信号值发送到 binding 定义的目标频道与接收者。
     *
     * @param bindingIndex 合并绑定列表中的索引
     * @param eventType    0=按下, 1=松开
     */
    public void sendBindingSignal(int bindingIndex, int eventType) {
        ControlBinding binding = controlGroupSet.findBindingByIndex(bindingIndex);
        if (binding == null || !this.isActive()) return;

        Object value = switch (binding.action) {
            case PRESS  -> (eventType == 0) ? 1.0f : null;
            case HOLD   -> (eventType == 0) ? 1.0f : EmptySignal.INSTANCE;
            case TOGGLE -> {
                if (eventType != 0) yield null;
                yield binding.flipToggleState() ? 1.0f : 0.0f;
            }
        };

        if (value != null) {
            for (String targetName : binding.targets) {
                this.sendSignalToTarget(binding.channel, targetName, value);
            }
            this.getOwner().getSubPart().part.assembly.activatePhysics();
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
