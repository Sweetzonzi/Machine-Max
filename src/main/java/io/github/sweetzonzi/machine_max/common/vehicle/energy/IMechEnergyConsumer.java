package io.github.sweetzonzi.machine_max.common.vehicle.energy;

public interface IMechEnergyConsumer {
    String getName();

    void onMechEnergyReceived(String producerName, MechPower power);

    float getFeedbackSpeed();
    void setFeedbackSpeed(float speed);

    default boolean isEnergyPathConnected(String producerName) {
        return true;
    }
}
