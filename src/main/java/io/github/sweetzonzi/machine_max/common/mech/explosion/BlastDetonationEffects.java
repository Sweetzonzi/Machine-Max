package io.github.sweetzonzi.machine_max.common.mech.explosion;

import cn.solarmoon.spark_core.api.SpreadingSoundHelper;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Vector3f;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 起爆的客户端一次性表现：爆炸粒子 + 带声速的爆炸音效。
 *
 * <p>由起爆包处理器在收到服务端起爆事件时调用（主线程）。表现只依赖起爆点与
 * {@link ExplosionParams}，不需要射线数据，因此与逻辑侧完全解耦。</p>
 *
 * <p><b>音效分档</b>：原版只有一个爆炸音效事件（{@code entity.generic.explode}），
 * 因此小/中两档借用原版烟花爆炸音（{@code entity.firework_rocket.blast}、
 * {@code entity.firework_rocket.large_blast}），大/特大两档用 {@code entity.generic.explode}；
 * 档位之间再用音高与可听半径拉开差异。分档口径是 {@link ExplosionParams#maxRadius()}。</p>
 *
 * <p><b>为什么重建 SoundEvent</b>：Spark-Core 的传播音效以 {@code soundEvent.getRange(1.0f)}
 * 作为波前推进上限，而原版记录的音效都是 variable-range（{@code getRange} 恒为 16 格），
 * 直接使用会让 16 格以外的听者永远听不到。故用 {@link SoundEvent#createFixedRangeEvent}
 * 按档位指定传播半径。</p>
 *
 * <p><b>粒子</b>：统一在起爆点生成一个原版 {@link ParticleTypes#EXPLOSION}，不随档位变化。</p>
 */
public final class BlastDetonationEffects {

    /** 工具类，不实例化。 */
    private BlastDetonationEffects() {
    }

    /**
     * 在客户端播放一次起爆表现；非客户端调用直接返回。
     *
     * @param level  收到起爆包的维度（客户端 level）
     * @param origin 起爆点（世界坐标，JME）
     * @param params 起爆参数集，用于取 {@code maxRadius} 分档
     */
    public static void playOnDetonate(Level level, Vector3f origin, ExplosionParams params) {
        if (!level.isClientSide()) return;
        // 原版爆炸烟球；SimpleParticleType 的额外参数无意义，沿用原版 (1, 0, 0)
        level.addParticle(ParticleTypes.EXPLOSION, origin.x, origin.y, origin.z, 1.0, 0.0, 0.0);
        playExplosionSound(level, origin, params.maxRadius());
    }

    /**
     * 按爆炸范围分档播放传播音效。声速由 Spark-Core 内部给定（约 100 格/秒），
     * 听者只有被波前扫到才会听到，因此远距离起爆天然带延迟。
     *
     * @param level     客户端 level
     * @param origin    起爆点
     * @param maxRadius 爆炸硬截断半径，作为分档口径
     */
    private static void playExplosionSound(Level level, Vector3f origin, float maxRadius) {
        Tier tier = Tier.of(maxRadius);
        // 音高抖动沿用原版写法 (1 + 0.2*(r1 - r2))，避免同一音效反复播放时发死
        float jitter = 1f + 0.2f * (level.random.nextFloat() - level.random.nextFloat());
        // 定点声源、起爆点静止：多普勒速度传 Vec3.ZERO
        SpreadingSoundHelper.playSpreadingSound(
                level, tier.soundEvent(), SoundSource.BLOCKS,
                SparkMathKt.toVec3(origin), Vec3.ZERO,
                tier.pitch() * jitter, 1.0f);
    }

    /**
     * 爆炸分档：口径是 {@link ExplosionParams#maxRadius()}。
     *
     * <p>阈值取自原版分界（原版以半径 2.0 区分大小爆炸），并按本模组尺度再细分出中/大/特大。
     * 音高随档位递减（越大越低沉），可听半径随档位递增（越大传得越远）。</p>
     */
    private enum Tier {
        /** 小：{@code maxRadius < 2} */
        SMALL(2f, 24f, 1.10f, SoundEvents.FIREWORK_ROCKET_BLAST),
        /** 中：{@code 2 <= maxRadius < 6} */
        MEDIUM(6f, 40f, 1.00f, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST),
        /** 大：{@code 6 <= maxRadius < 12} */
        LARGE(12f, 64f, 0.85f, SoundEvents.GENERIC_EXPLODE.value()),
        /** 特大：{@code maxRadius >= 12} */
        HUGE(Float.MAX_VALUE, 96f, 0.70f, SoundEvents.GENERIC_EXPLODE.value());

        /** 本档的上界（不含），用于顺序匹配 */
        private final float upperBound;
        /** 基准音高 */
        private final float pitch;
        /** 本档的传播音效：已在构造时按档位半径重建为定距事件，播放时直接取用 */
        private final SoundEvent soundEvent;

        Tier(float upperBound, float range, float pitch, SoundEvent vanillaSound) {
            this.upperBound = upperBound;
            this.pitch = pitch;
            // 原版音效事件是 variable-range（getRange 恒为 16 格），重建为按档位的固定传播半径
            this.soundEvent = SoundEvent.createFixedRangeEvent(vanillaSound.getLocation(), range);
        }

        /**
         * 按爆炸半径匹配档位。
         *
         * @param maxRadius 爆炸硬截断半径（m）
         * @return 对应档位，超出全部上界时取 {@link #HUGE}
         */
        static Tier of(float maxRadius) {
            for (Tier tier : values()) {
                if (maxRadius < tier.upperBound) return tier;
            }
            return HUGE;
        }

        float pitch() {
            return pitch;
        }

        SoundEvent soundEvent() {
            return soundEvent;
        }
    }
}
