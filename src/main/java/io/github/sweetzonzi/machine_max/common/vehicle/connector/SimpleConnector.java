package io.github.sweetzonzi.machine_max.common.vehicle.connector;

import com.jme3.math.Transform;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.ConnectorAttr;

public class SimpleConnector extends AbstractConnector{
    public SimpleConnector(String name, ConnectorAttr attr, SubPart subPart, Transform childPartTransform) {
        super(name, attr, subPart, childPartTransform);
    }
}
