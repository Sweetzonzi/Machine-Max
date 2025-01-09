package io.github.tt432.machinemax.util.data;

import org.ode4j.math.DQuaternion;
import org.ode4j.math.DVector3;

public record PosRot(DVector3 pos, DQuaternion rot) {

    public DVector3 getPos() {
        return pos;
    }
    public DQuaternion getRot() {
        return rot;
    }
}
