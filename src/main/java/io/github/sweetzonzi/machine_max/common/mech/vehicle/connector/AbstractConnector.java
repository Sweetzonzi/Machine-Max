package io.github.sweetzonzi.machine_max.common.mech.vehicle.connector;

import io.github.sweetzonzi.machine_max.common.mech.part.AbstractPart;
import io.github.sweetzonzi.machine_max.common.mech.part.AbstractPartType;
import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.physics.PhysicsHost;
import cn.solarmoon.spark_core.physics.body.CollisionGroups;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.api.SpreadingSoundHelper;
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
import io.github.sweetzonzi.machine_max.common.mech.energy.MechPowerPort;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalPort;

import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.IPartAssembly;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.connector.ConnectorAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.connector.JointAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.event.connector.ConnectorAttachEvent;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.event.connector.ConnectorDetachEvent;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.event.connector.ConnectorTickEvent;
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
import net.minecraft.server.MinecraftServer;
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


@Getter
public abstract class AbstractConnector implements PhysicsHost, SyncedDataHolder {
    public final String name;//连接点名称
    public final SubPart subPart;//连接点所属的零件
    public final boolean collideBetweenParts;//是否允许零件间碰撞
    public final boolean internal;//是否为内部连接点
    public final ConnectorAttr attr;//连接点属性
    public New6Dof joint;//在两个连接点间共享的关节
    public final SignalPort signalPort;//连接点资源/信号传输端口
    public final MechPowerPort mechanicalEnergyPort;//连接点机械能传输端口
    protected static final EntityDataAccessor<Float> DATA_INTEGRITY_ID = SynchedEntityData.defineId(AbstractConnector.class, EntityDataSerializers.FLOAT);
    protected final SynchedEntityData synchedData;
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
        this.signalPort = new SignalPort(this, attr.getSignalTargets(), attr.getSignalTranslations());
        this.mechanicalEnergyPort = new MechPowerPort(this, attr.getPowerTarget());
        this.collideBetweenParts = attr.hasCollideBetweenParts();
        this.internal = attr.isInternal();
        this.attr = attr;
        SynchedEntityData.Builder syncheddata$builder = new SynchedEntityData.Builder(this);
        syncheddata$builder.define(DATA_INTEGRITY_ID, attr.getIntegrity());
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

