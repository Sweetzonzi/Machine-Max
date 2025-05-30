package io.github.sweetzonzi.machinemax.common.vehicle.connector;

import cn.solarmoon.spark_core.physics.collision.PhysicsCollisionObjectTicker;
import cn.solarmoon.spark_core.physics.host.PhysicsHost;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.util.PPhase;
import com.jme3.bullet.RotationOrder;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.joints.New6Dof;
import com.jme3.bullet.joints.motors.MotorParam;
import com.jme3.bullet.objects.PhysicsBody;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Matrix3f;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.datafixers.util.Pair;
import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.client.renderer.PartAssemblyRenderer;
import io.github.sweetzonzi.machinemax.common.registry.MMVisualEffects;
import io.github.sweetzonzi.machinemax.common.vehicle.Part;
import io.github.sweetzonzi.machinemax.common.vehicle.PartType;
import io.github.sweetzonzi.machinemax.common.vehicle.SubPart;
import io.github.sweetzonzi.machinemax.common.vehicle.VehicleManager;
import io.github.sweetzonzi.machinemax.common.vehicle.attr.ConnectorAttr;
import io.github.sweetzonzi.machinemax.common.vehicle.attr.JointAttr;
import io.github.sweetzonzi.machinemax.common.vehicle.data.ConnectionData;
import io.github.sweetzonzi.machinemax.common.vehicle.data.PartData;
import io.github.sweetzonzi.machinemax.common.vehicle.signal.SignalPort;
import io.github.sweetzonzi.machinemax.network.payload.assembly.ConnectorDetachPayload;
import io.github.sweetzonzi.machinemax.util.MMMath;
import io.github.sweetzonzi.machinemax.util.data.Axis;
import jme3utilities.math.MyMath;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;


@Getter
public abstract class AbstractConnector implements PhysicsHost, PhysicsCollisionObjectTicker {
    public final String name;//接口名称
    public final SubPart subPart;//接口所属的零件
    public final List<String> acceptableVariants;//可接受的变体列表
    public final boolean collideBetweenParts;//是否允许零件间碰撞
    public final boolean breakable;//TODO:是否可拆解
    public final boolean internal;//是否为内部接口
    public final ConnectorAttr attr;//接口属性
    public New6Dof joint;//在两个对接口间共享的关节
    public final SignalPort signalPort;//接口资源/信号传输端口
    @Setter
    public AbstractConnector attachedConnector;//与本接口对接的接口
    public final Transform subPartTransform;//被安装零件的连接点相对本部件质心的位置与姿态
    public final CollisionShape shape = new BoxCollisionShape(0.25f);//接口碰撞形状
    public PhysicsRigidBody body;//部件接口安装判定区

    protected AbstractConnector(String name, ConnectorAttr attr, SubPart subPart, Transform subPartTransform) {
        this.name = name;
        this.subPart = subPart;
        this.subPartTransform = subPartTransform;
        this.acceptableVariants = attr.acceptableVariants();
        this.signalPort = new SignalPort(this, attr.signalTargets());
        this.collideBetweenParts = attr.collideBetweenParts();
        this.breakable = attr.breakable();
        this.internal = !attr.ConnectedTo().isEmpty();
        this.attr = attr;
        createAttachPointBody(
                MMMath.relPointWorldPos(subPartTransform.getTranslation(), subPart.body),
                subPart.body.getPhysicsRotation(null).mult(subPartTransform.getRotation()));
    }

    @Override
    public void prePhysicsTick(@NotNull PhysicsCollisionObject physicsCollisionObject, @NotNull PhysicsLevel physicsLevel) {
        if (!this.hasPart()) {//更新判定点位置姿态
            body.setPhysicsLocation(MMMath.relPointWorldPos(subPartTransform.getTranslation(), subPart.body));
            body.setPhysicsRotation(subPart.body.getPhysicsRotation(null).mult(subPartTransform.getRotation()));
        } else {
            removeAllBodies();
            this.body = null;
        }
    }

