package io.github.sweetzonzi.machine_max.common.mech.explosion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * 单次爆炸火光的参数：内容包爆炸参数里的可选子对象 {@code light}。
 *
 * <p>它只描述“这次起爆在客户端投射一颗多大的屏幕空间光源”，不改变伤害、冲量、波前速度、起火与粒子，
 * 也不参与任何逻辑结算。所有引用同一份爆炸参数的弹种/世界效果共用同一份火光参数；每种弹按需覆盖。</p>
 *
 * <p><b>它不含时间包络</b>：年龄到亮度的换算（起爆立即到峰值、结束时平滑归零）由客户端火光状态按
 * 同一个游戏时钟求值，见 {@code client/render/post/ExplosionLightState}。这里只放静态配置。</p>
 *
 * <p>三条取值边界同时写在 codec 与紧凑构造器里：codec 让内容包里的非法值在加载期带字段路径报错
 * （字段路径形如 {@code light.radius}），紧凑构造器做第二轮防呆——网络解码拿到的是对端字节，
 * 反序列化路径上抛异常只会把整包丢掉，因此那里收敛到合法区间而不抛。</p>
 *
 * <p>不可变对象；JSON 用 {@link #CODEC}，网络用 {@link #STREAM_CODEC}。</p>
 *
 * @param enabled       是否产生火光；false 表示该爆炸完全不发光，其余字段仍照常同步
 * @param colorRgb      颜色，打包的 {@code 0xRRGGBB}；每个通道都在 {@code [0,255]}
 * @param radius        固定照射半径（m），{@code 0 < R <= 64}；不随波前扩张
 * @param intensity     光源在 1 m 处的辐照度（{@code [0,4096]}）；0 表示不产生光照
 * @param durationTicks 总寿命（游戏 tick，{@code [1,200]}），按 20 TPS 计，暂停时冻结
 */
public record ExplosionLightParams(
        boolean enabled,
        int colorRgb,
        float radius,
        float intensity,
        int durationTicks
) {

    /** 照射半径上界（m）：建议内容边界，超出即内容包配置错误 */
    public static final float MAX_RADIUS_M = 64f;
    /**
     * 辐照度上界（1 m 处）：与枪口火光同一量纲。
     *
     * <p>缺省值约 166，4 个数量级的余量留给超大口径与特效弹种，同时挡住明显的笔误。</p>
     */
    public static final float MAX_INTENSITY = 4096f;
    /** 寿命下界（tick）：1 tick 即一次主线程 tick 的闪光 */
    public static final int MIN_DURATION_TICKS = 1;
    /** 寿命上界（tick）：200 tick = 10 秒 */
    public static final int MAX_DURATION_TICKS = 200;

    /**
     * 缺省火光：半径 8 m、辐照度 166、寿命 6 tick 的橙黄色闪光。
     *
     * <p>166 来自“半径 8 m、参考距离 4 m 处把 0.10 的灰板抬到 0.45”这个锚点（物理化设计 §4.2），
     * 是首轮标定起点，不是实测结论；内容包未写 {@code light}、或只写部分字段时逐字段回落到这里。</p>
     */
    public static final ExplosionLightParams DEFAULT = new ExplosionLightParams(true, 0xFFB464, 8f, 166f, 6);

    /** 颜色字段：恰好三个 {@code [0,255]} 整数，分别对应红、绿、蓝。 */
    private static final Codec<Integer> PACKED_COLOR_CODEC =
            Codec.intRange(0, 255).listOf(3, 3)
                    .xmap(ExplosionLightParams::packColor, ExplosionLightParams::unpackColor);

    /** JSON MapCodec，供内容包加载；字段名与 {@link #CODEC} 暴露的 JSON 一致。 */
    public static final MapCodec<ExplosionLightParams> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("enabled", DEFAULT.enabled())
                    .forGetter(ExplosionLightParams::enabled),
            PACKED_COLOR_CODEC.optionalFieldOf("color", DEFAULT.colorRgb())
                    .forGetter(ExplosionLightParams::colorRgb),
            boundedFloat("半径", MAX_RADIUS_M).optionalFieldOf("radius", DEFAULT.radius())
                    .forGetter(ExplosionLightParams::radius),
            boundedFloat("强度", MAX_INTENSITY).optionalFieldOf("intensity", DEFAULT.intensity())
                    .forGetter(ExplosionLightParams::intensity),
            Codec.INT.validate(ExplosionLightParams::validateDuration)
                    .optionalFieldOf("duration_ticks", DEFAULT.durationTicks())
                    .forGetter(ExplosionLightParams::durationTicks)
    ).apply(instance, ExplosionLightParams::new));

    /** JSON Codec（由 {@link #MAP_CODEC} 派生）。 */
    public static final Codec<ExplosionLightParams> CODEC = MAP_CODEC.codec();

    /**
     * 网络 StreamCodec：随 {@link ExplosionParams#STREAM_CODEC} 一起进入起爆包。
     *
     * <p>客户端只用服务端解析后的值重建火光，不重新读内容包，因此两侧数值必然一致。</p>
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, ExplosionLightParams> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ExplosionLightParams decode(RegistryFriendlyByteBuf buf) {
                    return new ExplosionLightParams(
                            buf.readBoolean(),
                            buf.readInt(),
                            buf.readFloat(),
                            buf.readFloat(),
                            buf.readVarInt());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, ExplosionLightParams p) {
                    buf.writeBoolean(p.enabled());
                    buf.writeInt(p.colorRgb());
                    buf.writeFloat(p.radius());
                    buf.writeFloat(p.intensity());
                    buf.writeVarInt(p.durationTicks());
                }
            };

    /**
     * 紧凑构造器：把三个数值字段收敛到合法区间，并归一化打包颜色。
     *
     * <p>NaN 与 Infinity 不在这里“修好”：半径非有限时 {@code Math.min/max} 会原样保留 NaN，
     * 交给运行时提交器按非法数据统计与限频报告，而不是悄悄换成一个看似正常的半径。</p>
     */
    public ExplosionLightParams {
        colorRgb = packColor(unpackColor(colorRgb));
        radius = Math.min(Math.max(radius, 0f), MAX_RADIUS_M);
        intensity = Math.min(Math.max(intensity, 0f), MAX_INTENSITY);
        durationTicks = Math.min(Math.max(durationTicks, MIN_DURATION_TICKS), MAX_DURATION_TICKS);
    }

    /** @return 红色通道，{@code [0,1]} */
    public float red() {
        return ((colorRgb >> 16) & 0xFF) / 255f;
    }

    /** @return 绿色通道，{@code [0,1]} */
    public float green() {
        return ((colorRgb >> 8) & 0xFF) / 255f;
    }

    /** @return 蓝色通道，{@code [0,1]} */
    public float blue() {
        return (colorRgb & 0xFF) / 255f;
    }

    private static int packColor(List<Integer> channels) {
        return (channels.get(0) << 16) | (channels.get(1) << 8) | channels.get(2);
    }

    private static List<Integer> unpackColor(int packed) {
        return List.of((packed >> 16) & 0xFF, (packed >> 8) & 0xFF, packed & 0xFF);
    }

    /**
     * 正数且有上界的浮点字段：非有限值与越界值都在内容包加载期带字段路径报错。
     *
     * @param label 报错里的字段含义（中文），与字段名一起构成可定位的消息
     * @param max   上界（含）
     */
    private static Codec<Float> boundedFloat(String label, float max) {
        return Codec.FLOAT.validate(value -> {
            if (!Float.isFinite(value)) {
                return DataResult.error(() -> label + "必须是有限数，实际为 " + value);
            }
            if (value <= 0f || value > max) {
                return DataResult.error(() -> label + "必须落在 (0, " + max + "] 内，实际为 " + value);
            }
            return DataResult.success(value);
        });
    }

    private static DataResult<Integer> validateDuration(int value) {
        if (value < MIN_DURATION_TICKS || value > MAX_DURATION_TICKS) {
            return DataResult.error(() -> "寿命必须落在 [" + MIN_DURATION_TICKS + ", "
                    + MAX_DURATION_TICKS + "] tick 内，实际为 " + value);
        }
        return DataResult.success(value);
    }
}
