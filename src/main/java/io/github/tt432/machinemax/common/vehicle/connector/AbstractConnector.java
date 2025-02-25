package io.github.tt432.machinemax.common.vehicle.connector;

import cn.solarmoon.spark_core.physics.collision.BodyPhysicsTicker;
import cn.solarmoon.spark_core.physics.host.PhysicsHost;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import com.jme3.bullet.RotationOrder;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.joints.New6Dof;
import com.jme3.bullet.joints.motors.MotorParam;
import com.jme3.bullet.objects.PhysicsBody;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.datafixers.util.Pair;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.vehicle.Part;
import io.github.tt432.machinemax.common.vehicle.SubPart;
import io.github.tt432.machinemax.common.vehicle.attr.ConnectorAttr;
import io.github.tt432.machinemax.common.vehicle.attr.JointAttr;
import io.github.tt432.machinemax.common.vehicle.data.ConnectionData;
import io.github.tt432.machinemax.common.vehicle.data.PartData;
import io.github.tt432.machinemax.network.payload.ConnectorDetachPayload;
import io.github.tt432.machinemax.util.MMMath;
import io.github.tt432.machinemax.util.data.Axis;
import jme3utilities.math.MyMath;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.List;


@Getter
public abstract class AbstractConnector implements PhysicsHost, BodyPhysicsTicker {
    public final String name;//接口名称
    public final SubPart subPart;//接口所属的零件
    public final List<String> acceptableVariants;//可接受的变体列表
    public final boolean collideBetweenParts;//是否允许零件间碰撞
    public final boolean breakable;//TODO:是否可拆解
    public final boolean internal;//是否为内部接口
    public final ConnectorAttr attr;//接口属性
    public New6Dof joint;//在两个对接口间共享的关节
    @Setter
    public AbstractConnector attachedConnector;//与本接口对接的接口
    public final Transform subPartTransform;//被安装零件的连接点相对本部件质心的位置与姿态
    public final PhysicsRigidBody body;//部件接口安装判定区

    public AbstractConnector(String name, ConnectorAttr attr, SubPart subPart, Transform subPartTransform) {
        this.name = name;
        this.subPart = subPart;
        this.subPartTransform = subPartTransform;
        this.acceptableVariants = attr.acceptableVariants();
        this.collideBetweenParts = attr.collideBetweenParts();
        this.breakable = attr.breakable();
        this.internal = !attr.ConnectedTo().isEmpty();
        this.attr = attr;
        if (!internal) {//为与外部部件连接的接口创建碰撞判定，供玩家通过视线选取
            body = new PhysicsRigidBody(name, this, new BoxCollisionShape(0.125f), PhysicsBody.massForStatic);
            body.setProtectGravity(true);
            body.setGravity(Vector3f.ZERO);
            body.setKinematic(true);
            body.setContactResponse(false);
            body.setCollisionGroup(PhysicsCollisionObject.COLLISION_GROUP_15);
            body.removeCollideWithGroup(PhysicsCollisionObject.COLLISION_GROUP_01);
            body.setCollideWithGroups(PhysicsCollisionObject.COLLISION_GROUP_16);
            bindBody(
                    body,
                    subPart.getPhysicsLevel(),
                    true,
                    (body) -> {
                        body.addPhysicsTicker(this);
                        return null;
                    }
            );
        } else body = null;
    }

    @Override
    public void physicsTick(@NotNull PhysicsCollisionObject physicsCollisionObject, @NotNull PhysicsLevel physicsLevel) {
        if (body != null) {
            if (!this.hasPart()) {//更新判定点位置姿态
                body.setPhysicsLocation(MMMath.RelPointWorldPos(subPartTransform.getTranslation(), subPart.body));
                body.setPhysicsRotation(subPart.body.getPhysicsRotation(null).mult(subPartTransform.getRotation()));
            } else {
                body.setPhysicsLocation(new Vector3f(0, -1000, 0));
//                MachineMax.LOGGER.info("{}角度:{}", name, joint.getAngles(null));
            }
        }
    }

    @Override
    public void mcTick(@NotNull PhysicsCollisionObject physicsCollisionObject, @NotNull Level level) {

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
        if ((!conditionCheck(new PartData(targetConnector.subPart.part)) || !targetConnector.conditionCheck(new PartData(this.subPart.part)) && !force)) {
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
                RotationOrder.XYZ);
        targetConnector.joint = this.joint;
        adjustJoint();//调整关节属性
    }

