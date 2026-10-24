package io.github.sweetzonzi.machine_max.common.mech.control.aim;

import io.github.sweetzonzi.machine_max.common.mech.control.view.ViewMath;
import lombok.Getter;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 操作者持有的瞄准输入状态，由客户端主线程更新。<br>
 * 世界点供稳定轴求角，相对角从主 WC 的实际机构角初始化，再累计鼠标输入。
 * 无稳轴的累计目标最多领先／落后实际角 60°；机械行程交给执行器。
 * 角度使用弧度，x=俯仰、y=偏航；连续偏航保留累计圈数。
 */
@Getter
public final class AimTargetState {
    public static final double MAX_RELATIVE_OFFSET_RAD = Math.toRadians(60);
    /** 每个轴选择世界点求角或绝对相对角控制。 */
    public enum Mode {
        RELATIVE(false, false),
        WORLD(true, true),
        YAW_STABLE(true, false),
        PITCH_STABLE(false, true);

        public final boolean yawStable;
        public final boolean pitchStable;

        Mode(boolean yawStable, boolean pitchStable) {
            this.yawStable = yawStable;
            this.pitchStable = pitchStable;
        }

        public static Mode of(boolean yaw, boolean pitch) {
            return yaw ? (pitch ? WORLD : YAW_STABLE) : (pitch ? PITCH_STABLE : RELATIVE);
        }
    }

    private Mode mode;
    /** 稳定轴保持的有限世界点；全无稳模式按累计机构目标的正解刷新。 */
    private Vec3 worldPoint;
    /** 机构参考中的累计目标；稳定轴分量不用于电机控制。 */
    private Vec2 relativeAim;

    public AimTargetState(Mode mode, Vec3 worldPoint, Vec2 relativeAim) {
        setTarget(mode, worldPoint, relativeAim);
    }

    /** 所属线程覆盖完整值；相同输入重复设置不会累计。 */
    public void setTarget(Mode mode, Vec3 worldPoint, Vec2 relativeAim) {
        this.mode = Objects.requireNonNull(mode);
        this.worldPoint = Objects.requireNonNull(worldPoint);
        this.relativeAim = Objects.requireNonNull(relativeAim);
    }

    /** 主线程更新全无稳目标的世界位置，使其随机构基座运动，而非固定在世界中。 */
    public void updateGeometry(AimGeometrySnapshot geometry) {
        if (mode == Mode.RELATIVE) {
            worldPoint = AimMath.buildAimPoint(geometry, relativeAim);
        }
    }

    /**
     * 主线程读取无稳轴累计目标的正解点，供瞄准圈对应的屏幕轴投影。<br>
     * 稳定轴使用本次实际角，使该点只表达无稳轴尚未追到的偏差。
     */
    public Vec3 relativePoint(AimGeometrySnapshot geometry) {
        Vec2 actual = geometry.actualAngles();
        return AimMath.buildAimPoint(geometry, new Vec2(mode.pitchStable ? actual.x : relativeAim.x,
                mode.yawStable ? actual.y : relativeAim.y));
    }

    /**
     * 主线程在炮镜观察参考内应用水平偏航、垂直俯仰输入。<br>
     * 稳定轴按目标相对光轴的偏角过滤输入；无稳轴限制累计目标相对机构实际角的偏差。
     * 到达输入边界时只拒绝继续扩大的增量，反向输入立即生效。
     *
     * @param sight 本次实际光轴位姿，仅用于解释鼠标方向和世界点投影
     * @param geometry 主 WC 的机构采样；全稳定模式可以为 null
     * @param delta 本次角增量，弧度，x=俯仰、y=偏航
     * @return 本次接受的角增量，零值表示目标保持原值
     */
    public Vec2 applyMouse(AimPose sight, @Nullable AimGeometrySnapshot geometry, Vec2 delta) {
        if (delta.x == 0 && delta.y == 0) {
            return Vec2.ZERO;
        }

        // 目标方向只在光轴局部空间表达，水平与垂直各处理一个输入分量。
        Vec2 offset = ViewMath.inherit(sight.rotation(), sight.position(), worldPoint);
        Vec2 limited = AimMath.filterLead(delta, offset, Vec2.ZERO);
        if (mode != Mode.WORLD && geometry == null) {
            throw new IllegalArgumentException("无稳轴输入需要主 WC 的机构采样");
        }
        Vec2 actual = geometry == null ? Vec2.ZERO : geometry.actualAngles();
        // native 偏航在 ±π 处换支；把实际角放回累计目标附近，跨周不产生整圈误差。
        double actualYaw = relativeAim.y + Math.IEEEremainder(actual.y - relativeAim.y, 2 * Math.PI);
        Vec2 accepted = new Vec2(mode.pitchStable ? limited.x
                : (float) AimMath.filterLead(delta.x, relativeAim.x, actual.x, MAX_RELATIVE_OFFSET_RAD),
                mode.yawStable ? limited.y
                : (float) AimMath.filterLead(delta.y, relativeAim.y, actualYaw, MAX_RELATIVE_OFFSET_RAD));
        if (accepted.x == 0 && accepted.y == 0) {
            return Vec2.ZERO;
        }

        // 无稳控制传完整累计值；重发和物理子步只读取，不重复消费鼠标增量。
        relativeAim = relativeAim.add(accepted);
        Vec2 next = new Vec2(offset.x + accepted.x, offset.y + accepted.y);
        worldPoint = ViewMath.follow(sight.rotation(), next, sight.position()).worldPoint();
        if (geometry != null) {
            updateGeometry(geometry);
        }
        return accepted;
    }
}
