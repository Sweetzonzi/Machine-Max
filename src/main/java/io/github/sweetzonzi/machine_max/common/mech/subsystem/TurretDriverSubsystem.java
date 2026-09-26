package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import com.jme3.bullet.RotationOrder;
import com.jme3.bullet.joints.New6Dof;
import com.jme3.bullet.joints.motors.MotorParam;
import com.jme3.bullet.joints.motors.RotationMotor;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;

import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.TurretDriverSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AdvancedConnector;
import jme3utilities.math.MyMath;
import org.jetbrains.annotations.Nullable;
import jme3utilities.math.MyQuaternion;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 炮塔驱动子系统。<br>
 * 控制炮塔的方向机(Yaw，偏航)和/或高低机(Pitch，俯仰)旋转到目标角度，均采用伺服模式。<br>
 * 旋转顺序固定为 YXZ（Yaw 先于 Pitch），保证两轴解耦，控制器可独立求解两个角度。<br>
 * 无控制器目标时，伺服锁定在进入空闲时采样出的固定锁止角上，抵抗重力与外力。<br>
 * 输入输出使用统一的 RotationSignal（x=pitch, y=yaw, z=roll）。<br>
 * <br>
 * <b>内容包布置约定：</b><br>
 * 本子系统应<b>安装在炮塔 SubPart 上</b>（即 AdvancedConnector 所在端），
 * 由此 bodyA = 炮塔刚体，bodyB = 对方刚体。<br>
 * <ul>
 *   <li><b>纯方向机</b>（仅偏航，hasYaw=true, hasPitch=false）：
 *       对方应为车体，bodyB=车体。偏航角在 bodyB 空间中计算。</li>
 *   <li><b>纯高低机</b>（仅俯仰，hasPitch=true, hasYaw=false）：
 *       对方应为炮管，bodyB=炮管。俯仰角在 bodyA（炮塔）空间中计算，
 *       因为炮塔是俯仰的不动参考体，炮管是绕炮塔旋转的从动体。</li>
 *   <li><b>双轴一体</b>（偏航+俯仰，hasYaw=true, hasPitch=true）：
 *       对方应为车体，bodyB=车体。偏航和俯仰均在 bodyB 空间中计算。
 *       适用于航向机枪塔等不区分方向机/高低机两个独立关节的场景。</li>
 * </ul>
 * 遵守本约定可确保 {@link #computeAimAngles(Vec3)} 在各场景下均返回正确的绝对角度。
 * <br>
 * TODO: 后续可引入是否耗电的可配置项
 */
public class TurretDriverSubsystem extends BasicSubsystem {

    public final TurretDriverSubsystemAttr attr;
    public final AdvancedConnector connector;
    private final boolean hasYaw;
    private final boolean hasPitch;
    private final float yawMaxForce;
    private final float yawMaxSpeed;
    private final float pitchMaxForce;
    private final float pitchMaxSpeed;

    /**
     * 多控制器目标角度映射。<br>
     * key = controlInputs 中的频道名，value = (pitch, yaw, roll) 弧度。<br>
     * 所有操作均在物理线程，ConcurrentHashMap 保证多写者安全。
     */
    private final ConcurrentHashMap<String, Vector3f> targetAngles = new ConcurrentHashMap<>();

    /**
     * 空闲锁止角 (pitch, yaw, roll)，单位弧度。<br>
     * 失去控制器目标时作为伺服的持续目标，使炮塔在无输入时锁止并抵抗重力与外力。<br>
     * 来源有两种：载具重建时由 {@link #loadData(CompoundTag)} 从存档写入退出游戏时的刹车角（主线程、任何物理刻之前）；
     * 无存档值时在进入空闲的那一刻采样当前关节角（物理线程）。<br>
     * 锁止角一经确定便固定不变：若每物理刻都刷新为当前关节角，伺服误差恒为 0，Bullet 伺服不会输出力矩，炮塔会退化为只受约束阻尼的自由关节。<br>
     * 有控制器接管时清空，下一次失去目标时重新确定。
     */
    @Nullable
    private Vector3f lockedAngle;

    public TurretDriverSubsystem(ISubsystemHost owner, String name, TurretDriverSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        // 从静态属性中读取各轴硬件参数
        var yawAttr = attr.staticAttribute.getYawAxis();
        var pitchAttr = attr.staticAttribute.getPitchAxis();
        this.hasYaw = yawAttr != null;
        this.hasPitch = pitchAttr != null;
        this.yawMaxForce = hasYaw ? yawAttr.maxForce() : 0f;
        this.yawMaxSpeed = hasYaw ? yawAttr.maxSpeed() * (float) Math.PI / 180f : 0f;
        this.pitchMaxForce = hasPitch ? pitchAttr.maxForce() : 0f;
        this.pitchMaxSpeed = hasPitch ? pitchAttr.maxSpeed() * (float) Math.PI / 180f : 0f;
        // 查找受控的高级连接器
        if (owner.getSubPart() != null &&
                owner.getSubPart().connectors.get(this.attr.controlledConnector) instanceof AdvancedConnector advancedConnector) {
            this.connector = advancedConnector;
        } else {
            this.connector = null;
            MachineMax.LOGGER.error("炮塔驱动子系统 {} 无法找到高级连接点 {}", name, this.attr.controlledConnector);
        }
    }

    @Override
    public void onTick() {
        super.onTick();
        if (this.connector != null && this.connector.joint instanceof New6Dof joint) {
            // 固定旋转顺序：Yaw(偏航) → Pitch(俯仰)，保证方向机不受高低机影响
            if (RotationOrder.YXZ != joint.getRotationOrder()) {
                joint.setRotationOrder(RotationOrder.YXZ);
            }
        }
    }

    @Override
    public void onPrePhysicsTick() {
        super.onPrePhysicsTick();
        if (this.connector == null || !(this.connector.joint instanceof New6Dof joint)) return;

        if (this.isActive() && !isDestroyed()) {
            // 物理关节轴映射：
            //   rotationMotor[0] = XR = 俯仰(Pitch)
            //   rotationMotor[1] = YR = 偏航(Yaw)
            // 旋转顺序 YXZ 保证 Yaw 先于 Pitch 应用，两轴解耦

            // 从 Map 按优先级读取目标角度，替代旧的信号轮询
            Vector3f target = getTargetAngle();

            if (target != null) {
                // 有控制器接管：解除锁止，跟随其下发的目标角
                lockedAngle = null;
            } else if (lockedAngle == null) {
                // 进入空闲：锁定当前关节角，并在空闲期间保持不变，伺服才能持续输出力矩抵抗外力
                lockedAngle = getRelativeAngle();
            }

            // 伺服目标：有控制器时跟随其目标角，空闲时锁定在固定的锁止角上
            Vector3f servoAngle = target != null ? target : lockedAngle;

            // 每刻都显式使能马达与伺服：关节重载后为新建对象，马达默认关闭时伺服完全无效
            if (hasYaw) {
                // servoAngle.y = yaw
                RotationMotor yawMotor = joint.getRotationMotor(1);
                yawMotor.setMotorEnabled(true);
                yawMotor.setServoEnabled(true);
                yawMotor.set(MotorParam.ServoTarget, servoAngle.y);
                yawMotor.set(MotorParam.TargetVelocity, yawMaxSpeed);
                yawMotor.set(MotorParam.MaxMotorForce, yawMaxForce);
            }

            if (hasPitch) {
                // servoAngle.x = pitch
                RotationMotor pitchMotor = joint.getRotationMotor(0);
                pitchMotor.setMotorEnabled(true);
                pitchMotor.setServoEnabled(true);
                pitchMotor.set(MotorParam.ServoTarget, servoAngle.x);
                pitchMotor.set(MotorParam.TargetVelocity, pitchMaxSpeed);
                pitchMotor.set(MotorParam.MaxMotorForce, pitchMaxForce);
            }
        }
    }

    @Override
    public void onPostPhysicsTick() {
        super.onPostPhysicsTick();
        if (this.connector != null && this.connector.joint instanceof New6Dof joint) {
            Vector3f relativeAngle = getRelativeAngle();
            // 反馈当前三轴角度（弧度）: x=pitch, y=yaw, z=roll
            // pitch 取自 XR，yaw 取自 YR（取负以与输入符号约定一致）
            Vector3f feedbackAngle = new Vector3f(
                    relativeAngle.x,     // pitch = XR 直接读数
                    relativeAngle.y,    // yaw = YR
                    0f                   // roll 暂不使用
            );
            for (String signalKey : attr.rotationAngleOutputs.keySet()) {
                sendSignalToAllTargets(signalKey, feedbackAngle);
            }
        }
    }

    @Override
    public void loadData(CompoundTag data) {
        super.loadData(data);
        //读取退出游戏时的刹车角度，作为重载后的初始锁止角，使炮塔停在退出时的位置而非被伺服驱向 0 位
        if (data.contains("brake_pitch") && data.contains("brake_yaw")) {
            this.lockedAngle = new Vector3f(
                    data.getFloat("brake_pitch"),
                    data.getFloat("brake_yaw"),
                    0f
            );
        }
    }

    @Override
    public CompoundTag saveData(CompoundTag data) {
        super.saveData(data);
        //保存当前关节角作为刹车位置，确保重新进入世界后炮塔停在退出时的角度
        Vector3f angle = getRelativeAngle();
        data.putFloat("brake_pitch", angle.x);
        data.putFloat("brake_yaw", angle.y);
        return data;
    }

    /**
     * 根据目标世界坐标计算炮塔需要的偏航/俯仰角。<br>
     * 计算步骤：<br>
     * ① 获取关节枢轴（旋转中心）的世界位置；<br>
     * ② 计算目标方向 = (targetWorldPos - pivotWorld).normalize()；<br>
     * ③ 偏航角 yaw：始终在 bodyB 局部空间中计算（bodyB 是偏航的不动参考体）；<br>
     * ④ 俯仰角 pitch：<br>
     * &nbsp;&nbsp;- 纯高低机(hasPitch &amp;&amp; !hasYaw)：在 bodyA 局部空间中计算，
     * 因为 bodyA(炮塔) 是俯仰的不动参考体，bodyB(炮管) 是被驱动的旋转体；<br>
     * &nbsp;&nbsp;- 双轴一体或纯方向机：在 bodyB 局部空间中计算，因为 bodyB 是偏航/俯仰的不动参考体。<br>
     * 返回 Vector3f(pitch, yaw, 0)，可直接用于构造 RotationSignal。
     *
     * @param targetWorldPos 目标世界坐标
     * @return Vector3f(pitch, yaw, 0) 单位弧度
     */
    public Vector3f computeAimAngles(Vec3 targetWorldPos) {
        if (connector == null || connector.joint == null) {
            return new Vector3f();
        }
        // ① 关节枢轴在 bodyA 局部空间的位置 → 转换到世界空间
        Vector3f pivotLocal = new Vector3f();
        connector.joint.getPivotA(pivotLocal);
        Transform bodyATf = connector.joint.getBodyA().getTransform(null);
        Vector3f pivotWorld = MyMath.transform(bodyATf, pivotLocal, null);

        // ② 世界方向 = 目标 - 枢轴
        Vector3f worldDir = new Vector3f(
                (float) (targetWorldPos.x - pivotWorld.x),
                (float) (targetWorldPos.y - pivotWorld.y),
                (float) (targetWorldPos.z - pivotWorld.z)
        ).normalize();

        // ③ 转换到 bodyB 局部空间，用于偏航（yaw）计算
        //    bodyB 在偏航轴上是不动参考体（车体），偏航角始终在 bodyB 空间中度量
        Transform bodyBTf = connector.joint.getBodyB().getTransform(null);
        Quaternion bodyBRot = bodyBTf.getRotation();
        Vector3f localDirB = new Vector3f();
        try {
            MyQuaternion.rotate(bodyBRot.inverse(), worldDir, localDirB);
        } catch (Exception e) {
            MachineMax.LOGGER.error("炮塔驱动子系统 {} 无法计算目标方向，请检查输入信号 {}", name, attr.staticAttribute.getControlInputs());
        }

        // ④ 偏航角 yaw：始终在 bodyB 空间中计算（bodyB 是偏航不动的参考体）
        float yaw = (float) (Math.atan2(localDirB.x, localDirB.z) + Math.PI);

        // ⑤ 俯仰角 pitch
        float pitch;
        if (hasPitch && !hasYaw) {
            // 纯高低机：bodyA(炮塔)是俯仰不动参考体，在 bodyA 空间计算绝对俯仰角
            // 若在 bodyB(炮管)空间计算，会得到相对于当前炮管朝向的增量角，导致俯仰不足
            Quaternion bodyARot = bodyATf.getRotation();
            Vector3f localDirA = new Vector3f();
            try {
                MyQuaternion.rotate(bodyARot.inverse(), worldDir, localDirA);
            } catch (Exception e) {
                MachineMax.LOGGER.error("炮塔驱动子系统 {} 无法计算目标方向（bodyA空间），请检查输入信号 {}", name, attr.staticAttribute.getControlInputs());
            }
            pitch = (float) Math.asin(Math.clamp(localDirA.y, -1.0f, 1.0f));
        } else {
            // 双轴一体或纯方向机：bodyB 是不动参考体，在 bodyB 空间计算俯仰
            pitch = (float) Math.asin(Math.clamp(localDirB.y, -1.0f, 1.0f));
        }

        return new Vector3f(pitch, yaw, 0f);
    }

    /**
     * 获取关节枢轴（旋转中心）在世界空间中的位置。
     */
    public Vec3 getJointPivotWorld() {
        if (connector == null || connector.joint == null) {
            return Vec3.ZERO;
        }
        Vector3f pivotLocal = new Vector3f();
        connector.joint.getPivotA(pivotLocal);
        Transform bodyATf = connector.joint.getBodyA().getTransform(null);
        Vector3f world = MyMath.transform(bodyATf, pivotLocal, null);
        return new Vec3(world.x, world.y, world.z);
    }

    /**
     * 由 WeaponController 直接调用，替代 RotationSignal 信号。<br>
     * 写入优先级权重：channel 对应 controlInputs 中的声明条目。
     *
     * @param channel 控制频道名（对应 controlInputs 中的条目）
     * @param angle   目标角度 (pitch, yaw, roll)，null = 该控制器释放控制权
     */
    public void setTargetAngle(String channel, @Nullable Vector3f angle) {
        if (angle == null) targetAngles.remove(channel);
        else targetAngles.put(channel, angle);
    }

    /**
     * 按 controlInputs 声明的频道顺序（优先级从高到低）获取最高优先级的目标角度。
     *
     * @return 最高优先级的有效目标角度，null = 无控制器持有控制权
     */
    @Nullable
    private Vector3f getTargetAngle() {
        for (String channel : attr.staticAttribute.getControlInputs()) {
            Vector3f angle = targetAngles.get(channel);
            if (angle != null) return angle;
        }
        return null;
    }

    /**
     * 获取关节的相对角度（弧度），返回值为局部坐标系下的角度。
     */
    public Vector3f getRelativeAngle() {
        if (connector == null || connector.joint == null) {
            return new Vector3f();
        }
        Vector3f result = new Vector3f();
        connector.joint.getAngles(result);
        return result;
    }

    @Override
    public List<String> getAcceptedChannels() {
        return attr.staticAttribute.getControlInputs();
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        Map<String, List<String>> result = new HashMap<>(2);
        result.putAll(attr.rotationAngleOutputs);
        return result;
    }
}
