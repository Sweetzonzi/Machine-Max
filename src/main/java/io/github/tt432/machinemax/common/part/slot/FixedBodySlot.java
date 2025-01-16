package io.github.tt432.machinemax.common.part.slot;

import io.github.tt432.machinemax.common.sloarphys.body.AbstractPartBody;
import io.github.tt432.machinemax.common.sloarphys.body.ModelPartBody;
import io.github.tt432.machinemax.util.data.PosRot;
import org.ode4j.ode.DFixedJoint;
import org.ode4j.ode.OdeHelper;

public class FixedBodySlot extends AbstractBodySlot {
    public FixedBodySlot(String name, AbstractPartBody slotOwnerElement, PosRot childElementAttachPoint) {
        super(name, slotOwnerElement, childElementAttachPoint);
    }

    @Override
    protected void attachJoint(AbstractPartBody element, String childPartAttachPoint) {
        DFixedJoint joint = OdeHelper.createFixedJoint(slotOwnerBody.getBody().getWorld());
        joint.attach(slotOwnerBody.getBody(), element.getBody());
        joints.put(childPartAttachPoint, joint);
        joint.setFixed();
    }
}
