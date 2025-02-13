package io.github.tt432.machinemax.common.vehicle.connector;

import com.jme3.bullet.RotationOrder;
import com.jme3.bullet.joints.New6Dof;
import com.jme3.math.Transform;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.vehicle.SubPart;
import io.github.tt432.machinemax.common.vehicle.attr.ConnectorAttr;
import com.mojang.datafixers.util.Pair;
import io.github.tt432.machinemax.common.vehicle.data.ConnectionData;
import io.github.tt432.machinemax.network.payload.ConnectorDetachPayload;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;


@Getter
public abstract class AbstractConnector {
    public final String name;//接口名称
    public final SubPart subPart;//接口所属的零件
    public final List<String> acceptableVariants;//可接受的变体列表
    public final boolean breakable;//是否可拆解
    public final boolean internal;//是否为内部接口
    public final ConnectorAttr attr;//接口属性
    public New6Dof joint;//关节
    @Setter
    public AbstractConnector attachedConnector;//与本接口对接的接口
    public final Transform subPartTransform;//被安装零件的连接点相对本部件质心的位置与姿态

    public AbstractConnector(String name, ConnectorAttr attr, SubPart subPart, Transform subPartTransform) {
        this.name = name;
        this.subPart = subPart;
        this.subPartTransform = subPartTransform;
        this.acceptableVariants = attr.acceptableVariants();
        this.breakable = attr.breakable();
        this.internal = !attr.ConnectedTo().isEmpty();
        this.attr = attr;
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

    protected void attachJoint(AttachPointConnector targetConnector) {
        this.joint = new New6Dof(this.subPart.body, targetConnector.subPart.body,
                this.subPartTransform.getTranslation(), targetConnector.subPartTransform.getTranslation(),
                this.subPartTransform.getRotation().toRotationMatrix(), targetConnector.subPartTransform.getRotation().toRotationMatrix(),
                RotationOrder.ZYX);
        targetConnector.joint = this.joint;
    }

    /**
     * 将此接口连接的零件从此接口拆下
     * 特别地，部件内零件之间的内部接口不允许被断开连接
     */
    public void detach(boolean force) {
        if (force || (!internal && hasPart())) {
            if (!internal) {//若是与外部部件连接的接口，则需要移除载具核心中记录的连接关系
                Pair<AbstractConnector, AttachPointConnector> connection;
                boolean removed;
                if (this instanceof AttachPointConnector) {//若是连接点接口
                    connection = Pair.of(attachedConnector, (AttachPointConnector) this);
                    removed = subPart.part.vehicle.partNet.removeEdge(connection);
                    if (!removed) {
                        connection = Pair.of(attachedConnector, (AttachPointConnector) this);
                        removed = subPart.part.vehicle.partNet.removeEdge(connection);
                    }
                } else {//若是一般接口
                    connection = Pair.of(this, (AttachPointConnector) attachedConnector);
                    removed = subPart.part.vehicle.partNet.removeEdge(connection);
                }
                if (!removed)//检查是否成功移除了连接关系
                    MachineMax.LOGGER.error("零件拆解失败，载具核心中找不到对接口{}与对接口{}的连接关系！", this.getName(), attachedConnector.getName());
                if (!this.subPart.part.level.isClientSide()) {//若是服务端，则向客户端发包通知拆解接口
                    PacketDistributor.sendToPlayersInDimension((ServerLevel) subPart.part.level, new ConnectorDetachPayload(
                            subPart.part.vehicle.uuid,
                            new ConnectionData(connection)
                    ));
                }
            }
            detachJoint();
            this.attachedConnector.attachedConnector = null;
            this.attachedConnector = null;
        } else if (internal)
            MachineMax.LOGGER.error("{}的内部接口{}不允许被断开连接！", this.subPart.part.name, this.getName());
    }

    protected void detachJoint() {
        if (attachedConnector != null)
            attachedConnector.joint = null;
        joint.destroy();//销毁关节约束
    }

    /**
     * 检查给定零件是否符合本接口的安装要求
     *
     * @param subPart 要检查的待安装零件
     * @return 给定零件是否满足当前接口安装条件
     */
    public boolean conditionCheck(SubPart subPart) {
        if (this.acceptableVariants.isEmpty() || this.acceptableVariants.contains(subPart.part.variant))
            //TODO:tag检查
            return true;
        else
            return false;
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
        detach(true);
    }

}
