package io.github.sweetzonzi.machine_max.common.mech.energy;

import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;

public class MechPowerPort implements IMechPowerConsumer {
    private final AbstractConnector owner;
    private final String powerTargetName;
    private IMechPowerConsumer resolvedLocalConsumer;

    public MechPowerPort(AbstractConnector owner, String powerTargetName) {
        this.owner = owner;
        this.powerTargetName = powerTargetName;
    }

    @Override
    public String getName() {
        return owner.name;
    }

    @Override
    public void onMechPowerReceived(String producerName, MechPower power) {
        AbstractConnector attached = owner.attachedConnector;
        if (attached != null && attached.mechanicalEnergyPort != null) {
            attached.mechanicalEnergyPort.deliverToLocalConsumer(producerName, power);
        }
    }

    public void deliverToLocalConsumer(String producerName, MechPower power) {
        if (resolvedLocalConsumer != null) {
            resolvedLocalConsumer.onMechPowerReceived(producerName, power);
        }
    }

    /**
     * 获取反馈速度。
     * 优先从本地已解析的消费端获取；若本地无法解析，则沿连接链向上游委托。
     * 注意防止两个对接的 MechPowerPort 均无法本地解析时形成无限递归（A→B→A→B→…）。
     */
    @Override
    public float getFeedbackSpeed() {
        if (resolvedLocalConsumer != null) {
            return resolvedLocalConsumer.getFeedbackSpeed();
        }
        AbstractConnector attached = owner.attachedConnector;
        if (attached != null && attached.mechanicalEnergyPort != null) {
            MechPowerPort targetPort = attached.mechanicalEnergyPort;
            // 若目标端口也无法本地解析，且两端口互为 attachedConnector，则构成循环，直接返回 0
            if (targetPort.resolvedLocalConsumer == null && targetPort.owner.attachedConnector == this.owner) {
                return 0;
            }
            return targetPort.getFeedbackSpeed();
        }
        return 0;
    }

    @Override
    public void setFeedbackSpeed(float speed) {
        if (resolvedLocalConsumer != null) {
            resolvedLocalConsumer.setFeedbackSpeed(speed);
        }
    }

    /**
     * 检查动力路径是否连通。
     * 优先通过本地已解析的消费端检查；若本地无法解析，则沿连接链向上游委托。
     * 注意防止两个对接的 MechPowerPort 均无法本地解析时形成无限递归（A→B→A→B→…）。
     */
    @Override
    public boolean isPowerPathConnected(String producerName) {
        if (resolvedLocalConsumer != null) {
            return resolvedLocalConsumer.isPowerPathConnected(producerName);
        }
        AbstractConnector attached = owner.attachedConnector;
        if (attached != null && attached.mechanicalEnergyPort != null) {
            MechPowerPort targetPort = attached.mechanicalEnergyPort;
            // 若目标端口也无法本地解析，且两端口互为 attachedConnector，则构成循环，直接返回 false
            if (targetPort.resolvedLocalConsumer == null && targetPort.owner.attachedConnector == this.owner) {
                return false;
            }
            return targetPort.isPowerPathConnected(producerName);
        }
        return false;
    }

    public void onConnectorAttach() {
        resolvedLocalConsumer = resolveLocalConsumer();
    }

    public void onConnectorDetach() {
        resolvedLocalConsumer = null;
    }

    private IMechPowerConsumer resolveLocalConsumer() {
        if (powerTargetName == null || powerTargetName.isEmpty()) return null;
        AbstractSubsystem subsystem = owner.subPart.subsystems.get(powerTargetName);
        if (subsystem instanceof IMechPowerConsumer consumer) return consumer;
        AbstractConnector connector = owner.subPart.connectors.get(powerTargetName);
        if (connector != null && connector.mechanicalEnergyPort != null) return connector.mechanicalEnergyPort;
        return null;
    }
}
