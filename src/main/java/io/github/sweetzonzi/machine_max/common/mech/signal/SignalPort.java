package io.github.sweetzonzi.machine_max.common.mech.signal;

import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import lombok.Getter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 用于在部件之间转发子系统信号，如玩家控制量、引擎功率、武器控制器信号等
 */
@Getter
public class SignalPort implements ISignalReceiver, ISignalSender {
    public final String name;
    public final AbstractConnector owner;
    public final Map<String, List<String>> targetNames;//接收哪些信号
    public final Map<String, String> signalTranslation;//信号频道转译映射（原始频道->转译后频道）
    public final Map<String, Map<String, ISignalReceiver>> targets = new HashMap<>();//频道名->接收方名->接收方
    public ConcurrentMap<String, SignalChannel> signalInputChannels = new ConcurrentHashMap<>();//仅应被查询

    /**
     * 为部件连接点创建信号传输端口
     *
     * @param owner             部件连接器
     * @param signalTargets     信号提供目标
     * @param signalTranslation 信号频道转译映射（原始频道->转译后频道）
     */
    public SignalPort(AbstractConnector owner, Map<String, List<String>> signalTargets, Map<String, String> signalTranslation) {
        this.name = owner.name;
        this.owner = owner;
        this.targetNames = signalTargets;
        this.signalTranslation = signalTranslation;
    }

    /** 信号端口以连接点名为寻址名 */
    @Override
    public String getSignalAddress() {
        return name;
    }

