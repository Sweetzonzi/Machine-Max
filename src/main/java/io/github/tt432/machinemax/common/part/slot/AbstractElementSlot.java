package io.github.tt432.machinemax.common.part.slot;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.part.PartElement;
import io.github.tt432.machinemax.util.data.PosRot;
import lombok.Getter;
import lombok.Setter;
import org.ode4j.math.DMatrix3;
import org.ode4j.math.DVector3;
import org.ode4j.ode.DJoint;
import org.ode4j.ode.internal.Rotation;

import java.util.HashMap;


public abstract class AbstractElementSlot {
    @Getter
    final String name;//槽位名称
    @Getter
    protected final PartElement slotOwnerElement;//槽位所属的零件
    @Getter
    @Setter
    protected PartElement childElement;//槽位上安装的零件
    @Getter
    final PosRot childElementAttachPoints;//被安装零件的连接点相对本部件质心的位置与姿态
    @Getter
    final HashMap<String, DJoint> joints = HashMap.newHashMap(2);//安装槽包含的关节

    //TODO:储存槽位的Tag，为连接部件自动选择变体模型，或安装条件检查提供依据（如，不同位置的车轮采用不同模型）
    public AbstractElementSlot(String name, PartElement slotOwnerElement, PosRot childElementAttachPoint) {
        this.name = name;
        this.slotOwnerElement = slotOwnerElement;
        this.childElementAttachPoints = childElementAttachPoint;
    }

    /**
     * 将给定的零件安装到槽位上
     * @param element 要安装的零件
     * @param attachPoint 槽位要对接的零件的连接点
     * @param force 是否跳过安装条件检查，强制安装
     */
    public void attachElement(PartElement element, String attachPoint, boolean force) {
        if (hasPart()) {
            MachineMax.LOGGER.error("零件安装失败，槽位已被占用！");
        } else if (!slotConditionCheck(element) && !force) {
            MachineMax.LOGGER.error("零件安装失败，零件不符合槽位安装条件！");
        } else {
            this.childElement = element;
            if (element.getMotherElement() != null) element.setMotherElement(this.slotOwnerElement);
            element.getParentElementAttachSlots().put(attachPoint, this);
            //处理安装偏移
            DVector3 pos = new DVector3();
            this.slotOwnerElement.getBody().vectorToWorld(
                    this.childElementAttachPoints.getPos()
                            .sub(element.getParentElementAttachPoints().get(attachPoint).getPos()), pos);//获取连接点在世界坐标系下的位置
            element.getBody().setPosition(pos);//子部件指定安装点对齐槽位安装点
            //处理安装角
            DMatrix3 elementRot = new DMatrix3().setIdentity();
            DMatrix3 temp = new DMatrix3();
            Rotation.dRfromQ(temp, element.getParentElementAttachPoints().get(attachPoint).rot());
            elementRot.eqMul(temp,elementRot);
            Rotation.dRfromQ(temp, this.childElementAttachPoints.getRot());
            elementRot.eqMul(temp,elementRot).eqMul(this.slotOwnerElement.getBody().getRotation(), elementRot);
            element.getBody().setRotation(elementRot);//调整姿态
            attachJoint(element, attachPoint);//连接零件
        }
    }

    /**
     * 将给定的零件安装到槽位上，进行安装条件检查
     * @param element 要安装的零件
     * @param attachPoint 槽位要对接的零件的连接点
     */
    public void attachElement(PartElement element, String attachPoint) {
        attachElement(element, attachPoint, false);
    }

    /**
     * 将给定的零件安装到槽位上，进行安装条件检查，安装点为给定零件的质心
     * @param element 要安装的零件
     */
    public void attachElement(PartElement element) {
        attachElement(element, "MassCenter", false);
    }

    abstract protected void attachJoint(PartElement element, String childPartAttachPoint);

    /**
     * 将此槽位连接的零件从此槽位拆下
     */
    public void detachElement() {
        if (hasPart()) {
            this.childElement.setMotherElement(null);
            this.childElement = null;
            detachJoint();
        }
    }

    protected void detachJoint() {
        for (DJoint joint : this.joints.values()) {
            joint.destroy();
        }
    }

    /**
     * 检查给定零件是否符合本槽位的安装要求
     *
     * @param element 要检查的待安装零件
     * @return 给定零件是否满足当前槽位安装条件
     */
    public boolean slotConditionCheck(PartElement element) {
        return true;//如有为此槽位指定安装条件的需要，继承此类并重载此方法
    }

    /**
     * 检查该槽位是否安装了零件
     *
     * @return 检查结果
     */
    public boolean hasPart() {
        return this.childElement != null;
    }

}
