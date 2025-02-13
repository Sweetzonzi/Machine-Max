package io.github.tt432.machinemax.common.vehicle.connector;

import com.jme3.math.Transform;
import io.github.tt432.machinemax.common.vehicle.SubPart;
import io.github.tt432.machinemax.common.vehicle.attr.ConnectorAttr;
import io.github.tt432.machinemax.util.data.PosRot;

public class Dof6Connector extends AbstractConnector{
    public Dof6Connector(String name, ConnectorAttr attr, SubPart subPart, Transform subPartTransform) {
        super(name, attr, subPart, subPartTransform);
    }

    @Override
    protected void attachJoint(AttachPointConnector targetConnector) {

    }
}
