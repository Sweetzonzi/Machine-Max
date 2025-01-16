package io.github.tt432.machinemax.common.sloarphys.body;

import cn.solarmoon.spark_core.registry.common.SparkVisualEffects;
import io.github.tt432.machinemax.common.part.slot.AbstractBodySlot;
import net.minecraft.world.level.Level;
import org.ode4j.math.DVector3;
import org.ode4j.ode.DContactBuffer;
import org.ode4j.ode.DFixedJoint;
import org.ode4j.ode.DGeom;
import org.ode4j.ode.OdeHelper;
import org.ode4j.ode.internal.DxBox;

public class PartSlotAttachPointBody extends AbstractBody {

    public final AbstractBodySlot slot;

    public PartSlotAttachPointBody(AbstractBodySlot slot) {
        super("PartSlotAttachPointBody", slot.getSlotOwnerBody().getLevel());
        this.slot = slot;
        DFixedJoint joint = OdeHelper.createFixedJoint(getPhysLevel().getPhysWorld().getWorld());
        DVector3 pos = slot.getChildBodyAttachPoint().getPos();//获取部件槽相对槽位拥有者的安装位置
        slot.getSlotOwnerBody().getBody().getRelPointPos(pos, pos);//获取安装位置的绝对位置
        body.setPosition(pos);
        joint.attach(slot.getSlotOwnerBody().getBody(), body);
        joint.setFixed();
        geoms.add(OdeHelper.laterCreateBox(body, getPhysLevel().getPhysWorld(), new DVector3(0.5,0.5,0.5)));
        this.enable();
    }

    @Override
    protected void onTick() {

    }

    @Override
    protected void onCollide(DGeom dGeom, DContactBuffer dContacts) {

    }

    @Override
    protected void onPhysTick() {
        if (level.isClientSide()) {//调试模式下绘制碰撞箱
            for (DGeom geom : geoms) {
                if (geom instanceof DxBox && geom.isEnabled())
                    SparkVisualEffects.getGEOM().getRenderableBox(geom.getUUID().toString()).refresh(geom, false);
            }
        }
    }

    public void attachBody(AbstractPartBody partBody, String attachPoint, boolean force){
        slot.attachBody(partBody, attachPoint, force);
    }

    @Override
    public void enable() {
        super.enable();
        geoms.getFirst().enable();
    }

    @Override
    public void disable() {
        super.disable();
        geoms.getFirst().disable();
    }
}
