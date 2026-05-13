package io.github.sweetzonzi.machine_max.common.mech.vehicle.connector;

import com.jme3.math.Transform;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.connector.ConnectorAttr;

public class AdvancedConnector extends AbstractConnector{
    public AdvancedConnector(String name, ConnectorAttr attr, SubPart subPart, Transform subPartTransform) {
        super(name, attr, subPart, subPartTransform);
    }
}
