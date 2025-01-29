package io.github.tt432.machinemax.common.part.port;

import com.mojang.datafixers.util.Pair;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.registry.PartPortType;
import io.github.tt432.machinemax.common.sloarphys.body.AbstractPartBody;
import io.github.tt432.machinemax.common.sloarphys.body.PartPortAttachPointBody;
import io.github.tt432.machinemax.util.data.PosRot;
import lombok.Getter;
import lombok.Setter;
import org.ode4j.math.DMatrix3;
import org.ode4j.math.DQuaternion;
import org.ode4j.math.DVector3;
import org.ode4j.math.DVector3C;
import org.ode4j.ode.DJoint;
import org.ode4j.ode.internal.Rotation;

import java.util.HashMap;


public abstract class AbstractPortPort {
    @Getter
    final String name;//槽位名称
    @Getter
    protected final AbstractPartBody portOwnerBody;//槽位所属的零件
    @Getter
    @Setter
    volatile protected AbstractPortPort attachedPort;//与本槽位对接的槽位
    @Getter
    final PosRot childBodyAttachPoint;//被安装零件的连接点相对本部件质心的位置与姿态
    @Getter
    PartPortAttachPointBody childBodyAttachPointBody;//槽位连接点物理对象(连接判定点)
    @Getter
    final HashMap<String, DJoint> joints = HashMap.newHashMap(2);//安装槽包含的关节
    @Getter
    final PartPortType type;//槽位类型

    /**
     * 仅应由子类调用，构造槽位
     *
     * @param typeBodyPair
     * @param nameAttachPointPair
     */
    public AbstractPortPort(Pair<PartPortType, AbstractPartBody> typeBodyPair, Pair<String, PosRot> nameAttachPointPair) {
        this.type = typeBodyPair.getFirst();
        this.portOwnerBody = typeBodyPair.getSecond();
        this.name = nameAttachPointPair.getFirst();
        this.childBodyAttachPoint = nameAttachPointPair.getSecond();
        this.childBodyAttachPointBody = new PartPortAttachPointBody(this);
    }

    //TODO:储存槽位的Tag，为连接部件自动选择变体模型，或安装条件检查提供依据（如，不同位置的车轮采用不同模型）
    public AbstractPortPort(PartPortType type, AbstractPartBody portOwnerBody, String name, PosRot childBodyAttachPoint) {
        this(Pair.of(type, portOwnerBody), Pair.of(name, childBodyAttachPoint));
    }

    /**
     * 当两个槽位激活的连接点判定区发生碰撞时，两个槽位的此方法将被调用
     * 进行安装条件检查(可选)，尝试将对方槽位对接到本槽位上
     * 特别地，所有类型的槽位都仅可与连接点槽位连接
     *
     * @param partPort 要对接的槽位
     * @param force    是否跳过安装条件检查，强制安装
     */
    public void attach(AttachPointPortPort partPort, boolean force) {
        if (hasPart()) {
            this.childBodyAttachPointBody.disable();
            return;
        }
        if(partPort.hasPart()){
            partPort.childBodyAttachPointBody.disable();
            return;
        }
        if ((!slotConditionCheck(partPort.getPortOwnerBody()) || !partPort.slotConditionCheck(this.getPortOwnerBody())) && !force) {
            MachineMax.LOGGER.error("零件安装失败，零件不符合槽位安装条件！");
        } else {
            this.attachedPort = partPort;
            partPort.attachedPort = this;
//            //处理安装偏移
//            DVector3 pos = getWorldAttachPos(partPort);
//            partPort.getPortOwnerBody().getBody().setPosition(pos);//子部件指定安装点对齐槽位安装点
//            //处理安装角
//            DQuaternion rot = getWorldAttachRot(partPort);
//            partPort.getPortOwnerBody().getBody().setQuaternion(rot);//调整姿态
            this.attachJoint(partPort);
            this.childBodyAttachPointBody.disable();
        }
    }

    /**
     * 当两个槽位激活的连接点判定区发生碰撞时，两个槽位的此方法将被调用
     * 进行安装条件检查，尝试将对方槽位对接到本槽位上
     * 特别地，所有类型的槽位都仅可与连接点槽位连接
     *
     * @param bodyPort 要对接的槽位
     */
    public void attach(AttachPointPortPort bodyPort) {
        this.attach(bodyPort, false);
    }

    abstract protected void attachJoint(AbstractPortPort bodyPort);

    /**
     * 将此槽位连接的零件从此槽位拆下
     */
    public void detach(Boolean thisEnable, Boolean AttachedPortEnable) {
        if (hasPart()) {
            detachJoint();
            if (thisEnable) this.childBodyAttachPointBody.enable();
            if (AttachedPortEnable) this.attachedPort.childBodyAttachPointBody.enable();
            this.attachedPort = null;
        }
    }

    protected void detachJoint() {
        for (DJoint joint : this.joints.values()) {
            joint.destroy();
        }
    }

    public DVector3 getWorldAttachPos(AbstractPortPort partPort){
        DVector3 pos = new DVector3();
        this.portOwnerBody.getBody().getRelPointPos(
                this.childBodyAttachPoint.pos()
                        .reSub(partPort.childBodyAttachPoint.pos()), pos);//获取连接点在世界坐标系下的位置
        return pos;
    }

    public DQuaternion getWorldAttachRot(AbstractPortPort partPort){
        DQuaternion rot = new DQuaternion();
        DMatrix3 BodyRot = new DMatrix3().setIdentity();
        DMatrix3 temp = new DMatrix3();
        Rotation.dRfromQ(temp, partPort.childBodyAttachPoint.rot());
        BodyRot.eqMul(temp, BodyRot);
        Rotation.dRfromQ(temp, this.childBodyAttachPoint.rot());
        BodyRot.eqMul(temp, BodyRot).eqMul(this.portOwnerBody.getBody().getRotation(), BodyRot);
        Rotation.dQfromR(rot, BodyRot);
        return rot;
    }

    /**
     * 检查给定零件是否符合本槽位的安装要求
     *
     * @param body 要检查的待安装零件
     * @return 给定零件是否满足当前槽位安装条件
     */
    public boolean slotConditionCheck(AbstractPartBody body) {
        return true;//如有为此槽位指定安装条件的需要，继承此类并重载此方法
    }

    /**
     * 检查该槽位是否安装了零件
     *
     * @return 检查结果
     */
    public boolean hasPart() {
        return this.attachedPort != null;
    }

    public void destroy() {
        detach(false, true);
        childBodyAttachPointBody.getPhysLevel().getWorld().laterConsume(() -> {
            this.childBodyAttachPointBody.getGeoms().getFirst().destroy();
            this.childBodyAttachPointBody.getBody().destroy();
            return null;
        });
    }

}
