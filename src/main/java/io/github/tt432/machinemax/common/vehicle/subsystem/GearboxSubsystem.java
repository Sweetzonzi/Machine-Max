package io.github.tt432.machinemax.common.vehicle.subsystem;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.entity.MMPartEntity;
import io.github.tt432.machinemax.common.vehicle.ISubsystemHost;
import io.github.tt432.machinemax.common.vehicle.attr.subsystem.GearboxSubsystemAttr;
import io.github.tt432.machinemax.common.vehicle.signal.*;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.*;

@Getter
public class GearboxSubsystem extends AbstractSubsystem implements ISignalReceiver, ISignalSender {
    public final GearboxSubsystemAttr attr;
    public final double[] gearRatios;//各级实际传动比率 Actual transmission ratio of each gear
    public final int minPositiveGear;
    public final int minNegativeGear;
    public final List<String> gearNames;//各级挡位的名称 Names of each gear position
    private int currentGear = 1; //当前挡位 Current gear position
    @Setter
    private boolean clutched = true;//离合状态，true为正常传动，false为不传动 Clutch status, true for normal transmission, false for no transmission
    private float remainingSwitchTime = 0.0f;//剩余换挡无动力时间 Remaining no-power time caused by switching gears

    public GearboxSubsystem(ISubsystemHost owner, String name, GearboxSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        this.gearRatios = attr.ratios.stream().mapToDouble(Float::floatValue).map(r -> r * attr.finalRatio).toArray();
        gearNames = generateGears(this.gearRatios);
        switchGear(0);
        int tempMinPositiveGear = this.gearRatios.length - 1;
        int tempMinNegativeGear = 0;
        for (int i = 0; i < this.gearRatios.length; i++) {
            if (this.gearRatios[i] > 0 && i < tempMinPositiveGear) {
                tempMinPositiveGear = i;
                break;
            }
            if (this.gearRatios[i] < 0 && i > tempMinNegativeGear) {
                tempMinNegativeGear = i;
            }
        }
        minPositiveGear = tempMinPositiveGear;
        minNegativeGear = tempMinNegativeGear;
    }

