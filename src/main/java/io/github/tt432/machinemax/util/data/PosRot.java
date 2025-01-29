package io.github.tt432.machinemax.util.data;

import org.ode4j.math.DQuaternion;
import org.ode4j.math.DQuaternionC;
import org.ode4j.math.DVector3;
import org.ode4j.math.DVector3C;

public record PosRot(DVector3C pos, DQuaternionC rot) {

}
