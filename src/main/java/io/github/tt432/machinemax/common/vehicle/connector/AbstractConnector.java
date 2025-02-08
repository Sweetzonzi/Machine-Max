package io.github.tt432.machinemax.common.vehicle.connector;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.vehicle.SubPart;
import io.github.tt432.machinemax.common.vehicle.attr.ConnectorAttr;
import io.github.tt432.machinemax.util.data.PosRot;
import lombok.Getter;
import lombok.Setter;
import org.ode4j.math.DMatrix3;
import org.ode4j.math.DQuaternion;
import org.ode4j.math.DVector3;
import org.ode4j.ode.internal.Rotation;

import java.util.List;


@Getter
public abstract class AbstractConnector {
    public final String name;//接口名称
    public final SubPart subPart;//接口所属的零件
    public final List<String> acceptableVariants;//可接受的变体列表
    public final boolean breakable;//是否可拆解

    @Setter
    public AbstractConnector attachedConnector;//与本接口对接的接口
    final PosRot subPartAttachPoint;//被安装零件的连接点相对本部件质心的位置与姿态

    public AbstractConnector(String name, ConnectorAttr attr, SubPart subPart, PosRot subPartAttachPoint) {
        this.name = name;
        this.subPart = subPart;
        this.subPartAttachPoint = subPartAttachPoint;
        this.acceptableVariants = attr.acceptableVariants();
        this.breakable = attr.breakable();
    }

    /**
     * 进行安装条件检查(可选)，尝试将对方接口对接到本接口上
     * 特别地，所有类型的接口都仅可与连接点接口连接
     *
     * @param targetConnector 要对接的接口
     * @param force           是否跳过安装条件检查，强制安装
     */
    public boolean attach(AttachPointConnector targetConnector, boolean force) {
        if (hasPart()) {
            MachineMax.LOGGER.error("零件安装失败，对接口{}已被占用！", this.getName());
            return false;
        }
        if (targetConnector.hasPart()) {
            MachineMax.LOGGER.error("零件安装失败，对接口{}已被占用！", targetConnector.getName());
            return false;
        }
        if ((!conditionCheck(this.subPart) || !targetConnector.conditionCheck(targetConnector.subPart) && !force)) {
            MachineMax.LOGGER.error("零件安装失败，零件不符合对接口安装条件！");
            return false;
        } else {
            this.attachedConnector = targetConnector;
            targetConnector.attachedConnector = this;
            //处理安装偏移
            DVector3 pos = getWorldAttachPos(targetConnector);
//            targetConnector.getSubPart().getBody().setPosition(pos);//子部件指定安装点对齐槽位安装点
            //处理安装角
            DQuaternion rot = getWorldAttachRot(targetConnector);
//            targetConnector.getSubPart().getBody().setQuaternion(rot);//调整姿态
            this.attachJoint(targetConnector);
            return true;
        }
    }

    /**
     * 进行安装条件检查，尝试将对方接口对接到本接口上
     * 特别地，所有类型的接口都仅可与连接点接口连接
     *
     * @param targetConnector 要对接的接口
     */
    public boolean attach(AttachPointConnector targetConnector) {
        return this.attach(targetConnector, false);
    }

    abstract protected void attachJoint(AttachPointConnector attachPoint);

    /**
     * 将此接口连接的零件从此接口拆下
     */
    public void detach() {
        if (hasPart()) {
            detachJoint();
            this.attachedConnector.attachedConnector = null;
            this.attachedConnector = null;
        }
    }

    protected void detachJoint() {
        //TODO:销毁关节约束
    }

    public DVector3 getWorldAttachPos(AbstractConnector partPort) {
        DVector3 pos = new DVector3();
//        this.subPart.getBody().getRelPointPos(
//                this.subPartAttachPoint.pos()
//                        .reSub(partPort.subPartAttachPoint.pos()), pos);//获取连接点在世界坐标系下的位置
        return pos;
    }

    public DQuaternion getWorldAttachRot(AbstractConnector partPort) {
        DQuaternion rot = new DQuaternion();
        DMatrix3 BodyRot = new DMatrix3().setIdentity();
        DMatrix3 temp = new DMatrix3();
        Rotation.dRfromQ(temp, partPort.subPartAttachPoint.rot());
        BodyRot.eqMul(temp, BodyRot);
        Rotation.dRfromQ(temp, this.subPartAttachPoint.rot());
//        BodyRot.eqMul(temp, BodyRot).eqMul(this.subPart.getBody().getRotation(), BodyRot);
        Rotation.dQfromR(rot, BodyRot);
        return rot;
    }

    /**
     * 检查给定零件是否符合本接口的安装要求
     *
     * @param subPart 要检查的待安装零件
     * @return 给定零件是否满足当前接口安装条件
     */
    public boolean conditionCheck(SubPart subPart) {
        if (this.acceptableVariants.isEmpty() || this.acceptableVariants.contains(subPart.part.variant))
            return true;
        else
            return false;
        //TODO:tag检查
    }

    /**
     * 检查该接口是否安装了零件
     *
     * @return 检查结果
     */
    public boolean hasPart() {
        return this.attachedConnector != null;
    }

    public void destroy() {
        detach();
    }

}
