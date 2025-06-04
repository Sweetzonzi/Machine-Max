package io.github.sweetzonzi.machinemax.common.vehicle.subsystem;

import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machinemax.common.vehicle.attr.subsystem.TransmissionSubsystemAttr;
import io.github.sweetzonzi.machinemax.common.vehicle.signal.*;
import lombok.Getter;

import java.util.*;

@Getter
public class TransmissionSubsystem extends AbstractSubsystem {
    public final TransmissionSubsystemAttr attr;
    private final Map<ISignalReceiver, Float> powerReceivers = new HashMap<>();//功率接收者及其反馈转速列表
    private final Map<ISignalReceiver, Float> powerReceiverGearRatios = new HashMap<>();//功率输出目标及其信号通道列表
    private final Map<ISignalReceiver, Float> powerReceiverWeights = new HashMap<>();//差速锁启用时的功率输出目标及其功率分配权重

    private boolean diffLock = false;
    private float avgFeedBackSpeed = 0f;

    public TransmissionSubsystem(ISubsystemHost owner, String name, TransmissionSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        if (attr.diffLock == TransmissionSubsystemAttr.diffLockMode.TRUE) diffLock = true;
    }

    @Override
    public void onPrePhysicsTick() {
        super.onPrePhysicsTick();
        //更新信号输出目标的平均反馈速度 Update the average feedback speed of the output targets
        avgFeedBackSpeed = 0f;
        for (float speed : powerReceivers.values()) {
            avgFeedBackSpeed -= speed;
        }
        if (!powerReceivers.isEmpty()) avgFeedBackSpeed /= powerReceivers.size();
        //差速锁控制 Differential Lock Control
        if (attr.diffLock == TransmissionSubsystemAttr.diffLockMode.AUTO) {
            //视情况更新自动差速锁状态 Update differential lock status automatically
            diffLock = false;
            for (float speed : powerReceivers.values()) {
                if (Math.abs((avgFeedBackSpeed + speed) / avgFeedBackSpeed) > attr.autoDiffLockThreshold / 100f) {
                    diffLock = true;
                    break;
                }
            }
        } else if (attr.diffLock == TransmissionSubsystemAttr.diffLockMode.MANUAL) {
            //根据信号进行的手动差速锁控制 Manually control the differential lock according to the input signals
            Boolean targetDiffLockStatus = null;
            if (!attr.manualDiffLockInputChannels.isEmpty()) {
                for (String diffLockChannelName : attr.manualDiffLockInputChannels) {
                    SignalChannel diffLockChannel = getSignalChannel(diffLockChannelName);
                    for (Object signalValue : diffLockChannel.values()) {
                        if (signalValue instanceof Boolean b) {
                            targetDiffLockStatus = b;
                            break;
                        }
                    }
                    if (targetDiffLockStatus != null) break;
                }
            }
            if (targetDiffLockStatus != null) diffLock = targetDiffLockStatus;//更新差速锁状态 Update differential lock status
        }
        //分配功率到所有已连接的功率传输目标 Distribute power to all connected power transfer targets
        distributePower();
    }

    @Override
    public void onPostPhysicsTick() {
        super.onPostPhysicsTick();
        updateFeedback();
    }

    /**
     * <p>初始化功率信号并发送握手功率信号到输出目标</p>
     * <p>Initialize power signals and send handshaking power signals to output targets.</p>
     */
    @Override
    public void onVehicleStructureChanged() {
        super.onVehicleStructureChanged();
        powerReceivers.clear();//清空功率接收者列表
        powerReceiverGearRatios.clear();//清空功率输出通道列表
        //初始化握手功率信号
        for (Map.Entry<String, Float> entry : attr.powerOutputs.entrySet()) {
            String targetName = entry.getKey();
            float gearRatio = entry.getValue();
            sendSignalToTargetWithCallback("power", targetName, gearRatio, true);//发送对应的减速比等待回传
            MechPowerSignal powerSignalToSend = new MechPowerSignal(0f, 0f);
            sendSignalToTarget("power", targetName, powerSignalToSend);//发送空功率信号
        }
    }

    /**
     * <p>接收功率接收者的速度反馈信号并更新反馈速度</p>
     * <p>Receive the speed feedback signal from the power receivers and update the feedback speed.</p>
     */
    public void onSignalUpdated(String channelName, ISignalSender sender) {
        super.onSignalUpdated(channelName, sender);
        Object signalValue = getSignalChannel(channelName).get(sender);
        if (channelName.equals("speed_feedback") && signalValue instanceof Float speedFeedback) {
            if (sender instanceof ISignalReceiver receiver) {
                if (powerReceiverGearRatios.get(receiver) != null)//存在减速比时视为一般反馈
                    powerReceivers.put(receiver, speedFeedback * powerReceiverGearRatios.get(receiver));//更新功率接收者的速度反馈信号
                else powerReceivers.put(receiver, 0f);//否则视为握手尝试
            }
        } else if (channelName.equals("callback") && signalValue instanceof Float gearRatio) {
            powerReceiverGearRatios.put((ISignalReceiver) sender, gearRatio);
        }
    }

