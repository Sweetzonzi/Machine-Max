package io.github.sweetzonzi.machine_max.common.mech.vehicle.track;

import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.DamageModifier;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.MaterialAttr;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * 履带属性。
 *
 * <p>当前由测试物品直接使用 {@link #DEFAULT}，尚未接入内容包 JSON；字段划分按
 * {@code docs/plan/进行中/履带系统-物理实现设计文档.md} §6.1 保留，后续加 Codec 即可。</p>
 *
 * <p>耐久按节声明：{@link #linkDurability()} 是每节耐久，整条带的节数
 * {@code N = max(1, round(L / linkLength))} 由包络总长推导，最大耐久为
 * {@code linkDurability × N}。因此轮组布局变化会改变整条带的血量上限。</p>
 *
 * @param linkThickness 几何厚度（米），供两端内表面包络扣半径与客户端节几何共用；
 *                      半径关系见 {@code docs/plan/进行中/履带系统-物理实现设计文档.md} §2.4
 */
public record TrackAttr(
        float linkDurability,
        float linkLength,
        float linkThickness,
        int minEffectiveWheels,
        MaterialAttr.ArmorAttr armor,
        Friction friction,
        Tension tension,
        Sounds sounds
) {
    public TrackAttr {
        if (!(linkDurability > 0f)) {
            throw new IllegalArgumentException("linkDurability must be > 0");
        }
        if (!(linkLength > 0f)) {
            throw new IllegalArgumentException("linkLength must be > 0");
        }
        if (!Float.isFinite(linkThickness) || !(linkThickness > 0f)) {
            throw new IllegalArgumentException("linkThickness must be finite and > 0");
        }
        if (minEffectiveWheels < 2) {
            throw new IllegalArgumentException("minEffectiveWheels must be >= 2");
        }
    }

    public static final TrackAttr DEFAULT = new TrackAttr(
            20f,
            0.2f,
            0.05f,
            2,
            new MaterialAttr.ArmorAttr(
                    30f, true,
                    1f, 0.95f,
                    0.5f, 0f, 1f, 1.5f,
                    0.6f,
                    DamageModifier.DEFAULT_DAMAGE_MODIFIERS,
                    DamageModifier.DEFAULT_PEN_DEPTH_MODIFIERS,
                    DamageModifier.DEFAULT_DAMAGE_MODIFIERS
            ),
            new Friction(new Vector3f(0.9f, 0.9f, 1f), 0.05f, 0f, 0f),
            new Tension(true, 12000f, 900f, 0f, 0.015f, 800f),
            Sounds.DEFAULT
    );

    /**
     * 整条履带的最大耐久：每节耐久 × 节数。
     */
    public float maxDurability(int linkCount) {
        return linkDurability * Math.max(1, linkCount);
    }


    public record Friction(
            Vector3f anisotropic,
            float rolling,
            float spinning,
            float restitution
    ) {
    }

    public record Tension(
            boolean enabled,
            float stiffness,
            float damping,
            float preload,
            float slack,
            float maxForce
    ) {
    }

    /**
     * 履带音效覆盖：滚动持续音 + 命中音。
     *
     * <p>初版单音效，不按地面材质细分；音源复用已有的轮胎/金属碰撞资源，
     * 等正式履带音效资源接入后再替换。</p>
     */
    public record Sounds(SoundEvent roll, SoundEvent hitUnPen, SoundEvent hitPen) {
        public static final Sounds DEFAULT = new Sounds(
                create("part.collision.terrain.tire_metal"),
                create("part.collision.terrain.hard_metal"),
                create("part.collision.terrain.hard_metal")
        );

        private static SoundEvent create(String path) {
            return SoundEvent.createFixedRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, path), 64f);
        }
    }
}
