package io.github.sweetzonzi.machine_max.common.vehicle.connector;

import com.jme3.math.Transform;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.ConnectorAttr;

public class SpecialConnector extends AbstractConnector{
    public SpecialConnector(String name, ConnectorAttr attr, SubPart subPart, Transform subPartTransform) {
        super(name, attr, subPart, subPartTransform);
    }
}
