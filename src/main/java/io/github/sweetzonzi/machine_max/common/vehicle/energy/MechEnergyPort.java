package io.github.sweetzonzi.machine_max.common.vehicle.energy;

import io.github.sweetzonzi.machine_max.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractSubsystem;

public class MechEnergyPort implements IMechEnergyConsumer {
    private final AbstractConnector owner;
    private final String powerTargetName;
    private IMechEnergyConsumer resolvedLocalConsumer;

    public MechEnergyPort(AbstractConnector owner, String powerTargetName) {
        this.owner = owner;
        this.powerTargetName = powerTargetName;
    }

    @Override
    public String getName() {
        return owner.name;
    }

    @Override
    public void onMechEnergyReceived(String producerName, MechPower power) {
        AbstractConnector attached = owner.attachedConnector;
        if (attached != null && attached.mechanicalEnergyPort != null) {
            attached.mechanicalEnergyPort.deliverToLocalConsumer(producerName, power);
        }
    }

    public void deliverToLocalConsumer(String producerName, MechPower power) {
        if (resolvedLocalConsumer != null) {
            resolvedLocalConsumer.onMechEnergyReceived(producerName, power);
        }
    }

    @Override
    public float getFeedbackSpeed() {
        if (resolvedLocalConsumer != null) {
            return resolvedLocalConsumer.getFeedbackSpeed();
        }
        AbstractConnector attached = owner.attachedConnector;
        if (attached != null && attached.mechanicalEnergyPort != null) {
            return attached.mechanicalEnergyPort.getFeedbackSpeed();
        }
        return 0;
    }

    @Override
    public void setFeedbackSpeed(float speed) {
        if (resolvedLocalConsumer != null) {
            resolvedLocalConsumer.setFeedbackSpeed(speed);
        }
    }

    @Override
    public boolean isEnergyPathConnected(String producerName) {
        if (resolvedLocalConsumer != null) {
            return resolvedLocalConsumer.isEnergyPathConnected(producerName);
        }
        AbstractConnector attached = owner.attachedConnector;
        if (attached != null && attached.mechanicalEnergyPort != null) {
            return attached.mechanicalEnergyPort.isEnergyPathConnected(producerName);
        }
        return false;
    }

    public void onConnectorAttach() {
        resolvedLocalConsumer = resolveLocalConsumer();
    }

    public void onConnectorDetach() {
        resolvedLocalConsumer = null;
    }

    private IMechEnergyConsumer resolveLocalConsumer() {
        if (powerTargetName == null || powerTargetName.isEmpty()) return null;
        AbstractSubsystem subsystem = owner.subPart.subsystems.get(powerTargetName);
        if (subsystem instanceof IMechEnergyConsumer consumer) return consumer;
        AbstractConnector connector = owner.subPart.connectors.get(powerTargetName);
        if (connector != null && connector.mechanicalEnergyPort != null) return connector.mechanicalEnergyPort;
        return null;
    }
}
