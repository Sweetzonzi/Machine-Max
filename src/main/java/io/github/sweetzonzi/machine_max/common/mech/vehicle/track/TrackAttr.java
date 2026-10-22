package io.github.sweetzonzi.machine_max.common.mech.vehicle.track;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.HitBoxAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.MaterialAttr;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.jetbrains.annotations.Nullable;

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
    private final MaterialAttr.MaterialSoundAttr sounds;
    private final Tension tension;
    /** 构造时由 {@link #materialId} + {@link #overwrite} 解析出的有效材料。 */
    private final MaterialAttr resolved;

    public TrackAttr(float durabilityPerLink,
                     float lengthPerLink,
                     float thicknessMm,
                     ResourceLocation materialId,
                     Optional<HitBoxAttr.OverwriteAttr> overwrite,
                     MaterialAttr.MaterialSoundAttr sounds,
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
        this.durabilityPerLink = durabilityPerLink;
        this.lengthPerLink = lengthPerLink;
        this.thicknessMm = thicknessMm;
        this.materialId = materialId;
        this.overwrite = overwrite;
        this.sounds = sounds;
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

    public MaterialAttr.MaterialSoundAttr sounds() {
        return sounds;
    }

    public Tension tension() {
        return tension;
    }

    /** 整条履带的最大耐久：每节耐久 × 节数，节数至少按 1 计。 */
    public float maxDurability(int linkCount) {
        return durabilityPerLink * Math.max(1, linkCount);
    }

    /**
     * 省略 {@code sounds} 时使用的默认命中音：两个固定范围（64 格）的硬金属碰撞事件。
     *
     * <p>与 {@link MaterialAttr.MaterialSoundAttr#DEFAULT}（碰撞箱的 {@code hit_box.hit.*}）
     * 不同：履带命中音默认落在 {@code part.collision.terrain.hard_metal}。</p>
     */
    public static final MaterialAttr.MaterialSoundAttr TRACK_DEFAULT_SOUNDS = new MaterialAttr.MaterialSoundAttr(
            createFixedRangeSound("part.collision.terrain.hard_metal"),
            createFixedRangeSound("part.collision.terrain.hard_metal")
    );

    private static SoundEvent createFixedRangeSound(String path) {
        return SoundEvent.createFixedRangeEvent(
                ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, path), 64f);
    }

    public static final MapCodec<TrackAttr> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("durability_per_link", 20f).forGetter(TrackAttr::durabilityPerLink),
            Codec.FLOAT.optionalFieldOf("length_per_link", 0.2f).forGetter(TrackAttr::lengthPerLink),
            Codec.FLOAT.optionalFieldOf("thickness_mm", 50f).forGetter(TrackAttr::thicknessMm),
            ResourceLocation.CODEC.fieldOf("material").forGetter(TrackAttr::materialId),
            HitBoxAttr.OverwriteAttr.CODEC.optionalFieldOf("overwrite").forGetter(TrackAttr::overwrite),
            MaterialAttr.MaterialSoundAttr.CODEC.optionalFieldOf("sounds", TRACK_DEFAULT_SOUNDS)
                    .forGetter(TrackAttr::sounds),
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

    /** 便捷构造：不带覆写、不带音效覆盖，用于测试与夹具。 */
    public static TrackAttr of(ResourceLocation material, float thicknessMm) {
        return new TrackAttr(20f, 0.2f, thicknessMm, material, Optional.empty(),
                TRACK_DEFAULT_SOUNDS, Tension.DEFAULT);
    }

    /** 便捷构造：显式给出全部字段，用于测试与夹具。 */
    public static TrackAttr of(float durabilityPerLink, float lengthPerLink, float thicknessMm,
                               ResourceLocation material, @Nullable MaterialAttr.MaterialSoundAttr sounds) {
        return new TrackAttr(durabilityPerLink, lengthPerLink, thicknessMm, material, Optional.empty(),
                sounds == null ? TRACK_DEFAULT_SOUNDS : sounds, Tension.DEFAULT);
    }
}
