package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * 锁扣子系统型号参数。
 *
 * <p>距离单位是米，力单位是 N，角度单位是度，速度单位是 m/s。所有数值在构造期收敛成有限的
 * 合法值，内容错误在装载期抛出，不在物理步才暴露。</p>
 */
@Getter
public class CouplerSubsystemStaticAttr extends BasicSubsystemStaticAttr {

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
            Codec.FLOAT.optionalFieldOf("max_capture_force", 0f)
                    .forGetter(CouplerSubsystemStaticAttr::getMaxCaptureForce),
            Codec.FLOAT.fieldOf("lock_angle_tolerance")
                    .forGetter(CouplerSubsystemStaticAttr::getLockAngleTolerance),
            Codec.FLOAT.optionalFieldOf("max_lock_speed", 0.5f)
                    .forGetter(CouplerSubsystemStaticAttr::getMaxLockSpeed),
            Codec.FLOAT.optionalFieldOf("rearm_distance_multiplier", 2f)
                    .forGetter(CouplerSubsystemStaticAttr::getRearmDistanceMultiplier),
            CouplerSoundAttr.CODEC.optionalFieldOf("sounds", CouplerSoundAttr.DEFAULT)
                    .forGetter(CouplerSubsystemStaticAttr::getSounds)
    ).apply(instance, CouplerSubsystemStaticAttr::new));

    public CouplerSubsystemStaticAttr(
            BasicAttr basicAttr,
            float captureDistance,
            float lockDistance,
            float maxCaptureForce,
            float lockAngleTolerance,
            float maxLockSpeed,
            float rearmDistanceMultiplier,
            CouplerSoundAttr sounds
    ) {
        super(basicAttr, sounds.basicSounds());
        requireFinite("capture_distance", captureDistance);
        requireFinite("lock_distance", lockDistance);
        requireFinite("max_capture_force", maxCaptureForce);
        requireFinite("lock_angle_tolerance", lockAngleTolerance);
        requireFinite("max_lock_speed", maxLockSpeed);
        requireFinite("rearm_distance_multiplier", rearmDistanceMultiplier);
        if (captureDistance <= 0f) throw new IllegalArgumentException("capture_distance must be > 0");
        if (lockDistance <= 0f) throw new IllegalArgumentException("lock_distance must be > 0");
        if (lockDistance > captureDistance) throw new IllegalArgumentException("lock_distance must not exceed capture_distance");
        if (maxCaptureForce < 0f) throw new IllegalArgumentException("max_capture_force must be >= 0");
        if (lockAngleTolerance <= 0f || lockAngleTolerance > 90f)
            throw new IllegalArgumentException("lock_angle_tolerance must be in (0, 90]");
        if (maxLockSpeed < 0f) throw new IllegalArgumentException("max_lock_speed must be >= 0");
        if (rearmDistanceMultiplier <= 1f) throw new IllegalArgumentException("rearm_distance_multiplier must be > 1");
        this.captureDistance = captureDistance;
        this.lockDistance = lockDistance;
        this.maxCaptureForce = maxCaptureForce;
        this.lockAngleTolerance = lockAngleTolerance;
        this.maxLockSpeed = maxLockSpeed;
        this.rearmDistanceMultiplier = rearmDistanceMultiplier;
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
