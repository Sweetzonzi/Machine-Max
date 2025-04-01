package io.github.tt432.machinemax.common.vehicle.signal;

import io.github.tt432.machinemax.common.vehicle.Part;
import io.github.tt432.machinemax.common.vehicle.connector.AbstractConnector;
import lombok.Getter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 用于在子系统之间传输信号，如玩家控制量、转速等
 */
@Getter
public class Port implements ISignalReceiver, ISignalSender {
    public final String name;
    public final AbstractConnector owner;
    public final Map<String, List<String>> targetNames;//接收哪些信号
    public final Map<String, Map<String, ISignalReceiver>> targets = new HashMap<>();//将信号发给哪些目标
    public ConcurrentMap<String, Signals> signalInputs = new ConcurrentHashMap<>();//仅应被查询

    /**
     * 为部件对接口创建信号传输端口
     *
     * @param owner         部件连接器
     * @param signalTargets 信号提供目标
     */
    public Port(AbstractConnector owner, Map<String, List<String>> signalTargets) {
        this.name = owner.name;
        this.owner = owner;
        this.targetNames = signalTargets;
    }

    public void onSignalUpdated(String signalKey) {
        if (owner instanceof AbstractConnector ownerConnector
                && ownerConnector.attachedConnector != null
                && ownerConnector.attachedConnector.port.getTargets().containsKey(signalKey)) {
            ownerConnector.attachedConnector.port.getTargets().get(signalKey).forEach((receiverName, signalReceiver) -> {
                Signals currentSignals = signalInputs.get(signalKey);
                Signals receiverSignals = signalReceiver.getSignalInputs().computeIfAbsent(signalKey, k -> new Signals());
                // 仅在实际发生变更时传播
                if (!receiverSignals.equals(currentSignals)) {
                    receiverSignals.putAll(currentSignals);
                    signalReceiver.onSignalUpdated(signalKey);
                }
            });
        }
    }


    /**
     * 对接口连接时，立即为对方更新一次信号
     */
    public void onConnectorAttach() {
        for (Map.Entry<String, Signals> entry : signalInputs.entrySet()) onSignalUpdated(entry.getKey());
    }

    /**
     * 与另一个部件连接器断开连接时更新端口引用
     */
    public void onConnectorDetach() {
        if (owner instanceof AbstractConnector ownerConnector
                && ownerConnector.attachedConnector != null) {
            for (Map.Entry<String, Map<String, ISignalReceiver>> entry : targets.entrySet()) {//遍历输出的信号
                for (ISignalReceiver receiver : entry.getValue().values()) {//遍历信号的输出目标
                    //从输出目标的信号输入中移除本端口的信号输出
                    receiver.getSignalInputs().remove(entry.getKey());
                    if (receiver instanceof ISignalSender sender) {
                        sender.clearCallbackTargets();//由于动力反馈信号等是接收到动力输入后额外添加的，因此需要额外移除
                    }
                }
            }
        }
    }

    @Override
    public Part getPart() {
        return owner.subPart.part;
    }

}