    /**
     * 根据储存的关节属性调整关节
     */
    protected void adjustJoint() {
        //设置关节属性，默认全自由度锁死，且相连零件之间无碰撞
        joint.setCollisionBetweenLinkedBodies(collideBetweenParts);
        joint.set(MotorParam.LowerLimit, 3, 0);
        joint.set(MotorParam.LowerLimit, 4, 0);
        joint.set(MotorParam.LowerLimit, 5, 0);
        joint.set(MotorParam.UpperLimit, 3, 0);
        joint.set(MotorParam.UpperLimit, 4, 0);
        joint.set(MotorParam.UpperLimit, 5, 0);
        for (int i = 0; i < 6; i++) {//设置各轴关节属性(0~2为XYZ轴平动，3~5为XYZ轴转动)
            if (this instanceof SpecialConnector) {
                JointAttr jointAttr = this.attr.jointAttrs().get(Axis.fromValue(i).name());
                if (jointAttr != null) {
                    if (jointAttr.lowerLimit() != null)
                        joint.set(MotorParam.LowerLimit, i, (float) (jointAttr.lowerLimit() * (i <= 2 ? 1 : Math.PI / 180)));
                    if (jointAttr.upperLimit() != null)
                        joint.set(MotorParam.UpperLimit, i, (float) (jointAttr.upperLimit() * (i <= 2 ? 1 : Math.PI / 180)));
                    if (jointAttr.equilibrium() != null)
                        joint.set(MotorParam.Equilibrium, i, (float) (jointAttr.equilibrium() * (i <= 2 ? 1 : Math.PI / 180)));
                    if (jointAttr.stiffness() != null) {
                        joint.set(MotorParam.Stiffness, i, (float) (jointAttr.stiffness() * (i <= 2 ? 1 : Math.PI / 180)));
                        joint.enableSpring(i, true);
                    }
                    if (jointAttr.damping() != null) {
                        joint.set(MotorParam.Damping, i, (float) (jointAttr.damping() * (i <= 2 ? 1 : Math.PI / 180)));
                        joint.enableSpring(i, true);
                    }
                }
            } else {
                JointAttr jointAttr1 = this.attr.jointAttrs().get(Axis.fromValue(i).name());
                JointAttr jointAttr2 = attachedConnector.attr.jointAttrs().get(Axis.fromValue(i).name());
                //TODO:混合两个AttachPoint关节的属性信息，并应用于关节
            }
        }
    }

    /**
     * 将此接口连接的零件从此接口拆下
     * 特别地，部件内零件之间的内部接口不允许被断开连接
     */
    public void detach(boolean force) {
        if ((force || !internal) && hasPart()) {
            if (!internal) {//若是与外部部件连接的接口，则需要移除载具核心中记录的连接关系
                Pair<AbstractConnector, AttachPointConnector> connection;
                boolean removed;
                if (this instanceof AttachPointConnector) {//若是连接点接口
                    connection = Pair.of(attachedConnector, (AttachPointConnector) this);
                    removed = subPart.part.vehicle.partNet.removeEdge(connection);
                    if (!removed) {
                        connection = Pair.of(this, (AttachPointConnector) attachedConnector);
                        removed = subPart.part.vehicle.partNet.removeEdge(connection);
                    }
                } else {//若是一般接口
                    connection = Pair.of(this, (AttachPointConnector) attachedConnector);
                    removed = subPart.part.vehicle.partNet.removeEdge(connection);
                }
                if (!removed)//检查是否成功移除了连接关系
                    MachineMax.LOGGER.error("零件拆解失败，载具核心中找不到对接口{}与对接口{}的连接关系！", this.getName(), attachedConnector.getName());
                else
                    MachineMax.LOGGER.info("零件拆解成功，接口{}与接口{}已断开连接！", this.getName(), attachedConnector.getName());
                if (!this.subPart.part.level.isClientSide()) {//若是服务端，则向客户端发包通知拆解接口
                    PacketDistributor.sendToPlayersInDimension((ServerLevel) subPart.part.level, new ConnectorDetachPayload(
                            subPart.part.vehicle.uuid,
                            new ConnectionData(connection)
                    ));
                }
            }
            detachJoint();
            if (body != null) body.activate();
            this.attachedConnector.attachedConnector = null;
            this.attachedConnector = null;
        } else if (internal)
            MachineMax.LOGGER.error("{}的内部接口{}不允许被断开连接！", this.subPart.part.name, this.getName());
    }

    protected void detachJoint() {
        if (attachedConnector != null)
            attachedConnector.joint = null;
        getPhysicsLevel().submitTask((a, b) -> {
            getPhysicsLevel().getWorld().removeJoint(joint);
            return null;
        });
    }

    public void adjustTransform(Part part, AbstractConnector partConnector) {
        Transform targetTransform = subPart.body.getTransform(null);
        MyMath.combine(this.subPartTransform, targetTransform, targetTransform);
        MyMath.combine(partConnector.subPartTransform.invert(), targetTransform, targetTransform);
        Transform rootTransform = part.rootSubPart.body.getTransform(null).invert();
        part.rootSubPart.body.setPhysicsTransform(targetTransform);
        for (SubPart subPart : part.subParts.values()) {
            if (subPart == part.rootSubPart) continue;
            Transform transform = subPart.body.getTransform(null);
            MyMath.combine(transform, rootTransform, transform);
            MyMath.combine(transform, targetTransform, transform);
            subPart.body.setPhysicsTransform(transform);
        }
    }

    /**
     * 检查给定零件是否符合本接口的安装要求
     *
     * @param part 要检查的待安装部件
     * @return 给定零件是否满足当前接口安装条件
     */
    public boolean conditionCheck(PartData part) {
        return conditionCheck(part.variant);
    }

    /**
     * 检查给定零件是否符合本接口的安装要求
     *
     * @param variant 要检查的待安装部件的变体类型
     * @return 给定零件是否满足当前接口安装条件
     */
    public boolean conditionCheck(String variant) {
        if (!this.hasPart() && (this.acceptableVariants.isEmpty() || this.acceptableVariants.contains(variant)))
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

    public void addToLevel() {
        if (hasPart() && joint != null) subPart.getPhysicsLevel().submitTask((a, b) -> {
            subPart.getPhysicsLevel().getWorld().addJoint(joint);
            return null;
        });
    }

    public void destroy() {
        detach(true);
        this.removeAllBodies();
    }

    @NotNull
    @Override
    public PhysicsLevel getPhysicsLevel() {
        return subPart.getPhysicsLevel();
    }
}
