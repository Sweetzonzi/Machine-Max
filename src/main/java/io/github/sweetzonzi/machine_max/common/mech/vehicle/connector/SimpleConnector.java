package io.github.sweetzonzi.machine_max.common.mech.vehicle.connector;

import com.jme3.math.Transform;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.connector.ConnectorAttr;

public class SimpleConnector extends AbstractConnector{
    public SimpleConnector(String name, ConnectorAttr attr, SubPart subPart, Transform childPartTransform) {
        super(name, attr, subPart, childPartTransform);
    }
}
