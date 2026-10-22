package io.github.sweetzonzi.machine_max.common.mech.vehicle.fitting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackAttr;

/**
 * 履带型号：在 {@link FittingType} 的公共外观之外持有 {@link TrackAttr}。
 *
 * <p>履带字段平铺在型号 JSON 的顶层，外观是同一层的 {@code "visual"} 对象；型号本身
 * 不承担运行时职责，只提供数据。型号 id 由内容包加载器写入
 * {@link FittingType#setRegistryKey}。</p>
 */
public class TrackType extends FittingType {
    private final TrackAttr attr;

    public TrackType(FittingVisual visual, TrackAttr attr) {
        super(visual);
        this.attr = attr;
    }

    @Override
    public String getSerializedName() {
        return "track";
    }

    public TrackAttr attr() {
        return attr;
    }

    public static final MapCodec<TrackType> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            TrackAttr.MAP_CODEC.forGetter(TrackType::attr),
            FittingVisual.CODEC.fieldOf("visual").forGetter(FittingType::visual)
    ).apply(instance, (attr, visual) -> new TrackType(visual, attr)));

    public static final Codec<TrackType> CODEC = MAP_CODEC.codec();
}