    @Override
    public void onTick() {
        super.onTick();
        if (getPart().level.isClientSide) {
            if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.getVehicle() instanceof MMPartEntity) {
                String gear = gearNames.get(currentGear);
                if (!clutched || remainingSwitchTime > 0.0f) gear = "N";
                Object engineSpeed = getPart().vehicle.subSystemController.getSignals("engine_speed").getFirst();
                float engineRPM = engineSpeed instanceof Float f ? (float) (f / Math.PI * 30f) : 0.0f;
                Minecraft.getInstance().player.displayClientMessage(Component.empty().append("Gear: " + gear + " RPM:" + engineRPM).withColor(engineRPM > 6500 ? 0xff0000 : 0xffffff), true);
            }
        }
    }

    @Override
    public void onPrePhysicsTick() {
        distributePower();
        if (remainingSwitchTime > 0.0f) remainingSwitchTime -= 1 / 60.0f;
    }

    @Override
    public void onPostPhysicsTick() {
        updateFeedback();//更新反馈信号
    }

    public void switchGear(int gear) {
        if (currentGear == gear) return;//当前挡位与目标挡位相同，无需切换
        if (gear >= 0 && gear < gearRatios.length) {//目标挡位有效
            this.currentGear = gear;//更新当前挡位
            if (clutched) this.remainingSwitchTime = attr.switchTime;//若未踩离合，开始换挡时间倒计时
            //更新挡位信号
            for (Map.Entry<String, List<String>> entry : attr.gearOutputTargets.entrySet()) {
                String signalKey = entry.getKey();
                List<String> targets = entry.getValue();
                for (String targetName : targets) {
                    sendSignalToTarget(targetName, signalKey, currentGear);
                }
            }
        }
    }

    public void upShift() {
        if (currentGear < gearRatios.length - 1) {
            switchGear(currentGear + 1);
        }
    }

    public void downShift() {
        if (currentGear > 0) {
            switchGear(currentGear - 1);
        }
    }

    private void distributePower() {
        if (!clutched || remainingSwitchTime > 0.0f) {
            sendSignalToTarget(attr.powerOutputTarget, "power", new MechPowerSignal(0.0f, 0f));
            return;
        }
        double totalPower = 0.0;
        double averageSpeed = 0.0;
        int count = 0;
        Signals powerSignal = getSignals("power");
        for (Map.Entry<ISignalSender, Object> entry : powerSignal.entrySet()) {
            if (entry.getValue() instanceof MechPowerSignal power) {
                totalPower += power.getPower();//计算收到的总功率
                averageSpeed += power.getSpeed();
                count++;
                ISignalSender sender = entry.getKey();
                if (sender instanceof ISignalReceiver receiver) {//当发送者同时也是接收者时，自动反馈速度到发送者
                    addCallbackTarget("speed_feedback", receiver);
                }
            }
        }
        if (count > 0) averageSpeed /= count;//计算转速平均值
        MechPowerSignal powerSignalToSend = new MechPowerSignal((float) totalPower, (float) (averageSpeed / gearRatios[currentGear]));
        sendSignalToTarget(attr.powerOutputTarget, "power", powerSignalToSend);//发送功率信号
    }

    private void updateFeedback() {
        if (!clutched || remainingSwitchTime > 0.0f) {//空挡时或正在换挡时，不反馈速度信号
            sendCallbackToAllListeners("speed_feedback", new EmptySignal());
            return;
        }
        float speed;
        Signals speedSignal = getSignals("speed_feedback");
        if (!speedSignal.values().isEmpty()) {
            for (Object value : speedSignal.values()) {
                if (value instanceof Float f) {
                    speed = f;
                    sendCallbackToAllListeners("speed_feedback", (float) (speed * gearRatios[currentGear]));
                    return;
                }
            }
            sendCallbackToAllListeners("speed_feedback", new EmptySignal());//没有收到反馈速度信号时，发送空信号
        }
    }

    /**
     * 在此填入各个信号名对应的接收者名列表，用于自动组织信号传输关系。<p>
     * Return a map of signal names to a list of receiver names here, to automatically organize signal transfer.
     *
     * @return 信号名称->接收者名称列表 Map of signal names to a list of receiver names.
     */
    @Override
    public Map<String, List<String>> getTargetNames() {
        Map<String, List<String>> result = new HashMap<>(attr.gearOutputTargets);
        result.put("power", List.of(attr.powerOutputTarget));
        return result;
    }

    private static List<String> generateGears(double[] ratios) {
        // 初始化一个空的字符串列表来存储生成的挡位 Initialize an empty string list to store the generated gears
        List<String> gears = new ArrayList<>();
        // 初始化计数器，用于统计负数、零和正数的个数 Initialize counters to count the number of negative, zero, and positive numbers
        int negativeCount = 0;
        int zeroCount = 0;
        int positiveCount = 0;
        // 遍历比率数组，统计每个比率的符号类型 Traverse the ratios array to count the sign type of each ratio
        for (double r : ratios) {
            if (r < 0) negativeCount++; // 如果比率是负数，负数计数器加一 If the ratio is negative, increment the negative counter
            else if (r == 0) zeroCount++; // 如果比率是零，零计数器加一 If the ratio is zero, increment the zero counter
            else positiveCount++; // 如果比率是正数，正数计数器加一 If the ratio is positive, increment the positive counter
        }
        // 根据统计的负数个数生成相应的挡位标识符 Generate the corresponding gear identifiers based on the counted negative numbers
        for (int i = negativeCount; i > 0; i--) gears.add("R" + i);
        // 根据统计的零个数生成相应的挡位标识符 Generate the corresponding gear identifiers based on the counted zero numbers
        for (int i = 0; i < zeroCount; i++) gears.add("N");
        // 根据统计的正数个数生成相应的挡位标识符 Generate the corresponding gear identifiers based on the counted positive numbers
        for (int i = 1; i <= positiveCount; i++) gears.add(String.valueOf(i));
        return gears;
    }

}
