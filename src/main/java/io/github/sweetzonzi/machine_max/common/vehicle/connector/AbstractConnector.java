package io.github.sweetzonzi.machine_max.common.vehicle.connector;

import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.PhysicsHost;
import cn.solarmoon.spark_core.physics.body.CollisionGroups;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.sound.SpreadingSoundHelper;
import cn.solarmoon.spark_core.util.PPhase;
import cn.solarmoon.spark_core.util.SparkMathKt;
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
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.vehicle.PartType;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.ConnectorAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.JointAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.event.connector.ConnectorAttachEvent;
import io.github.sweetzonzi.machine_max.common.vehicle.event.connector.ConnectorDetachEvent;
import io.github.sweetzonzi.machine_max.common.vehicle.event.connector.ConnectorTickEvent;
import io.github.sweetzonzi.machine_max.common.vehicle.signal.SignalPort;
import io.github.sweetzonzi.machine_max.common.visual.VisualEffectHelper;
import io.github.sweetzonzi.machine_max.network.payload.ConnectorSyncPayload;
import io.github.sweetzonzi.machine_max.util.MMMath;
import io.github.sweetzonzi.machine_max.util.data.Axis;
import jme3utilities.math.MyMath;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SyncedDataHolder;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;


@Getter
public abstract class AbstractConnector implements PhysicsHost, SyncedDataHolder {
    public final String name;//连接点名称
    public final SubPart subPart;//连接点所属的零件
    public final boolean collideBetweenParts;//是否允许零件间碰撞
    public final boolean internal;//是否为内部连接点
    public final ConnectorAttr attr;//连接点属性
    public New6Dof joint;//在两个连接点间共享的关节
    public final SignalPort signalPort;//连接点资源/信号传输端口
    protected static final EntityDataAccessor<Float> DATA_INTEGRITY_ID = SynchedEntityData.defineId(AbstractConnector.class, EntityDataSerializers.FLOAT);
    protected final SynchedEntityData synchedData;
    protected final ConcurrentLinkedQueue<Float> accumulatedImpact = new ConcurrentLinkedQueue<>();
    protected final ConcurrentLinkedQueue<Float> accumulatedIntegrityChange = new ConcurrentLinkedQueue<>();
    public final float impactReduction;
    public final float impactMultiplier;
    @Setter
    public AbstractConnector attachedConnector;//与本连接点对接的连接点
    public final Transform offsetFromMassCenter;//被安装零件的连接点相对本部件质心的位置与姿态
    @Setter
    public Transform actualTransform;//本安装点为确保法线方向和连接点方向一致所需的实际变换
    public final CollisionShape shape = new BoxCollisionShape(0.125f);//连接点碰撞形状
    public PhysicsRigidBody body;//部件连接点安装判定区
    private final HashMap<String, PhysicsCollisionObject> allPhysicsBodies = new HashMap<>();

    protected AbstractConnector(String name, ConnectorAttr attr, SubPart subPart, Transform offsetFromMassCenter) {
        this.name = name;
        this.subPart = subPart;
        this.offsetFromMassCenter = offsetFromMassCenter;
        this.actualTransform = offsetFromMassCenter.clone();
        this.signalPort = new SignalPort(this, attr.signalTargets(), attr.signalTranslations());
        this.collideBetweenParts = attr.collideBetweenParts();
        this.impactReduction = attr.connectedTo().isEmpty() ? attr.impactReduction() : 0;
        this.impactMultiplier = attr.connectedTo().isEmpty() ? Math.max(attr.impactMultiplier(), 0) : 0;
        this.internal = !attr.connectedTo().isEmpty();
        this.attr = attr;
        SynchedEntityData.Builder syncheddata$builder = new SynchedEntityData.Builder(this);
        syncheddata$builder.define(DATA_INTEGRITY_ID, attr.integrity());
        this.defineSyncedData(syncheddata$builder);
        this.synchedData = syncheddata$builder.build();
        createAttachPointBody(
                MMMath.relPointWorldPos(offsetFromMassCenter.getTranslation(), subPart.body),
                subPart.body.getPhysicsRotation(null).mult(offsetFromMassCenter.getRotation()));
    }

