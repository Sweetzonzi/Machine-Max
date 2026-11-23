package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import cn.solarmoon.spark_core.animation.anim.AnimInstance;
import cn.solarmoon.spark_core.animation.anim.AnimLayer;
import cn.solarmoon.spark_core.animation.anim.KeyAnimData;
import cn.solarmoon.spark_core.animation.model.ModelInstance;
import cn.solarmoon.spark_core.animation.model.ModelPose;
import cn.solarmoon.spark_core.animation.model.origin.OBone;
import cn.solarmoon.spark_core.animation.model.origin.OLocator;
import cn.solarmoon.spark_core.util.PPhase;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.bullet.RotationOrder;
import com.jme3.bullet.joints.New6Dof;
import com.jme3.bullet.joints.motors.MotorParam;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.JointDriverSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.JointDriverAxisAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AdvancedConnector;
import io.github.sweetzonzi.machine_max.util.data.Axis;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 以连接点 locator 的动画姿态为目标，按配置力值驱动高级连接器的 6 自由度关节。 */
public class JointDriverSubsystem extends BasicSubsystem {
    public final JointDriverSubsystemAttr attr;
    @Nullable
    public final AdvancedConnector connector;
    private final RotationOrder rotationOrder;

    private New6Dof drivenJoint;
    @Nullable
    private RotationOrder previousRotationOrder;
    private Map<Integer, MotorState> previousMotorStates = Map.of();

    public JointDriverSubsystem(ISubsystemHost owner, String name, JointDriverSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        this.rotationOrder = RotationOrder.valueOf(attr.staticAttribute.rotationOrder);
        if (owner.getSubPart() != null
                && owner.getSubPart().connectors.get(attr.controlledConnector) instanceof AdvancedConnector advanced) {
            this.connector = advanced;
        } else {
            this.connector = null;
            MachineMax.LOGGER.error("动画关节执行器 {} 找不到高级连接点 {}", name, attr.controlledConnector);
        }
    }

    @Override
    public void onPostAnimationPhysicsTick() {
        if (!isActive() || connector == null || connector.joint == null
                || connector.joint.getPhysicsSpace() == null) {
            releaseDrive();
            return;
        }

        List<String> locatorBoneChain = getLocatorBoneChain();
        if (locatorBoneChain.isEmpty() || !hasAnimationContribution(locatorBoneChain)) {
            releaseDrive();
            return;
        }

        New6Dof joint = connector.joint;
        if (joint != drivenJoint) {
            releaseDrive();
            previousRotationOrder = joint.getRotationOrder();
            previousMotorStates = captureMotorStates(joint);
            joint.setRotationOrder(rotationOrder);
            drivenJoint = joint;
        }

        Transform animatedLocator = sampleAnimatedLocator();
        if (animatedLocator == null) {
            releaseDrive();
            return;
        }

        Transform restLocator = connector.subPart.getLocatorLocalTransform(connector.attr.locatorName);
        Quaternion frameRotation = connector.actualTransform.getRotation()
                .mult(new Quaternion().fromAngles(0f, (float) Math.PI, 0f));
        JointTargetSampler.Target target = JointTargetSampler.sample(
                restLocator, animatedLocator, frameRotation, rotationOrder);

        for (Map.Entry<Axis, JointDriverAxisAttr> entry : attr.staticAttribute.axes.entrySet()) {
            int axis = entry.getKey().getValue();
            JointDriverAxisAttr axisAttr = entry.getValue();
            applyServoTarget(joint, axis, JointTargetSampler.servoTarget(target, entry.getKey()), axisAttr);
        }
    }

    private void applyServoTarget(New6Dof joint, int axis, float requestedTarget, JointDriverAxisAttr attr) {
        float lower = joint.get(MotorParam.LowerLimit, axis);
        float upper = joint.get(MotorParam.UpperLimit, axis);
        float target = lower <= upper ? Math.clamp(requestedTarget, lower, upper) : requestedTarget;
        float maxSpeed = axis < 3 ? attr.maxSpeed() : (float) Math.toRadians(attr.maxSpeed());

        joint.set(MotorParam.MaxMotorForce, axis, attr.maxForce());
        joint.set(MotorParam.TargetVelocity, axis, maxSpeed);
        joint.set(MotorParam.ServoTarget, axis, target);
        setMotorEnabled(joint, axis, true);
        setServoEnabled(joint, axis, true);
        joint.getBodyA().activate();
        joint.getBodyB().activate();
    }

    private List<String> getLocatorBoneChain() {
        ModelInstance model = getSubPart().part.modelController.getModel();
        if (model == null) return List.of();
        OLocator locator = model.getOrigin().getLocators().get(connector.attr.locatorName);
        if (locator == null) return List.of();

        List<String> names = new ArrayList<>();
        for (OBone bone = locator.getBone(); bone != null; bone = bone.getParent()) {
            names.add(bone.getName());
        }
        return names;
    }

    private boolean hasAnimationContribution(List<String> boneChain) {
        for (AnimLayer layer : getSubPart().part.animController.getLayers().values()) {
            for (AnimInstance animation : layer.snapshotAnimations()) {
                if (!JointTargetSampler.hasDriveTarget(animation.getOrigin().getLoop(), animation.getTime(),
                        animation.getMaxLength(), animation.getCurrentWeight())) continue;
                for (String boneName : boneChain) {
                    if (animation.getOrigin().getBones().containsKey(boneName)) return true;
                }
            }
        }
        return false;
    }

