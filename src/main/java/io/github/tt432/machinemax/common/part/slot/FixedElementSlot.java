package io.github.tt432.machinemax.common.part.slot;

import io.github.tt432.machinemax.common.part.PartElement;
import io.github.tt432.machinemax.util.data.PosRot;
import org.ode4j.ode.DFixedJoint;
import org.ode4j.ode.OdeHelper;

public class FixedElementSlot extends AbstractElementSlot{
    public FixedElementSlot(String name, PartElement slotOwnerElement, PosRot childElementAttachPoint) {
        super(name, slotOwnerElement, childElementAttachPoint);
    }

    @Override
    protected void attachJoint(PartElement element, String childPartAttachPoint) {
        DFixedJoint joint = OdeHelper.createFixedJoint(slotOwnerElement.getBody().getWorld());
        joint.attach(slotOwnerElement.getBody(), element.getBody());
        joints.put(childPartAttachPoint, joint);
        joint.setFixed();
    }
}
