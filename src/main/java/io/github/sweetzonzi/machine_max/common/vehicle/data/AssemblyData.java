package io.github.sweetzonzi.machine_max.common.vehicle.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

@Getter
public class AssemblyData {
    public final ResourceLocation template; // 模板路径
    public final ResourceLocation tooltip; // 描述路径
    public final ResourceLocation icon; // 图标路径
    public final float scale; // 模型缩尺比 1:scale

    public static final AssemblyData DEFAULT = new AssemblyData();

    public static final ResourceLocation EMPTY = ResourceLocation.withDefaultNamespace("missingno");

    public static final Codec<AssemblyData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("template").forGetter(AssemblyData::getTemplate),
            ResourceLocation.CODEC.optionalFieldOf("tooltip", EMPTY).forGetter(AssemblyData::getTooltip),
            ResourceLocation.CODEC.optionalFieldOf("icon", EMPTY).forGetter(AssemblyData::getIcon),
            Codec.FLOAT.optionalFieldOf("scale", 35.0f).forGetter(AssemblyData::getScale)
    ).apply(instance, AssemblyData::new));

    public static final StreamCodec<FriendlyByteBuf, AssemblyData> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull AssemblyData decode(FriendlyByteBuf buffer) {
            ResourceLocation template = buffer.readResourceLocation();
            ResourceLocation tooltip = buffer.readResourceLocation();
            ResourceLocation icon = buffer.readResourceLocation();
            float scale = buffer.readFloat();
            return new AssemblyData(template, tooltip, icon, scale);
        }

        @Override
        public void encode(FriendlyByteBuf buffer, AssemblyData value) {
            buffer.writeResourceLocation(value.template);
            buffer.writeResourceLocation(value.tooltip);
            buffer.writeResourceLocation(value.icon);
            buffer.writeFloat(value.scale);
        }
    };

    public AssemblyData(ResourceLocation template, ResourceLocation tooltip, ResourceLocation icon, float scale) {
        this.template = template;
        this.tooltip = tooltip;
        this.icon = icon;
        this.scale = scale;
    }

    public AssemblyData(float scale) {
        this(EMPTY, EMPTY, EMPTY, scale);
    }

    public AssemblyData() {
        this(35f);
    }
}
