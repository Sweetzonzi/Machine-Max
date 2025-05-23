package io.github.sweetzonzi.machinemax.util.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import lombok.Getter;

@Getter
public enum Axis{
    x(0), X(0),
    y(1), Y(1),
    z(2), Z(2),
    xr(3), XR(3),
    yr(4), YR(4),
    zr(5), ZR(5);

    private final int value;

    Axis(int value) {
        this.value = value;
    }

    /**
     * 根据int值获取对应的枚举实例
     */
    public static String fromValue(int value) {
        for (Axis key : Axis.values()) {
            if (key.getValue() == value) {
                return key.name();
            }
        }
        throw new IllegalArgumentException("No KeyMapping with value: " + value);
    }

    /**
     * 根据字符串获取对应的枚举实例的int值
     */
    public static int getValue(String name) {
        try {
            Axis axis = Axis.valueOf(name);
            return axis.getValue();
        } catch (NumberFormatException e) {
            throw new NumberFormatException();
        }
    }

    public static final Codec<Integer> CODEC = Codec.STRING.comapFlatMap(
            s -> { // Return data result containing error on failure
                try {
                    return DataResult.success(getValue(s));
                } catch (NumberFormatException e) {
                    return DataResult.error(()->"Invalid axis: " + s);
                }
            },
            Axis::fromValue // Regular function
    );
}
