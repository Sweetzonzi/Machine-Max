package io.github.tt432.machinemax.common.part.old.slot;

import io.github.tt432.machinemax.common.part.old.AbstractPart;

/**
 * 占位符槽位类，不可安装任何部件
 */
public class UndefinedPartSlot extends AbstractPartSlot {
    public UndefinedPartSlot(AbstractPart owner) {
        super(owner,null,null,null);
    }

    @Override
    protected void attachJoint(AbstractPart part) {

    }

    @Override
    public boolean slotConditionCheck(AbstractPart part) {
        return false;
    }
}