    public void prePhysicsTick() {
        NeoForge.EVENT_BUS.post(new ConnectorTickEvent.PrePhys(this));
        if (!this.hasPart()) {//更新判定点位置姿态
            if (body == null) {
                createAttachPointBody(
                        MMMath.relPointWorldPos(offsetFromMassCenter.getTranslation(), subPart.body),
                        subPart.body.getPhysicsRotation(null).mult(offsetFromMassCenter.getRotation()));
            } else {
                body.setPhysicsLocation(MMMath.relPointWorldPos(offsetFromMassCenter.getTranslation(), subPart.body));
                body.setPhysicsRotation(subPart.body.getPhysicsRotation(null).mult(offsetFromMassCenter.getRotation()));
            }
        } else if (body != null) {
            PhysicsBodyExtensionKt.removePhysicsBody(subPart.getLevel(), body);
            this.body = null;
        }
    }

    public void postPhysicsTick() {
        NeoForge.EVENT_BUS.post(new ConnectorTickEvent.PostPhys(this));
    }

    public void mcTick() {
        NeoForge.EVENT_BUS.post(new ConnectorTickEvent.Pre(this));
        if (subPart.level.isClientSide() && body != null) {
            if (!this.hasPart()) {
                VisualEffectHelper.attachPoints.put(this, body);
            } else {
                VisualEffectHelper.attachPoints.remove(this);
            }
        }
        if (!subPart.level.isClientSide()) {
            handleAccumulatedIntegrityChange();
            handleAccumulatedImpact();
            syncToClient();
        }
        NeoForge.EVENT_BUS.post(new ConnectorTickEvent.Post(this));
    }

    /**
     * <p>线程安全地改变连接点结构完整性，会被在主线程统一处理，参见 {@link #handleAccumulatedIntegrityChange()}</p>
     * <p>Thread-safe change of the interface structure integrity, which will be handled in the main thread, see {@link #handleAccumulatedIntegrityChange()}</p>
     *
     * @param integrity 结构完整性改变量，可小于0 delta of the structure integrity, can be negative
     */
    public void addIntegrity(float integrity) {
        if (!subPart.level.isClientSide()) {
            accumulatedIntegrityChange.add(integrity);
        }
    }

    /**
     * <p>线程安全地对部件造成伤害，伤害会被在主线程统一处理，参见 {@link #handleAccumulatedImpact()}</p>
     * <p>Accumulates impact to the part thread safely, which will be handled in the main thread, see {@link #handleAccumulatedImpact()}</p>
     *
     * @param impact 伤害值 impact value
     */
    public void accumulateImpact(float impact) {
        if (!subPart.level.isClientSide() && impact > 0) {
            accumulatedImpact.add(impact);
        }
    }

    /**
     * <p>处理各线程连接点造成的完整性改变量，在主线程中统一处理，参见 {@link #mcTick()}</p>
     * <p>Handles the integrity change caused by other threads to the connector, which will be handled in the main thread, see {@link #mcTick()}</p>
     */
    protected void handleAccumulatedIntegrityChange() {
        if (!subPart.level.isClientSide() && !accumulatedIntegrityChange.isEmpty()) {
            float totalChange = 0;
            while (!accumulatedIntegrityChange.isEmpty()) {
                totalChange += accumulatedIntegrityChange.poll();
            }
            if (totalChange != 0) {
                setIntegrityInternal(Math.clamp(getIntegrity() + totalChange, 0, getBasicIntegrity()));
            }
        }
    }

