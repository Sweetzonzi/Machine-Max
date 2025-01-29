package io.github.tt432.machinemax.common.component;

import com.mojang.serialization.Codec;
import io.github.tt432.machinemax.common.part.AbstractPart;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record PartComponent(AbstractPart part) {
    public static final StreamCodec<ByteBuf, PartComponent> STREAM_CODEC = StreamCodec.unit(
            new PartComponent(null)
    );
    public static final Codec<PartComponent> CODEC = Codec.unit(()->new PartComponent(null));
}
