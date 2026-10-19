package io.github.sweetzonzi.machine_max.common.mech.vehicle.track;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * 履带成员的持久标识。
 *
 * <p>{@code SubPart#getId()} 是运行时自增计数器，服务端加载存档时不会复用存档里的 id
 * （{@code Part} 只在客户端 {@code setId}），因此不能用作履带成员的持久引用。成员名在
 * 单个 Part 内唯一、跨 Part 可重复，所以这里使用「Part UUID + SubPart 名」的组合。</p>
 */
public record TrackMemberRef(UUID partUuid, String subPartName) {
    public static final Codec<TrackMemberRef> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("part").forGetter(ref -> ref.partUuid().toString()),
            Codec.STRING.fieldOf("sub_part").forGetter(TrackMemberRef::subPartName)
    ).apply(instance, (part, name) -> new TrackMemberRef(UUID.fromString(part), name)));

    public static final StreamCodec<FriendlyByteBuf, TrackMemberRef> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull TrackMemberRef decode(FriendlyByteBuf buffer) {
            return new TrackMemberRef(buffer.readUUID(), buffer.readUtf());
        }

        @Override
        public void encode(FriendlyByteBuf buffer, TrackMemberRef value) {
            buffer.writeUUID(value.partUuid());
            buffer.writeUtf(value.subPartName());
        }
    };

    public static TrackMemberRef of(SubPart subPart) {
        return new TrackMemberRef(subPart.part.uuid, subPart.name);
    }
}
