package io.github.tt432.machinemax.common.sloarphys.body;

import cn.solarmoon.spark_core.registry.common.SparkVisualEffects;
import io.github.tt432.machinemax.common.part.port.AbstractPortPort;
import io.github.tt432.machinemax.common.part.port.AttachPointPortPort;
import io.github.tt432.machinemax.common.registry.MMBodyTypes;
import org.ode4j.math.DVector3;
import org.ode4j.ode.DContactBuffer;
import org.ode4j.ode.DFixedJoint;
import org.ode4j.ode.DGeom;
import org.ode4j.ode.OdeHelper;
import org.ode4j.ode.internal.DxBox;

public class PartPortAttachPointBody extends AbstractBody {

    public final AbstractPortPort port;

    public PartPortAttachPointBody(AbstractPortPort port) {
        super(port.getName(), port.getPortOwnerBody().getLevel());
        body = OdeHelper.createBody(MMBodyTypes.getPART_SLOT_ATTACH_POINT().get(), port, port.getName(), false, getPhysLevel().getWorld());
        body.disable();
        body.onTick(this::onTick);
        body.onPhysTick(this::onPhysTick);
        this.port = port;
        DFixedJoint joint = OdeHelper.createFixedJoint(getPhysLevel().getWorld());
        DVector3 pos = (DVector3) port.getChildBodyAttachPoint().pos();//获取部件槽相对槽位拥有者的安装位置
        port.getPortOwnerBody().getBody().getRelPointPos(pos, pos);//获取安装位置的绝对位置
        body.setPosition(pos);
        joint.attach(port.getPortOwnerBody().getBody(), body);
        joint.setFixed();
        DGeom attachPointGeom = OdeHelper.laterCreateBox(body, getPhysLevel().getWorld(), new DVector3(0.5, 0.5, 0.5));
        attachPointGeom.onCollide(this::onCollide);
        geoms.add(attachPointGeom);
        this.enable();
    }

    @Override
    protected void onTick() {

    }

    @Override
    protected void onCollide(DGeom dGeom, DContactBuffer dContacts) {
        if (dGeom.getBody().getOwner() instanceof AttachPointPortPort otherPort) {
            if (dGeom.getBody() == this.body) return;//不允许自我连接
            if(this.port.getPortOwnerBody() == otherPort.getPortOwnerBody()) return;//不允许同部件连接点自我连接
            if (this.port.getPortOwnerBody().getPart().core == null) return;
            if (otherPort.getPortOwnerBody().getPart().core == null) return;
            if (otherPort.getPortOwnerBody().getPart().core == this.port.getPortOwnerBody().getPart().core) {
                this.port.attach(otherPort, false);
            }
        }
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
