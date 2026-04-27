package io.github.sweetzonzi.machine_max.common.vehicle.energy;

public interface IMechPowerConsumer {
    String getName();

    void onMechPowerReceived(String producerName, MechPower power);

    float getFeedbackSpeed();
    void setFeedbackSpeed(float speed);

    default boolean isPowerPathConnected(String producerName) {
        return true;
    }
}