    /**
     * <p>处理各线程对连接点造成的冲击，在主线程中统一处理，参见 {@link #mcTick()}</p>
     * <p>Handles the impact caused by other threads to the connector, which will be handled in the main thread, see {@link #mcTick()}</p>
     */
    protected void handleAccumulatedImpact() {
        if (!subPart.level.isClientSide()) {
            float totalImpact = 0;
            if (impactMultiplier >= 0 && !accumulatedImpact.isEmpty()) {
                while (!accumulatedImpact.isEmpty()) {
                    totalImpact += accumulatedImpact.poll();
                }
                totalImpact -= getImpactReduction();
                totalImpact *= getImpactMultiplier();
                if (totalImpact > 0) {
                    if (totalImpact >= getIntegrity() && hasPart()) {
                        //强冲击，立即击落部件
                        subPart.part.vehicle.detachConnector(this);
                        float finalImpact = (subPart.destroyed ? 0.5f * totalImpact : 0.1f * totalImpact);
                        SparkLevel.submitImmediateTask(subPart.level, PPhase.ALL, () -> {
                            SoundEvent sound = SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part.torn_apart"), 64f);
                            SpreadingSoundHelper.playSpreadingSound(subPart.level, sound, SoundSource.NEUTRAL, SparkMathKt.toVec3(subPart.getPosition()), Vec3.ZERO,
                                    (float) ((2 - Math.min(getBasicIntegrity(), finalImpact) / getBasicIntegrity()) * (1f + 0.2f * (Math.random() - 0.5f))),
                                    0.2f + 0.8f * Math.min(getBasicIntegrity(), finalImpact) / getBasicIntegrity());
                        });
                    }
                    //削减部件完整性
                    setIntegrityInternal(Math.clamp(getIntegrity() - (subPart.destroyed ? totalImpact : 0.2f * totalImpact), 0, getBasicIntegrity()));
                }
                accumulatedImpact.clear();
            }
        }
    }

    /**
     * 进行安装条件检查(可选)，尝试将对方连接点对接到本连接点上
     * 特别地，所有类型的连接点都仅可与连接点连接点连接
     *
     * @param targetConnector 要对接的连接点
     * @param force           是否跳过安装条件检查，强制安装
     * @return 是否成功安装
     */
    public boolean attach(SimpleConnector targetConnector, boolean force) {
        if (hasPart()) {
            MachineMax.LOGGER.error("零件安装失败，连接点{}已被占用！", this.getName());
            return false;
        }
        if (targetConnector.hasPart()) {
            MachineMax.LOGGER.error("零件安装失败，连接点{}已被占用！", targetConnector.getName());
            return false;
        }
        if ((!conditionCheck(targetConnector.subPart.part) || !targetConnector.conditionCheck(this.subPart.part) && !force)) {
            MachineMax.LOGGER.error("零件安装失败，零件不符合连接点安装条件！");
            return false;
        } else {
            if (!NeoForge.EVENT_BUS.post(new ConnectorAttachEvent.Pre(this, targetConnector)).isCanceled()) {
                this.attachedConnector = targetConnector;
                targetConnector.attachedConnector = this;
                this.attachJoint(targetConnector);
                if (this.signalPort != null && attachedConnector.signalPort != null) {
                    this.signalPort.onConnectorAttach();
                    attachedConnector.signalPort.onConnectorAttach();
                }
                NeoForge.EVENT_BUS.post(new ConnectorAttachEvent.Post(this, targetConnector));
                return true;
            } else return false;
        }
    }

    /**
     * 进行安装条件检查，尝试将对方连接点对接到本连接点上
     * 特别地，所有类型的连接点都仅可与一般连接点连接
     *
     * @param targetConnector 要对接的连接点
     */
    public boolean attach(SimpleConnector targetConnector) {
        return this.attach(targetConnector, false);
    }

