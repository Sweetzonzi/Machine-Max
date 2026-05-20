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
    public boolean isPowerPathConnected(String producerName) {
        if (resolvedLocalConsumer != null) {
            return resolvedLocalConsumer.isPowerPathConnected(producerName);
        }
        AbstractConnector attached = owner.attachedConnector;
        if (attached != null && attached.mechanicalEnergyPort != null) {
            return attached.mechanicalEnergyPort.isPowerPathConnected(producerName);
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