    @Nullable
    private Transform sampleAnimatedLocator() {
        SubPart subPart = connector.subPart;
        ModelInstance model = subPart.part.modelController.getModel();
        if (model == null) return null;
        ModelPose pose = model.getPose();
        OLocator locator = model.getOrigin().getLocators().get(connector.attr.locatorName);
        if (locator == null) return null;

        Matrix4f bodyFromModel = SparkMathKt.toMatrix4f(subPart.getLocalMassCenterTransform().clone().invert())
                .mul(subPart.getAttr().getModelToStartBone(subPart.part.variant));
        Matrix4f bodyFromLocator = bodyFromModel.mul(getPhysicsSpaceLocatorMatrix(pose, locator));
        org.joml.Vector3f modelTranslation = bodyFromLocator.getTranslation(new org.joml.Vector3f());
        Vector3f translation = new Vector3f(modelTranslation.x, modelTranslation.y, modelTranslation.z);
        Quaternion rotation = SparkMathKt.toBQuaternion(bodyFromLocator.getNormalizedRotation(new Quaternionf()));
        return new Transform(translation, rotation);
    }

    private static Matrix4f getPhysicsSpaceLocatorMatrix(ModelPose pose, OLocator locator) {
        List<OBone> chain = new ArrayList<>();
        for (OBone bone = locator.getBone(); bone != null; bone = bone.getParent()) chain.add(bone);

        Matrix4f matrix = new Matrix4f();
        for (int i = chain.size() - 1; i >= 0; i--) {
            OBone bone = chain.get(i);
            KeyAnimData animation = pose.getPhysicsLocalTransform(bone.getName());
            Vec3 position = animation == null ? Vec3.ZERO : animation.getPosition();
            Vec3 rotation = animation == null ? Vec3.ZERO : animation.getRotation();
            Vec3 scale = animation == null ? new Vec3(1.0, 1.0, 1.0) : animation.getScale();
            Vec3 pivot = bone.getPivot();

            matrix.translate((float) pivot.x, (float) pivot.y, (float) pivot.z)
                    .translate((float) position.x, (float) position.y, (float) position.z)
                    .rotateZYX(
                            (float) (rotation.z + bone.getRotation().z),
                            (float) (rotation.y + bone.getRotation().y),
                            (float) (rotation.x + bone.getRotation().x))
                    .scale((float) scale.x, (float) scale.y, (float) scale.z)
                    .translate((float) -pivot.x, (float) -pivot.y, (float) -pivot.z);
        }

        Vec3 offset = locator.getOffset();
        Vec3 rotation = locator.getRotation();
        return matrix.translate((float) offset.x, (float) offset.y, (float) offset.z)
                .rotateZYX((float) rotation.z, (float) rotation.y, (float) rotation.x);
    }

    private Map<Integer, MotorState> captureMotorStates(New6Dof joint) {
        Map<Integer, MotorState> states = new LinkedHashMap<>();
        for (Axis configuredAxis : attr.staticAttribute.axes.keySet()) {
            int axis = configuredAxis.getValue();
            boolean motorEnabled = joint.isMotorEnabled(axis);
            boolean servoEnabled = joint.isServoEnabled(axis);
            states.put(axis, new MotorState(
                    motorEnabled,
                    servoEnabled,
                    joint.get(MotorParam.MaxMotorForce, axis),
                    joint.get(MotorParam.TargetVelocity, axis),
                    joint.get(MotorParam.ServoTarget, axis)));
        }
        return Map.copyOf(states);
    }

    private void releaseDrive() {
        New6Dof joint = drivenJoint;
        if (joint == null) return;
        if (joint.getPhysicsSpace() != null) {
            previousMotorStates.forEach((axis, state) -> restoreAxis(joint, axis, state));
            if (previousRotationOrder != null) joint.setRotationOrder(previousRotationOrder);
        }
        drivenJoint = null;
        previousRotationOrder = null;
        previousMotorStates = Map.of();
    }

    private static void restoreAxis(New6Dof joint, int axis, MotorState state) {
        setMotorEnabled(joint, axis, state.motorEnabled());
        setServoEnabled(joint, axis, state.servoEnabled());
        joint.set(MotorParam.MaxMotorForce, axis, state.maxMotorForce());
        joint.set(MotorParam.TargetVelocity, axis, state.targetVelocity());
        joint.set(MotorParam.ServoTarget, axis, state.servoTarget());
    }

    private static void setMotorEnabled(New6Dof joint, int axis, boolean enabled) {
        if (axis < 3) joint.getTranslationMotor().setMotorEnabled(axis, enabled);
        else joint.getRotationMotor(axis - 3).setMotorEnabled(enabled);
    }

    private static void setServoEnabled(New6Dof joint, int axis, boolean enabled) {
        if (axis < 3) joint.getTranslationMotor().setServoEnabled(axis, enabled);
        else joint.getRotationMotor(axis - 3).setServoEnabled(enabled);
    }

    @Override
    public void onDetach() {
        getPhysicsLevel().submitImmediateTask(PPhase.PRE, () -> {
            releaseDrive();
            return null;
        });
        super.onDetach();
    }

    private record MotorState(
            boolean motorEnabled,
            boolean servoEnabled,
            float maxMotorForce,
            float targetVelocity,
            float servoTarget) {
    }
}
