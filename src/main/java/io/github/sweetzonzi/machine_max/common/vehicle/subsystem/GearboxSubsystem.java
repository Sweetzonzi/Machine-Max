package io.github.sweetzonzi.machine_max.common.vehicle.subsystem;

import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.util.PPhase;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.dynamic_attr.GearboxSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.signal.*;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class GearboxSubsystem extends AbstractSubsystem {
    public final GearboxSubsystemAttr attr;
    public final double[] gearRatios;//各级实际传动比率 Actual transmission ratio of each gear
    public final int minPositiveGear;
    public final int minNegativeGear;
    public final List<String> gearNames;//各级挡位的名称 Names of each gear position
    protected static final EntityDataAccessor<Integer> CURRENT_GEAR_ID = SynchedEntityData.defineId(GearboxSubsystem.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Boolean> CLUTCHED_ID = SynchedEntityData.defineId(GearboxSubsystem.class, EntityDataSerializers.BOOLEAN);
    private int lastGear = 0;//上一次的挡位，仅用于客户端控制音效 Last gear position, only used for client control of sound effects
    private float remainingSwitchTime = 0.0f;//剩余换挡无动力时间 Remaining no-power time caused by switching gears

    public GearboxSubsystem(ISubsystemHost owner, String name, GearboxSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        this.gearRatios = attr.staticAttribute.ratios.stream().mapToDouble(Float::floatValue).map(r -> r * attr.staticAttribute.finalRatio).toArray();
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
        setCurrentGear(minPositiveGear);
    }

    @Override
    public void onTick() {
        super.onTick();
        String gear = this.gearNames.get(this.getCurrentGear());
        if (!this.isClutched() || this.getRemainingSwitchTime() > 0.0f) gear = "N";
        for (Map.Entry<String, List<String>> entry : attr.gearOutputTargets.entrySet()) {
            String signalChannel = entry.getKey();
            List<String> targets = entry.getValue();
            for (String targetName : targets) sendSignalToTarget(signalChannel, targetName, gear);
        }
    }

    @Override
    public void onPrePhysicsTick() {
        distributePower();
        if (remainingSwitchTime > 0.0f) {
            remainingSwitchTime -= 1 / 60.0f;
            if (remainingSwitchTime <= 0.0f) {
                if (!isClutched()) setClutched(true);
            }
        }
    }

    @Override
    public void onPostPhysicsTick() {
        super.onPostPhysicsTick();
        updateFeedback();//更新反馈信号
    }

    @Override
    public void onAttach() {
        super.onAttach();
        sendSignalToTarget("power", attr.getPowerOutputTarget(), MechPowerSignal.ZERO);
    }

    @Override
    public void onVehicleStructureChanged() {
        super.onVehicleStructureChanged();
        sendSignalToTarget("power", attr.getPowerOutputTarget(), MechPowerSignal.ZERO);
    }

    @Override
    public void onSignalUpdated(String channelName, ISignalSender sender) {
        super.onSignalUpdated(channelName, sender);
        SignalChannel channel = getSignalChannel(channelName);
        for (Map.Entry<ISignalSender, Object> entry : channel.entrySet()) {
            if (entry.getValue() instanceof MechPowerSignal) {
                if (sender instanceof ISignalReceiver receiver) {//当发送者同时也是接收者时，自动反馈速度到发送者
                    addCallbackTarget("speed_feedback", receiver);
                }
            }
        }
    }

    public void switchGear(int gear) {
        if (getCurrentGear() == gear) return;//当前挡位与目标挡位相同，无需切换
        if (gear >= 0 && gear < gearRatios.length) {//目标挡位有效
            if (!getLevel().isClientSide()) { // 换挡逻辑
                if (isClutched()) {
                    this.remainingSwitchTime = attr.staticAttribute.switchTime;//若未踩离合，开始换挡时间倒计时
                    setClutched(false);
                }
                setCurrentGear(gear);//更新当前挡位
                //更新挡位信号
                for (Map.Entry<String, List<String>> entry : attr.gearOutputTargets.entrySet()) {
                    String signalChannel = entry.getKey();
                    List<String> targets = entry.getValue();
                    for (String targetName : targets) {
                        sendSignalToTarget(signalChannel, targetName, gear);
                    }
                }
            }
        }
    }

    public void upShift() {
        if (getCurrentGear() < gearRatios.length - 1) {
            switchGear(getCurrentGear() + 1);
        }
    }

    public void downShift() {
        if (getCurrentGear() > 0) {
            switchGear(getCurrentGear() - 1);
        }
    }

    public void setClutched(boolean clutched) {
        this.getSynchedData().set(CLUTCHED_ID, clutched);
    }

    private void distributePower() {
        if (!isClutched() || remainingSwitchTime > 0.0f) {
            sendSignalToTarget("power", attr.powerOutputTarget, MechPowerSignal.ZERO);
            return;
        }
        double totalPower = 0.0;
        double averageSpeed = 0.0;
        int count = 0;
        SignalChannel powerSignal = getSignalChannel("power");
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
        MechPowerSignal powerSignalToSend = new MechPowerSignal((float) totalPower, (float) (averageSpeed / gearRatios[getCurrentGear()]));
        if (isActive()) sendSignalToTarget("power", attr.powerOutputTarget, powerSignalToSend);//发送功率信号
        else resetSignalOutputs();
    }

    private void updateFeedback() {
        if (!isClutched() || remainingSwitchTime > 0.0f) {//空挡时或正在换挡时，不反馈速度信号
            sendCallbackToAllListeners("speed_feedback", EmptySignal.INSTANCE);
            return;
        }
        float speed;
        SignalChannel speedSignal = getSignalChannel("speed_feedback");
        if (!speedSignal.isEmpty()) {
            for (Object value : speedSignal.values()) {
                if (value instanceof Float f) {
                    speed = f;//发送第一个反馈转速 TODO:发送平均值？
                    sendCallbackToAllListeners("speed_feedback", (float) (speed * gearRatios[getCurrentGear()]));
                    return;
                }
            }
            sendCallbackToAllListeners("speed_feedback", EmptySignal.INSTANCE);//没有收到反馈速度信号时，发送空信号
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CURRENT_GEAR_ID, 1);
        builder.define(CLUTCHED_ID, true);
    }

    /**
     * 在离合或换挡状态发生变化时播放相应音效
     *
     * @param dataAccessor 变化的数据
     */
    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> dataAccessor) {
        super.onSyncedDataUpdated(dataAccessor);
        if (!getLevel().isClientSide()) return;
        Vector3f pos = getSubPart().getPosition();
        if (dataAccessor == CURRENT_GEAR_ID) {
            if (getCurrentGear() > lastGear) {
                SparkLevel.submitDeduplicatedTask(getLevel(), getSubPart().getId() + "_" + this.name + "_gear_up", PPhase.ALL, () -> {
                            getLevel().playLocalSound(
                                    pos.x, pos.y, pos.z,
                                    attr.staticAttribute.getGearUpSound(),
                                    SoundSource.NEUTRAL,
                                    0.5f,
                                    1.2f,
                                    false
                            );
                        }
                );
            } else {
                SparkLevel.submitDeduplicatedTask(getLevel(), getSubPart().getId() + "_" + this.name + "_gear_down", PPhase.ALL, () -> {
                            getLevel().playLocalSound(
                                    pos.x, pos.y, pos.z,
                                    attr.staticAttribute.getGearDownSound(),
                                    SoundSource.NEUTRAL,
                                    0.5f,
                                    1.2f,
                                    false
                            );
                        }
                );
            }
            lastGear = getCurrentGear();
        } else if (dataAccessor == CLUTCHED_ID) {
            if (isClutched()) {
                SparkLevel.submitDeduplicatedTask(getLevel(), getSubPart().getId() + "_" + this.name + "_clutch_in", PPhase.ALL, () -> {
                            getLevel().playLocalSound(
                                    pos.x, pos.y, pos.z,
                                    attr.staticAttribute.getClutchInSound(),
                                    SoundSource.NEUTRAL,
                                    0.5f,
                                    1.2f,
                                    false
                            );
                        }
                );
            } else {
                SparkLevel.submitDeduplicatedTask(getLevel(), getSubPart().getId() + "_" + this.name + "_clutch_in", PPhase.ALL, () -> {
                            getLevel().playLocalSound(
                                    pos.x, pos.y, pos.z,
                                    attr.staticAttribute.getClutchOutSound(),
                                    SoundSource.NEUTRAL,
                                    0.5f,
                                    1.2f,
                                    false
                            );
                        }
                );
            }
        }
    }

    public int getCurrentGear() {
        return getSynchedData().get(CURRENT_GEAR_ID);
    }

    public void setCurrentGear(int currentGear) {
        getSynchedData().set(CURRENT_GEAR_ID, currentGear);
    }

    public boolean isClutched() {
        return getSynchedData().get(CLUTCHED_ID);
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
