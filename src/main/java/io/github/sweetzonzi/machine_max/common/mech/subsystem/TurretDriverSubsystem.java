package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import com.jme3.bullet.RotationOrder;
import com.jme3.bullet.joints.New6Dof;
import com.jme3.bullet.joints.motors.MotorParam;
import com.jme3.bullet.joints.motors.RotationMotor;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.signal.EmptySignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.RotationSignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalChannel;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.TurretDriverSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AdvancedConnector;
import jme3utilities.math.MyMath;
import jme3utilities.math.MyQuaternion;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 炮塔驱动子系统。<br>
 * 控制炮塔的方向机(Yaw，偏航)和/或高低机(Pitch，俯仰)旋转到目标角度，均采用伺服模式。<br>
 * 旋转顺序固定为 YXZ（Yaw 先于 Pitch），保证两轴解耦，控制器可独立求解两个角度。<br>
 * 输入输出使用统一的 RotationSignal（x=pitch, y=yaw, z=roll）。<br>
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

            RotationSignal rotationSignal = readRotationSignal(attr.staticAttribute.getControlInputs());

            if (hasYaw) {
                RotationMotor yawMotor = joint.getRotationMotor(1);
                if (rotationSignal != null) {
                    // rotationSignal.yaw 即为 computeAimAngles 返回的 yaw
                    // setServoTarget 取负，与伺服马达方向约定一致
                    yawMotor.setMotorEnabled(true);
                    yawMotor.setServoEnabled(true);
                    yawMotor.set(MotorParam.ServoTarget, rotationSignal.getYaw());
                    yawMotor.set(MotorParam.TargetVelocity, yawMaxSpeed);
                    yawMotor.set(MotorParam.MaxMotorForce, yawMaxForce);
                } else {
                    // 无有效信号时保持位置并停止
                    yawMotor.setServoEnabled(true);
                    yawMotor.set(MotorParam.TargetVelocity, 0f);
                    yawMotor.set(MotorParam.MaxMotorForce, yawMaxForce);
                }
            }

            if (hasPitch) {
                RotationMotor pitchMotor = joint.getRotationMotor(0);
                if (rotationSignal != null) {
                    // rotationSignal.pitch 即为 computeAimAngles 返回的 pitch
                    pitchMotor.setMotorEnabled(true);
                    pitchMotor.setServoEnabled(true);
                    pitchMotor.set(MotorParam.ServoTarget, rotationSignal.getPitch());
                    pitchMotor.set(MotorParam.TargetVelocity, pitchMaxSpeed);
                    pitchMotor.set(MotorParam.MaxMotorForce, pitchMaxForce);
                } else {
                    pitchMotor.setServoEnabled(true);
                    pitchMotor.set(MotorParam.TargetVelocity, 0f);
                    pitchMotor.set(MotorParam.MaxMotorForce, pitchMaxForce);
                }
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

    /**
     * 根据目标世界坐标计算炮塔需要的偏航/俯仰角。<br>
     * 计算步骤：<br>
     * ① 获取关节枢轴（旋转中心）的世界位置；<br>
     * ② 计算目标方向 = (targetWorldPos - pivotWorld).normalize()；<br>
     * ③ 将方向向量转换到 bodyA（底座/车体）的局部坐标系；<br>
     * ④ 分解为偏航角 yaw = atan2(localDir.x, -localDir.z)；<br>
     * ⑤ 俯仰角 pitch = asin(clamp(localDir.y, -1, 1))。<br>
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

        // ③ 转换到 bodyB 局部空间（底座坐标系）
        Transform bodyBTf = connector.joint.getBodyB().getTransform(null);
        Quaternion bodyBRot = bodyBTf.getRotation();
        Vector3f localDir = new Vector3f();
        try {
            MyQuaternion.rotate(bodyBRot.inverse(), worldDir, localDir);
        } catch (Exception e) {
            MachineMax.LOGGER.error("炮塔驱动子系统 {} 无法计算目标方向，请检查输入信号 {}", name, attr.staticAttribute.getControlInputs());
        }

        // ④ 分解角度：yaw = 水平偏航，pitch = 垂直俯仰
        float yaw = (float) (Math.atan2(localDir.x, localDir.z) + Math.PI);
        float pitch = (float) Math.asin(Math.clamp(localDir.y, -1.0f, 1.0f));

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
     * 从配置的输入信号频道中读取 RotationSignal。
     * 轮询频道列表，返回第一个非空非EmptySignal的 RotationSignal；无有效信号则返回null。
     */
    private RotationSignal readRotationSignal(List<String> targetInputs) {
        for (String signalKey : targetInputs) {
            SignalChannel channel = getSignalChannel(signalKey);
            if (!channel.isEmpty() && !(channel.getFirstSignal() instanceof EmptySignal)) {
                Object first = channel.getFirstSignal();
                if (first instanceof RotationSignal rs) return rs;
                if (first instanceof Vector3f v) return new RotationSignal(v);
            }
        }
        return null;
    }

    /**
     * 获取关节的相对角度（弧度），返回值为局部坐标系下的角度。
     */
    private Vector3f getRelativeAngle() {
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
