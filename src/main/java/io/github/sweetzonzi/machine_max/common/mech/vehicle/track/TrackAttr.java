package io.github.sweetzonzi.machine_max.common.mech.vehicle.track;

import cn.solarmoon.spark_core.pack.diagnostic.SparkPackIssues;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.HitBoxAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.MaterialAttr;
import io.github.sweetzonzi.machine_max.datagen.MMLanguageRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * 履带属性：型号 JSON 顶层与几何 / 装甲 / 接触 / 音效有关的字段。
 *
 * <p>字段划分与单位见 {@code docs/plan/进行中/履带系统-数据模型设计.md} §3。材料在构造时
 * 由 {@link HitBoxAttr#resolve} 解析成 {@link MaterialAttr} 并缓存：材料是型号的硬依赖，
 * 缺失时构造直接失败，由内容包加载器记为内容包错误。</p>
 *
 * <p>耐久按节声明：{@link #durabilityPerLink()} 是每节耐久，整条带的节数 {@code N} 由包络
 * 总长与 {@link #lengthPerLink()} 推导，最大耐久为 {@code durabilityPerLink × max(1, N)}。</p>
 */
public final class TrackAttr {
    private final float durabilityPerLink;
    private final float lengthPerLink;
    private final float thicknessMm;
    private final ResourceLocation materialId;
    private final Optional<HitBoxAttr.OverwriteAttr> overwrite;
    private final Tension tension;
    /** 构造时由 {@link #materialId} + {@link #overwrite} 解析出的有效材料。 */
    private final MaterialAttr resolved;

    public TrackAttr(float durabilityPerLink,
                     float lengthPerLink,
                     float thicknessMm,
                     ResourceLocation materialId,
                     Optional<HitBoxAttr.OverwriteAttr> overwrite,
                     Tension tension) {
        if (!Float.isFinite(durabilityPerLink) || !(durabilityPerLink > 0f)) {
            throw new IllegalArgumentException("durability_per_link 必须是有限正数: " + durabilityPerLink);
        }
        if (!Float.isFinite(lengthPerLink) || !(lengthPerLink > 0f)) {
            throw new IllegalArgumentException("length_per_link 必须是有限正数: " + lengthPerLink);
        }
        if (!Float.isFinite(thicknessMm) || !(thicknessMm > 0f)) {
            throw new IllegalArgumentException("thickness_mm 必须是有限正数: " + thicknessMm);
        }
        MaterialAttr material = HitBoxAttr.resolve(materialId, overwrite.orElse(null));
        if (material == null) {
            throw new IllegalArgumentException("履带材料不存在: " + materialId);
        }
        // 履带轮绕自身局部 X 轴转，而各向异性缩放是按局部轴逐分量做的：切平面里横向恒为 X，
        // 纵向在 Y–Z 平面内随转角变化。只有 y 与 z 相等时纵向缩放才恒定且不偏斜，
        // 否则摩擦大小随轮子相位在 y 与 z 之间摆动，方向还会被转偏（45° 时最明显）。
        var friction = material.physics().friction();
        if (Math.abs(friction.y - friction.z) > 1e-6) {
            SparkPackIssues.warn(MMLanguageRegistry.WARN_PART_TRACK_MATERIAL_ANISOTROPY
                    .component(materialId, friction.x, friction.y, friction.z));
        }
        this.durabilityPerLink = durabilityPerLink;
        this.lengthPerLink = lengthPerLink;
        this.thicknessMm = thicknessMm;
        this.materialId = materialId;
        this.overwrite = overwrite;
        this.tension = tension;
        this.resolved = material;
    }

    public float durabilityPerLink() {
        return durabilityPerLink;
    }

    public float lengthPerLink() {
        return lengthPerLink;
    }

    public float thicknessMm() {
        return thicknessMm;
    }

    /** 几何厚度（米），供包络扣半径与客户端节几何共用。 */
    public float thicknessMeters() {
        return thicknessMm * 0.001f;
    }

    public ResourceLocation materialId() {
        return materialId;
    }

    /** 材料覆写；空表示完全沿用 {@link #materialId()} 的材料。 */
    public Optional<HitBoxAttr.OverwriteAttr> overwrite() {
        return overwrite;
    }

    public MaterialAttr material() {
        return resolved;
    }

    public MaterialAttr.ArmorAttr armor() {
        return resolved.armor();
    }

    public MaterialAttr.PhysicsAttr physics() {
        return resolved.physics();
    }

    /**
     * 命中音效与预留 FMOD bank 取解析后的材料，型号可用 {@code overwrite.sounds} 按字段定制。
     *
     * <p>履带没有自己的命中音字段。要改音效就改材料，或在型号里用
     * {@code overwrite.sounds} 覆写——覆写会随材料一起解析进 {@code resolved}。</p>
     */
    public MaterialAttr.MaterialSoundAttr sounds() {
        return resolved.sounds();
    }

    public Tension tension() {
        return tension;
    }

    /** 整条履带的最大耐久：每节耐久 × 节数，节数至少按 1 计。 */
    public float maxDurability(int linkCount) {
        return durabilityPerLink * Math.max(1, linkCount);
    }

    public static final MapCodec<TrackAttr> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("durability_per_link", 20f).forGetter(TrackAttr::durabilityPerLink),
            Codec.FLOAT.optionalFieldOf("length_per_link", 0.2f).forGetter(TrackAttr::lengthPerLink),
            Codec.FLOAT.optionalFieldOf("thickness_mm", 50f).forGetter(TrackAttr::thicknessMm),
            ResourceLocation.CODEC.fieldOf("material").forGetter(TrackAttr::materialId),
            HitBoxAttr.OverwriteAttr.CODEC.optionalFieldOf("overwrite").forGetter(TrackAttr::overwrite),
            Tension.CODEC.optionalFieldOf("tension", Tension.DEFAULT).forGetter(TrackAttr::tension)
    ).apply(instance, TrackAttr::new));

    /**
     * 相邻成员轮之间的单边弹簧-阻尼参数。
     *
     * <p>{@code rest} 不在这里声明：装配 / 载入时按相邻轮心距采样。</p>
     */
    public record Tension(boolean enabled, float stiffness, float damping,
                          float preload, float slack, float maxForce) {
        public static final Tension DEFAULT = new Tension(true, 12000f, 900f, 0f, 0.015f, 800f);

        public Tension {
            requireNonNegative("stiffness", stiffness);
            requireNonNegative("damping", damping);
            requireNonNegative("preload", preload);
            requireNonNegative("slack", slack);
            requireNonNegative("max_force", maxForce);
        }

        public static final MapCodec<Tension> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("enabled", true).forGetter(Tension::enabled),
                Codec.FLOAT.optionalFieldOf("stiffness", 12000f).forGetter(Tension::stiffness),
                Codec.FLOAT.optionalFieldOf("damping", 900f).forGetter(Tension::damping),
                Codec.FLOAT.optionalFieldOf("preload", 0f).forGetter(Tension::preload),
                Codec.FLOAT.optionalFieldOf("slack", 0.015f).forGetter(Tension::slack),
                Codec.FLOAT.optionalFieldOf("max_force", 800f).forGetter(Tension::maxForce)
        ).apply(instance, Tension::new));

        public static final Codec<Tension> CODEC = MAP_CODEC.codec();

        private static void requireNonNegative(String name, float value) {
            if (!Float.isFinite(value) || value < 0f) {
                throw new IllegalArgumentException(name + " 必须是有限非负数: " + value);
            }
        }
    }

}
