package io.github.tt432.machinemax.common.vehicle.signal;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.vehicle.Part;
import io.github.tt432.machinemax.common.vehicle.attr.PortAttr;
import io.github.tt432.machinemax.common.vehicle.connector.AbstractConnector;
import io.github.tt432.machinemax.common.vehicle.subsystem.AbstractSubsystem;
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
    public final PortAttr attr;
    public final Map<String, List<ISignalReceiver>> targets = new HashMap<>();//将信号发给哪些目标
    public ConcurrentMap<String, Signals> signalInputs = new ConcurrentHashMap<>();//仅应被查询
    public ConcurrentMap<String, Signals> signalOutputs = new ConcurrentHashMap<>();//可查可改

    /**
     * 为部件对接口创建信号传输端口
     *
     * @param owner 部件连接器
     * @param attr  端口属性
     */
    public Port(AbstractConnector owner, PortAttr attr) {
        this.name = owner.name;
        this.owner = owner;
        this.attr = attr;
        for (String signalKey : attr.acceptableSignals()) {
            signalOutputs.put(signalKey, new Signals());
        }
    }

    /**
     * 与另一个部件连接器对接时更新端口引用
     */
    public void onConnectorAttach() {
        if (owner instanceof AbstractConnector ownerConnector
                && ownerConnector.attachedConnector != null
                && ownerConnector.attachedConnector.port != null) {
            Port attachedPort = ownerConnector.attachedConnector.port;
            for (Map.Entry<String, List<ISignalReceiver>> signalKeyEntry : attachedPort.targets.entrySet()) {//遍历对侧端口输出给对侧部件内其他目标的信号
//                if (attr.acceptableSignals().contains(signalKeyEntry.getKey()) || attr.acceptableSignals().isEmpty()) {}
                for (ISignalReceiver receiver : signalKeyEntry.getValue()) {//遍历信号的输出目标
                    //转发本端口接收到的所有匹配信号给输出目标的信号输入列表
                    receiver.getSignalInputs().put(signalKeyEntry.getKey(), this.signalInputs.getOrDefault(signalKeyEntry.getKey(), new Signals()));
                }
            }
            attachedPort.signalOutputs = this.signalInputs;
        }
    }

    /**
     * 与另一个部件连接器断开连接时更新端口引用
     */
    public void onConnectorDetach() {
        if (owner instanceof AbstractConnector ownerConnector
                && ownerConnector.attachedConnector != null
                && ownerConnector.attachedConnector.port != null) {
            for (Map.Entry<String, List<ISignalReceiver>> entry : targets.entrySet()) {//遍历输出的信号
                for (ISignalReceiver receiver : entry.getValue()) {//遍历信号的输出目标
                    //从输出目标的信号输入中移除本端口的信号输出
                    receiver.getSignalInputs().remove(entry.getKey());
                }
            }
        }
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        return attr.signalTargets();
    }

    @Override
    public Part getPart() {
        return owner.subPart.part;
    }

    @Override
    public void resetSignalOutputs() {//什么也不做
    }

}
