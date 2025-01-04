package io.github.tt432.machinemax.common.part.old.slot;

import io.github.tt432.machinemax.common.part.old.AbstractPart;
import io.github.tt432.machinemax.common.phys.AbstractPhysThread;
import org.ode4j.math.DQuaternion;
import org.ode4j.math.DVector3;
import org.ode4j.ode.*;

public class WheelPartSlot extends AbstractPartSlot {

    public double kp;//悬挂刚度系数，单位N/m
    public double kd;//悬挂阻尼系数，单位N/(m/s)

    public WheelPartSlot(AbstractPart owner, String locator, DVector3 attachPos, DQuaternion attachRot, double kp, double kd) {
        super(owner, locator, attachPos, attachRot);
        this.kp = kp;
        this.kd = kd;
    }

    @Override
    protected void attachJoint(AbstractPart part) {
        joints.add(OdeHelper.createHinge2Joint(part.dbody.getWorld()));
        joints.getFirst().attach(slotOwnerPart.dbody, part.dbody);
        DVector3 pos = new DVector3();
        slotOwnerPart.dbody.getRelPointPos(childPartAttachPos, pos);
        ((DHinge2Joint) joints.getFirst()).setAnchor(pos);
        ((DHinge2Joint) joints.getFirst()).setAxes(0, 1, 0, 1, 0, 0);
        ((DHinge2Joint) joints.getFirst()).setParamLoStop(-0);//限制轮胎转角
        ((DHinge2Joint) joints.getFirst()).setParamHiStop(0);
        //设置轮胎朝向软约束
        joints.add(OdeHelper.createAMotorJoint(part.dbody.getWorld()));
        joints.get(1).attach(slotOwnerPart.dbody, part.dbody);
        ((DAMotorJoint) joints.get(1)).setNumAxes(1);
        ((DAMotorJoint) joints.get(1)).setAxis(0,1,0,1,0);
        ((DAMotorJoint) joints.get(1)).setParamFMax(500000);
        //设置减震器属性
        ((DHinge2Joint) joints.getFirst()).setParamSuspensionERP(((double) AbstractPhysThread.STEP_SIZE / 1000 * kp) / (((double) AbstractPhysThread.STEP_SIZE / 1000 * kp) + kd));
        ((DHinge2Joint) joints.getFirst()).setParamSuspensionCFM(1 / (((double) AbstractPhysThread.STEP_SIZE / 1000 * kp) + kd));
    }

    @Override
    public boolean slotConditionCheck(AbstractPart part) {
        return part.PART_TYPE == AbstractPart.partTypes.WHEEL;
    }
}
