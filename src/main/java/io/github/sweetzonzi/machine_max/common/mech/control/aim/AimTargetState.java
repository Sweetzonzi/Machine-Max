package io.github.sweetzonzi.machine_max.common.mech.control.aim;

import net.minecraft.world.phys.Vec2;

import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * 由单一线程持有的瞄准目标累计状态。
 * 服务端实例由物理线程推进，客户端预测实例由主线程独立推进；同一实例不能跨线程修改。
 * 角度使用弧度，Vec2 的 x 为俯仰、y 为偏航。
 */
public final class AimTargetState {
    public enum Mode {
        RELATIVE(false, false), WORLD(true, true), YAW_STABLE(true, false), PITCH_STABLE(false, true);
        public final boolean yawStable;
        public final boolean pitchStable;
        Mode(boolean yawStable, boolean pitchStable) { this.yawStable = yawStable; this.pitchStable = pitchStable; }
        public static Mode of(boolean yaw, boolean pitch) {
            return yaw ? (pitch ? WORLD : YAW_STABLE) : (pitch ? PITCH_STABLE : RELATIVE);
        }
    }
    /** 供跨线程发布的目标快照：保持模式、世界点、相对保持角与本次机构执行目标。 */
    public record Snapshot(Mode mode, Vec3 worldPoint, Vec2 held, Vec2 command) {}
    private final Mode mode;
    private Vec3 worldPoint;
    private Vec2 held;
    private Vec2 command;

    public AimTargetState(Mode mode, Vec3 point, Vec2 initialRelative, AimGeometrySnapshot b) {
        this.mode = Objects.requireNonNull(mode);
        worldPoint = Objects.requireNonNull(point);
        held = b.constrain(Objects.requireNonNull(initialRelative));
        command = held;
        update(b);
    }

    /**
     * 所属线程消费已通过领先限幅的鼠标增量，返回机械行程裁剪后实际接受的增量。
     * 零增量保持保存的世界点；稳定轴和无稳轴共用相同的输入累计规则。
     */
    public Vec2 applyMouse(AimGeometrySnapshot b, Vec2 accepted) {
        Vec2 before = update(b);
        Vec2 after = AimMath.advanceRelativeAim(b, before, accepted);
        Vec2 effective = new Vec2((float) (after.x-before.x), (float) (after.y-before.y));
        if ((effective.x == 0 && effective.y == 0)) return effective;
        held = new Vec2((float) (mode.pitchStable ? held.x : after.x), (float) (mode.yawStable ? held.y : after.y));
        if (mode != Mode.RELATIVE) worldPoint = AimMath.buildAimPoint(b, after);
        command = after;
        return effective;
    }

    /** 所属线程消费一份输入：稳定轴采用输入世界点，只有无稳轴累计角增量。 */
    public void applyCommand(Vec3 point, Vec2 delta, AimGeometrySnapshot b) {
        if (mode != Mode.RELATIVE) worldPoint = Objects.requireNonNull(point);
        held = b.constrain(new Vec2((float) (held.x+(mode.pitchStable ? 0 : delta.x)), (float) (held.y+(mode.yawStable ? 0 : delta.y))));
        update(b);
    }

    /** 所属线程按当前机构几何求执行目标，保持世界点与无稳轴相对目标。 */
    public Vec2 update(AimGeometrySnapshot b) {
        command = switch (mode) {
            case RELATIVE -> held;
            case WORLD -> AimMath.solveAim(b, worldPoint, command).angles();
            case YAW_STABLE -> AimMath.solveSingleAxisAim(b, worldPoint, held, true, command).angles();
            case PITCH_STABLE -> AimMath.solveSingleAxisAim(b, worldPoint, held, false, command).angles();
        };
        return command;
    }

    /** 所属线程先求本次执行目标，再生成可交付其他线程读取的快照。 */
    public Snapshot snapshot(AimGeometrySnapshot b) {
        update(b);
        return new Snapshot(mode, mode == Mode.RELATIVE ? AimMath.buildAimPoint(b, held) : worldPoint, held, command);
    }
}
