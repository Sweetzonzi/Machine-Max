package io.github.tt432.machinemax.common.part.port;

import com.mojang.datafixers.util.Pair;
import io.github.tt432.machinemax.common.registry.PartPortType;
import io.github.tt432.machinemax.common.sloarphys.body.AbstractPartBody;
import io.github.tt432.machinemax.util.data.PosRot;
import org.ode4j.ode.DFixedJoint;
import org.ode4j.ode.OdeHelper;

public class FixedPartPort extends AbstractPortPort {

    public FixedPartPort(String name, AbstractPartBody slotOwnerBody, PosRot childElementAttachPoint) {
        this(Pair.of(PartPortType.FIXED_PART_PORT.get(), slotOwnerBody), Pair.of(name, childElementAttachPoint));
    }

    public FixedPartPort(Pair<PartPortType, AbstractPartBody> typeBodyPair, Pair<String, PosRot> nameAttachPointPair) {
        super(typeBodyPair, nameAttachPointPair);
    }

    @Override
    protected void attachJoint(AbstractPortPort bodyPort) {
        DFixedJoint joint = OdeHelper.createFixedJoint(portOwnerBody.getBody().getWorld());
        joint.attach(portOwnerBody.getBody(), bodyPort.getPortOwnerBody().getBody());
        joints.put("FixedJoint", joint);
        joint.setFixed();
    }
}
