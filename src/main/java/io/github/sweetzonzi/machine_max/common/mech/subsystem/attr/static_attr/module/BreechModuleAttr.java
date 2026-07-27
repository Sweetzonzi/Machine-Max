package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.module;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.AbstractModuleAttr;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 炮闩模块属性——定义射速、后坐力吸收和弹药兼容性约束。
 * <p>
 * 炮闩受损 → 闭锁/供弹机构故障 → {@code critical=true} 硬编码，归零即触发子系统整体摧毁。
 * 当前版本炮闩无战损衰减——摧毁后 {@code isActive()} 返回 false，发射器无法开火。
 * </p>
 */
public class BreechModuleAttr extends AbstractModuleAttr {
    /** 射速 (RPM) */
    private final float fireRate;
    /** 后坐力吸收率：0.0=全后坐力，1.0=完全吸收 */
    private final float recoilAbsorption;
    /** 弹药必须全部具备的 tag，空列表表示不要求 */
    private final List<ResourceLocation> requiredTags;
    /** 弹药至少具备其一即可的 tag，空列表表示接受任意 */
    private final List<ResourceLocation> acceptableTags;
    /** 弹药不能包含的 tag，空列表表示不禁止 */
    private final List<ResourceLocation> forbiddenTags;

    public static final MapCodec<BreechModuleAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("durability_weight", 1.0f).forGetter(AbstractModuleAttr::durabilityWeight),
            Codec.FLOAT.fieldOf("fire_rate").forGetter(BreechModuleAttr::fireRate),
            Codec.FLOAT.optionalFieldOf("recoil_absorption", 0.0f).forGetter(BreechModuleAttr::recoilAbsorption),
            ResourceLocation.CODEC.listOf().optionalFieldOf("required_tags", List.of())
                    .forGetter(BreechModuleAttr::requiredTags),
            ResourceLocation.CODEC.listOf().optionalFieldOf("acceptable_tags", List.of())
                    .forGetter(BreechModuleAttr::acceptableTags),
            ResourceLocation.CODEC.listOf().optionalFieldOf("forbidden_tags", List.of())
                    .forGetter(BreechModuleAttr::forbiddenTags)
    ).apply(instance, BreechModuleAttr::new));

    /** 炮闩模块默认实例（耐久权重=0.4，射速=600 RPM，无后坐力吸收，无弹药约束） */
    public static final BreechModuleAttr DEFAULT = new BreechModuleAttr(
            0.4f, 600f, 0.0f, List.of(), List.of(), List.of());

    public BreechModuleAttr(float durabilityWeight, float fireRate, float recoilAbsorption,
                            List<ResourceLocation> requiredTags, List<ResourceLocation> acceptableTags,
                            List<ResourceLocation> forbiddenTags) {
        super(durabilityWeight, true); // 炮闩 critical，硬编码
        this.fireRate = fireRate;
        this.recoilAbsorption = recoilAbsorption;
        this.requiredTags = requiredTags;
        this.acceptableTags = acceptableTags;
        this.forbiddenTags = forbiddenTags;
    }

    /**
     * 判断传入的弹药 tag 列表是否与该炮闩的弹药兼容性约束匹配。
     * 规则同连接点 tag 匹配：
     * <ul>
     *   <li>{@code requiredTags} 必须全部包含，空=不要求</li>
     *   <li>{@code acceptableTags} 至少包含一个，空=允许任意</li>
     *   <li>{@code forbiddenTags} 不能包含任何，空=不禁止</li>
     * </ul>
     *
     * @param ammoTags 弹药的 tag 列表
     * @return 兼容返回 true
     */
    public boolean isAmmoCompatible(List<ResourceLocation> ammoTags) {
        Set<ResourceLocation> tagSet = new HashSet<>(ammoTags);

        if (!requiredTags.isEmpty() && !tagSet.containsAll(requiredTags)) return false;

        if (!acceptableTags.isEmpty()) {
            boolean hasAny = false;
            for (ResourceLocation tag : acceptableTags) {
                if (tagSet.contains(tag)) { hasAny = true; break; }
            }
            if (!hasAny) return false;
        }

        if (!forbiddenTags.isEmpty()) {
            for (ResourceLocation tag : forbiddenTags) {
                if (tagSet.contains(tag)) return false;
            }
        }

        return true;
    }

    /**
     * 判断传入的投射物类型是否与该炮闩的弹药兼容性约束匹配。
     * 委托到 {@link #isAmmoCompatible(List)}。
     */
    public boolean isAmmoCompatible(ProjectileType type) {
        return isAmmoCompatible(type.getTags());
    }

    // ====== getter ======

    public float fireRate() { return fireRate; }
    public float recoilAbsorption() { return recoilAbsorption; }
    public List<ResourceLocation> requiredTags() { return requiredTags; }
    public List<ResourceLocation> acceptableTags() { return acceptableTags; }
    public List<ResourceLocation> forbiddenTags() { return forbiddenTags; }
}