    private void distributePower() {
        //接收功率并合并 Merge power from all sources
        double totalPower = 0.0;
        float inputSpeed = 0.0F;
        SignalChannel powerSignal = getSignalChannel("power");
        for (Map.Entry<ISignalSender, Object> entry : powerSignal.entrySet()) {
            if (entry.getValue() instanceof MechPowerSignal power) {
                totalPower += Math.signum(power.getSpeed()) * power.getPower();//计算收到的总功率
                inputSpeed += power.getSpeed();
                ISignalSender sender = entry.getKey();
                if (sender instanceof ISignalReceiver receiver) {//当发送者同时也是接收者时，自动反馈速度到发送者
                    addCallbackTarget("speed_feedback", receiver);
                }
            }
        }
        totalPower *= Math.signum(inputSpeed);//根据速度方向调整总功率正负
        //计算并分配输出功率 Calculate and distribute output power
        if (powerReceivers.isEmpty()) return; //无输出目标则不发出功率信号
        if (diffLock) {//差速锁模式，限制输出端转速相等
            //TODO:无功率输入时，单个输出端带动其他输出端旋转
            float totalWeight = 0f;
            float weight;
            float speed;
            powerReceiverWeights.clear();
            //根据转速与输入转速的差距决定功率分配权重
            for (Map.Entry<ISignalReceiver, Float> target : powerReceivers.entrySet()) {
                ISignalReceiver receiver = target.getKey();
                speed = -target.getValue();
                if (Math.signum(speed * inputSpeed) >= 0) {//同向旋转正常分配功率
                    weight = 1f;
                } else {//与动力源旋转方向不同则额外分配功率
                    weight = -3f;
                }
                weight /= (float) Math.pow(Math.max(1f, Math.abs(speed) + 1f), attr.diffLockSensitivity);
                powerReceiverWeights.put(receiver, weight);
                totalWeight += Math.abs(weight);
            }
            for (Map.Entry<ISignalReceiver, Float> target : powerReceivers.entrySet()) {
                ISignalReceiver receiver = target.getKey();
                Float receiverGearRatio = powerReceiverGearRatios.get(receiver);
                if (receiverGearRatio == null || receiverGearRatio == 0f) continue;//跳过减速比非法的功率输出目标
                weight = powerReceiverWeights.get(receiver);
                float power = (float) (totalPower * weight / totalWeight);
                speed = -0.995f * powerReceivers.get(receiver) + 0.005f * inputSpeed;
                if (speed == 0f) speed = 0.005f * inputSpeed;
                MechPowerSignal powerSignalToSend = new MechPowerSignal(power, speed / receiverGearRatio);
                sendCallbackToListener("power", receiver, powerSignalToSend);//发送功率信号
            }
        } else {//差速器模式，不限制输出端转速，各输出端扭矩相等
            //TODO:无功率输入时，一边带动另一边旋转？
            if (totalPower == 0f) return;//如果没有收到功率信号，则不发出功率信号
            float receiverTotalSpeed = 0.0F;//统计接收者的总速度用于扭矩计算进而计算输出功率
            for (Map.Entry<ISignalReceiver, Float> target : powerReceivers.entrySet()) {
                receiverTotalSpeed -= target.getValue();
            }
            receiverTotalSpeed += 0.005f * inputSpeed;
            if (receiverTotalSpeed == 0f) receiverTotalSpeed = 0.0005f * inputSpeed;//避免反推出的功率过大
            float torque = (float) (totalPower / receiverTotalSpeed);//计算输出端扭矩
            for (Map.Entry<ISignalReceiver, Float> target : powerReceivers.entrySet()) {
                ISignalReceiver receiver = target.getKey();
                Float receiverGearRatio = powerReceiverGearRatios.get(receiver);
                if (receiverGearRatio == null || receiverGearRatio == 0f) continue;//跳过减速比非法的功率输出目标
                float receiverSpeed = -0.995f * target.getValue() + 0.005f * inputSpeed;//避免接收者转速为0时无法起步
                if (receiverSpeed == 0f) receiverSpeed = 0.005f * inputSpeed;
                float power = torque * receiverSpeed;
                MechPowerSignal powerSignalToSend = new MechPowerSignal(power, receiverSpeed / receiverGearRatio);
                sendCallbackToListener("power", receiver, powerSignalToSend);//发送功率信号
            }
        }
    }

    /**
     * <p>为提供功率输入的动力源更新反馈速度</p>
     * <p>Update the feedback speed for the power sources.</p>
     */
    private void updateFeedback() {
        float speed = 0;
        int count = 0;
        if (!powerReceivers.isEmpty()) {
            for (Object value : powerReceivers.values()) {
                if (value instanceof Float f) {
                    speed += f;
                    count++;
                }
            }
            if (count > 0) speed /= count;
            sendCallbackToAllListeners("speed_feedback", speed);
        } else
            sendCallbackToAllListeners("speed_feedback", new EmptySignal());
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        Map<String, List<String>> targetNames = new HashMap<>(4);
        //添加功率输出目标
        for (String targetName : attr.powerOutputs.keySet()) {
            String signalChannel = "power";
            targetNames.computeIfAbsent(signalChannel, k -> new ArrayList<>()).add(targetName);
        }
        return targetNames;
    }
}
