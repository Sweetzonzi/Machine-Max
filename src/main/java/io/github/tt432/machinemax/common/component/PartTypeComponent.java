package io.github.tt432.machinemax.common.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.tt432.machinemax.common.vehicle.PartType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record PartTypeComponent(PartType partType) {
//    public static final Codec<PartTypeComponent> CODEC = RecordCodecBuilder.create(instance ->
//            instance.group(
//                            Codec.STRING.fieldOf("part_type").forGetter(PartTypeComponent::getRegistryKey))
//                    .apply(instance, PartTypeComponent::getPartType)
//    );
//    public static final StreamCodec<ByteBuf, PartTypeComponent> STREAM_CODEC = StreamCodec.composite(
//            ByteBufCodecs.INT, PartTypeComponent::getPartTypeId,
//            PartTypeComponent::getPartTypeById
//    );

    /**
     * 获取部件的注册表名
     *
     * @return 部件的注册表名
     */
    private String getRegistryKey() {
        return partType.getRegistryKey().toString();
    }

//    private int getPartTypeId() {
//        return PartType.PART_REGISTRY.getId(partType);
//    }

    /**
     * 根据注册表名称获取PartTypeComponent
     *
     * @param registryKey 部件的注册表名
     * @return 用于物品的Component实例
     */
//    private static PartTypeComponent getPartType(String registryKey) {
//        return new PartTypeComponent(PartType.PART_REGISTRY.get(ResourceLocation.parse(registryKey)));
//    }
//
//    private static PartTypeComponent getPartTypeById(int partTypeId) {
//        return new PartTypeComponent(PartType.PART_REGISTRY.byId(partTypeId));
//    }
}
