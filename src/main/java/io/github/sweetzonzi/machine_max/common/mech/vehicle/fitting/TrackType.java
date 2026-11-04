package io.github.sweetzonzi.machine_max.common.mech.vehicle.fitting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackAttr;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 履带型号：在 {@link FittingType} 的公共外观之外持有 {@link TrackAttr} 与逐节骨骼名。
 *
 * <p>履带字段平铺在型号 JSON 的顶层，外观是同一层的 {@code "visual"} 对象；型号本身
 * 不承担运行时职责，只提供数据。型号 id 由内容包加载器写入
 * {@link FittingType#setRegistryKey}。</p>
 *
 * <p>{@link #linkBones()} 是模型里逐节骨骼的名字，按节序排列；长度就是最小重复单元的节数
 * {@link #linkPeriod()}。空列表表示整个模型就是一节。</p>
 */
public class TrackType extends FittingType {
    private final TrackAttr attr;
    private final List<String> linkBones;

    public TrackType(FittingVisual visual, TrackAttr attr, List<String> linkBones) {
        super(visual);
        Set<String> seen = new HashSet<>(linkBones.size());
        for (String name : linkBones) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("link_bones 里不能有空名字");
            }
            if (!seen.add(name)) {
                throw new IllegalArgumentException("link_bones 里有重名骨骼: " + name);
            }
        }
        this.attr = attr;
        this.linkBones = List.copyOf(new ArrayList<>(linkBones));
    }

    @Override
    public String getSerializedName() {
        return "track";
    }

    public TrackAttr attr() {
        return attr;
    }

    /** 逐节骨骼名，按节序排列；空列表表示整个模型是一节。 */
    public List<String> linkBones() {
        return linkBones;
    }

    /**
     * 最小重复单元的节数 K：{@link #linkBones()} 的长度，空列表为 1。
     *
     * <p>闭环要求节数 N 是 K 的整数倍，见
     * {@code docs/plan/进行中/履带系统-物理实现设计文档.md} §8.6。</p>
     */
    public int linkPeriod() {
        return Math.max(1, linkBones.size());
    }

    public static final MapCodec<TrackType> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            TrackAttr.MAP_CODEC.forGetter(TrackType::attr),
            FittingVisual.CODEC.fieldOf("visual").forGetter(FittingType::visual),
            Codec.STRING.listOf().optionalFieldOf("link_bones", List.of()).forGetter(TrackType::linkBones)
    ).apply(instance, (attr, visual, linkBones) -> new TrackType(visual, attr, linkBones)));

    public static final Codec<TrackType> CODEC = MAP_CODEC.codec();
}
