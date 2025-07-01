package io.github.sweetzonzi.machinemax.common.vehicle.signal;

import io.github.sweetzonzi.machinemax.common.vehicle.Part;
import io.github.sweetzonzi.machinemax.common.vehicle.connector.AbstractConnector;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 用于在部件之间转发子系统信号，如玩家控制量、引擎功率、火控信号等
 */
@Getter
public class SignalPort implements ISignalReceiver, ISignalSender {
    public final String name;
    public final AbstractConnector owner;
    public final Map<String, List<String>> targetNames;//接收哪些信号
    public final Map<String, Map<String, ISignalReceiver>> targets = new HashMap<>();//将信号发给哪些目标
    public ConcurrentMap<String, SignalChannel> signalInputChannels = new ConcurrentHashMap<>();//仅应被查询

    /**
     * 为部件对接口创建信号传输端口
     *
     * @param owner         部件连接器
     * @param signalTargets 信号提供目标
     */
    public SignalPort(AbstractConnector owner, Map<String, List<String>> signalTargets) {
        this.name = owner.name;
        this.owner = owner;
        this.targetNames = signalTargets;
    }

    @Override
    public void onSignalUpdated(String channelName, ISignalSender sender) {
        if (owner instanceof AbstractConnector ownerConnector
                && ownerConnector.attachedConnector != null
                && ownerConnector.attachedConnector.signalPort.getTargets().containsKey(channelName)) {
            ownerConnector.attachedConnector.signalPort.getTargets().get(channelName).forEach((receiverName, signalReceiver) -> {
                SignalChannel currentChannels = signalInputChannels.get(channelName);
                SignalChannel receiverChannels = signalReceiver.getSignalInputChannels().computeIfAbsent(channelName, k -> new SignalChannel());
                // 仅在实际发生变更时传播
                if (!receiverChannels.equals(currentChannels)) {
                    receiverChannels.putAll(currentChannels);
                    for (ISignalSender trueSender : currentChannels.keySet()) {
                        signalReceiver.onSignalUpdated(channelName, trueSender);
                    }
                }
            });
        }
    }

    /**
     * 对接口连接时，立即为对方更新一次信号
     */
    public void onConnectorAttach() {
        for (Map.Entry<String, SignalChannel> entry : signalInputChannels.entrySet()) onSignalUpdated(entry.getKey(), this);
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
                    receiver.getSignalInputChannels().remove(entry.getKey());
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
