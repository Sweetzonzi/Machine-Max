package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.util.data.Axis;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * 锁扣子系统型号参数。
 *
 * <p>距离单位是米，力单位是 N，冲量单位是 N·s，角度单位是度，速度单位是 m/s。所有数值在构造期
 * 收敛成有限的合法值，内容错误在装载期抛出，不在物理步才暴露。</p>
 */
@Getter
public class CouplerSubsystemStaticAttr extends BasicSubsystemStaticAttr {

    /**
     * 默认分离冲量（N·s）：落在空手一击与铁剑一击之间，在 24 kg 车门上约 0.33 m/s。
     *
     * <p>默认非零是有意的：不写这个字段的型号也在分离时给出一点看得见的初速度，避免“没写就不生效”
     * 这种静默坑。不需要分离运动的型号显式写 {@code 0}。</p>
     */
    public static final float DEFAULT_SEPARATION_IMPULSE = 8f;

    /** 吸合范围：端点距离不超过此值时允许施加合拢力。 */
    public final float captureDistance;
    /** 锁止窗口：端点距离不超过此值、朝向与相对速率同时满足时提交连接。 */
    public final float lockDistance;
    /** 合拢力上限（N）。{@code 0} 表示只靠已有作动器或外力合拢。 */
    public final float maxCaptureForce;
    /** 允许的相向法线误差（度）。 */
    public final float lockAngleTolerance;
    /** 锁止时允许的端点相对速率（m/s）。 */
    public final float maxLockSpeed;
    /** 重武装距离倍率；重武装距离 = max(capture_distance, n * lock_distance)。 */
    public final float rearmDistanceMultiplier;
    /**
     * 分离冲量（N·s）：解扣或意外脱锁时施加给离开的那一个子零件；{@code 0} 关闭。
     *
     * <p>冲量对质量是盲的，默认值只对轻件有意义，重件必须自己给。</p>
     */
    public final float separationImpulse;
    /**
     * 分离方向：宿主子零件局部帧的方向轴；{@code null} 表示按“宿主质心指向分离点”自动取。
     *
     * <p>不从连接点法线推导：法线只保证两端相反，引擎盖与车门的实际取值并不都朝外。</p>
     */
    @Nullable
    public final Axis separationDirection;
    /** {@code true} 表示只有锁扣自己解开的那一次施加，意外脱锁不施加。 */
    public final boolean separationCommandOnly;
    /** {@code false} 施加在分离点（有力臂，产生初始自转），{@code true} 施加在质心（纯平移）。 */
    public final boolean separationApplyOnCenter;

    public final CouplerSoundAttr sounds;

