package io.github.sweetzonzi.machine_max.common.mech.energy;

public interface IMechPowerConsumer {
    String getName();

    void onMechPowerReceived(String producerName, MechPower power);

    float getFeedbackSpeed();

    /**
     * 按 producer 返回反馈转速。多输入驱动按各路输入转速比例分摊实测总量；
     * 单输入驱动保持默认实现。
     *
     * @param producerName 上游 producer 的名称
     */
    default float getFeedbackSpeed(String producerName) {
        return getFeedbackSpeed();
    }

    void setFeedbackSpeed(float speed);

    default boolean isPowerPathConnected(String producerName) {
        return true;
    }
}
