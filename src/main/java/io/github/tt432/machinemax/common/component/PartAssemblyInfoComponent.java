package io.github.tt432.machinemax.common.component;

import com.jme3.math.Transform;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record PartAssemblyInfoComponent(
        String variant,
        String connector,
        String connectorType
//        Transform extraTransform//TODO: 实现自定义零件安装角
) {

    public static final StreamCodec<RegistryFriendlyByteBuf, PartAssemblyInfoComponent> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            PartAssemblyInfoComponent::variant,
            ByteBufCodecs.STRING_UTF8,
            PartAssemblyInfoComponent::connector,
            ByteBufCodecs.STRING_UTF8,
            PartAssemblyInfoComponent::connectorType,
            PartAssemblyInfoComponent::new
    );

    public static final Codec<PartAssemblyInfoComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("variant").forGetter(PartAssemblyInfoComponent::variant),
            Codec.STRING.fieldOf("seatLocator").forGetter(PartAssemblyInfoComponent::connector),
            Codec.STRING.fieldOf("connectorType").forGetter(PartAssemblyInfoComponent::connectorType)
    ).apply(instance, PartAssemblyInfoComponent::new));

}
