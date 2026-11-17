package io.github.sweetzonzi.machine_max.common.mech.vehicle.track;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.fitting.FittingType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 履带的持久状态。
 *
 * <p>{@link TrackMemberRef} 使用「RegularPart UUID + SubPart 名」，加载存档时在部件与连接关系
 * 重建之后解析；{@code GearJoint}、{@code SubPart.trackLink} 反向引用与张力 rest 长度
 * 都是运行时对象，不进入本记录。履带没有名字：载具内的履带以列表项存在，成员引用
 * 本身就是足够的身份信息。</p>
 *
 * <p><b>耐久按材料比例存。</b>没有绝对点数，也没有单独的断带布尔位：权威状态是
 * {@link #sectionFill()}（每扇区剩余比例）与 {@link #torn()}（断口标记），
 * {@link TrackSectors} 把它们包成一个不可变快照。整条带的点数、已上料节数 {@code links}、
 * 断带状态 {@link #isBroken()} 全部现算，因此内容包调整 {@code durability_per_link}
 * 或换材质时，已放置的履带保持「还剩几成」。</p>
 *
 * <p><b>缺字段与空数组是两态。</b>{@code section_fill} 缺失（旧存档、模板、手写 JSON）按满载
 * 铺开 {@code S = members.size()} 个 {@code 1.0}；写出 {@code []} 表示未建造——只有设计、
 * 没有材料。两种状态不能压成同一个值：只写了 {@code link_count} 的模板必须解出满载，
 * 否则装配体物品放出来的整备车开不动。蓝图路径另有 {@link #asPlan()} 显式清空。</p>
 *
 * <p>{@code clockwise} 表示主动轮局部 (Y,Z) 平面内的顺时针铺设绕向。
 * 该值在创建时确定，随存档、蓝图和网络传递，轮子自转不改变它。</p>
 *
 * <p>{@link #trackType()} 是型号 id（{@code fittings/} 下的文件名），不是 JSON 里
 * {@code "type"} 的种类。旧存档没有该字段时回落 {@link #DEFAULT_TYPE}。</p>
 *
 * <p>{@link #linkCount()} 是整条履带的节数 N。它在创建 / 成员变化时由包络周长推导一次，
 * 之后作为权威值随存档、蓝图和网络传递，加载端直接使用：按本端某一帧的轮位姿重算会在
 * 位姿尚未就绪时（载具刚放出，成员全落在同一点）把 N 锁在退化包络上。
 * 内容包可以在模板 / 蓝图的 {@code track_links} 里写死该值（按实车资料填写），
 * 于是同一型号套在不同轮系上各自记下自己的节数。
 * 旧存档 / 旧蓝图没有该字段时为 {@code 0}，加载端按当前包络推导一次（迁移路径）。</p>
 */
public record TrackLinkData(
        TrackMemberRef sprocket,
        List<TrackMemberRef> members,
        List<Float> sectionFill,
        List<Boolean> torn,
        float sectorPhase,
        boolean clockwise,
        ResourceLocation trackType,
        int linkCount
) {
    /** 缺省型号 id：内容包 fixture {@code fittings/default.json}。 */
    public static final ResourceLocation DEFAULT_TYPE = FittingType.DEFAULT_ID;

    /** 节数未持久化（旧存档 / 旧蓝图）；加载端按当前包络推导。 */
    public static final int UNKNOWN_LINK_COUNT = 0;

    /**
     * 节数上限：只用于挡存档 / 网络里的垃圾值（渲染与耐久都按 N 线性放大）。
     * 实际履带在几十到几百节量级。
     */
    public static final int MAX_LINK_COUNT = 2048;

    /**
     * 扇区数上限：只用于挡存档 / 网络里的垃圾值。扇区数等于成员数，
     * 而一条履带的成员是轮子，实际在几十个以内。
     */
    public static final int MAX_SECTORS = 256;

    public TrackLinkData {
        members = List.copyOf(members);
        sectionFill = List.copyOf(sectionFill);
        torn = List.copyOf(torn);
    }

    /** 扇区快照视图：权威状态的两个等长列表。 */
    public TrackSectors sectors() {
        return sectionFill.isEmpty() ? TrackSectors.UNBUILT : new TrackSectors(sectionFill, torn);
    }

    /** 是否已建造：{@code section_fill} 非空。*/
    public boolean isBuilt() {
        return !sectionFill.isEmpty();
    }

    /** 是否还有没焊满的断口。 */
    public boolean isBroken() {
        return !torn.isEmpty() && sectors().isBroken();
    }

    /** 已上料节数 {@code links}，与断口个数一起派生（见 {@link TrackSectors#links(int)}）。 */
    public int links() {
        return sectors().links(linkCount);
    }

    /**
     * 蓝图归一化：蓝图不承载履带的内容物数据与组装进度，材料状态清空、相位归零，
     * 型号、成员与节数保留。放出来是 0/N 的方案，装配完成后再由焊枪扣 N 节建成。
     *
     * <p>收纳（{@code VehicleCaptureItem}）不走本方法：它用 {@code VehicleData.rebased()}
     * 抄录真实状态，受损与断带随成品物品一起走。</p>
     *
     * <p>成员引用保留；放置时会按当前载具拓扑重新解析，失效成员由
     * {@link TrackLink#refreshMembers()} 丢弃。节数与几何无关，原样带过去，
     * 使蓝图放出的履带与抄录时同样是 N 节。</p>
     */
    public TrackLinkData asPlan() {
        return new TrackLinkData(sprocket, members, List.of(), List.of(), 0f, clockwise, trackType, linkCount);
    }

    private static final Codec<TrackLinkData> BASE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            TrackMemberRef.CODEC.fieldOf("sprocket").forGetter(TrackLinkData::sprocket),
            TrackMemberRef.CODEC.listOf().fieldOf("members").forGetter(TrackLinkData::members),
            // 缺字段与空数组必须区分：Optional 为空 = 旧数据按满载铺开，Optional.of([]) = 未建造
            Codec.FLOAT.listOf().optionalFieldOf("section_fill")
                    .forGetter(data -> Optional.of(data.sectionFill())),
            Codec.BOOL.listOf().optionalFieldOf("torn")
                    .forGetter(data -> Optional.of(data.torn())),
            Codec.FLOAT.optionalFieldOf("sector_phase", 0f).forGetter(TrackLinkData::sectorPhase),
            Codec.BOOL.fieldOf("clockwise").forGetter(TrackLinkData::clockwise),
            ResourceLocation.CODEC.optionalFieldOf("track_type", DEFAULT_TYPE)
                    .forGetter(TrackLinkData::trackType),
            Codec.intRange(UNKNOWN_LINK_COUNT, MAX_LINK_COUNT)
                    .optionalFieldOf("link_count", UNKNOWN_LINK_COUNT)
                    .forGetter(TrackLinkData::linkCount)
    ).apply(instance, TrackLinkData::decode));

    public static final Codec<TrackLinkData> CODEC = BASE_CODEC.flatXmap(
            data -> data.sectionFill.size() > MAX_SECTORS
                    ? DataResult.error(() -> "履带扇区过多: " + data.sectionFill.size())
                    : DataResult.success(data),
            DataResult::success);

    public static final StreamCodec<FriendlyByteBuf, TrackLinkData> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull TrackLinkData decode(FriendlyByteBuf buffer) {
            TrackMemberRef sprocket = TrackMemberRef.STREAM_CODEC.decode(buffer);
            List<TrackMemberRef> members = buffer.readList(TrackMemberRef.STREAM_CODEC);
            // 线格式不做解码期拒绝：畸形扇区数只会让本端少画几节，不该断开连接
            int sectors = Math.clamp(buffer.readVarInt(), 0, MAX_SECTORS);
            List<Float> fill = new ArrayList<>(sectors);
            for (int i = 0; i < sectors; i++) fill.add(TrackSectors.clampRatio(buffer.readFloat()));
            List<Boolean> torn = new ArrayList<>(sectors);
            for (int i = 0; i < sectors; i++) torn.add(buffer.readBoolean());
            float sectorPhase = buffer.readFloat();
            boolean clockwise = buffer.readBoolean();
            ResourceLocation trackType = ResourceLocation.STREAM_CODEC.decode(buffer);
            int linkCount = Math.clamp(buffer.readVarInt(), UNKNOWN_LINK_COUNT, MAX_LINK_COUNT);
            return normalize(sprocket, members, fill, torn, sectorPhase, clockwise, trackType, linkCount);
        }

        @Override
        public void encode(FriendlyByteBuf buffer, TrackLinkData value) {
            TrackMemberRef.STREAM_CODEC.encode(buffer, value.sprocket());
            buffer.writeCollection(value.members(), TrackMemberRef.STREAM_CODEC);
            buffer.writeVarInt(value.sectionFill().size());
            for (Float ratio : value.sectionFill()) buffer.writeFloat(ratio);
            for (Boolean flag : value.torn()) buffer.writeBoolean(flag);
            buffer.writeFloat(value.sectorPhase());
            buffer.writeBoolean(value.clockwise());
            ResourceLocation.STREAM_CODEC.encode(buffer, value.trackType());
            buffer.writeVarInt(value.linkCount());
        }
    };

    /** 编解码共用的收口：缺字段铺满、断口长度对齐、比例与相位夹取。 */
    private static TrackLinkData decode(TrackMemberRef sprocket, List<TrackMemberRef> members,
                                        Optional<List<Float>> sectionFill, Optional<List<Boolean>> torn,
                                        float sectorPhase, boolean clockwise,
                                        ResourceLocation trackType, int linkCount) {
        // 缺 section_fill = 旧数据 / 模板：按满载铺开，S = 成员数
        List<Float> fill = sectionFill.orElseGet(() -> fullFill(members.size()));
        return normalize(sprocket, members, fill, torn.orElse(List.of()), sectorPhase, clockwise, trackType, linkCount);
    }

    private static TrackLinkData normalize(TrackMemberRef sprocket, List<TrackMemberRef> members,
                                           List<Float> rawFill, List<Boolean> rawTorn,
                                           float sectorPhase, boolean clockwise,
                                           ResourceLocation trackType, int linkCount) {
        List<Float> fill = new ArrayList<>(rawFill.size());
        for (Float ratio : rawFill) fill.add(TrackSectors.clampRatio(ratio));
        List<Boolean> torn = alignTorn(rawTorn, fill.size());
        return new TrackLinkData(sprocket, members, fill, torn,
                wrapPhase(sectorPhase, fill.size()), clockwise, trackType, linkCount);
    }

    private static List<Float> fullFill(int sectors) {
        int count = Math.max(0, sectors);
        List<Float> fill = new ArrayList<>(count);
        for (int i = 0; i < count; i++) fill.add(1f);
        return fill;
    }

    /**
     * 断口标记对齐到 {@code sectors} 段：长度相同原样返回，不同则按材料区间映射（近邻取并）。
     *
     * <p>长度不匹配只发生在手写数据与版本错位上，按 {@code section_fill} 的长度重采样断口，
     * 不因长度不符拒绝载入。</p>
     */
    private static List<Boolean> alignTorn(List<Boolean> torn, int sectors) {
        if (torn.size() == sectors) return List.copyOf(torn);
        List<Boolean> aligned = new ArrayList<>(sectors);
        for (int j = 0; j < sectors; j++) {
            boolean flagged = false;
            if (!torn.isEmpty()) {
                long start = (long) j * torn.size() / sectors;
                long end = Math.max(start + 1, (long) (j + 1) * torn.size() / sectors);
                for (long i = start; i < end && i < torn.size(); i++) {
                    flagged |= torn.get((int) i);
                }
            }
            aligned.add(flagged);
        }
        return aligned;
    }

    /** 相位归一化到 {@code [0, S)}；未建造或非法值一律归零。 */
    public static float wrapPhase(float phase, int sectors) {
        if (sectors <= 0 || !Float.isFinite(phase)) return 0f;
        float wrapped = phase % sectors;
        return wrapped < 0f ? wrapped + sectors : wrapped;
    }
}
