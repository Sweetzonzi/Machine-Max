package io.github.sweetzonzi.machine_max.common.mech.vehicle.track;

import cn.solarmoon.spark_core.api.SpreadingSoundHelper;
import cn.solarmoon.spark_core.sound.ISoundSpreader;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.collision.CollisionEffectManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * 一条履带的轮面音效，由 {@link TrackLink#clientTick()} 每 tick 驱动。
 *
 * <p>两层叠加，各自一条循环音：</p>
 * <ul>
 *   <li><b>滚动</b>：任一成员接地即起声，音量随带速从 0 连续上升，不设速度阈值</li>
 *   <li><b>滑移</b>：音量随「带速与接地成员对地速度之差」上升，差值小于
 *       {@link #SLIP_FLOOR} 时不起声</li>
 * </ul>
 *
 * <p>一条履带只有这两条音效，与它有多少个负重轮无关。断带的履带由成员各自按
 * 一般轮子放音，本类在断带时停止。</p>
 *
 * <p>线程：主线程。位置取本 tick 接地成员的接触点形心。</p>
 */
public class TrackAudio implements ISoundSpreader {

    /** 滚动音满刻度带速（m/s）与对应的音量。 */
    private static final float ROLL_FULL_SPEED = 20f;
    private static final float ROLL_MAX_VOLUME = 0.4f;
    /** 滑移音起声阈值、满刻度偏差（m/s）与对应的音量。 */
    private static final float SLIP_FLOOR = 0.5f;
    private static final float SLIP_FULL_SPEED = 8f;
    private static final float SLIP_MAX_VOLUME = 0.35f;

    private UUID rollUuid;
    private UUID slipUuid;
    private float rollVolume;
    private float slipVolume;
    private float rollPitch = 1f;
    private float slipPitch = 1f;
    private double positionX;
    private double positionY;
    private double positionZ;

    /**
     * 主线程：按本 tick 的接地与速度状态更新两层音效。
     *
     * @param contact    本 tick 是否有成员接地
     * @param beltSpeed  带速（m/s，绝对值）
     * @param slip       带速与接地成员对地速度之差的最大值（m/s）
     * @param centerX    接地接触点形心
     * @param centerY    接地接触点形心
     * @param centerZ    接地接触点形心
     */
    void tick(Level level, boolean contact, float beltSpeed, float slip,
              float centerX, float centerY, float centerZ) {
        positionX = centerX;
        positionY = centerY;
        positionZ = centerZ;

        float speedRatio = Math.clamp(beltSpeed / ROLL_FULL_SPEED, 0f, 1f);
        float slipAmount = Math.clamp((slip - SLIP_FLOOR) / (SLIP_FULL_SPEED - SLIP_FLOOR), 0f, 1f);

        if (contact) {
            if (rollUuid == null) {
                rollUuid = playSpreadingSound(level, CollisionEffectManager.TIRE_METAL,
                        SoundSource.BLOCKS, 4, 8, true);
            }
            rollVolume = speedRatio * ROLL_MAX_VOLUME;
            rollPitch = 0.8f + 0.4f * speedRatio;
        } else {
            fadeRoll(level);
        }

        if (contact && slip > SLIP_FLOOR) {
            if (slipUuid == null) {
                slipUuid = playSpreadingSound(level, CollisionEffectManager.TIRE_EXTRA_SLIP,
                        SoundSource.BLOCKS, 4, 8, true);
            }
            slipVolume = slipAmount * SLIP_MAX_VOLUME;
            slipPitch = 0.85f + 0.5f * slipAmount;
        } else {
            fadeSlip(level);
        }
    }

    /** 主线程：履带被移除 / 解散时停掉两层音效。 */
    void stop(Level level) {
        fadeRoll(level);
        fadeSlip(level);
    }

    private void fadeRoll(Level level) {
        if (rollUuid != null) {
            SpreadingSoundHelper.fadeSound(level, rollUuid);
            rollUuid = null;
            rollVolume = 0f;
            rollPitch = 1f;
        }
    }

    private void fadeSlip(Level level) {
        if (slipUuid != null) {
            SpreadingSoundHelper.fadeSound(level, slipUuid);
            slipUuid = null;
            slipVolume = 0f;
            slipPitch = 1f;
        }
    }

    @NotNull
    @Override
    public Vec3 getPosition(UUID uuid, SoundEvent event) {
        return new Vec3(positionX, positionY, positionZ);
    }

    @NotNull
    @Override
    public Vec3 getSpeed(UUID uuid, SoundEvent event) {
        return Vec3.ZERO;
    }

    @Override
    public float getVolume(UUID uuid, SoundEvent event) {
        if (uuid.equals(rollUuid)) return rollVolume;
        if (uuid.equals(slipUuid)) return slipVolume;
        return 0f;
    }

    @Override
    public float getPitch(UUID uuid, SoundEvent event) {
        if (uuid.equals(rollUuid)) return rollPitch;
        if (uuid.equals(slipUuid)) return slipPitch;
        return 1f;
    }
}
