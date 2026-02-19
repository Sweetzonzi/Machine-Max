package io.github.sweetzonzi.machine_max.common.vehicle.subsystem;

import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.bullet.joints.New6Dof;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.dynamic_attr.CarControllerSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.dynamic_attr.MotorbikeControllerSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AdvancedConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.signal.*;
import io.github.sweetzonzi.machine_max.util.MMMath;
import io.github.sweetzonzi.machine_max.util.control.PDController;
import io.github.sweetzonzi.machine_max.util.control.PIDController;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class MotorbikeControllerSubsystem extends CarControllerSubsystem {
    public final MotorbikeControllerSubsystemAttr attr;
    public float roll = 0.0f;

    private final PIDController rollController;

    public MotorbikeControllerSubsystem(ISubsystemHost owner, String name, MotorbikeControllerSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        this.rollController = new PIDController(10.0f, 5.0f, 7.0f, 1f / 60f, -200, 200);
    }

    @Override
    public void onPrePhysicsTick() {
        this.speed = -getOwner().getSubPart().getLinearVelocityLocal().z;
        this.roll = SparkMathKt.toDegrees(getOwner().getSubPart().getRoll());
        if (isActive() && getOwner().getSubPart().getPart().vehicle.mode == VehicleCore.ControlMode.GROUND) {
            //更新受灵敏度影响的实际控制量，油门与刹车控制在分发控制信号时进行
            if (this.moveInput != null) {
                actualSteering = actualSteering * 0.9f + (moveInput[4]) * 0.1f;
            }
            actualHandBrake = actualHandBrake * 0.9f + (handBrake ? 1 : 0) * 0.1f;
            if (getLevel().isClientSide() && Math.random() < 0.01) MachineMax.LOGGER.debug("roll:{}", roll);
            // 增稳
            float rollControl = (float) Math.clamp(rollController.step(actualSteering / 100 * getAttr().getStaticAttribute().getMaxAngle(), roll), -2250, 2250);
            getSubPart().body.applyTorque(MMMath.localVectorToWorldVector(new Vector3f(0, 0, -1.0f * rollControl), getOwner().getSubPart().body));
            if (Math.abs(speed) < 2 && moveInput != null) {
                getSubPart().body.applyCentralForce(MMMath.localVectorToWorldVector(new Vector3f(0, 0, - 5 * moveInput[2]), getOwner().getSubPart().body));
            }
            distributeControlSignals();
        } else resetSignalOutputs();
    }
}
