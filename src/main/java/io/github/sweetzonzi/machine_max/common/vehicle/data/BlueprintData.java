package io.github.sweetzonzi.machine_max.common.vehicle.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
@Getter
public class BlueprintData {
    public final ResourceLocation template;
    public final ResourceLocation tooltip;
    public final ResourceLocation icon;

    public static final BlueprintData EMPTY_BLUEPRINT = new BlueprintData();

    public static final ResourceLocation EMPTY = ResourceLocation.withDefaultNamespace("missingno");

    public static final Codec<BlueprintData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("template").forGetter(BlueprintData::getTemplate),
            ResourceLocation.CODEC.optionalFieldOf("tooltip", EMPTY).forGetter(BlueprintData::getTooltip),
            ResourceLocation.CODEC.optionalFieldOf("icon", EMPTY).forGetter(BlueprintData::getIcon)
    ).apply(instance, BlueprintData::new));

    public static final StreamCodec<FriendlyByteBuf, BlueprintData> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull BlueprintData decode(FriendlyByteBuf buffer) {
            ResourceLocation template = buffer.readResourceLocation();
            ResourceLocation tooltip = buffer.readResourceLocation();
            ResourceLocation icon = buffer.readResourceLocation();
            return new BlueprintData(template, tooltip, icon);
        }

        @Override
        public void encode(FriendlyByteBuf buffer, BlueprintData value) {
            buffer.writeResourceLocation(value.template);
            buffer.writeResourceLocation(value.tooltip);
            buffer.writeResourceLocation(value.icon);
        }
    };

    public BlueprintData(ResourceLocation template, ResourceLocation tooltip, ResourceLocation icon) {
        this.template = template;
        this.tooltip = tooltip;
        this.icon = icon;
    }

    public BlueprintData() {
        this(EMPTY, EMPTY, EMPTY);
    }
}
