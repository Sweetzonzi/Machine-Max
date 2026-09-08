package io.github.sweetzonzi.machine_max.util.data;

import lombok.Getter;

@Getter
public enum KeyInputMapping {
    FREE_CAM(0),
    INTERACT(1),
    LEAVE_VEHICLE(2),
    TOGGLE_LIGHT(3),

    CLUTCH(101),
    UP_SHIFT(102),
    DOWN_SHIFT(103),
    HAND_BRAKE(104),
    TOGGLE_HAND_BRAKE(105),

    CYCLE_PART_RECIPES(504),

    /* 武器控制 (200号段) */
    MAIN_FIRE(200),              // 主武器开火（hold 类型）
    SECONDARY_FIRE(201),         // 副武器开火（hold 类型）
    NEXT_AMMO_TYPE(202),         // 下一个弹种
    PREV_AMMO_TYPE(203),         // 上一个弹种

    /* 控制组轮换 (300号段) */
    CYCLE_CONTROL_GROUP(300);    // 轮换激活控制组

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
