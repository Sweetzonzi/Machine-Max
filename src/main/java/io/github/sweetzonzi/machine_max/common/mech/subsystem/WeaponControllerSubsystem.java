package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import com.jme3.math.Vector3f;

import io.github.sweetzonzi.machine_max.common.mech.signal.EmptySignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalSender;
import io.github.sweetzonzi.machine_max.common.mech.signal.RotationSignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalChannel;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalResult;
import io.github.sweetzonzi.machine_max.common.mech.signal.ViewInputSignal;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.WeaponControllerSubsystemAttr;
import lombok.Getter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 武器控制器子系统。<br>
 * 接收目标坐标和开火指令，控制炮塔驱动子系统指向目标，
 * 并控制发射器子系统在瞄准完毕后开火。<br>
 * 通过握手（callback）自动发现并绑定同载具内的 TurretDriver 和 Launcher 子系统。
 */
@Getter
public class WeaponControllerSubsystem extends BasicSubsystem {

    public final WeaponControllerSubsystemAttr attr;

    /** 通过握手发现的炮塔驱动子系统及其对应的控制频道名 */
    private final Map<TurretDriverSubsystem, String> turrets = new HashMap<>();
    /** 通过握手发现的发射器子系统及其对应的控制频道名 */
    private final Map<LauncherSubsystem, String> launchers = new HashMap<>();

    /** 从 ViewInputSignal 读到的目标世界坐标 */
    private volatile Vec3 targetPosition = null;
    /** 完整的视角输入信号（含分轴稳定/偏移信息） */
    private volatile ViewInputSignal currentViewSignal = null;
    /** 当前是否有开火指令 */
    private volatile boolean firing = false;

    /** 弹药切换信号（来自座座椅透传的按键信号） */
    private volatile boolean ammoSwitchPressed = false;

    /** 弹药切换防抖计数器 */
    private int ammoSwitchCooldown = 0;

    /** 轮射模式下当前发射的索引 */
    private int rippleIndex = 0;
    /** 轮射模式下的tick计时器 */
    private int rippleTickCounter = 0;

    public WeaponControllerSubsystem(ISubsystemHost owner, String name, WeaponControllerSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
    }

    @Override
    public void onTick() {
        super.onTick();
        readInputSignals();

        // 弹药切换防抖递减
        if (ammoSwitchCooldown > 0) ammoSwitchCooldown--;
    }

