package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.bullet.joints.New6Dof;
import com.jme3.bullet.joints.motors.MotorParam;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.common.attachment.ControlPreference;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalSender;
import io.github.sweetzonzi.machine_max.common.mech.signal.WheelControlSignal;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.CarControllerSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AdvancedConnector;
import io.github.sweetzonzi.machine_max.util.MMMath;
import io.github.sweetzonzi.machine_max.util.control.PIDController;
import lombok.Getter;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class CarControllerSubsystem extends AbstractGroundControllerSubsystem {
    public final CarControllerSubsystemAttr attr;
    Vector3f[] vs = new Vector3f[6];
    public float avgSlipRatio = 0.0f;

    private final Map<WheelDriverSubsystem, String> wheels = new HashMap<>();

    /**
     * 手刹状态，仅用于客户端音效
     */
    private boolean handBrakeEnabled = false;

    private final PIDController driftingPD;
    /**
     * 漂移角，弧度制
     */
    public float driftRad = 0.0f;
    public boolean drifting = false;
    private final float DRIFT_START_RAD = SparkMathKt.toRadians(5.0f);
    private final float DRIFT_END_RAD = SparkMathKt.toRadians(3.0f);
    /**
     * 漂移控制与一般控制的融合权重，0为纯原生控制，1为漂移接管
     */
    protected float driftWeight = 0;
    /**
     * 漂移转向输入，弧度制
     */
    private float driftControl = 0;

    /** 当前车体局部 Y 轴角速度，由 onGroundControlBegin 读取 */
    protected float bodyYawRate = 0f;

    /** 根据已装配车轮几何位置自动计算的转向中心，用于阿克曼转向和漂移判断 */
    protected volatile Vec3 computedSteeringCenter = Vec3.ZERO;

    public CarControllerSubsystem(ISubsystemHost owner, String name, CarControllerSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        this.driftingPD = new PIDController(1.5, 0.01, 0.1, 1.0 / getPhysicsLevel().getTps(), -1, 1);
    }

    @Override
    public void onTick() {
        super.onTick();
        // 手刹车音效播放
        var pos = getSubPart().getPosition();
        if (handBrakeEnabled) {
            if (actualHandBrake < 0.1f) {
                handBrakeEnabled = false;
                if (getLevel().isClientSide()) {
                    getLevel().playLocalSound(
                            pos.x, pos.y, pos.z,
                            getAttr().getStaticAttribute().getHandBrakeOffSound(),
                            SoundSource.NEUTRAL,
                            1.0f,
                            1.0f,
                            false
                    );
                }
            }
        } else if (actualHandBrake >= 0.1f) {
            handBrakeEnabled = true;
            if (getLevel().isClientSide()) {
                getLevel().playLocalSound(
                        pos.x, pos.y, pos.z,
                        getAttr().getStaticAttribute().getHandBrakeOnSound(),
                        SoundSource.NEUTRAL,
                        1.0f,
                        1.0f,
                        false
                );
            }
        }
    }

    @Override
    protected void onGroundControlBegin() {
        Vector3f localVel = getOwner().getSubPart().getLinearVelocityLocal();
        this.driftRad = (float) Math.atan2(localVel.x, Math.abs(localVel.z) + 0.1f);
        this.bodyYawRate = getOwner().getSubPart().body.getAngularVelocityLocal(null).y;

        if (drifting && Math.abs(driftRad) < DRIFT_END_RAD) {
            drifting = false;
        } else if (!drifting && Math.abs(driftRad) > DRIFT_START_RAD && speed > 0) {
            drifting = true;
        }
        if (!ControlPreference.shouldDriftAssist(this)) {
            driftWeight = 0.0f;
        } else if (actualHandBrake > 0.5f && localVel.lengthSquared() > 4) {
            driftWeight = 1.0f;
        } else if (drifting && localVel.lengthSquared() > 4) {
            driftWeight = 1.0f;
        } else {
            driftWeight = 0.0f;
        }
    }

    @Override
    protected void onGroundControlAfterInput() {
        if (this.moveInput != null) {
            float maxDriftAngularVelocity = attr.staticAttribute.getMaxDriftAngularVelocityAtSpeed(Math.abs(speed))
                    * (float) Math.PI / 180f;
            if (driftWeight > 1e-4) {
                driftControl = (float) driftingPD.step(actualSteering * maxDriftAngularVelocity, bodyYawRate);
            } else {
                driftControl = 0.0f;
                driftingPD.resetError();
            }
        }
    }

    @Override
    protected void onActuatorDiscovered(ISignalSender sender, String channel) {
        if (sender instanceof WheelDriverSubsystem wheel) {
            if (wheel.getOwner().getSubPart().getPart().assembly
                    != this.getOwner().getSubPart().getPart().assembly) {
                wheels.remove(wheel);
                removeCallbackTarget(channel, wheel);
            } else {
                wheels.put(wheel, channel);
                addCallbackTarget(channel, wheel);
            }
        }
    }

    @Override
    protected void clearActuatorBindings() {
        super.clearActuatorBindings();
        wheels.clear();
    }

    @Override
    protected void onHandShakeComplete() {
        recalculateSteeringCenter();
    }

    /**
     * 根据已装配车轮的关节枢轴位置自动计算转向中心。<br>
     * 遍历所有受控车轮驱动子系统，读取关节 Y 轴旋转限位以区分转向/非转向轮，<br>
     * 用非转向轮枢轴的 Z 均值作为转向参考轴（X 取左右对称中心），<br>
     * 若全为转向轮则回退到全部车轮的几何中心。
     */
    protected void recalculateSteeringCenter() {
        float sumNonSteerX = 0, sumNonSteerZ = 0;
        int nonSteerCount = 0;
        float sumAllX = 0, sumAllZ = 0;
        int allCount = 0;

        var carBody = getOwner().getSubPart().body;

        for (WheelDriverSubsystem wheel : getWheels().keySet()) {
            if (wheel.connector == null || wheel.connector.joint == null) continue;

            New6Dof joint = wheel.connector.joint;
            Vector3f pivotLocal = new Vector3f();
            if (wheel.connector.subPart.body == joint.getBodyA()) {
                joint.getPivotA(pivotLocal);
            } else {
                joint.getPivotB(pivotLocal);
            }
            // 统一转换到车控子系统的刚体局部坐标系，确保不同 SubPart 上的车轮在同一空间内对比
            Vector3f worldPivot = MMMath.relPointWorldPos(pivotLocal, wheel.connector.subPart.body);
            Vector3f carLocalPivot = MMMath.worldPointLocalPos(worldPivot, carBody);

            // 检查 Y 轴旋转自由度范围判断是否为转向轮（带 1e-4 容差）
            double lowerYr = joint.get(MotorParam.LowerLimit, 4);
            double upperYr = joint.get(MotorParam.UpperLimit, 4);
            boolean isSteering = Math.abs(upperYr - lowerYr) > 1e-4;

            if (!isSteering) {
                sumNonSteerX += carLocalPivot.x;
                sumNonSteerZ += carLocalPivot.z;
                nonSteerCount++;
            }
            sumAllX += carLocalPivot.x;
            sumAllZ += carLocalPivot.z;
            allCount++;
        }

        float avgX, avgZ;
        if (nonSteerCount > 0) {
            avgX = sumNonSteerX / nonSteerCount;
            avgZ = sumNonSteerZ / nonSteerCount;
        } else if (allCount > 0) {
            avgX = sumAllX / allCount;
            avgZ = sumAllZ / allCount;
        } else {
            avgX = 0;
            avgZ = 0;
        }

        this.computedSteeringCenter = new Vec3(avgX, 0, avgZ);
    }

    @Override
    protected void distributeDriveControl() {
        for (Map.Entry<WheelDriverSubsystem, String> entry : wheels.entrySet()) {
            String channel = entry.getValue();
            WheelDriverSubsystem wheel = entry.getKey();
            if (wheel.connector.joint != null) {
                float steeringInput = steering(actualSteering, wheel.connector);
                // 漂移差动制动偏置：叠加到驾驶员刹车后走 ABS
                float driftBrakeBias = calculateDriftBrakeBias(wheel, getWheelCarLocalPivot(wheel));
                float effectiveBrake = calculateEffectiveBrake(wheel,
                        Math.clamp(actualBrake + driftBrakeBias, 0f, 1f));
                sendCallbackToListener(channel, wheel,
                        new WheelControlSignal(effectiveBrake, actualHandBrake, steeringInput));
            }
        }
    }

    protected float steering(float steeringInput, AdvancedConnector wheelDrive) {
        if (driftWeight <= 0f) {
            return ackermannSteering(steeringInput, wheelDrive);
        }
        return (1 - driftWeight) * ackermannSteering(steeringInput, wheelDrive)
                + driftWeight * driftSteering(wheelDrive);
    }

    protected float ackermannSteering(float steeringInput, AdvancedConnector wheelDrive) {
        New6Dof joint = wheelDrive.joint;
        Vector3f pivot = new Vector3f();
        if (wheelDrive.subPart.body == joint.getBodyA()) {
            joint.getPivotA(pivot);
        } else {
            joint.getPivotB(pivot);
        }
        if (steeringInput == 0) {
            return 0;
        } else {
            //实际转向半径(米) Actual steering radius (m)
            float steeringRadius = ControlPreference.shouldLimitSpeedTurning(this)
                    ? attr.staticAttribute.getSteeringRadiusAtSpeed(speed) / steeringInput
                    : attr.staticAttribute.getMinSteeringRadius() / steeringInput;
            double deltaRadius = pivot.x - computedSteeringCenter.x;
            deltaRadius *= Math.signum(steeringInput);
            double deltaForward = pivot.z - computedSteeringCenter.z;
            return (float) Math.atan(deltaForward / (steeringRadius + deltaRadius));
        }
    }

    /**
     * 漂移模式下自动反打方向，并使用PD控制器逼近目标角速度，仅前轮转向
     *
     * @return 轮胎转向角度，以弧度为单位
     */
    protected float driftSteering(AdvancedConnector wheelDrive) {
        New6Dof joint = wheelDrive.joint;
        Vector3f pivot = new Vector3f();
        if (wheelDrive.subPart.body == joint.getBodyA()) {
            joint.getPivotA(pivot);
        } else {
            joint.getPivotB(pivot);
        }
        return pivot.z <= computedSteeringCenter.z ? 0.5f * driftRad - driftControl : 0;
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        Map<String, List<String>> result = super.getTargetNames();
        result.putAll(attr.getSteeringOutputTargets());
        return result;
    }

    @Override
    protected List<String> getControlInputKeys() {
        return attr.staticAttribute.getControlInputKeys();
    }

    @Override
    protected Map<String, List<String>> getControlOutputTargets() {
        return attr.getControlOutputTargets();
    }

    @Override
    protected Map<String, List<String>> getSpeedOutputTargets() {
        return attr.getSpeedOutputTargets();
    }

    @Override
    protected Map<String, List<String>> getThrottleOutputTargets() {
        return attr.getThrottleOutputTargets();
    }

    @Override
    protected Map<String, List<String>> getBrakeOutputTargets() {
        return attr.getBrakeOutputTargets();
    }

    @Override
    protected Map<String, List<String>> getHandbrakeOutputTargets() {
        return attr.getHandbrakeOutputTargets();
    }

    @Override
    public boolean isAutoHandBrakeEnabled() {
        return attr.staticAttribute.isAutoHandBrake();
    }

    @Override
    public boolean isManualGearShift() {
        return attr.staticAttribute.isManualGearShift();
    }

    /**
     * 计算考虑ABS的有效刹车力
     *
     * @param wheel    轮胎驱动子系统
     * @param rawBrake 原始刹车力
     * @return 经过ABS调整后的有效刹车力
     */
    protected float calculateEffectiveBrake(WheelDriverSubsystem wheel, float rawBrake) {
        float vehicleSpeed = Math.abs(this.speed);
        if (!wheel.attr.staticAttribute.isAbsEnabled() || rawBrake <= 0 || vehicleSpeed < 2f) {
            return rawBrake;
        }
        float slipRatio = calculateSlipRatio(wheel);
        float targetSlipRatio = wheel.attr.staticAttribute.getAbsTargetSlipRatio();
        float effectiveBrake = rawBrake;

        if (slipRatio > targetSlipRatio) {
            float reductionFactor = Math.clamp(1.0f - (slipRatio - targetSlipRatio) / 0.15f, 0.1f, 1.0f);
            effectiveBrake = rawBrake * reductionFactor;
        }

        return Math.max(0f, Math.min(1f, effectiveBrake));
    }

    /**
     * 获取轮子关节在车体局部坐标系下的枢轴位置。
     */
    private Vector3f getWheelCarLocalPivot(WheelDriverSubsystem wheel) {
        New6Dof joint = wheel.connector.joint;
        Vector3f pivotLocal = new Vector3f();
        if (wheel.connector.subPart.body == joint.getBodyA()) {
            joint.getPivotA(pivotLocal);
        } else {
            joint.getPivotB(pivotLocal);
        }
        Vector3f worldPivot = MMMath.relPointWorldPos(pivotLocal, wheel.connector.subPart.body);
        return MMMath.worldPointLocalPos(worldPivot, getOwner().getSubPart().body);
    }

    /**
     * 计算漂移差动制动的刹车偏置量。
     */
    private float calculateDriftBrakeBias(WheelDriverSubsystem wheel, Vector3f carLocalPivot) {
        if (driftWeight <= 1e-4f || Math.abs(driftRad) <= 0.05f) {
            return 0f;
        }

        boolean isFront = carLocalPivot.z <= computedSteeringCenter.z;
        boolean isOuter = Math.signum(carLocalPivot.x) == Math.signum(driftRad);

        float driftIntensity = driftWeight * Math.abs(driftRad) / (float) Math.PI;

        if (isFront && isOuter) {
            return driftIntensity * attr.staticAttribute.getDriftFrontOuterBrakeGain();
        } else if (!isFront && !isOuter) {
            return driftIntensity * attr.staticAttribute.getDriftRearInnerBrakeGain();
        }
        return 0f;
    }

    protected float calculateSlipRatio(WheelDriverSubsystem wheel) {
        float angularVelocity = -wheel.getRelativeAngularVel().get(0);
        float wheelLinearSpeed = Math.abs(angularVelocity * wheel.attr.staticAttribute.getAbsWheelRadius());
        float absSpeed = Math.abs(speed);
        return Math.abs(absSpeed - wheelLinearSpeed) / (Math.min(absSpeed, wheelLinearSpeed) + 0.1f);
    }
}