    protected void attachJoint(SimpleConnector targetConnector) {
        this.joint = new New6Dof(this.subPart.body, targetConnector.subPart.body,
                this.actualTransform.getTranslation(), targetConnector.actualTransform.getTranslation(),
                this.actualTransform.getRotation().toRotationMatrix(), targetConnector.actualTransform.getRotation().toRotationMatrix(),
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
        if (!collideBetweenParts) { // 若两零件被设置为不与相邻零件碰撞，则脱落后保持这种关系
//            joint.getBodyA().addToIgnoreList(joint.getBodyB());
//            joint.getBodyB().addToIgnoreList(joint.getBodyA());
        }
        joint.set(MotorParam.LowerLimit, 3, 0);
        joint.set(MotorParam.LowerLimit, 4, 0);
        joint.set(MotorParam.LowerLimit, 5, 0);
        joint.set(MotorParam.UpperLimit, 3, 0);
        joint.set(MotorParam.UpperLimit, 4, 0);
        joint.set(MotorParam.UpperLimit, 5, 0);
        for (Map.Entry<String, JointAttr> entry : attr.jointAttrs().entrySet()) {//设置各轴关节属性(0~2为XYZ轴平动，3~5为XYZ轴转动)
            int i = Axis.getValue(entry.getKey());//获取轴序号
            JointAttr jointAttr = entry.getValue();//获取轴属性
            if (this instanceof AdvancedConnector) {
                if (jointAttr != null) {
                    float m_eff;//有效质量估算值，用于限制关节刚度和阻尼，避免数值不稳定
                    float safe = 0.95f;//安全系数
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
                        float maxStiffness = safe * 4 * m_eff / (1f / getPhysicsLevel().getTps() / getPhysicsLevel().getTps());  // 稳定性条件: k_max = 4·m_eff/Δt²
                        if (jointAttr.stiffness() > maxStiffness)
                            MachineMax.LOGGER.warn("连接点{}(部件{})与连接点{}(部件{})的{}轴的刚度值过大:{}，已自动限制为{}！",
                                    Component.translatable(this.getName()).getString(),
                                    Component.translatable(this.subPart.part.name).getString(),
                                    Component.translatable(attachedConnector.getName()).getString(),
                                    Component.translatable(attachedConnector.subPart.part.name).getString(),
                                    i, jointAttr.stiffness(), maxStiffness);
                        joint.set(MotorParam.Stiffness, i, Math.min(jointAttr.stiffness(), maxStiffness));
                        joint.enableSpring(i, true);
                    }
                    if (jointAttr.damping() != null) {
                        //限制最大阻尼以确保稳定性
                        float maxDamping;
                        float stiffness = joint.get(MotorParam.Stiffness, i) / getPhysicsLevel().getTps() / getPhysicsLevel().getTps() / m_eff;
                        if (stiffness >= 4f - 1e-6f) {
                            maxDamping = 0f; // 刚度已达上限，阻尼只能为0
                        } else {
                            float C_max = (4f - stiffness) / 2f;
                            maxDamping = C_max * m_eff * getPhysicsLevel().getTps();
                        }
                        maxDamping *= safe; // 安全系数
                        if (jointAttr.damping() > maxDamping) {
                            joint.set(MotorParam.MotorErp, i, 0.5f);
                            joint.set(MotorParam.StopErp, i, 0.2f);
                            MachineMax.LOGGER.warn("连接点{}(部件{})与连接点{}(部件{})的{}轴的阻尼值过大:{}，已自动限制为{}！",
                                    Component.translatable(this.getName()).getString(),
                                    Component.translatable(this.subPart.part.name).getString(),
                                    Component.translatable(attachedConnector.getName()).getString(),
                                    Component.translatable(attachedConnector.subPart.part.name).getString(),
                                    i, jointAttr.damping(), maxDamping);
                        }
                        joint.set(MotorParam.Damping, i, Math.min(jointAttr.damping(), maxDamping));
                        joint.set(MotorParam.MotorCfm, i, 1e-5f);
                        joint.set(MotorParam.StopCfm, i, 1e-5f);
                        joint.enableSpring(i, true);
                    }
                }
            } else {
                //TODO:混合两个AttachPoint关节的属性信息，并应用于关节
            }
        }
    }

