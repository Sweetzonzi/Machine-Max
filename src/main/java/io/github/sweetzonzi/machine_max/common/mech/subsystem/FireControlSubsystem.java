package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import com.jme3.math.Vector3f;

import io.github.sweetzonzi.machine_max.common.mech.signal.EmptySignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalSender;
import io.github.sweetzonzi.machine_max.common.mech.signal.RotationSignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalChannel;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalResult;
import io.github.sweetzonzi.machine_max.common.mech.signal.ViewInputSignal;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.FireControlSubsystemAttr;
import lombok.Getter;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 火控子系统。<br>
 * 接收目标坐标和开火指令，控制炮塔驱动子系统指向目标，
 * 并控制发射器子系统在瞄准完毕后开火。<br>
 * 通过握手（callback）自动发现并绑定同载具内的 TurretDriver 和 Launcher 子系统。
 */
@Getter
public class FireControlSubsystem extends BasicSubsystem {

    public final FireControlSubsystemAttr attr;

    /** 通过握手发现的炮塔驱动子系统及其对应的控制频道名 */
    private final Map<TurretDriverSubsystem, String> turrets = new HashMap<>();
    /** 通过握手发现的发射器子系统及其对应的控制频道名 */
    private final Map<LauncherSubsystem, String> launchers = new HashMap<>();

    /** 缓存的目标世界坐标，每物理tick更新 */
    private Vec3 targetPosition = null;
    /** 当前是否有开火指令 */
    private boolean firing = false;

    /** 轮射模式下当前发射的索引 */
    private int rippleIndex = 0;
    /** 轮射模式下的tick计时器 */
    private int rippleTickCounter = 0;

    public FireControlSubsystem(ISubsystemHost owner, String name, FireControlSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
    }

    @Override
    public void onTick() {
        super.onTick();
        readInputSignals();
    }

    @Override
    public void onPrePhysicsTick() {
        super.onPrePhysicsTick();
        if (!isActive() || isDestroyed() || targetPosition == null) {
            resetSignalOutputs();
            return;
        }

        var staticAttr = attr.staticAttribute;
        float tolDeg = staticAttr.isAimToleranceEnabled() ? staticAttr.getAimToleranceDeg() : Float.MAX_VALUE;

        // ① 向每个炮塔驱动发送目标旋转角度
        //    Send target rotation angles to each turret driver
        for (Map.Entry<TurretDriverSubsystem, String> entry : turrets.entrySet()) {
            TurretDriverSubsystem turret = entry.getKey();
            String channel = entry.getValue();
            if (turret.isDestroyed() || !turret.isActive()) continue;
            Vector3f aimAngles = turret.computeAimAngles(targetPosition);
            sendCallbackToListener(channel, turret, new RotationSignal(aimAngles));
        }

        // ② 筛选瞄准目标的发射器
        //    Filter launchers that are aimed at the target
        List<LauncherSubsystem> aimedLaunchers = new ArrayList<>();
        for (LauncherSubsystem launcher : launchers.keySet()) {
            if (launcher.isDestroyed() || !launcher.isActive()) continue;
            if (launcher.isAimedAt(targetPosition, tolDeg)) {
                aimedLaunchers.add(launcher);
            }
        }

        // ③ 按射击模式开火
        //    Fire according to the configured fire mode
        if (firing && !aimedLaunchers.isEmpty()) {
            switch (staticAttr.getDefaultFireMode()) {
                case SALVO -> fireSalvo(aimedLaunchers);
                case RIPPLE -> fireRipple(aimedLaunchers);
            }
        } else {
            // 无开火指令或无可开火发射器 → 发送空信号停止射击
            //    No fire command or no aimed launchers → send empty signal to stop
            for (Map.Entry<LauncherSubsystem, String> entry : launchers.entrySet()) {
                sendCallbackToListener(entry.getValue(), entry.getKey(), EmptySignal.INSTANCE);
            }
            rippleTickCounter = 0;
            rippleIndex = 0;
        }
    }

    /**
     * 齐射：所有已瞄准的发射器同时开火
     */
    private void fireSalvo(List<LauncherSubsystem> aimedLaunchers) {
        for (LauncherSubsystem launcher : aimedLaunchers) {
            String channel = launchers.get(launcher);
            sendCallbackToListener(channel, launcher, 1.0f);
        }
    }

