package io.github.sweetzonzi.machinemax.common.vehicle.connector;

import com.jme3.math.Transform;
import io.github.sweetzonzi.machinemax.common.vehicle.SubPart;
import io.github.sweetzonzi.machinemax.common.vehicle.attr.ConnectorAttr;

public class SpecialConnector extends AbstractConnector{
    public SpecialConnector(String name, ConnectorAttr attr, SubPart subPart, Transform subPartTransform) {
        super(name, attr, subPart, subPartTransform);
    }
}