    /**
     * 将此连接点连接的零件从此连接点拆下
     * 特别地，部件内零件之间的内部连接点不允许被断开连接
     */
    public void detach(boolean destroy) {
        if ((destroy || !internal) && hasPart()) {
            AbstractConnector attachedConnector = this.attachedConnector;
            AbstractConnector advancedConnector = this instanceof SimpleConnector ? attachedConnector : this;
            SimpleConnector simpleConnector = this == advancedConnector ? (SimpleConnector) attachedConnector : (SimpleConnector) this;
            if (destroy || !NeoForge.EVENT_BUS.post(new ConnectorDetachEvent.Pre(advancedConnector, simpleConnector)).isCanceled()) {
                if (this.signalPort != null && attachedConnector.signalPort != null) {
                    this.signalPort.onConnectorDetach();
                    attachedConnector.signalPort.onConnectorDetach();
                }
                detachJoint();
                //重建部件连接点
                if (!destroy) {
                    this.createAttachPointBody(
                            MMMath.relPointWorldPos(offsetFromMassCenter.getTranslation(), subPart.body),
                            subPart.body.getPhysicsRotation(null).mult(offsetFromMassCenter.getRotation()));
                    attachedConnector.createAttachPointBody(
                            MMMath.relPointWorldPos(attachedConnector.offsetFromMassCenter.getTranslation(), attachedConnector.subPart.body),
                            attachedConnector.subPart.body.getPhysicsRotation(null).mult(attachedConnector.offsetFromMassCenter.getRotation()));
                }
                //重置安装姿态变换
                this.actualTransform = this.offsetFromMassCenter.clone();
                this.attachedConnector.actualTransform = this.attachedConnector.offsetFromMassCenter.clone();
                //重置安装状态
                this.attachedConnector.attachedConnector = null;
                this.attachedConnector = null;
                NeoForge.EVENT_BUS.post(new ConnectorDetachEvent.Post(advancedConnector, simpleConnector));
            }
        }
    }

    protected void detachJoint() {
        if (attachedConnector != null)
            attachedConnector.joint = null;
        getPhysicsLevel().submitImmediateTask(PPhase.ALL, () -> {
            if (this.joint.getPhysicsSpace() != null) getPhysicsLevel().getWorld().removeJoint(joint);
            this.joint = null;
            return null;
        });
    }

    /**
     * 计算使要安装的目标连接点的法线方向与本连接点的法线方向相反所需的实际旋转变换，并存储于 {@link #actualTransform} 中
     *
     * @param partConnector  将要安装的连接点
     * @param attachRotation 玩家输入的附加旋转角度
     * @return 旋转变换
     */
    public Transform calculateExtraTransform(
            AbstractConnector partConnector,
            float attachRotation
    ) {
        return calculateExtraTransform(
                partConnector.attr.direction(),
                partConnector.offsetFromMassCenter.getTranslation(),
                partConnector.offsetFromMassCenter.getRotation(),
                attachRotation);
    }

