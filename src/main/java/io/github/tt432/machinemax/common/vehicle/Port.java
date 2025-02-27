package io.github.tt432.machinemax.common.vehicle;

import io.github.tt432.machinemax.common.vehicle.attr.PortAttr;
import io.github.tt432.machinemax.common.vehicle.connector.AbstractConnector;
import lombok.Getter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * 用于在子系统之间传输信号与资源，如玩家控制量、转速、空气、燃油、扭矩等
 */
@Getter
public class Port {
    public final IPortHost owner;
    public final PortAttr attr;
    public Port targetPort;
    private ConcurrentMap<String, Function<Port, Object>> signalInputs = new ConcurrentHashMap<>();//仅应被查询
    public ConcurrentMap<String, Function<Port, Object>> signalOutputs = new ConcurrentHashMap<>();//可查可改
    private ConcurrentMap<String, Function<Port, Float>> resourceInputs = new ConcurrentHashMap<>();//仅应被查询
    public ConcurrentMap<String, Function<Port, Float>> resourceOutputs = new ConcurrentHashMap<>();//可查可改

    public Port(IPortHost owner, PortAttr attr) {
        this.owner = owner;
        this.attr = attr;
    }

    /**
     * 与另一个部件连接器对接时更新端口引用
     */
    public void onConnectorAttach() {
        if (owner instanceof AbstractConnector ownerConnector
                && ownerConnector.attachedConnector != null
                && ownerConnector.attachedConnector.port != null) {
            Port attachedPort = ownerConnector.attachedConnector.port;
            ConcurrentMap<String, Function<Port, Object>> filteredSignalInputs = new ConcurrentHashMap<>();
            ConcurrentMap<String, Function<Port, Float>> filteredResourceInputs = new ConcurrentHashMap<>();
            //过滤出目标端口可接收且本端口可提供的信号类型
            for (Map.Entry<String, Function<Port, Object>> entry : this.signalOutputs.entrySet()) {
                String signalKey = entry.getKey();
                if (attachedPort.attr.requiredSignals.contains(signalKey) && this.attr.providedSignals.contains(signalKey)) {
                    filteredSignalInputs.put(signalKey, entry.getValue());
                }
            }
            //过滤出目标端口可接收且本端口可提供的资源类型
            for (Map.Entry<String, Function<Port, Float>> entry : this.resourceOutputs.entrySet()) {
                String resourceKey = entry.getKey();
                if (attachedPort.attr.requiredResources.contains(resourceKey) && this.attr.providedResources.contains(resourceKey)) {
                    filteredResourceInputs.put(resourceKey, entry.getValue());
                }
            }
            attachedPort.signalInputs = filteredSignalInputs;
            attachedPort.resourceInputs = filteredResourceInputs;
        }
    }


    /**
     * 与另一个部件连接器断开连接时更新端口引用
     */
    public void onConnectorDetach() {
        if (owner instanceof AbstractConnector ownerConnector
                && ownerConnector.attachedConnector != null
                && ownerConnector.attachedConnector.port != null) {
            ownerConnector.attachedConnector.port.signalOutputs = new ConcurrentHashMap<>();
            ownerConnector.attachedConnector.port.resourceOutputs = new ConcurrentHashMap<>();
        }
    }

    public void setTargetPort(Port targetPort) {
        this.targetPort = targetPort;
        ConcurrentMap<String, Function<Port, Object>> filteredSignalInputs = new ConcurrentHashMap<>();
        ConcurrentMap<String, Function<Port, Float>> filteredResourceInputs = new ConcurrentHashMap<>();
        if(targetPort.owner instanceof SubsystemController bus){
            //TODO:传输到载具信号资源总线时的特殊处理
//            for (Map.Entry<String, Function<Port, Object>> entry : this.signalOutputs.entrySet()) {
//                String signalKey = entry.getKey();
//                if (this.attr.providedSignals.contains(signalKey)) {
//                    filteredSignalInputs.put(signalKey, entry.getValue());
//                }
//                targetPort.signalInputs = filteredSignalInputs;
//            }
        }else {
            //过滤出目标端口可接收且本端口可提供的信号类型
            for (Map.Entry<String, Function<Port, Object>> entry : this.signalOutputs.entrySet()) {
                String signalKey = entry.getKey();
                if (targetPort.attr.requiredSignals.contains(signalKey) && this.attr.providedSignals.contains(signalKey)) {
                    filteredSignalInputs.put(signalKey, entry.getValue());
                }
            }
            //过滤出目标端口可接收且本端口可提供的资源类型
            for (Map.Entry<String, Function<Port, Float>> entry : this.resourceOutputs.entrySet()) {
                String resourceKey = entry.getKey();
                if (targetPort.attr.requiredResources.contains(resourceKey) && this.attr.providedResources.contains(resourceKey)) {
                    filteredResourceInputs.put(resourceKey, entry.getValue());
                }
            }
            targetPort.signalInputs = filteredSignalInputs;
            targetPort.resourceInputs = filteredResourceInputs;
        }
    }

    /**
     * 获取指定信号
     *
     * @param signalKey 信号类型
     * @return 信号值
     */
    public Object getSignal(String signalKey) {
        Function<Port, Object> signalInput = signalInputs.get(signalKey);
        if (signalInput == null) {
            return null;
        } else return signalInput.apply(this);
    }

    /**
     * 获取指定资源
     * @param resourceKey 资源类型
     * @return 资源值
     */
    public float getResource(String resourceKey) {
        Function<Port, Float> resourceInput = resourceInputs.get(resourceKey);
        if (resourceInput == null) {
            return 0.0f;
        } else return resourceInput.apply(this);
    }

    /**
     * 设置指定信号输出。
     * 通过将信号值包装为函数保证信号接收方随时能够获取最新值。
     * 部件连接器理论上无信号输出，因此不应调用此方法。
     * @param signalKey 信号类型
     * @param signalOutput 包装为函数的信号值
     */
    public void setSignalOutput(String signalKey, Function<Port, Object> signalOutput) {
        //检查是否允许输出指定信号
        if(this.owner instanceof AbstractConnector || !this.attr.providedSignals.contains(signalKey)) return;
        this.signalOutputs.put(signalKey, signalOutput);
    }

    /**
     * 设置指定资源输出。
     * 通过将资源值包装为函数保证资源接收方随时能够获取最新值。
     * 部件连接器理论上无资源输出，因此不应调用此方法。
     * @param resourceKey 资源类型
     * @param resourceOutput 包装为函数的资源值
     */
    public void setResourceOutput(String resourceKey, Function<Port, Float> resourceOutput) {
        //检查是否允许输出指定资源
        if(this.owner instanceof AbstractConnector || !this.attr.providedResources.contains(resourceKey)) return;
        this.resourceOutputs.put(resourceKey, resourceOutput);
    }
}
