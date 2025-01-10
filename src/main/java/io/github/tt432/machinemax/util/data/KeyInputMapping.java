package io.github.tt432.machinemax.util.data;

public enum KeyInputMapping {
    FREE_CAM(0),
    INTERACT(1);

    private final int value;

    KeyInputMapping(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
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