    /**
     * 计算使要安装的目标连接点的法线方向与本连接点的法线方向相反所需的实际旋转变换，并存储于 {@link #actualTransform} 中
     *
     * @param partConnectorDirection 将要安装的连接点方向
     * @param partConnectorOffset    将要安装的连接点位置
     * @param partConnectorRotation  将要安装的连接点姿态
     * @param attachRotation         玩家输入的附加旋转角度
     * @return 旋转变换
     */
    public Transform calculateExtraTransform(
            Axis partConnectorDirection,
            Vector3f partConnectorOffset,
            Quaternion partConnectorRotation,
            float attachRotation
    ) {
        // --- 1. 获取双方连接点的装配法线（局部空间） ---
        Axis targetNormalAxis = this.attr.direction();

        var partNormal = SparkMathKt.toVector3f(Axis.axisToVector(partConnectorDirection));
        var targetNormal = SparkMathKt.toVector3f(Axis.axisToVector(targetNormalAxis));

        // --- 2. 构造“法线对齐旋转”：targetNormal -> -partNormal ---
        Quaternionf alignNormal = new Quaternionf();
        alignNormal.rotationTo(targetNormal, partNormal.negate());

        // --- 3. 构造绕法线的离散旋转（玩家输入） ---
        Quaternionf twist = SparkMathKt.toQuaternionf(Axis.discreteTwist(targetNormalAxis, attachRotation));

        // --- 4. 合成最终局部旋转 ---
        // 顺序非常重要：先对齐法线，再绕法线旋转
        Quaternion finalRotation = SparkMathKt.toBQuaternion(alignNormal.mul(twist));

        // --- 5. 将旋转作用到待安装连接点的 offset ---
        return new Transform(partConnectorOffset, finalRotation.mult(partConnectorRotation));
    }

    public void adjustTransform(
            AbstractConnector partConnector,
            float attachRotation
    ) {
        // 计算待安装连接点的局部变换
        partConnector.actualTransform = calculateExtraTransform(partConnector, attachRotation);
        // 将相对刚体的局部变换与刚体的姿态合并
        Transform targetTransform = mergeTransform(partConnector.actualTransform.invert());

        Transform rootTransform =
                partConnector.subPart.body.getTransform(null).invert();

        // 设置根 SubPart 的物理变换
        partConnector.subPart.body.setPhysicsTransform(targetTransform);

        // 同步所有子部件
        for (SubPart subPart : partConnector.subPart.part.subParts.values()) {
            if (subPart == partConnector.subPart) continue;
            Transform transform = subPart.body.getTransform(null);
            MyMath.combine(transform, rootTransform, transform);
            MyMath.combine(transform, targetTransform, transform);
            subPart.body.setPhysicsTransform(transform);
        }
    }


    public Transform mergeTransform(Transform transform) {
        Transform result = subPart.body.getTransform(null);
        MyMath.combine(this.offsetFromMassCenter, result, result);
        return MyMath.combine(transform, result, result);
    }

    /**
     * <p>获取当前帧关节的偏移，若当前连接点未被连接到另一连接点，则返回零向量</p>
     * <p>Get the frame offset of the joint, if the connector is not connected to another connector, return the zeros</p>
     *
     * @return 当前帧关节的偏移 (meter)
     */
    public Vector3f getPivotOffset() {
        if (attachedConnector != null && joint != null) {
            return joint.getPivotOffset(null);
        } else return Vector3f.ZERO;
    }

    /**
     * <p>获取当前帧关节的旋转，若当前连接点未被连接到另一连接点，则返回零向量</p>
     * <p>Get the frame rotation of the joint, if the connector is not connected to another connector, return the zeros</p>
     *
     * @return 当前帧关节的旋转 (deg, ZYX order)
     */
    public Vector3f getPivotRotation() {
        if (attachedConnector != null && joint != null) {
            return joint.getAngles(null).mult((float) (180f / Math.PI));
        } else return Vector3f.ZERO;
    }

    /**
     * 检查给定零件是否符合本连接点的安装要求
     *
     * @param part 要检查的待安装部件
     * @return 给定零件是否满足当前连接点安装条件
     */
    public boolean conditionCheck(Part part) {
        return conditionCheck(part.type, part.variantName);
    }

    /**
     * 检查给定零件是否符合本连接点的安装要求
     *
     * @param partType 要检查的待安装部件的类型
     * @param variant  要检查的待安装部件的变体类型
     * @return 给定零件是否满足当前连接点安装条件
     */
    public boolean conditionCheck(PartType partType, String variant) {
        if (!this.hasPart()) {
            return attr.conditionCheck(partType, variant);
        } else return false;
    }

