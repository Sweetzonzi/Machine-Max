package io.github.sweetzonzi.machine_max.common.mech.signal;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SubsystemController;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.InteractBox;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public interface ISignalSender {
    /**
     * 在此填入各个信号名对应的接收者名列表，用于自动组织信号传输关系。<p>
     * Return a map of signal names to a list of receiver names here, to automatically organize signal transfer.
     *
     * @return 信号频道->接收者名称列表 Map of signal channels to a list of receiver names.
     */
    Map<String, List<String>> getTargetNames();

    Map<String, Map<String, ISignalReceiver>> getTargets();//信号频道名称->接收者名称->接收者 Signal channel name -> receiver name -> receiver

    default Map<String, Set<ISignalReceiver>> getCallbackTargets() {
        return HashMap.newHashMap(1);
    }

    default void addCallbackTarget(String signalChannel, ISignalReceiver target) {
        getCallbackTargets().computeIfAbsent(signalChannel, k -> new HashSet<>()).add(target);
    }

    default void removeCallbackTarget(String signalChannel, ISignalReceiver target) {
        getCallbackTargets().computeIfAbsent(signalChannel, k -> new HashSet<>()).remove(target);
    }

    default void clearCallbackTargets() {
        getCallbackTargets().clear();
    }

    /**
     * 将发送的信号输出类型全部重置为空信号
     */
    default void resetSignalOutputs() {
        if (this instanceof SignalPort) return;//信号端口只应转发信号，不应对其输出信号进行操作
        for (Map.Entry<String, Map<String, ISignalReceiver>> entry : getTargets().entrySet()) {
            entry.getValue().forEach((receiverName, signalReceiver) -> {
                var emptySignal = EmptySignal.INSTANCE;
                signalReceiver.getSignalInputChannels().computeIfAbsent(entry.getKey(), k -> new SignalChannel()).put(this, emptySignal);
                signalReceiver.onSignalUpdated(entry.getKey(), this);
            });
        }
        for (Map.Entry<String, Set<ISignalReceiver>> entry : getCallbackTargets().entrySet()) {
            entry.getValue().forEach((signalReceiver) -> {
                var emptySignal = EmptySignal.INSTANCE;
                signalReceiver.getSignalInputChannels().computeIfAbsent(entry.getKey(), k -> new SignalChannel()).put(this, emptySignal);
                signalReceiver.onSignalUpdated(entry.getKey(), this);
            });
        }
    }

    /**
     * 将信号发送到所有接收此信号的目标，所有目标收到同名同数值的信号
     *
     * @param signalChannel 信号频道名称
     * @param signalValue   信号值
     */
    default void sendSignalToAllTargets(String signalChannel, Object signalValue) {
        sendSignalToAllTargets(signalChannel, signalValue, false, false);
    }

    /**
     * 将信号发送到所有接收此信号的目标，所有目标收到同名同数值的信号
     *
     * @param signalChannel              信号频道名称
     * @param signalValue                信号值
     * @param callbackReturnsSignalValue true: 回调函数返回信号值，false: 回调函数返回信号频道名称
     */
    default void sendSignalToAllTargetsWithCallback(String signalChannel, Object signalValue, boolean callbackReturnsSignalValue) {
        sendSignalToAllTargets(signalChannel, signalValue, true, callbackReturnsSignalValue);
    }

    /**
     * 将信号发送到所有接收此信号的目标，所有目标在同一频道收到同数值的信号
     *
     * @param signalChannel              信号频道名
     * @param signalValue                信号值
     * @param requiresImmediateCallback  是否需要即时回调
     * @param callbackReturnsSignalValue true: 回调函数返回信号值，false: 回调函数返回信号频道名称
     */
    default void sendSignalToAllTargets(String signalChannel, Object signalValue, boolean requiresImmediateCallback, boolean callbackReturnsSignalValue) {
        if (getTargets().containsKey(signalChannel))
            getTargets().get(signalChannel).forEach((receiverName, signalReceiver) -> sendSignalToTarget(signalChannel, receiverName, signalValue, requiresImmediateCallback, callbackReturnsSignalValue));
    }

    /**
     * 将信号发送到指定接收者，可用于发送同名不同值信号给不同目标
     *
     * @param signalChannel 信号频道
     * @param targetName    接收者名称
     * @param signalValue   信号值
     * @return 接收者的处理结果
     */
    default SignalResult sendSignalToTarget(String signalChannel, String targetName, Object signalValue) {
        return sendSignalToTarget(signalChannel, targetName, signalValue, false, false);
    }

    /**
     * 将信号发送到指定接收者，可用于发送同名不同值信号给不同目标
     *
     * @param signalChannel              信号频道
     * @param targetName                 接收者名称
     * @param signalValue                信号值
     * @param callbackReturnsSignalValue true: 回调函数返回信号值，false: 回调函数返回信号频道名称
     * @return 接收者的处理结果
     */
    default SignalResult sendSignalToTargetWithCallback(String signalChannel, String targetName, Object signalValue, boolean callbackReturnsSignalValue) {
        return sendSignalToTarget(signalChannel, targetName, signalValue, true, callbackReturnsSignalValue);
    }

    /**
     * 将信号发送到指定接收者，可用于发送同名不同值信号给不同目标
     *
     * @param signalChannel              信号频道
     * @param targetName                 接收者名称
     * @param signalValue                信号值
     * @param requiresImmediateCallback  是否需要即时回调
     * @param callbackReturnsSignalValue true: 回调函数返回信号值，false: 回调函数返回信号频道名称
     * @return 接收者的处理结果
     */
    default SignalResult sendSignalToTarget(String signalChannel, String targetName, Object signalValue, boolean requiresImmediateCallback, boolean callbackReturnsSignalValue) {
        if (getTargets().containsKey(signalChannel)) {
            ISignalReceiver signalReceiver = getTargets().get(signalChannel).get(targetName);
            // 如果接收者为 null，尝试找到全局控制器或子部件
            if (signalReceiver == null && (targetName.equals("vehicle") || targetName.equals("subpart"))) {
                for (ISignalReceiver target : getTargets().get(signalChannel).values()) {
                    if (targetName.equals("vehicle") && target instanceof SubsystemController global) {
                        signalReceiver = global;
                        break;
                    } else if (targetName.equals("subpart") && target instanceof SubPart subPart) {
                        signalReceiver = subPart;
                        break;
                    }
                }
            }
            if (signalReceiver != null) {
                signalReceiver.getSignalInputChannels().computeIfAbsent(signalChannel, k -> new SignalChannel()).put(this, signalValue);
                if (signalReceiver instanceof SubsystemController global) {
                    global.signalStorage.put(signalChannel, signalValue);
                } else if (signalReceiver instanceof SubPart subPart) {
                    subPart.signalStorage.put(signalChannel, signalValue);
                }
                SignalResult result = signalReceiver.onSignalUpdated(signalChannel, this);
                if (requiresImmediateCallback && this instanceof ISignalReceiver) {
                    // 递归收集信号传播链上最终到达的终端接收者，支持多跳传递和频道转译
                    Set<ISignalReceiver> terminalTargets = new HashSet<>();
                    collectTerminalTargets(signalReceiver, signalChannel, terminalTargets, new HashSet<>());
                    for (ISignalReceiver target : terminalTargets) {
                        if (target instanceof ISignalSender callbackSender) {
                            if (callbackReturnsSignalValue)
                                callbackSender.sendCallbackToListener("callback", (ISignalReceiver) this, signalValue);
                            else
                                callbackSender.sendCallbackToListener("callback", (ISignalReceiver) this, signalChannel);
                        }
                    }
                }
                return result;
            }
        }
        return SignalResult.PASS;
    }

    /**
     * <p>将信号发送到指定接收者，可用于发送同名不同值信号给不同目标</p>
     * <p>Send a signal to a specified receiver, which can be used to send different values of the same signal to different targets.</p>
     *
     * @param signalChannel              信号频道
     * @param target                     接收者
     * @param signalValue                信号值
     * @param callbackReturnsSignalValue true: 回调函数返回信号值，false: 回调函数返回信号频道名称
     * @return 接收者的处理结果
     */
    default SignalResult sendSignalToTargetWithCallback(String signalChannel, ISignalReceiver target, Object signalValue, boolean callbackReturnsSignalValue) {
        return this.sendSignalToTarget(signalChannel, target.getName(), signalValue, true, callbackReturnsSignalValue);
    }

    default void sendCallbackToAllListeners(String signalChannel, Object signalValue) {
        if (this instanceof ISignalReceiver) {
            var targets = getCallbackTargets().computeIfAbsent(signalChannel, k -> new HashSet<>());
            for (ISignalReceiver target : targets) {
                target.getSignalInputChannels().computeIfAbsent(signalChannel, k -> new SignalChannel()).put(this, signalValue);
                target.onSignalUpdated(signalChannel, this);
            }
        }
    }

    default void sendCallbackToListener(String signalChannel, ISignalReceiver receiver, Object signalValue) {
        if (this instanceof ISignalReceiver) {
            receiver.getSignalInputChannels().computeIfAbsent(signalChannel, k -> new SignalChannel()).put(this, signalValue);
            receiver.onSignalUpdated(signalChannel, this);
        }
    }

    /**
     * 递归收集信号经过 SignalPort 传播链后实际到达的终端接收者。
     * 沿着 {@link SignalPort#onSignalUpdated} 的转发逻辑（含频道转译）追踪信号路径，
     * 使反馈信号能够跨越多跳连接点传递到最终的信号消费方。
     *
     * @param receiver      当前信号接收者
     * @param channel       当前频道名称（已被转译后的名称）
     * @param terminals     输出参数：收集到的终端接收者集合
     * @param visited       防止循环的已访问集合
     */
    default void collectTerminalTargets(
            ISignalReceiver receiver,
            String channel,
            Set<ISignalReceiver> terminals,
            Set<ISignalReceiver> visited
    ) {
        if (receiver == null || !visited.add(receiver)) return;

        if (receiver instanceof SignalPort port
                && port.getOwner() instanceof AbstractConnector ownerConnector
                && ownerConnector.attachedConnector != null
                && ownerConnector.attachedConnector.signalPort instanceof SignalPort otherPort) {
            // 遵循 SignalPort.onSignalUpdated() 的实际转发逻辑：先转译频道，再查找目标
            String translated = otherPort.translateChannel(channel);
            Map<String, ISignalReceiver> targets = otherPort.getTargets().get(translated);
            if (targets != null) {
                for (ISignalReceiver target : targets.values()) {
                    collectTerminalTargets(target, translated, terminals, visited);
                }
            }
        } else {
            terminals.add(receiver);
        }
    }

    /**
     * 获取此发送者所属的 SubPart。
     * 没有所属 SubPart 的发送者（如 {@link SubsystemController}）返回 null。
     */
    @Nullable
    default SubPart getSubPart(){
        return null;
    }

    /**
     * 设置信号传输目标
     */
    default void setTargetFromNames() {
        if (getSubPart() != null) {
            Map<String, AbstractSubsystem> subSystems = getSubPart().subsystems;
            Map<String, InteractBox> interactBoxes = new HashMap<>();
            if (getSubPart().interactBoxes != null)
                interactBoxes.putAll(getSubPart().interactBoxes);
            Map<String, SignalPort> ports = new HashMap<>();
            getSubPart().connectors.forEach((name, connector) -> ports.put(name, connector.signalPort));
            for (Map.Entry<String, List<String>> entry : getTargetNames().entrySet()) {
                Map<String, ISignalReceiver> signalReceivers = new HashMap<>(2);
                if (entry.getKey().isEmpty()) continue;
                getReceiversFromNames(entry.getValue(), getSubPart(), subSystems, interactBoxes, ports).forEach(receiver -> signalReceivers.put(receiver.getName(), receiver));
                getTargets().put(entry.getKey(), signalReceivers);
            }
        }
    }

    default List<ISignalReceiver> getReceiversFromNames(
            List<String> targetNames,
            SubPart ownerPart,
            Map<String, AbstractSubsystem> subSystems,
            Map<String, InteractBox> interactBoxes,
            Map<String, SignalPort> ports) {
        List<ISignalReceiver> targets = new ArrayList<>();
        for (String targetName : targetNames) {
            if (targetName.equals("vehicle")) {
                if (ownerPart.part.assembly != null)
                    targets.add(ownerPart.part.assembly.getSubsystemController());
            } else if (targetName.equals("subpart")) {
                targets.add(ownerPart);
            } else if (subSystems.containsKey(targetName)) {
                AbstractSubsystem subSystem = subSystems.get(targetName);
                if (subSystem != null) {
                    targets.add(subSystem);
                }
            } else if (interactBoxes.containsKey(targetName)) {
                InteractBox interactBox = interactBoxes.get(targetName);
                targets.add(interactBox);
            } else if (ports.containsKey(targetName)) {
                SignalPort signalPort = ports.get(targetName);
                targets.add(signalPort);
            } else MachineMax.LOGGER.error("未在部件内找到目标端口、子系统或交互区: {}", targetName);
        }
        return targets;
    }
}
