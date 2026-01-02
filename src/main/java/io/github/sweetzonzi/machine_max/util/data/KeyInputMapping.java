package io.github.sweetzonzi.machine_max.util.data;

import lombok.Getter;

@Getter
public enum KeyInputMapping {
    FREE_CAM(0),
    INTERACT(1),
    LEAVE_VEHICLE(2),

    CLUTCH(101),
    UP_SHIFT(102),
    DOWN_SHIFT(103),
    HAND_BRAKE(104),
    TOGGLE_HAND_BRAKE(105),

    ADD_PART_ATTACH_ANGLE(500),
    SUB_PART_ATTACH_ANGLE(501),
    CYCLE_PART_CONNECTORS(502),
    CYCLE_PART_VARIANTS(503),
    CYCLE_PART_RECIPES(504);
    private final int value;

    KeyInputMapping(int value) {
        this.value = value;
    }

    /**
     * 根据int值获取对应的枚举实例
     */
    public static KeyInputMapping fromValue(int value) {
        for (KeyInputMapping key : KeyInputMapping.values()) {
            if (key.getValue() == value) {
                return key;
            }
        }
        throw new IllegalArgumentException("No KeyMapping with value: " + value);
    }
}
