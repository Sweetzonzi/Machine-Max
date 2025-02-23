package io.github.tt432.machinemax.util.data;

import lombok.Getter;

@Getter
public enum Axis{
    x(0),
    y(1),
    z(2),
    xr(3),
    yr(4),
    zr(5);

    private final int value;

    Axis(int value) {
        this.value = value;
    }

    /**
     * 根据int值获取对应的枚举实例
     */
    public static Axis fromValue(int value) {
        for (Axis key : Axis.values()) {
            if (key.getValue() == value) {
                return key;
            }
        }
        throw new IllegalArgumentException("No KeyMapping with value: " + value);
    }
}