    /**
     * 连接点的每 tick 生命周期：同步数据与客户端表现。
     *
     * <p>结构冲击与完整性变更不在这里结算——它们已经由主线程入口
     * {@link #applyImpact(float)} 与 {@link #changeIntegrity(float)} 即时应用，
     * 连接点不维护待处理队列。</p>
     *
     * <p><b>调用线程：</b>主线程（服务端与客户端）。</p>
     */
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
            syncToClient();
        }
        NeoForge.EVENT_BUS.post(new ConnectorTickEvent.Post(this));
    }

    /**
     * 主线程即时改变连接点结构完整性（维修 / 加固）。
     *
     * <p>跨线程投递由零件命中记录承担；需要从其它线程变更完整性时，应先把数据交给主线程。</p>
     *
     * <p><b>调用线程：</b>主线程。非主线程调用会被拒绝并记录错误。</p>
     *
     * @param delta 结构完整性改变量，可小于 0
     */
    public void changeIntegrity(float delta) {
        if (subPart.level.isClientSide() || delta == 0f) return;
        if (!checkMainThread("changeIntegrity")) return;
        setIntegrityInternal(Math.clamp(getIntegrity() + delta, 0f, getBasicIntegrity()));
    }

    /**
     * 主线程即时应用一次命中分配到的结构冲击。
     *
     * <p>每次应用都独立完成减免、倍率、完整性更新与断开判定（逐次命中规则），
     * 不按 tick 合并多笔冲击。冲击量达到当前完整性且连接有效时走
     * {@link IPartAssembly#disconnect} 的正常拆分流程；失效物理关节的移除由
     * {@link #detachJoint()} 投递的 {@code PPhase.ALL} 任务落地，主线程不直接操作关节。</p>
     *
     * <p><b>调用线程：</b>主线程。非主线程调用会被拒绝并记录错误。</p>
     *
     * @param impact 本次命中分配到本连接点的结构冲击量（尚未减免与乘倍率）
     * @return 实际生效的冲击量（减免与倍率之后）；未应用时为 0
     */
    public float applyImpact(float impact) {
        if (subPart.level.isClientSide() || internal || impact <= 0f) return 0f;
        if (!checkMainThread("applyImpact")) return 0f;
        // 与冲击分配表一致：倍率必须为正，倍率为 0 或负的连接点不参与结构损伤
        if (attr.getImpactMultiplier() < 0f) return 0f;
        float applied = (impact - attr.getImpactReduction()) * attr.getImpactMultiplier();
        if (applied <= 0f) return 0f;
        if (applied >= getIntegrity() && hasPart()) {
            // 强冲击：立即击落部件，走正常拆分流程（拓扑分裂、HP 分配、信号与网络同步都由装配体负责）
            subPart.part.assembly.disconnect(this);
            playTornApartSound(impactAbsorption() * applied);
        }
        // 削减连接的完整性，使用对应状态下的冲击吸收比例
        setIntegrityInternal(Math.clamp(getIntegrity() - impactAbsorption() * applied, 0f, getBasicIntegrity()));
        return applied;
    }

    /** 当前状态下有效冲击的吸收比例（零件已摧毁时用摧毁态配置）。 */
    private float impactAbsorption() {
        return subPart.isDestroyed() ? attr.getImpactAbsorptionDestroyed() : attr.getImpactAbsorption();
    }

    /** 击落部件的撕裂音效；只播放声音，不操作物理关节。 */
    private void playTornApartSound(float finalImpact) {
        float basic = getBasicIntegrity();
        if (basic <= 0f) return;
        float soundImpact = Math.min(basic, finalImpact);
        SparkLevel.submitImmediateTask(subPart.level, PPhase.ALL, () -> {
            SoundEvent sound = SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part.torn_apart"), 64f);
            SpreadingSoundHelper.playSpreadingSound(subPart.level, sound, SoundSource.NEUTRAL, SparkMathKt.toVec3(subPart.getPosition()), Vec3.ZERO,
                    (float) ((2 - soundImpact / basic) * (1f + 0.2f * (Math.random() - 0.5f))),
                    0.2f + 0.8f * soundImpact / basic);
        });
    }

    /**
     * 主线程入口检查：结构冲击与完整性变更只允许在主线程应用。
     *
     * <p>单线程物理模式下物理步进也在主线程执行，因此该检查放行；多线程物理模式下
     * 物理线程的调用会被拒绝，以免跨线程改动拓扑与同步数据。</p>
     *
     * @param operation 操作名，仅用于错误日志
     * @return true 表示当前线程可以安全应用
     */
    private boolean checkMainThread(String operation) {
        MinecraftServer server = subPart.level.getServer();
        if (server != null && !server.isSameThread()) {
            MachineMax.LOGGER.error("连接点 {}({}) 的 {} 被非主线程调用，已忽略；跨线程命中必须经零件命中记录",
                    getName(), subPart.name, operation);
            return false;
        }
        return true;
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
                if (this.mechanicalEnergyPort != null) {
                    this.mechanicalEnergyPort.onConnectorAttach();
                }
                if (targetConnector.mechanicalEnergyPort != null) {
                    targetConnector.mechanicalEnergyPort.onConnectorAttach();
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
                this.actualTransform.getTranslation(),
                targetConnector.actualTransform.getTranslation(),
                // 旋转180°，以匹配blockbench的x+朝右，y+朝上，z+朝后的坐标系
                this.actualTransform.getRotation().mult(new Quaternion().fromAngles(0, (float) Math.PI, 0)).toRotationMatrix(),
                targetConnector.actualTransform.getRotation().mult(new Quaternion().fromAngles(0, (float) Math.PI, 0)).toRotationMatrix(),
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
        for (Map.Entry<String, JointAttr> entry : attr.getJointAttrs().entrySet()) {//设置各轴关节属性(0~2为XYZ轴平动，3~5为XYZ轴转动)
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
                    // 取反，以匹配Blockbench的坐标系
                    if (i != 4) {
                        var v = i <= 2 ? (i == 1 ? 1 : -1) : Math.PI / 180;
                        if (jointAttr.lowerLimit() != null)
                            joint.set(MotorParam.LowerLimit, i, (float) (jointAttr.lowerLimit() * v));
                        if (jointAttr.upperLimit() != null)
                            joint.set(MotorParam.UpperLimit, i, (float) (jointAttr.upperLimit() * v));
                        if (jointAttr.equilibrium() != null)
                            joint.set(MotorParam.Equilibrium, i, (float) (jointAttr.equilibrium() * v));
                    } else { // yr轴不知为何朝下了，特殊处理
                        if (jointAttr.upperLimit() != null)
                            joint.set(MotorParam.LowerLimit, i, (float) (jointAttr.upperLimit() * - Math.PI / 180));
                        if (jointAttr.lowerLimit() != null)
                            joint.set(MotorParam.UpperLimit, i, (float) (jointAttr.lowerLimit() * - Math.PI / 180));
                        if (jointAttr.equilibrium() != null)
                            joint.set(MotorParam.Equilibrium, i, (float) (jointAttr.equilibrium() * - Math.PI / 180));
                    }
                    if (jointAttr.stiffness() != null) {
                        // 旋转轴刚度: JSON中为N·m/deg，Bullet需要N·m/rad，乘以180/π转换
                        float stiffness = (float) (jointAttr.stiffness() * (i <= 2 ? 1 : 180.0 / Math.PI));
                        if (!getPhysicsLevel().getMcLevel().isClientSide()) {
                            float maxStiffness = safe * 4 * m_eff / (1f / getPhysicsLevel().getTps() / getPhysicsLevel().getTps());  // 稳定性条件: k_max = 4·m_eff/Δt²
                            if (stiffness > maxStiffness) {
                                stiffness = maxStiffness;
                                MachineMax.LOGGER.warn("连接点{}(部件{})与连接点{}(部件{})的{}轴的刚度值过大:{}，已自动限制为{}！",
                                        Component.translatable(this.getName()).getString(),
                                        Component.translatable(this.subPart.part.name).getString(),
                                        Component.translatable(attachedConnector.getName()).getString(),
                                        Component.translatable(attachedConnector.subPart.part.name).getString(),
                                        i, stiffness, maxStiffness);
                            }
                        }
                        joint.set(MotorParam.Stiffness, i, stiffness);
                        joint.enableSpring(i, true);
                    }
                    if (jointAttr.damping() != null) {
                        //限制最大阻尼以确保稳定性
                        // 旋转轴阻尼: JSON中为N·m·s/deg，Bullet需要N·m·s/rad，乘以180/π转换
                        float damping = (float) (jointAttr.damping() * (i <= 2 ? 1 : 180.0 / Math.PI));
                        if (!getPhysicsLevel().getMcLevel().isClientSide()) {
                            float stiffness = joint.get(MotorParam.Stiffness, i) / getPhysicsLevel().getTps() / getPhysicsLevel().getTps() / m_eff;
                            float maxDamping;
                            if (stiffness >= 4f - 1e-6f) {
                                maxDamping = 0f; // 刚度已达上限，阻尼只能为0
                            } else {
                                float C_max = (4f - stiffness) / 2f;
                                maxDamping = C_max * m_eff * getPhysicsLevel().getTps();
                            }
                            maxDamping *= safe; // 安全系数
                            if (damping > maxDamping) {
                                damping = maxDamping;
                                joint.set(MotorParam.MotorErp, i, 0.5f);
                                joint.set(MotorParam.StopErp, i, 0.2f);
                                MachineMax.LOGGER.warn("连接点{}(部件{})与连接点{}(部件{})的{}轴的阻尼值过大:{}，已自动限制为{}！",
                                        Component.translatable(this.getName()).getString(),
                                        Component.translatable(this.subPart.part.name).getString(),
                                        Component.translatable(attachedConnector.getName()).getString(),
                                        Component.translatable(attachedConnector.subPart.part.name).getString(),
                                        i, damping, maxDamping);
                            }
                        }
                        joint.set(MotorParam.Damping, i, damping);
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
                if (this.mechanicalEnergyPort != null) {
                    this.mechanicalEnergyPort.onConnectorDetach();
                }
                if (attachedConnector != null && attachedConnector.mechanicalEnergyPort != null) {
                    attachedConnector.mechanicalEnergyPort.onConnectorDetach();
                }
                detachJoint();
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
                partConnector.attr.getDirection(),
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
        Axis targetNormalAxis = this.attr.getDirection();

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

        Transform rootTransform = partConnector.subPart.body.getTransform(null).invert();

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

    private Transform getConnectorWorldTransform() {
        Vector3f worldPos = MMMath.relPointWorldPos(this.offsetFromMassCenter.getTranslation(), this.subPart.body);
        Quaternion worldRotation = this.subPart.body.getPhysicsRotation(null).mult(this.offsetFromMassCenter.getRotation());
        return new Transform(worldPos, worldRotation);
    }

    private Transform worldTransformToConnectorLocal(Transform worldTransform) {
        Vector3f localPos = MMMath.worldPointLocalPos(worldTransform.getTranslation(), this.subPart.body);
        Quaternion localRotation = this.subPart.body.getPhysicsRotation(null).inverse().mult(worldTransform.getRotation());
        return new Transform(localPos, localRotation);
    }

    /**
     * 在不移动刚体的前提下，将两个连接点的 actualTransform 对齐到同一世界关节参考系
     * 用于已放置完成后的补连场景（如 ComboAttach），避免关节创建时出现过大的初始内力。
     *
     * @param targetConnector 目标连接点
     */
    public void alignActualTransformForJoint(AbstractConnector targetConnector) {
        Transform jointWorldFrame = this.getConnectorWorldTransform();
        this.actualTransform = this.worldTransformToConnectorLocal(jointWorldFrame);
        targetConnector.actualTransform = targetConnector.worldTransformToConnectorLocal(jointWorldFrame);
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
    public boolean conditionCheck(AbstractPart<?> part) {
        return conditionCheck(part.type, part.variantName);
    }

    /**
     * 检查给定零件是否符合本连接点的安装要求
     *
     * @param partType 要检查的待安装部件的类型
     * @param variant  要检查的待安装部件的变体类型
     * @return 给定零件是否满足当前连接点安装条件
     */
    public boolean conditionCheck(AbstractPartType partType, String variant) {
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
                if (!getPhysicsLevel().getMcLevel().isClientSide()) // 仅服务端才实际将关节加入世界，避免不必要的求解
                    subPart.getPhysicsLevel().getWorld().addJoint(joint); // 服务端两刚体均为动力学模式，安全；Bullet 崩溃仅发生在两运动学体间的关节（实测确认，与加入顺序无关）
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
        //加载连接点完整度（按比例持久化；旧数据缺失该键时视为满完整度）
        float ratio = data.contains("integrity_ratio") ? data.getFloat("integrity_ratio") : 1f;
        setIntegrityInternal(Math.clamp(ratio, 0f, 1f) * getBasicIntegrity());
    }

    public CompoundTag saveData(CompoundTag data) {
        //保存连接点完整度比例，避免内容包调整完整度上限后存档数值失真
        float basicIntegrity = getBasicIntegrity();
        data.putFloat("integrity_ratio", basicIntegrity > 0f
                ? Math.clamp(getIntegrity() / basicIntegrity, 0f, 1f)
                : 1f);
        return data;
    }

    private void setIntegrityInternal(float integrity) {
        getSynchedData().set(DATA_INTEGRITY_ID, integrity);
    }

    public float getIntegrity() {
        return getSynchedData().get(DATA_INTEGRITY_ID);
    }

    public float getBasicIntegrity() {
        return attr.getIntegrity();
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
