package io.github.sweetzonzi.machine_max.common.mech.control.aim;

import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import jme3utilities.math.MyMath;
import jme3utilities.math.MyQuaternion;
import net.minecraft.world.phys.Vec2;

import java.util.Objects;

/**
 * 机构几何快照，按“根基座 → 方向机 → 高低机 → 光轴”的关系正解期望位姿。
 * 各关节变换表示相对上一级的局部零位，轴向符号由调用方在构造快照时确定。
 * 世界原点保留双精度，JME 变换只计算局部偏移；可变的变换和轴向输入在构造时复制。
 * 快照不读取活体刚体，可由主线程或物理线程使用。
 */
public final class AimGeometrySnapshot {
    /** 根参考的世界位姿；后续变换均为相对上级的局部零位。 */
    private final AimPose root;
    private final Transform yawFrame;
    private final Vector3f yawAxis;
    private final Transform pitchFrame;
    private final Vector3f pitchAxis;
    private final Transform opticFrame;
    private final AimAxisLimit yawLimit;
    private final AimAxisLimit pitchLimit;
    /** 与几何在同一物理步采样的实际语义角，供输入初始化与累计偏差限幅使用。 */
    private final Vec2 actualAngles;

    public AimGeometrySnapshot(AimPose root, Transform yawFrame, Vector3f yawAxis,
                               Transform pitchFrame, Vector3f pitchAxis, Transform opticFrame,
                               AimAxisLimit yawLimit, AimAxisLimit pitchLimit) {
        this(root, yawFrame, yawAxis, pitchFrame, pitchAxis, opticFrame, yawLimit, pitchLimit, Vec2.ZERO);
    }

    public AimGeometrySnapshot(AimPose root, Transform yawFrame, Vector3f yawAxis,
                               Transform pitchFrame, Vector3f pitchAxis, Transform opticFrame,
                               AimAxisLimit yawLimit, AimAxisLimit pitchLimit, Vec2 actualAngles) {
        this.root = Objects.requireNonNull(root);
        this.yawFrame = yawFrame.clone();
        this.pitchFrame = pitchFrame.clone();
        this.opticFrame = opticFrame.clone();
        this.yawAxis = axis(yawAxis);
        this.pitchAxis = axis(pitchAxis);
        this.yawLimit = Objects.requireNonNull(yawLimit);
        this.pitchLimit = Objects.requireNonNull(pitchLimit);
        this.actualAngles = Objects.requireNonNull(actualAngles);
    }

    private static Vector3f axis(Vector3f axis) {
        if (!Vector3f.isValidVector(axis) || axis.lengthSquared() < 1e-12f) {
            throw new IllegalArgumentException("关节轴必须是有限的非零向量");
        }
        return axis.normalize();
    }

    /**
     * 按根参考、方向机、高低机和完整光轴变换正解位姿，不读取实际追赶角。
     *
     * @param q 期望相对角，弧度，x=俯仰、y=偏航
     * @return 期望世界光轴的位置和旋转
     */
    public AimPose forward(Vec2 q) {
        Transform local = MyMath.combine(new Transform(Vector3f.ZERO,
                new Quaternion().fromAngleNormalAxis(q.y, yawAxis)), yawFrame, null);
        local = MyMath.combine(pitchFrame, local, null);
        local = MyMath.combine(new Transform(Vector3f.ZERO,
                new Quaternion().fromAngleNormalAxis(q.x, pitchAxis)), local, null);
        local = MyMath.combine(opticFrame, local, null);
        Quaternion worldRotation = root.rotation();
        return new AimPose(root.position().add(SparkMathKt.toVec3(MyQuaternion.rotate(worldRotation, local.getTranslation(), null))),
                worldRotation.mult(local.getRotation()));
    }

    public AimAxisLimit yawLimit() {
        return yawLimit;
    }

    public AimAxisLimit pitchLimit() {
        return pitchLimit;
    }

    public Vec2 actualAngles() {
        return actualAngles;
    }

    /** 将绝对目标限制在机械行程内；连续轴保留角度分支。 */
    public Vec2 constrain(Vec2 q) {
        return new Vec2((float) (pitchLimit.apply(q.x)), (float) (yawLimit.apply(q.y)));
    }
}
