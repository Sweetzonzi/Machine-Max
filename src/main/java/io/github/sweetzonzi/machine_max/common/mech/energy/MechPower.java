package io.github.sweetzonzi.machine_max.common.mech.energy;

public record MechPower(float power, float speed) {
    public static final MechPower ZERO = new MechPower(0, 0);
    public static final MechPower EMPTY = new MechPower(Float.NaN, Float.NaN);

    public boolean isEmpty() {
        return Float.isNaN(power) || Float.isNaN(speed);
    }
}
