package io.github.tt432.machinemax.common.component;

import com.jme3.math.Transform;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public record PartAssemblyInfoComponent(
        String variant,
        String connector,
        String connectorType,
        Vector3f offset,
        Quaternionf rotation
//        Transform extraTransform//TODO: 实现自定义零件安装角
) {

    public static final StreamCodec<RegistryFriendlyByteBuf, PartAssemblyInfoComponent> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            PartAssemblyInfoComponent::variant,
            ByteBufCodecs.STRING_UTF8,
            PartAssemblyInfoComponent::connector,
            ByteBufCodecs.STRING_UTF8,
            PartAssemblyInfoComponent::connectorType,
            ByteBufCodecs.VECTOR3F,
            PartAssemblyInfoComponent::offset,
            ByteBufCodecs.QUATERNIONF,
            PartAssemblyInfoComponent::rotation,
            PartAssemblyInfoComponent::new
    );

    public static final Codec<PartAssemblyInfoComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("variant").forGetter(PartAssemblyInfoComponent::variant),
            Codec.STRING.fieldOf("seatLocator").forGetter(PartAssemblyInfoComponent::connector),
            Codec.STRING.fieldOf("connectorType").forGetter(PartAssemblyInfoComponent::connectorType),
            ExtraCodecs.VECTOR3F.fieldOf("offset").forGetter(PartAssemblyInfoComponent::offset),
            ExtraCodecs.QUATERNIONF.fieldOf("rotation").forGetter(PartAssemblyInfoComponent::rotation)
    ).apply(instance, PartAssemblyInfoComponent::new));

}