    @Override
    public void onPrePhysicsTick() {
        super.onPrePhysicsTick();
        // 物理线程开始时读取一次 volatile 字段到局部变量，避免竞态
        ViewInputSignal vis = this.currentViewSignal;
        Vec3 target = this.targetPosition;
        if (!isActive() || isDestroyed() || vis == null || target == null) {
            resetSignalOutputs();
            return;
        }

        var staticAttr = attr.staticAttribute;
        float tolDeg = staticAttr.getAimToleranceDeg();

        // ① 分轴驱动每个炮塔：稳定轴位置控制(aimPoint)，无稳轴增量偏移(turret current + offset)
        for (Map.Entry<TurretDriverSubsystem, String> entry : turrets.entrySet()) {
            TurretDriverSubsystem turret = entry.getKey();
            String channel = entry.getValue();
            if (turret.isDestroyed() || !turret.isActive()) continue;

            Vector3f targetAngle = new Vector3f(turret.getRelativeAngle());
            boolean computed = false;
            Vector3f aimAngles = null;

            // 俯仰轴（x）
            if (vis.pitchStabilized) {
                if (!computed) { aimAngles = turret.computeAimAngles(target); computed = true; }
                targetAngle.x = aimAngles.x;
            } else if (vis.pitchOffsetDeg != 0) {
                targetAngle.x += (float) Math.toRadians(vis.pitchOffsetDeg);
            }
            // 无稳且 offset=0 → targetAngle.x 保持当前角度（炮塔锁死）

            // 偏航轴（y）
            if (vis.yawStabilized) {
                if (!computed) { aimAngles = turret.computeAimAngles(target); computed = true; }
                targetAngle.y = aimAngles.y;
            } else if (vis.yawOffsetDeg != 0) {
                targetAngle.y += (float) Math.toRadians(vis.yawOffsetDeg);
            }
            // 无稳且 offset=0 → targetAngle.y 保持当前角度（炮塔锁死）

            sendCallbackToListener(channel, turret, new RotationSignal(targetAngle));
        }

        // ② 筛选瞄准目标的发射器
        List<LauncherSubsystem> aimedLaunchers = new ArrayList<>();
        for (LauncherSubsystem launcher : launchers.keySet()) {
            if (launcher.isDestroyed() || !launcher.isActive()) continue;
            if (launcher.isAimedAt(target, tolDeg)) {
                aimedLaunchers.add(launcher);
            }
        }

        // ③ 按射击模式开火
        if (firing && !aimedLaunchers.isEmpty()) {
            switch (staticAttr.getDefaultFireMode()) {
                case SALVO -> fireSalvo(aimedLaunchers);
                case RIPPLE -> fireRipple(aimedLaunchers);
            }
        } else {
            // 无开火指令或无可开火发射器 → 发送空信号停止射击
            for (Map.Entry<LauncherSubsystem, String> entry : launchers.entrySet()) {
                sendCallbackToListener(entry.getValue(), entry.getKey(), EmptySignal.INSTANCE);
            }
            rippleTickCounter = 0;
            rippleIndex = 0;
        }

        // ④ 弹药选择：多供给源时响应弹药切换信号
        if (ammoSwitchPressed && ammoSwitchCooldown <= 0) {
            for (LauncherSubsystem launcher : launchers.keySet()) {
                if (launcher.getSuppliers().size() > 1) {
                    int current = launcher.getSuppliers().indexOf(launcher.getCurrentSupplier());
                    int next = (current + 1) % launcher.getSuppliers().size();
                    launcher.setCurrentSupplier(next);
                }
            }
            ammoSwitchPressed = false;
            ammoSwitchCooldown = 10; // 10 tick 防抖
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
            for (Map.Entry<LauncherSubsystem, String> entry : launchers.entrySet()) {
                sendCallbackToListener(entry.getValue(), entry.getKey(), EmptySignal.INSTANCE);
            }

            // 只让当前索引的发射器开火
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
        // 读取目标坐标：优先尝试 ViewInputSignal（完整的分轴信息），否则 fallback 到 Vec3
        ViewInputSignal newVis = null;
        Vec3 pos = null;
        for (String signalKey : attr.staticAttribute.getAimInputs()) {
            SignalChannel channel = getSignalChannel(signalKey);
            Object signal = channel.getFirstSignal();
            if (signal instanceof ViewInputSignal vis) {
                newVis = vis;
                pos = vis.aimPoint;
                break;
            } else if (signal instanceof Vec3 vec3) {
                pos = vec3;
                break;
            } else if (signal instanceof Vector3f jmeVec) {
                pos = new Vec3(jmeVec.x, jmeVec.y, jmeVec.z);
                break;
            }
        }
        this.targetPosition = pos;
        this.currentViewSignal = newVis;

        // 读取开火指令：轮询 fireInputs 频道，任一非EmptySignal即视为开火
        this.firing = false;
        for (String signalKey : attr.staticAttribute.getFireInputs()) {
            SignalChannel channel = getSignalChannel(signalKey);
            if (!channel.isEmpty() && !(channel.getFirstSignal() instanceof EmptySignal)) {
                this.firing = true;
                break;
            }
        }

        // 读取弹药切换指令：轮询 ammo_switch 频道
        this.ammoSwitchPressed = false;
        SignalChannel ammoSwitchChannel = getSignalChannel("ammo_switch");
        if (!ammoSwitchChannel.isEmpty() && !(ammoSwitchChannel.getFirstSignal() instanceof EmptySignal)) {
            this.ammoSwitchPressed = true;
        }
    }

    /**
     * 获取已瞄准目标的发射器数量。
     *
     * @param target 目标世界坐标
     * @param tolDeg 瞄准容差（度）
     * @return 已瞄准且可用的发射器数量
     */
    public int getAimedLauncherCount(Vec3 target, float tolDeg) {
        if (target == null) return 0;
        int count = 0;
        for (LauncherSubsystem launcher : launchers.keySet()) {
            if (!launcher.isDestroyed() && launcher.isActive() && launcher.isAimedAt(target, tolDeg)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void loadData(CompoundTag data) {
        super.loadData(data);
        if (data.contains("target_x") && data.contains("target_y") && data.contains("target_z")) {
            this.targetPosition = new Vec3(
                data.getDouble("target_x"),
                data.getDouble("target_y"),
                data.getDouble("target_z")
            );
        }
    }

    @Override
    public CompoundTag saveData(CompoundTag data) {
        super.saveData(data);
        if (targetPosition != null) {
            data.putDouble("target_x", targetPosition.x);
            data.putDouble("target_y", targetPosition.y);
            data.putDouble("target_z", targetPosition.z);
        }
        return data;
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
                if (turret.getOwner().getSubPart().getPart().assembly
                        != this.getOwner().getSubPart().getPart().assembly) {
                    turrets.remove(turret);
                } else {
                    turrets.put(turret, controlChannel);
                    addCallbackTarget(controlChannel, turret);
                }
            } else if (sender instanceof LauncherSubsystem launcher) {
                if (launcher.getOwner().getSubPart().getPart().assembly
                        != this.getOwner().getSubPart().getPart().assembly) {
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
    public List<String> getAcceptedChannels() {
        var staticAttr = attr.staticAttribute;
        List<String> channels = new ArrayList<>(staticAttr.getAimInputs().size() + staticAttr.getFireInputs().size() + 1);
        channels.addAll(staticAttr.getAimInputs());
        channels.addAll(staticAttr.getFireInputs());
        channels.add("ammo_switch");
        return channels;
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        Map<String, List<String>> result = new HashMap<>(attr.controlOutputTargets);
        return result;
    }
}