    /**
     * 锁扣专属音效：基础生命周期音效加一次连接／断开音。
     *
     * <p>结构沿用 {@code GearboxSubsystemStaticAttr.GearBoxSoundAttr}：{@code sounds} 仍是一个
     * 扁平对象，基础三音由 {@link BasicSoundAttr#basicSounds} 复用，另加两个字段。</p>
     */
    public record CouplerSoundAttr(
            BasicSoundAttr basicSounds,
            SoundEvent onAttach,
            SoundEvent onDetach
    ) {
        /** 默认锁扣音：与原版绊线挂接同一事件，内容可分别覆写。 */
        public static final SoundEvent DEFAULT_ATTACH =
                SoundEvent.createVariableRangeEvent(ResourceLocation.withDefaultNamespace("block.tripwire.attach"));

        public static final CouplerSoundAttr DEFAULT =
                new CouplerSoundAttr(BasicSoundAttr.DEFAULT, DEFAULT_ATTACH, DEFAULT_ATTACH);

        public static final Codec<CouplerSoundAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BasicSoundAttr.basicSounds(CouplerSoundAttr::basicSounds),
                SoundEvent.DIRECT_CODEC.optionalFieldOf("on_attach", DEFAULT.onAttach)
                        .forGetter(CouplerSoundAttr::onAttach),
                SoundEvent.DIRECT_CODEC.optionalFieldOf("on_detach", DEFAULT.onDetach)
                        .forGetter(CouplerSoundAttr::onDetach)
        ).apply(instance, CouplerSoundAttr::new));
    }

    public static final MapCodec<CouplerSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
            Codec.FLOAT.fieldOf("capture_distance").forGetter(CouplerSubsystemStaticAttr::getCaptureDistance),
            Codec.FLOAT.fieldOf("lock_distance").forGetter(CouplerSubsystemStaticAttr::getLockDistance),
            Codec.FLOAT.optionalFieldOf("max_capture_force", 5f)
                    .forGetter(CouplerSubsystemStaticAttr::getMaxCaptureForce),
            Codec.FLOAT.fieldOf("lock_angle_tolerance")
                    .forGetter(CouplerSubsystemStaticAttr::getLockAngleTolerance),
            Codec.FLOAT.optionalFieldOf("max_lock_speed", 0.5f)
                    .forGetter(CouplerSubsystemStaticAttr::getMaxLockSpeed),
            Codec.FLOAT.optionalFieldOf("rearm_distance_multiplier", 2f)
                    .forGetter(CouplerSubsystemStaticAttr::getRearmDistanceMultiplier),
            Codec.FLOAT.optionalFieldOf("separation_impulse", DEFAULT_SEPARATION_IMPULSE)
                    .forGetter(CouplerSubsystemStaticAttr::getSeparationImpulse),
            Axis.CODEC.optionalFieldOf("separation_direction")
                    .forGetter(attr -> Optional.ofNullable(attr.separationDirection)),
            Codec.BOOL.optionalFieldOf("separation_command_only", false)
                    .forGetter(CouplerSubsystemStaticAttr::isSeparationCommandOnly),
            Codec.BOOL.optionalFieldOf("separation_apply_on_center", false)
                    .forGetter(CouplerSubsystemStaticAttr::isSeparationApplyOnCenter),
            CouplerSoundAttr.CODEC.optionalFieldOf("sounds", CouplerSoundAttr.DEFAULT)
                    .forGetter(CouplerSubsystemStaticAttr::getSounds)
    ).apply(instance, (basic, capture, lock, force, angle, speed, rearm, impulse, direction,
                       commandOnly, onCenter, sounds) ->
            new CouplerSubsystemStaticAttr(basic, capture, lock, force, angle, speed, rearm,
                    impulse, direction.orElse(null), commandOnly, onCenter, sounds)));

    public CouplerSubsystemStaticAttr(
            BasicAttr basicAttr,
            float captureDistance,
            float lockDistance,
            float maxCaptureForce,
            float lockAngleTolerance,
            float maxLockSpeed,
            float rearmDistanceMultiplier,
            float separationImpulse,
            @Nullable Axis separationDirection,
            boolean separationCommandOnly,
            boolean separationApplyOnCenter,
            CouplerSoundAttr sounds
    ) {
        super(basicAttr, sounds.basicSounds());
        requireFinite("capture_distance", captureDistance);
        requireFinite("lock_distance", lockDistance);
        requireFinite("max_capture_force", maxCaptureForce);
        requireFinite("lock_angle_tolerance", lockAngleTolerance);
        requireFinite("max_lock_speed", maxLockSpeed);
        requireFinite("rearm_distance_multiplier", rearmDistanceMultiplier);
        requireFinite("separation_impulse", separationImpulse);
        if (captureDistance <= 0f) throw new IllegalArgumentException("capture_distance must be > 0");
        if (lockDistance <= 0f) throw new IllegalArgumentException("lock_distance must be > 0");
        if (lockDistance > captureDistance) throw new IllegalArgumentException("lock_distance must not exceed capture_distance");
        if (maxCaptureForce < 0f) throw new IllegalArgumentException("max_capture_force must be >= 0");
        if (lockAngleTolerance <= 0f || lockAngleTolerance > 90f)
            throw new IllegalArgumentException("lock_angle_tolerance must be in (0, 90]");
        if (maxLockSpeed < 0f) throw new IllegalArgumentException("max_lock_speed must be >= 0");
        if (rearmDistanceMultiplier <= 1f) throw new IllegalArgumentException("rearm_distance_multiplier must be > 1");
        if (separationImpulse < 0f) throw new IllegalArgumentException("separation_impulse must be >= 0");
        if (separationDirection != null && !separationDirection.isDirectional())
            throw new IllegalArgumentException("separation_direction must be a directional axis: " + separationDirection);
        this.captureDistance = captureDistance;
        this.lockDistance = lockDistance;
        this.maxCaptureForce = maxCaptureForce;
        this.lockAngleTolerance = lockAngleTolerance;
        this.maxLockSpeed = maxLockSpeed;
        this.rearmDistanceMultiplier = rearmDistanceMultiplier;
        this.separationImpulse = separationImpulse;
        this.separationDirection = separationDirection;
        this.separationCommandOnly = separationCommandOnly;
        this.separationApplyOnCenter = separationApplyOnCenter;
        this.sounds = sounds;
    }

    private static void requireFinite(String field, float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException(field + " must be finite");
    }

    /** 重武装距离：端点离开到这个距离之后才允许再次捕获。 */
    public float rearmDistance() {
        return Math.max(captureDistance, rearmDistanceMultiplier * lockDistance);
    }

    /** 角度容差换算成弧度，供物理判据比较。 */
    public float lockAngleToleranceRadians() {
        return (float) Math.toRadians(lockAngleTolerance);
    }

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }
}
