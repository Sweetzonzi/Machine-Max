package io.github.tt432.machinemax.common.vehicle.connector;

import com.jme3.math.Transform;
import io.github.tt432.machinemax.common.vehicle.SubPart;
import io.github.tt432.machinemax.common.vehicle.attr.ConnectorAttr;
import io.github.tt432.machinemax.util.data.PosRot;

public class AttachPointConnector extends AbstractConnector{
    public AttachPointConnector(String name, ConnectorAttr attr, SubPart subPart, Transform childPartTransform) {
        super(name, attr, subPart, childPartTransform);
    }

    @Override
    protected void attachJoint(AttachPointConnector targetConnector) {

    }
}
