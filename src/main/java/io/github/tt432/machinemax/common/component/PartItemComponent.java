package io.github.tt432.machinemax.common.component;

import com.mojang.serialization.Codec;
import io.github.tt432.machinemax.common.vehicle.Part;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record PartItemComponent(Part part) {
    public static final StreamCodec<ByteBuf, PartItemComponent> STREAM_CODEC = StreamCodec.unit(
            new PartItemComponent(null)
    );
    public static final Codec<PartItemComponent> CODEC = Codec.unit(()->new PartItemComponent(null));
}
