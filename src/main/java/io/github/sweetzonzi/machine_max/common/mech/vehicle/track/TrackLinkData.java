package io.github.sweetzonzi.machine_max.common.mech.vehicle.track;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 履带的持久状态。
 *
 * <p>{@link TrackMemberRef} 使用「Part UUID + SubPart 名」，加载存档时在部件与连接关系
 * 重建之后解析；{@code GearJoint}、{@code SubPart.trackLink} 反向引用与张力 rest 长度
 * 都是运行时对象，不进入本记录。履带没有名字：载具内的履带以列表项存在，成员引用
 * 本身就是足够的身份信息。</p>
 */
public record TrackLinkData(
        TrackMemberRef sprocket,
        List<TrackMemberRef> members,
        float durability,
        boolean broken
) {
    public static final Codec<TrackLinkData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            TrackMemberRef.CODEC.fieldOf("sprocket").forGetter(TrackLinkData::sprocket),
            TrackMemberRef.CODEC.listOf().fieldOf("members").forGetter(TrackLinkData::members),
            Codec.FLOAT.optionalFieldOf("durability", TrackAttr.DEFAULT.durability())
                    .forGetter(TrackLinkData::durability),
            Codec.BOOL.optionalFieldOf("broken", false).forGetter(TrackLinkData::broken)
    ).apply(instance, TrackLinkData::new));

    public static final StreamCodec<FriendlyByteBuf, TrackLinkData> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull TrackLinkData decode(FriendlyByteBuf buffer) {
            TrackMemberRef sprocket = TrackMemberRef.STREAM_CODEC.decode(buffer);
            List<TrackMemberRef> members = buffer.readList(TrackMemberRef.STREAM_CODEC);
            float durability = buffer.readFloat();
            boolean broken = buffer.readBoolean();
            return new TrackLinkData(sprocket, members, durability, broken);
        }

        @Override
        public void encode(FriendlyByteBuf buffer, TrackLinkData value) {
            TrackMemberRef.STREAM_CODEC.encode(buffer, value.sprocket());
            buffer.writeCollection(value.members(), TrackMemberRef.STREAM_CODEC);
            buffer.writeFloat(value.durability());
            buffer.writeBoolean(value.broken());
        }
    };

    /**
     * 蓝图 / 收纳归一化：履带耐久回满、断带状态清除。
     *
     * <p>成员引用保留；放置时会按当前载具拓扑重新解析，失效成员由
     * {@link TrackLink#refreshMembers()} 丢弃。</p>
     */
    public TrackLinkData normalized(float fullDurability) {
        return new TrackLinkData(sprocket, members, fullDurability, false);
    }
}
