package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 发射器子系统静态属性。<br>
 * 定义射速、初速加成、精度修正、开火信号输入频道、弹药输入频道等硬件参数。<br>
 * 一个子系统代表一个发射管/挂架/炮闩。
 */
@Getter
public class LauncherSubsystemStaticAttr extends BasicSubsystemStaticAttr {

    private final float fireRate;                       // 射速 (RPM)
    private final float velocityMultiplier;             // 初速乘数，先应用此
    private final float velocityBonus;                  // 初速线性加成 (m/s)
    private final float horizontalAccuracyMultiplier;   // 水平精度乘子，1.0=不改变弹丸默认水平精度
    private final float verticalAccuracyMultiplier;     // 垂直精度乘子，1.0=不改变弹丸默认垂直精度
    /** 后坐力吸收率：0.0=全后坐力，1.0=完全吸收 */
    private final float recoilAbsorption;
    /** 开火信号输入频道列表，优先级从高到低 */
    private final List<String> controlInputs;
    /** 投射物类型ID，指向 {@code projectiles/*.json} 中定义的投射物类型 */
    private final ResourceLocation projectileTypeId;

    // TODO: 弹药tag过滤 —— 预留占位，待 AmmoLoaderSubsystem 实现后用于弹药兼容性检查
    /** 弹药必须全部具备的tag，空列表表示不要求 */
    private final List<ResourceLocation> requiredTags;
    /** 弹药至少具备其一即可的tag，空列表表示接受任意 */
    private final List<ResourceLocation> acceptableTags;
    /** 弹药不能包含的tag，空列表表示不禁止 */
    private final List<ResourceLocation> forbiddenTags;

    /**
     * 弹药供给发现频道列表。<br>
     * 发射器通过此列表中的频道接收来自 IAmmoSupplier 的握手信号。<br>
     * <b>空列表表示接受任意频道信号</b>（万能接收模式）。
     */
    private final List<String> ammoInputs;

    public static final MapCodec<LauncherSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
            Codec.FLOAT.fieldOf("fire_rate").forGetter(LauncherSubsystemStaticAttr::getFireRate),
            Codec.FLOAT.optionalFieldOf("velocity_multiplier", 1.0f).forGetter(LauncherSubsystemStaticAttr::getVelocityMultiplier),
            Codec.FLOAT.optionalFieldOf("velocity_bonus", 0f).forGetter(LauncherSubsystemStaticAttr::getVelocityBonus),
            Codec.FLOAT.optionalFieldOf("horizontal_accuracy_multiplier", 1.0f).forGetter(LauncherSubsystemStaticAttr::getHorizontalAccuracyMultiplier),
            Codec.FLOAT.optionalFieldOf("vertical_accuracy_multiplier", 1.0f).forGetter(LauncherSubsystemStaticAttr::getVerticalAccuracyMultiplier),
            Codec.FLOAT.optionalFieldOf("recoil_absorption", 0.0f).forGetter(LauncherSubsystemStaticAttr::getRecoilAbsorption),
            Codec.STRING.listOf().optionalFieldOf("control_inputs", List.of("weapon_control")).forGetter(LauncherSubsystemStaticAttr::getControlInputs),
            ResourceLocation.CODEC.optionalFieldOf("projectile_type", ResourceLocation.parse("machine_max:20mm_ap"))
                .forGetter(LauncherSubsystemStaticAttr::getProjectileTypeId),
            // TODO: 弹药tag过滤 —— 占位，待 AmmoLoaderSubsystem 集成后启用
            ResourceLocation.CODEC.listOf().optionalFieldOf("required_tags", List.of())
                .forGetter(LauncherSubsystemStaticAttr::getRequiredTags),
            ResourceLocation.CODEC.listOf().optionalFieldOf("acceptable_tags", List.of())
                .forGetter(LauncherSubsystemStaticAttr::getAcceptableTags),
            ResourceLocation.CODEC.listOf().optionalFieldOf("forbidden_tags", List.of())
                .forGetter(LauncherSubsystemStaticAttr::getForbiddenTags),
            Codec.STRING.listOf().optionalFieldOf("ammo_inputs", List.of())
                .forGetter(LauncherSubsystemStaticAttr::getAmmoInputs),
            BasicSoundAttr.CODEC.codec().optionalFieldOf("sounds", BasicSoundAttr.DEFAULT).forGetter(BasicSubsystemStaticAttr::getSoundAttr)
    ).apply(instance, LauncherSubsystemStaticAttr::new));

    public LauncherSubsystemStaticAttr(
            BasicAttr basicAttr,
            float fireRate,
            float velocityMultiplier,
            float velocityBonus,
            float horizontalAccuracyMultiplier,
            float verticalAccuracyMultiplier,
            float recoilAbsorption,
            List<String> controlInputs,
            ResourceLocation projectileTypeId,
            List<ResourceLocation> requiredTags,
            List<ResourceLocation> acceptableTags,
            List<ResourceLocation> forbiddenTags,
            List<String> ammoInputs,
            BasicSoundAttr sounds) {
        super(basicAttr, sounds);
        this.fireRate = fireRate;
        this.velocityMultiplier = velocityMultiplier;
        this.velocityBonus = velocityBonus;
        this.horizontalAccuracyMultiplier = horizontalAccuracyMultiplier;
        this.verticalAccuracyMultiplier = verticalAccuracyMultiplier;
        this.recoilAbsorption = recoilAbsorption;
        this.controlInputs = controlInputs;
        this.projectileTypeId = projectileTypeId;
        this.requiredTags = requiredTags;
        this.acceptableTags = acceptableTags;
        this.forbiddenTags = forbiddenTags;
        this.ammoInputs = ammoInputs;
    }

    /**
     * 判断传入的弹药tag列表是否与该发射器的弹药tag约束兼容。<br>
     * 规则同连接点tag匹配：
     * <ul>
     *   <li>{@code requiredTags} 必须全部包含，空=不要求</li>
     *   <li>{@code acceptableTags} 至少包含一个，空=允许任意</li>
     *   <li>{@code forbiddenTags} 不能包含任何，空=不禁止</li>
     * </ul>
     *
     * @param ammoTags 弹药的tag列表
     * @return 兼容返回true
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
     * 判断传入的投射物类型是否与该发射器的弹药tag约束兼容。<br>
     * 委托到 {@link #isAmmoCompatible(List)}。
     *
     * @param type 投射物类型
     * @return 兼容返回true
     */
    public boolean isAmmoCompatible(ProjectileType type) {
        return isAmmoCompatible(type.getTags());
    }

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.LAUNCHER;
    }
}
