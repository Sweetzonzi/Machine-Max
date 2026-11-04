package io.github.sweetzonzi.machine_max.common.mech.vehicle.track;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.fitting.FittingType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 履带的持久状态。
 *
 * <p>{@link TrackMemberRef} 使用「RegularPart UUID + SubPart 名」，加载存档时在部件与连接关系
 * 重建之后解析；{@code GearJoint}、{@code SubPart.trackLink} 反向引用与张力 rest 长度
 * 都是运行时对象，不进入本记录。履带没有名字：载具内的履带以列表项存在，成员引用
 * 本身就是足够的身份信息。</p>
 *
 * <p>{@link #durability()} 是当前绝对耐久。{@link #FULL_DURABILITY}（-1）表示
 * 「满耐久」哨兵，只用于蓝图 / 收纳：加载时按包络推导出的最大耐久落值。</p>
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
        float durability,
        boolean broken,
        boolean clockwise,
        ResourceLocation trackType,
        int linkCount
) {
    /** 满耐久哨兵：加载时解析为推导出的最大耐久。 */
    public static final float FULL_DURABILITY = -1f;

    /** 缺省型号 id：内容包 fixture {@code fittings/default.json}。 */
    public static final ResourceLocation DEFAULT_TYPE = FittingType.DEFAULT_ID;

    /** 节数未持久化（旧存档 / 旧蓝图）；加载端按当前包络推导。 */
    public static final int UNKNOWN_LINK_COUNT = 0;

    /**
     * 节数上限：只用于挡存档 / 网络里的垃圾值（渲染与耐久都按 N 线性放大）。
     * 实际履带在几十到几百节量级。
     */
    public static final int MAX_LINK_COUNT = 2048;

    public static final Codec<TrackLinkData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            TrackMemberRef.CODEC.fieldOf("sprocket").forGetter(TrackLinkData::sprocket),
            TrackMemberRef.CODEC.listOf().fieldOf("members").forGetter(TrackLinkData::members),
            Codec.FLOAT.optionalFieldOf("durability", FULL_DURABILITY)
                    .forGetter(TrackLinkData::durability),
            Codec.BOOL.optionalFieldOf("broken", false).forGetter(TrackLinkData::broken),
            Codec.BOOL.fieldOf("clockwise").forGetter(TrackLinkData::clockwise),
            ResourceLocation.CODEC.optionalFieldOf("track_type", DEFAULT_TYPE)
                    .forGetter(TrackLinkData::trackType),
            Codec.intRange(UNKNOWN_LINK_COUNT, MAX_LINK_COUNT)
                    .optionalFieldOf("link_count", UNKNOWN_LINK_COUNT)
                    .forGetter(TrackLinkData::linkCount)
    ).apply(instance, TrackLinkData::new));

    public static final StreamCodec<FriendlyByteBuf, TrackLinkData> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull TrackLinkData decode(FriendlyByteBuf buffer) {
            TrackMemberRef sprocket = TrackMemberRef.STREAM_CODEC.decode(buffer);
            List<TrackMemberRef> members = buffer.readList(TrackMemberRef.STREAM_CODEC);
            float durability = buffer.readFloat();
            boolean broken = buffer.readBoolean();
            boolean clockwise = buffer.readBoolean();
            ResourceLocation trackType = ResourceLocation.STREAM_CODEC.decode(buffer);
            // 线格式不做解码期拒绝：畸形节数只会让本端少画几节，不该断开连接
            int linkCount = Math.clamp(buffer.readVarInt(), UNKNOWN_LINK_COUNT, MAX_LINK_COUNT);
            return new TrackLinkData(sprocket, members, durability, broken, clockwise, trackType, linkCount);
        }

        @Override
        public void encode(FriendlyByteBuf buffer, TrackLinkData value) {
            TrackMemberRef.STREAM_CODEC.encode(buffer, value.sprocket());
            buffer.writeCollection(value.members(), TrackMemberRef.STREAM_CODEC);
            buffer.writeFloat(value.durability());
            buffer.writeBoolean(value.broken());
            buffer.writeBoolean(value.clockwise());
            ResourceLocation.STREAM_CODEC.encode(buffer, value.trackType());
            buffer.writeVarInt(value.linkCount());
        }
    };

    /**
     * 蓝图 / 收纳归一化：履带耐久置为满耐久哨兵、断带状态清除，型号与节数保留。
     *
     * <p>成员引用保留；放置时会按当前载具拓扑重新解析，失效成员由
     * {@link TrackLink#refreshMembers()} 丢弃。节数与几何无关，原样带过去，
     * 使蓝图 / 收纳放出的履带与抄录时同样是 N 节。</p>
     */
    public TrackLinkData normalized() {
        return new TrackLinkData(sprocket, members, FULL_DURABILITY, false, clockwise, trackType, linkCount);
    }
}
