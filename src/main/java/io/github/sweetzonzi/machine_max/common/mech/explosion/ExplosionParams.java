package io.github.sweetzonzi.machine_max.common.mech.explosion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 单发爆炸的参数。
 *
 * <p><b>参数表里没有任何能量量纲</b>：三个终端标定量（{@code base_penetration}、
 * {@code base_damage}、{@code base_impulse}）都以"参考半径处、无介质消耗、单位迎流面积"标定，
 * 因此最直观的读法是"贴脸时每平方米吃多少伤害/多少冲量"。</p>
 *
 * <p><b>射线数不在这里</b>：它由 {@link BlastRayGenerator#adaptiveRayCount(float)} 在起爆时
 * 依 {@code near_radius} 程序化导出，JSON 不可配置——客户端按同一公式重算即可，两侧不可能取值不一致。</p>
 *
 * <p>不可变对象；JSON 用 {@link #CODEC}，网络用 {@link #STREAM_CODEC}。</p>
 *
 * @param basePenetration 参考半径处、无介质消耗时的穿深（mm，<b>强度量</b>，必填）
 * @param baseDamage      参考半径处、无介质消耗时单位迎流面积被完全击穿所受的伤害（必填）
 * @param baseImpulse     参考半径处、无介质消耗时单位迎流面积所受的冲量（必填）
 * @param nearRadius      参考距离，三个标定量的公共参考点；同时是近场平台半径（m，建议 2–4）
 * @param maxRadius       硬截断半径，射线推进终点（m，必填）
 * @param frontSpeed      波前推进速度（m/s，默认 20.0）
 * @param destroyBlocks   是否破坏地形（默认 true）
 * @param dropItems       摧毁方块是否掉落（默认 false）
 * @param causesFire      是否点燃；首期强制 false（尚无实现）
 * @param particles       起爆瞬间在起爆点播放的基岩版粒子特效 id 列表（默认空列表）。
 *                        按 Spark-Core 内容包的粒子定义解析，因此可写 {@code machine_max:blast_medium}
 *                        这类标识符；列表为空即该爆炸不起爆粒子。构造时会复制为不可变列表。
 * @param particleScale   {@code particles} 的整体缩放系数（默认 1.0，下界 1e-3）。
 *                        <b>它缩放的是散布而不是贴图尺寸</b>：Spark-Core 只在粒子生成瞬间把发射器变换
 *                        作用在局部偏移与初始速度上，因此形状半径与飞散距离按此系数等比放大，
 *                        而 {@code particle_appearance_billboard.size} 决定的单个烟团大小不变；
 *                        模拟随后回到世界空间，重力与浮力加速度也不随此系数放大。
 */
public record ExplosionParams(
        float basePenetration,
        float baseDamage,
        float baseImpulse,
        float nearRadius,
        float maxRadius,
        float frontSpeed,
        boolean destroyBlocks,
        boolean dropItems,
        boolean causesFire,
        List<ResourceLocation> particles,
        float particleScale
) {

    /**
     * JSON MapCodec，供内容包加载。
     * <p>
     * 字段直接平铺在当前对象上，因此本类型可直接作为其它 Codec 的一层
     * （如 {@code ExplosionWorldEffect.CODEC} 的 xmap 源），不需要额外的字段包装。
     */
    public static final MapCodec<ExplosionParams> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.fieldOf("base_penetration").forGetter(ExplosionParams::basePenetration),
            Codec.FLOAT.fieldOf("base_damage").forGetter(ExplosionParams::baseDamage),
            Codec.FLOAT.fieldOf("base_impulse").forGetter(ExplosionParams::baseImpulse),
            Codec.FLOAT.optionalFieldOf("near_radius", 3.0f).forGetter(ExplosionParams::nearRadius),
            Codec.FLOAT.fieldOf("max_radius").forGetter(ExplosionParams::maxRadius),
            Codec.FLOAT.optionalFieldOf("front_speed", 20.0f).forGetter(ExplosionParams::frontSpeed),
            Codec.BOOL.optionalFieldOf("destroy_blocks", true).forGetter(ExplosionParams::destroyBlocks),
            Codec.BOOL.optionalFieldOf("drop_items", false).forGetter(ExplosionParams::dropItems),
            Codec.BOOL.optionalFieldOf("causes_fire", false).forGetter(ExplosionParams::causesFire),
            Codec.list(ResourceLocation.CODEC).optionalFieldOf("particles", List.of())
                    .forGetter(ExplosionParams::particles),
            Codec.FLOAT.optionalFieldOf("particle_scale", 1.0f).forGetter(ExplosionParams::particleScale)
    ).apply(instance, ExplosionParams::new));

    /** JSON Codec（由 {@link #MAP_CODEC} 派生），供内容包加载。 */
    public static final Codec<ExplosionParams> CODEC = MAP_CODEC.codec();

    /** 网络 StreamCodec：起爆包携带"起爆点 + 种子 + 参数集"中的参数集部分。 */
    public static final StreamCodec<RegistryFriendlyByteBuf, ExplosionParams> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ExplosionParams decode(RegistryFriendlyByteBuf buf) {
            return new ExplosionParams(
                    buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                    PARTICLES_STREAM_CODEC.decode(buf),
                    buf.readFloat());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, ExplosionParams p) {
            buf.writeFloat(p.basePenetration());
            buf.writeFloat(p.baseDamage());
            buf.writeFloat(p.baseImpulse());
            buf.writeFloat(p.nearRadius());
            buf.writeFloat(p.maxRadius());
            buf.writeFloat(p.frontSpeed());
            buf.writeBoolean(p.destroyBlocks());
            buf.writeBoolean(p.dropItems());
            buf.writeBoolean(p.causesFire());
            PARTICLES_STREAM_CODEC.encode(buf, p.particles());
            buf.writeFloat(p.particleScale());
        }
    };

    /** {@link ExplosionParams#particles()} 的网络编解码；空列表按长度 0 编解码。 */
    private static final StreamCodec<ByteBuf, List<ResourceLocation>> PARTICLES_STREAM_CODEC =
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list());

    /**
     * 紧凑构造器：做最小必要的防呆。
     *
     * <ul>
     *   <li>{@code causesFire} 强制为 false——内容包里写 true 也不会生效，避免出现"没有实现的开关"；</li>
     *   <li>{@code nearRadius}/{@code frontSpeed} 取下界，避免除零与零步长；</li>
     *   <li>{@code maxRadius} 不小于 {@code nearRadius}；</li>
     *   <li>{@code particles} 复制为不可变列表，使实例整体保持不可变；</li>
     *   <li>{@code particleScale} 取下界，避免零或负系数把粒子云压成一点或反向。</li>
     * </ul>
     */
    public ExplosionParams {
        causesFire = false;
        nearRadius = Math.max(nearRadius, 0.1f);
        frontSpeed = Math.max(frontSpeed, 1e-3f);
        maxRadius = Math.max(maxRadius, nearRadius);
        particles = List.copyOf(particles);
        particleScale = Math.max(particleScale, 1e-3f);
    }

    /** 每主线程 tick 的推进距离（m）。 */
    public float advancePerStep() {
        return frontSpeed / 20f;
    }
}