    /**
     * 轮射：按顺序每次只让一个发射器开火，间隔由 rippleIntervalTick 控制
     */
    private void fireRipple(List<LauncherSubsystem> aimedLaunchers) {
        if (aimedLaunchers.isEmpty()) return;

        int interval = attr.staticAttribute.getRippleIntervalTick();

        if (rippleTickCounter <= 0) {
            // 发送空信号给所有发射器，先停止上轮射击
            //    Stop all launchers first
            for (Map.Entry<LauncherSubsystem, String> entry : launchers.entrySet()) {
                sendCallbackToListener(entry.getValue(), entry.getKey(), EmptySignal.INSTANCE);
            }

            // 只让当前索引的发射器开火
            //    Only fire the launcher at current index
            if (rippleIndex < aimedLaunchers.size()) {
                LauncherSubsystem launcher = aimedLaunchers.get(rippleIndex);
                String channel = launchers.get(launcher);
                sendCallbackToListener(channel, launcher, 1.0f);
            }

            rippleIndex = (rippleIndex + 1) % aimedLaunchers.size();
            rippleTickCounter = interval;
        } else {
            rippleTickCounter--;
        }
    }

    /**
     * 从输入信号频道读取目标坐标和开火指令
     */
    private void readInputSignals() {
        // 读取目标坐标：轮询 targetInputs 频道，取第一个 Vec3
        //    Read target position: poll targetInputs channels, take the first Vec3
        this.targetPosition = null;
        for (String signalKey : attr.staticAttribute.getTargetInputs()) {
            SignalChannel channel = getSignalChannel(signalKey);
            for (Object signal : channel.values()) {
                if (signal instanceof ViewInputSignal vis) {
                    this.targetPosition = vis.value;
                    break;
                } else if (signal instanceof Vec3 vec3) {
                    this.targetPosition = vec3;
                    break;
                } else if (signal instanceof Vector3f jmeVec) {
                    this.targetPosition = new Vec3(jmeVec.x, jmeVec.y, jmeVec.z);
                    break;
                }
            }
            if (this.targetPosition != null) break;
        }

        // 读取开火指令：轮询 fireInputs 频道，任一非EmptySignal即视为开火
        //    Read fire command: poll fireInputs channels, any non-EmptySignal is fire command
        this.firing = false;
        for (String signalKey : attr.staticAttribute.getFireInputs()) {
            SignalChannel channel = getSignalChannel(signalKey);
            if (!channel.isEmpty() && !(channel.getFirstSignal() instanceof EmptySignal)) {
                this.firing = true;
                break;
            }
        }
    }

    @Override
    public void onAttach() {
        super.onAttach();
        handShake();
    }

    @Override
    public void onVehicleStructureChanged() {
        super.onVehicleStructureChanged();
        turrets.clear();
        launchers.clear();
        handShake();
    }

    /**
     * 握手：发送空信号到所有控制输出频道，根据回调发现并绑定下属子系统。<br>
     * Handshake: send empty signals to all control output channels,
     * discover and bind sub-subsystems via callbacks.<br>
     * 回调自动由 ISignalSender.sendSignalToTarget 基础设施处理。
     */
    protected void handShake() {
        for (String signalChannel : attr.controlOutputTargets.keySet()) {
            sendSignalToAllTargetsWithCallback(signalChannel, EmptySignal.INSTANCE, false);
        }
    }

    /**
     * 处理回调信号：<br>
     * - "callback" 频道且值为 String（控制频道名），根据发送者类型存入对应集合<br>
     * - 同载具外的子系统（通过连接点穿透）会被过滤移除
     */
    @Override
    public SignalResult onSignalUpdated(String channelName, ISignalSender sender) {
        super.onSignalUpdated(channelName, sender);
        Object signalValue = getSignalChannel(channelName).get(sender);
        if (channelName.equals("callback") && signalValue instanceof String controlChannel) {
            if (sender instanceof TurretDriverSubsystem turret) {
                // 判断是否在同一载具内（避免跨载具连接）
                //    Check if within the same vehicle
                if (turret.getOwner().getSubPart().getPart().vehicle
                        != this.getOwner().getSubPart().getPart().vehicle) {
                    turrets.remove(turret);
                } else {
                    turrets.put(turret, controlChannel);
                    addCallbackTarget(controlChannel, turret);
                }
            } else if (sender instanceof LauncherSubsystem launcher) {
                if (launcher.getOwner().getSubPart().getPart().vehicle
                        != this.getOwner().getSubPart().getPart().vehicle) {
                    launchers.remove(launcher);
                } else {
                    launchers.put(launcher, controlChannel);
                    addCallbackTarget(controlChannel, launcher);
                }
            }
        }
        return SignalResult.PASS;
    }

    @Override
    public Set<String> getAcceptedChannels() {
        var staticAttr = attr.staticAttribute;
        Set<String> channels = HashSet.newHashSet(staticAttr.getTargetInputs().size() + staticAttr.getFireInputs().size());
        channels.addAll(staticAttr.getTargetInputs());
        channels.addAll(staticAttr.getFireInputs());
        return channels;
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        Map<String, List<String>> result = new HashMap<>(attr.controlOutputTargets);
        return result;
    }
}
