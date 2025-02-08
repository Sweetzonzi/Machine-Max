package io.github.tt432.machinemax.common.vehicle.connector;

import io.github.tt432.machinemax.common.vehicle.SubPart;
import io.github.tt432.machinemax.common.vehicle.attr.ConnectorAttr;
import io.github.tt432.machinemax.util.data.PosRot;

public class Dof6Connector extends AbstractConnector{
    public Dof6Connector(String name, ConnectorAttr attr, SubPart subPart, PosRot childBodyAttachPoint) {
        super(name, attr, subPart, childBodyAttachPoint);
    }

    @Override
    protected void attachJoint(AttachPointConnector attachPoint) {

    }
}