    @Override
    public void mcTick(@NotNull PhysicsCollisionObject physicsCollisionObject, @NotNull Level level) {
        if (level.isClientSide() && body != null) {
            PartAssemblyRenderer renderer = MMVisualEffects.getPART_ASSEMBLY();
            if (!this.hasPart()) {
                renderer.attachPoints.put(this, body);
            } else {
                renderer.attachPoints.remove(this);
            }
        }
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
            if (this.signalPort != null && attachedConnector.signalPort != null) {
                this.signalPort.onConnectorAttach();
                attachedConnector.signalPort.onConnectorAttach();
            }
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
        for (Map.Entry<String, JointAttr> entry : attr.jointAttrs().entrySet()) {//设置各轴关节属性(0~2为XYZ轴平动，3~5为XYZ轴转动)
            int i = Axis.getValue(entry.getKey());//获取轴序号
            JointAttr jointAttr = entry.getValue();//获取轴属性
            if (this instanceof SpecialConnector) {
                if (jointAttr != null) {
                    float m_eff;//有效质量估算值，用于限制关节刚度和阻尼，避免数值不稳定
                    float safe = 0.8f;//安全系数
                    if (i <= 2)//平动轴以两物体质量计算等效质量
                        m_eff = computeEffectiveMass(joint.getBodyA().getMass(), joint.getBodyB().getMass());
                    else {//转动轴以两物体转动惯量计算等效质量
                        Vector3f axis = joint.getAxis(i - 3, null);
                        m_eff = computeEffectiveInertia(
                                computeMomentOfInertia(joint.getBodyA(), axis),
                                computeMomentOfInertia(joint.getBodyB(), axis)
                        );
                    }
                    if (jointAttr.lowerLimit() != null)
                        joint.set(MotorParam.LowerLimit, i, (float) (jointAttr.lowerLimit() * (i <= 2 ? 1 : Math.PI / 180)));
                    if (jointAttr.upperLimit() != null)
                        joint.set(MotorParam.UpperLimit, i, (float) (jointAttr.upperLimit() * (i <= 2 ? 1 : Math.PI / 180)));
                    if (jointAttr.equilibrium() != null)
                        joint.set(MotorParam.Equilibrium, i, (float) (jointAttr.equilibrium() * (i <= 2 ? 1 : Math.PI / 180)));
                    if (jointAttr.stiffness() != null) {
                        float maxStiffness = 4 * m_eff / (1f / 60f / 60f);  // 稳定性条件: k_max = 4·m_eff/Δt²
                        if (jointAttr.stiffness() > maxStiffness)
                            MachineMax.LOGGER.warn("接口{}(部件{})与接口{}(部件{})的{}轴的刚度值过大:{}，已自动限制为{}！", this.getName(), this.subPart.part.name, attachedConnector.getName(), attachedConnector.subPart.part.name, i, jointAttr.stiffness(), maxStiffness);
                        joint.set(MotorParam.Stiffness, i, Math.min(jointAttr.stiffness(), maxStiffness));
                        joint.enableSpring(i, true);
                    }
                    if (jointAttr.damping() != null) {
                        //限制最大阻尼以确保稳定性
                        float maxDamping;
                        float stiffness = joint.get(MotorParam.Stiffness, i);
                        if (stiffness > 0)//最大阻尼估算值
                            maxDamping = safe * Math.min((float) (2 * Math.sqrt(stiffness * m_eff)), safe * 2 * m_eff * 60f);
                        else maxDamping = safe * 2 * m_eff * 60f;//纯阻尼系统的处理，采用显式欧拉稳定性条件
                        if (jointAttr.damping() > maxDamping) {
                            joint.set(MotorParam.MotorErp, i, 0.1f);
                            joint.set(MotorParam.StopErp, i, 0.1f);
                            MachineMax.LOGGER.warn("接口{}(部件{})与接口{}(部件{})的{}轴的阻尼值过大:{}，已自动限制为{}！", this.getName(), this.subPart.part.name, attachedConnector.getName(), attachedConnector.subPart.part.name, i, jointAttr.damping(), maxDamping);
                        }
                        joint.set(MotorParam.Damping, i, Math.min(jointAttr.damping(), maxDamping));
                        joint.set(MotorParam.MotorCfm, i, 1e-4f);
                        joint.set(MotorParam.StopCfm, i, 1e-4f);
                        joint.enableSpring(i, true);
                    }
                }
            } else {
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
                    if (!removed && attachedConnector instanceof AttachPointConnector) {
                        connection = Pair.of(this, (AttachPointConnector) attachedConnector);
                        removed = subPart.part.vehicle.partNet.removeEdge(connection);
                    }
                } else {//若是一般接口
                    connection = Pair.of(this, (AttachPointConnector) attachedConnector);
                    removed = subPart.part.vehicle.partNet.removeEdge(connection);
                }
                if (!removed)//检查是否成功移除了连接关系
                    MachineMax.LOGGER.error("零件拆解失败，载具核心中找不到对接口{}与对接口{}的连接关系！", this.getName(), attachedConnector.getName());
                else {
                    MachineMax.LOGGER.debug("零件拆解成功，接口{}与接口{}已断开连接！", this.getName(), attachedConnector.getName());
                    if (!this.subPart.part.level.isClientSide()) {//若是服务端，则向客户端发包通知拆解接口
                        PacketDistributor.sendToPlayersInDimension((ServerLevel) subPart.part.level, new ConnectorDetachPayload(
                                subPart.part.vehicle.uuid,
                                List.of(new ConnectionData(connection))
                        ));
                    }
                }
            }
            if (this.signalPort != null && attachedConnector.signalPort != null) {
                this.signalPort.onConnectorDetach();
                attachedConnector.signalPort.onConnectorDetach();
            }
            detachJoint();
            //重建部件连接点
            this.createAttachPointBody(
                    MMMath.relPointWorldPos(subPartTransform.getTranslation(), subPart.body),
                    subPart.body.getPhysicsRotation(null).mult(subPartTransform.getRotation()));
            attachedConnector.createAttachPointBody(
                    MMMath.relPointWorldPos(attachedConnector.subPartTransform.getTranslation(), attachedConnector.subPart.body),
                    attachedConnector.subPart.body.getPhysicsRotation(null).mult(attachedConnector.subPartTransform.getRotation()));
            this.attachedConnector.attachedConnector = null;
            this.attachedConnector = null;
        } else if (internal)
            MachineMax.LOGGER.error("{}的内部接口{}不允许被断开连接！", this.subPart.part.name, this.getName());
    }

    protected void detachJoint() {
        if (attachedConnector != null)
            attachedConnector.joint = null;
        getPhysicsLevel().submitImmediateTask(PPhase.PRE, () -> {
            getPhysicsLevel().getWorld().removeJoint(joint);
            this.joint = null;
            return null;
        });
    }

    public void adjustTransform(Part part, AbstractConnector partConnector) {
        Transform targetTransform = mergeTransform(partConnector.subPartTransform.invert());
        Transform rootTransform = part.rootSubPart.body.getTransform(null).invert();
        part.rootSubPart.body.setPhysicsTransform(targetTransform);
        //相应调整部件内子零件的位置姿态
        for (SubPart subPart : part.subParts.values()) {
            if (subPart == part.rootSubPart) continue;
            Transform transform = subPart.body.getTransform(null);
            MyMath.combine(transform, rootTransform, transform);
            MyMath.combine(transform, targetTransform, transform);
            subPart.body.setPhysicsTransform(transform);
        }
    }

    public Transform mergeTransform(Transform transform) {
        Transform result = subPart.body.getTransform(null);
        MyMath.combine(this.subPartTransform, result, result);
        return MyMath.combine(transform, result, result);
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
     * @param partType 要检查的待安装部件的类型
     * @param variant  要检查的待安装部件的变体类型
     * @return 给定零件是否满足当前接口安装条件
     */
    public boolean conditionCheck(PartType partType, String variant) {
        return conditionCheck(variant);
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
        if (hasPart() && joint != null) subPart.getPhysicsLevel().submitImmediateTask(PPhase.PRE, () -> {
            subPart.getPhysicsLevel().getWorld().addJoint(joint);
            return null;
        });
    }

    public void destroy() {
        detach(true);
        if (subPart.part.level.isClientSide())
            MMVisualEffects.getPART_ASSEMBLY().attachPoints.remove(this);
        this.removeAllBodies();
    }

    @NotNull
    @Override
    public PhysicsLevel getPhysicsLevel() {
        return subPart.getPhysicsLevel();
    }

    private void createAttachPointBody(Vector3f position, Quaternion rotation) {
        if (!internal && this.body == null) {//为与外部部件连接的接口创建碰撞判定，供玩家通过视线选取
            body = new PhysicsRigidBody(name, this, shape, PhysicsBody.massForStatic);
            body.setProtectGravity(true);
            body.setGravity(Vector3f.ZERO);
            body.setKinematic(true);
            body.setContactResponse(false);
            body.setCollisionGroup(VehicleManager.COLLISION_GROUP_NO_COLLISION);
            body.setCollideWithGroups(VehicleManager.COLLISION_GROUP_NONE);
            body.setPhysicsLocation(position);
            body.setPhysicsRotation(rotation);
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

    // 计算物体绕特定轴的转动惯量
    private static float computeMomentOfInertia(PhysicsRigidBody body, Vector3f axisWorld) {

        // 静态物体处理
        if (body.getMass() == 0f) {
            return Float.MAX_VALUE; // 静态物体视为无穷大惯量
        }

        // 获取逆转动惯量张量（世界坐标系）
        Matrix3f invInertiaWorld = new Matrix3f();
        body.getInverseInertiaWorld(invInertiaWorld);

        // 计算轴方向上的逆转动惯量: (axis^T * invI * axis)
        Vector3f tmp = new Vector3f();
        invInertiaWorld.mult(axisWorld, tmp);
        float invI_axis = axisWorld.dot(tmp);

        // 避免除零错误
        if (invI_axis <= 1e-6f) return Float.MAX_VALUE;

        return 1f / invI_axis; // 实际转动惯量 = 1 / (axis^T * invI * axis)
    }

    // 计算等效转动惯量
    private static float computeEffectiveInertia(float I1, float I2) {
        // 处理静态物体（无穷大惯量）
        if (I1 == Float.MAX_VALUE || I1 <= 0) return I2;
        if (I2 == Float.MAX_VALUE || I2 <= 0) return I1;

        // 标准公式: I_eff = (I1 * I2) / (I1 + I2)
        return (I1 * I2) / (I1 + I2);
    }

    // 计算等效质量
    private static float computeEffectiveMass(float m1, float m2) {
        // 处理静态物体（无穷大质量）
        if (m1 == Float.MAX_VALUE || m1 <= 0) return m2;
        if (m2 == Float.MAX_VALUE || m2 <= 0) return m1;

        // 标准公式: m_eff = (m1 * m2) / (m1 + m2)
        return (m1 * m2) / (m1 + m2);
    }
}