    /**
     * 信号实际发生变化时，将信号连同发送者传播至与自己连接的对侧接口的信号传输目标，并将接收到的信号频道名转译为对侧接口配置的目标频道名
     *
     * @param channelName 原始信号频道名
     * @param sender      信号发送者
     */
    @Override
    public SignalResult onSignalUpdated(String channelName, ISignalSender sender) {
        SignalResult result = SignalResult.PASS;
        if (owner instanceof AbstractConnector ownerConnector
                && ownerConnector.attachedConnector != null
                && ownerConnector.attachedConnector.signalPort != null) {
            AbstractConnector attachedConnector = ownerConnector.attachedConnector;
            // 应用信号转译（如果对侧接口配置了转译规则）
            String targetChannelName = attachedConnector.signalPort.translateChannel(channelName);

            // 检查转译后的频道是否在目标接收列表中
            if (attachedConnector.signalPort.getTargets().containsKey(targetChannelName)) {
                Map<String, ISignalReceiver> targetReceivers =
                        attachedConnector.signalPort.getTargets().get(targetChannelName);

                SignalChannel currentChannels = signalInputChannels.get(channelName);
                if (currentChannels == null) {
                    return SignalResult.PASS; // 没有信号可转发
                }
                // 仅在实际发生变更时传播
                for (Map.Entry<String, ISignalReceiver> entry : targetReceivers.entrySet()) {
                    ISignalReceiver signalReceiver = entry.getValue();
                    // 检查接收者是否声明接受此频道（acceptAllRoutingInput 跳过检查）
                    boolean accepts = signalReceiver.acceptAllRoutingInput()
                            || signalReceiver.getAcceptedChannels().contains(targetChannelName);
                    if (!accepts) continue;

                    SignalChannel receiverChannels = signalReceiver.getSignalInputChannels()
                            .computeIfAbsent(targetChannelName, k -> new SignalChannel());

                    boolean hasChanged = false;
                    for (Map.Entry<ISignalSender, Object> entry1 : currentChannels.entrySet()) {
                        ISignalSender originalSender = entry1.getKey();
                        Object signalValue = entry1.getValue();

                        // 检查信号是否已存在且相同
                        Object existingValue = receiverChannels.get(originalSender);
                        if (!signalValue.equals(existingValue)) {
                            receiverChannels.put(originalSender, signalValue);
                            hasChanged = true;
                        }
                    }

                    if (hasChanged) {
                        // 使用转译后的频道名通知接收者
                        var tmp = signalReceiver.onSignalUpdated(targetChannelName, sender);
                        if (result == SignalResult.PASS) { // 返回第一个接收者的结果作为最终结果，但依旧向其他目标发送信号
                            if (tmp == SignalResult.CONSUME)
                                result = SignalResult.CONSUME;
                            else if (tmp == SignalResult.FAIL)
                                result = SignalResult.FAIL;
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * 连接点连接时，立即为对方更新一次信号
     * 注意：需要应用转译规则
     */
    public void onConnectorAttach() {
        // 检查基本前提条件
        if (owner.attachedConnector == null || owner.attachedConnector.signalPort == null) {
            return;
        }

        AbstractConnector attachedConnector = owner.attachedConnector;
        Map<String, Map<String, ISignalReceiver>> attachedTargets = attachedConnector.signalPort.getTargets();

        for (Map.Entry<String, SignalChannel> entry : signalInputChannels.entrySet()) {
            String originalChannel = entry.getKey();

            // 只转发目标接收方需要的信号
            String translatedChannel = owner.attachedConnector.signalPort.translateChannel(originalChannel);
            if (attachedTargets.containsKey(translatedChannel)) {
                onSignalUpdated(originalChannel, this);
            }
        }
    }

    /**
     * 与另一个部件连接器断开连接时更新端口引用
     */
    public void onConnectorDetach() {
        if (owner instanceof AbstractConnector ownerConnector
                && ownerConnector.attachedConnector != null) {
            for (Map.Entry<String, Map<String, ISignalReceiver>> entry : getTargets().entrySet()) {
                for (ISignalReceiver receiver : entry.getValue().values()) {
                    // 从输出目标的信号输入中移除本端口的信号输出
                    receiver.getSignalInputChannels().remove(entry.getKey());
                    if (receiver instanceof ISignalSender sender) {
                        sender.clearCallbackTargets();
                    }
                }
            }
        }
    }

    @Override
    public SubPart getSubPart() {
        return owner.getSubPart();
    }

    /**
     * 添加信号转译规则
     *
     * @param originalChannel   原始信号频道
     * @param translatedChannel 转译后信号频道
     */
    public void addTranslation(String originalChannel, String translatedChannel) {
        signalTranslation.put(originalChannel, translatedChannel);
    }

    /**
     * 移除信号转译规则
     *
     * @param originalChannel 原始信号频道
     */
    public void removeTranslation(String originalChannel) {
        signalTranslation.remove(originalChannel);
    }

    /**
     * 获取信号转译结果
     *
     * @param originalChannel 原始信号频道
     * @return 转译后频道名（如无转译则返回原频道名）
     */
    public String translateChannel(String originalChannel) {
        return signalTranslation.getOrDefault(originalChannel, originalChannel);
    }

    @Override
    public boolean acceptAllRoutingInput() {
        return true;
    }

    /**
     * 递归穿透到对侧端口的下一跳目标，触发它们的 respondCallbackToSender。
     * <p>
     * SignalPort 自身不作为终端发声，但必须将回调请求传递给信号路径上的最终接收者。
     * 因为 Port 内部转发走 {@link #onSignalUpdated}，绕过 {@link ISignalSender#sendSignalToTarget}，
     * 所以需要在此处手动穿透到下一跳。
     */
    @Override
    public void respondCallbackToSender(
            String channelName, ISignalSender sender, Object value,
            boolean requiresImmediateCallback, boolean callbackReturnsSignalValue) {
        if (!requiresImmediateCallback) return;
        // 穿透到对侧端口的下一跳目标，递归触发它们的 respondCallbackToSender
        if (owner instanceof AbstractConnector ownerConnector
                && ownerConnector.attachedConnector != null
                && ownerConnector.attachedConnector.signalPort instanceof SignalPort otherPort) {
            String translated = otherPort.translateChannel(channelName);
            Map<String, ISignalReceiver> targets = otherPort.getTargets().get(translated);
            if (targets != null) {
                for (ISignalReceiver target : targets.values()) {
                    target.respondCallbackToSender(channelName, sender, value,
                            requiresImmediateCallback, callbackReturnsSignalValue);
                }
            }
        }
    }
}