    /**
     * 检查该连接点是否安装了零件
     *
     * @return 检查结果
     */
    public boolean hasPart() {
        return this.attachedConnector != null;
    }

    public void addToLevel() {
        if (hasPart() && joint != null) subPart.getPhysicsLevel().submitImmediateTask(PPhase.PRE, () -> {
            if (joint.getPhysicsSpace() == null) {
                if (!joint.getBodyA().isInWorld())
                    subPart.getPhysicsLevel().getWorld().addCollisionObject(joint.getBodyA());
                if (!joint.getBodyB().isInWorld())
                    subPart.getPhysicsLevel().getWorld().addCollisionObject(joint.getBodyB());
                subPart.getPhysicsLevel().getWorld().addJoint(joint);
            }
            return null;
        });
    }

    public void destroy() {
        detach(true);
        if (subPart.part.getLevel().isClientSide())
            VisualEffectHelper.attachPoints.remove(this);
        getPhysicsLevel().submitImmediateTask(PPhase.ALL, () -> {
            if (this.body != null && this.body.isInWorld())
                PhysicsBodyExtensionKt.removePhysicsBody(subPart.getLevel(), this.body);
            return null;
        });
    }

    @Override
    public void onSyncedDataUpdated(@NotNull List<SynchedEntityData.DataValue<?>> newData) {
    }

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> dataAccessor) {
    }

    protected void defineSyncedData(SynchedEntityData.Builder builder) {
    }

    protected void syncToClient() {
        if (!getSubPart().level.isClientSide()) {
            SynchedEntityData synchedentitydata = this.getSynchedData();
            List<SynchedEntityData.DataValue<?>> list = synchedentitydata.packDirty();
            if (list != null) {
                PacketDistributor.sendToPlayersInDimension((ServerLevel) getSubPart().level, new ConnectorSyncPayload(getSubPart().getId(), name, list));
            }
        }
    }

    public void loadData(CompoundTag data) {
        //加载子系统耐久度
        setIntegrityInternal(data.getFloat("integrity"));
    }

    public CompoundTag saveData(CompoundTag data) {
        //保存子系统耐久度
        data.putFloat("integrity", getIntegrity());
        return data;
    }

    private void setIntegrityInternal(float integrity) {
        getSynchedData().set(DATA_INTEGRITY_ID, integrity);
    }

    public float getIntegrity() {
        return getSynchedData().get(DATA_INTEGRITY_ID);
    }

    public float getBasicIntegrity() {
        return attr.integrity();
    }

    @NotNull
    @Override
    public PhysicsLevel getPhysicsLevel() {
        return subPart.getPhysicsLevel();
    }

    private void createAttachPointBody(Vector3f position, Quaternion rotation) {
        if (!internal && this.body == null) {//为与外部部件连接的连接点创建碰撞判定，供玩家通过视线选取
            body = new PhysicsRigidBody(shape, PhysicsBody.massForStatic);
            PhysicsBodyExtensionKt.setOwner(this.body, this);
            body.setProtectGravity(true);
            body.setGravity(Vector3f.ZERO);
            body.setKinematic(true);
            body.setContactResponse(false);
            body.setCollisionGroup(CollisionGroups.TRIGGER);
            body.setCollideWithGroups(CollisionGroups.NONE);
            body.setPhysicsLocation(position);
            body.setPhysicsRotation(rotation);
//            PhysicsBodyExtensionKt.onPrePhysicsTick(body, event -> {
//                prePhysicsTick();
//                return null;
//            });
//            PhysicsBodyExtensionKt.onTick(body, event -> {
//                mcTick();
//                return null;
//            });
            PhysicsBodyExtensionKt.addPhysicsBody(subPart.getLevel(), this.body);
        }
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
