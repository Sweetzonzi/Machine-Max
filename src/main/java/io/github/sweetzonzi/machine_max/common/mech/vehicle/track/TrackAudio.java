package io.github.sweetzonzi.machine_max.common.mech.vehicle.track;

import cn.solarmoon.spark_core.sound.ISoundSpreader;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
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
 * <p>两层各自优先走 FMOD：型号在 {@code fmod} 里写了这一层的事件就由 Studio 发声，
 * 写空或实例起不来（bank 未加载、事件缺失、参数写不进去）时该层回落 {@link #TIRE_METAL}
 * 与 {@link #TIRE_EXTRA_SLIP} 两条 OGG 采样。同层两路不同时响。</p>
 *
 * <p>一条履带只有这两条音效，与它有多少个负重轮无关。断带的履带由成员各自按
 * 一般轮子放音，本类在断带时停止。</p>
 *
 * <p>线程：主线程。位置取本 tick 接地成员的接触点形心。</p>
 */
public class TrackAudio implements ISoundSpreader {

    /** 履带当前使用的滚动与滑移采样，传播范围为 64 格。 */
    private static final SoundEvent TIRE_METAL = SoundEvent.createFixedRangeEvent(
            ResourceLocation.parse("machine_max:part.collision.terrain.tire_metal"), 64f);
    private static final SoundEvent TIRE_EXTRA_SLIP = SoundEvent.createFixedRangeEvent(
            ResourceLocation.parse("machine_max:part.collision.terrain.tire_extra_slip"), 64f);

    /** 滚动音满刻度带速（m/s）与对应的音量。 */
    private static final float ROLL_FULL_SPEED = 20f;
    private static final float ROLL_MAX_VOLUME = 0.4f;
    /** 滑移音起声阈值、满刻度偏差（m/s）与对应的音量。 */
    private static final float SLIP_FLOOR = 0.5f;
    private static final float SLIP_FULL_SPEED = 8f;
    private static final float SLIP_MAX_VOLUME = 0.35f;

    /**
     * 滚动层 FMOD 的起声带速（m/s）。
     *
     * <p>履带常年接地，{@code contact} 在停车时同样成立；不带这一档的话停着的车会有一条
     * 常驻的 rpm=0 循环音。带速低于该值按停住处理，实例淡出。</p>
     */
    private static final float ROLL_FLOOR = 0.2f;
    /** 事件 rpm 的满刻度实际转速（r/min），与 {@code CollisionEffectManager} 的接触音同一基准。 */
    private static final float FULL_RPM = 1200f;
    /** 实例创建失败后至多每 20 tick 重试一次。 */
    private static final int RETRY_TICKS = 20;

    private final Level level;
    /** 型号配置的两层事件；空表示两层都用 OGG 采样。 */
    @Nullable
    private final FmodLayer rollLayer;
    @Nullable
    private final FmodLayer screechLayer;

    private UUID rollUuid;
    private UUID slipUuid;
    private float rollVolume;
    private float slipVolume;
    private float rollPitch = 1f;
    private float slipPitch = 1f;
    private double positionX;
    private double positionY;
    private double positionZ;

    TrackAudio(Level level, Optional<TrackAttr.FmodSounds> fmod) {
        this.level = level;
        TrackAttr.FmodSounds sounds = fmod.orElse(null);
        String rollEvent = sounds == null ? null : sounds.roll().orElse(null);
        String screechEvent = sounds == null ? null : sounds.screech().orElse(null);
        this.rollLayer = rollEvent == null ? null : new FmodLayer(sounds.bank(), rollEvent, "rpm");
        this.screechLayer = screechEvent == null ? null : new FmodLayer(sounds.bank(), screechEvent, "amount");
    }

    /** 履带音效与所属载具共用一个世界。 */
    @NotNull
    @Override
    public Level getLevel() {
        return level;
    }

    /**
     * 主线程：按本 tick 的接地与速度状态更新两层音效。
     *
     * @param contact    本 tick 是否有成员接地
     * @param beltSpeed  带速（m/s，绝对值）
     * @param omega      主动轮相对车体的局部 X 角速度（rad/s），滚动层的 rpm 由此换算
     * @param slip       带速与接地成员对地速度之差的最大值（m/s）
     * @param centerX    接地接触点形心
     * @param centerY    接地接触点形心
     * @param centerZ    接地接触点形心
     */
    void tick(boolean contact, float beltSpeed, float omega, float slip,
              float centerX, float centerY, float centerZ) {
        positionX = centerX;
        positionY = centerY;
        positionZ = centerZ;

        float speedRatio = Math.clamp(beltSpeed / ROLL_FULL_SPEED, 0f, 1f);
        float slipAmount = Math.clamp((slip - SLIP_FLOOR) / (SLIP_FULL_SPEED - SLIP_FLOOR), 0f, 1f);
        float rpmStrength = contact && beltSpeed > ROLL_FLOOR ? rpm(omega) : 0f;
        float slipStrength = contact && slip > SLIP_FLOOR ? slipAmount : 0f;

        // 先问 FMOD：实例在发声时同层的 OGG 让位；起不来时这一行返回 false，走下面的 OGG 分支
        boolean rollFmod = rollLayer != null && rollLayer.tick(rpmStrength);
        if (rollFmod) {
            fadeRoll();
        } else if (contact) {
            if (rollUuid == null) {
                rollUuid = playSpreadingSound(TIRE_METAL,
                        SoundSource.BLOCKS, 4, 8, true);
            }
            rollVolume = speedRatio * ROLL_MAX_VOLUME;
            rollPitch = 0.8f + 0.4f * speedRatio;
        } else {
            fadeRoll();
        }

        boolean slipFmod = screechLayer != null && screechLayer.tick(slipStrength);
        if (slipFmod) {
            fadeSlip();
        } else if (contact && slip > SLIP_FLOOR) {
            if (slipUuid == null) {
                slipUuid = playSpreadingSound(TIRE_EXTRA_SLIP,
                        SoundSource.BLOCKS, 4, 8, true);
            }
            slipVolume = slipAmount * SLIP_MAX_VOLUME;
            slipPitch = 0.85f + 0.5f * slipAmount;
        } else {
            fadeSlip();
        }
    }

    /** 主线程：履带被移除 / 解散时停掉两层音效，OGG 与 FMOD 都停。 */
    void stop() {
        fadeRoll();
        fadeSlip();
        if (rollLayer != null) rollLayer.stop();
        if (screechLayer != null) screechLayer.stop();
    }

    private void fadeRoll() {
        if (rollUuid != null) {
            fadeSound(rollUuid);
            rollUuid = null;
            rollVolume = 0f;
            rollPitch = 1f;
        }
    }

    private void fadeSlip() {
        if (slipUuid != null) {
            fadeSound(slipUuid);
            slipUuid = null;
            slipVolume = 0f;
            slipPitch = 1f;
        }
    }

    /** 主动轮转速 → 事件 rpm（0～100，满刻度 {@link #FULL_RPM}）。 */
    private static float rpm(float omega) {
        float actual = Math.abs(omega) * (30f / (float) Math.PI);
        return Math.clamp(actual / FULL_RPM, 0f, 1f) * 100f;
    }

    @NotNull
    @Override
    public Vec3 getPosition(UUID uuid) {
        return new Vec3(positionX, positionY, positionZ);
    }

    @NotNull
    @Override
    public Vec3 getSpeed(UUID uuid) {
        return Vec3.ZERO;
    }

    @Override
    public float getVolume(UUID uuid) {
        if (uuid.equals(rollUuid)) return rollVolume;
        if (uuid.equals(slipUuid)) return slipVolume;
        return 0f;
    }

    @Override
    public float getPitch(UUID uuid) {
        if (uuid.equals(rollUuid)) return rollPitch;
        if (uuid.equals(slipUuid)) return slipPitch;
        return 1f;
    }

    /** 客户端实时听者判定与发动机、接触音共用同一规则：车内听者拿 {@code interior} 变体。 */
    @Override
    public boolean shouldApplyInteriorEffect(@Nullable Entity listener, boolean firstPerson) {
        return AbstractSubsystem.isInteriorListener(listener, firstPerson);
    }

    /**
     * 一层 FMOD 实例：起声时创建并写入初值，运转中每 tick 更新参数，静音时请求停止过渡。
     *
     * <p>创建失败后按 {@link #RETRY_TICKS} 限频重试——bank 是客户端资源，可能在履带已经开始
     * 滚动之后才加载完。参数写不进去说明实例已经失效，与创建失败同样处理，让这一 tick
     * 就回落到 OGG。</p>
     */
    private final class FmodLayer {
        private final ResourceLocation bank;
        private final String event;
        /** 驱动强度的参数名：滚动层 {@code rpm}，滑移层 {@code amount}。 */
        private final String parameter;
        private UUID voice;
        /** 已请求停止过渡的上一实例，下一次淡出前先释放，防止接缝处堆积。 */
        private UUID fadingVoice;
        private int retryTicks;

        private FmodLayer(ResourceLocation bank, String event, String parameter) {
            this.bank = bank;
            this.event = event;
            this.parameter = parameter;
        }

        /**
         * 主线程：按本 tick 的强度更新实例。
         *
         * @param strength 写进事件的参数值；0 表示这一层此刻不该发声
         * @return true 表示这一 tick 由 FMOD 发声，同层的 OGG 须让位
         */
        private boolean tick(float strength) {
            if (retryTicks > 0) retryTicks--;
            if (strength <= 0f) {
                fade();
                return false;
            }
            if (voice == null) {
                if (retryTicks > 0) return false;
                voice = tryPlayFmodSound(bank, event, SoundSource.BLOCKS, Map.of(parameter, strength));
                if (voice == null) {
                    retryTicks = RETRY_TICKS;
                    return false;
                }
                return true;
            }
            if (!setFmodParameter(voice, parameter, strength)) {
                stopFmodSound(voice);
                voice = null;
                retryTicks = RETRY_TICKS;
                return false;
            }
            return true;
        }

        /** 主线程：请求 Studio 停止过渡，进入尾音的实例由播放器自己释放。 */
        private void fade() {
            if (voice == null) return;
            if (fadingVoice != null) stopFmodSound(fadingVoice);
            if (fadeFmodSound(voice)) fadingVoice = voice;
            else stopFmodSound(voice);
            voice = null;
        }

        /** 主线程：生命周期结束立即停止，不走停止过渡。 */
        private void stop() {
            if (voice != null) stopFmodSound(voice);
            if (fadingVoice != null) stopFmodSound(fadingVoice);
            voice = null;
            fadingVoice = null;
            retryTicks = 0;
        }
    }
}
