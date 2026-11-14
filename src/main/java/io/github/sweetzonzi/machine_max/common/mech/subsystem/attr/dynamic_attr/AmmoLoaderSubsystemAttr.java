package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AmmoLoaderSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.AmmoLoaderSubsystemStaticAttr;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * 自动装弹机子系统动态属性。<br>
 * 机构同时是下游（Launcher）的提供方与上游（弹药架）的需求方，因此两个发现字段都在这里：
 * <ul>
 *   <li>{@code ammo_discovery_inputs} — 本机构接受的供弹服务频道，下游按它发现本机</li>
 *   <li>{@code ammo_discovery_targets} — 本机构查询上游弹药架用的频道与目标</li>
 * </ul>
 * 上游查询只接受库存持有者（弹药架），机构之间不互认上游，机械链因此不会成环。
 */
@Getter
public class AmmoLoaderSubsystemAttr extends BasicSubsystemDynamicAttr {
    public final AmmoLoaderSubsystemStaticAttr staticAttribute;

    /** 本机构接受的供弹服务频道（下游 Launcher 按它发现本机） */
    public final List<String> ammoDiscoveryInputs;

    /** 上游查询：供弹服务频道 → 目标名称列表（指向本机的上游弹药架） */
    public final Map<String, List<String>> ammoDiscoveryTargets;

    public static final MapCodec<AmmoLoaderSubsystemAttr> CODEC =
            RecordCodecBuilder.<AmmoLoaderSubsystemAttr>mapCodec(instance -> instance.group(
                    ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName),
                    Codec.STRING.listOf().optionalFieldOf("ammo_discovery_inputs", List.of())
                            .forGetter(AmmoLoaderSubsystemAttr::getAmmoDiscoveryInputs),
                    AbstractSubsystemAttr.SIGNAL_TARGETS_CODEC
                            .optionalFieldOf("ammo_discovery_targets", Map.of())
                            .forGetter(AmmoLoaderSubsystemAttr::getAmmoDiscoveryTargets)
            ).apply(instance, AmmoLoaderSubsystemAttr::new));

    public AmmoLoaderSubsystemAttr(
            ResourceLocation modelName,
            List<String> ammoDiscoveryInputs,
            Map<String, List<String>> ammoDiscoveryTargets) {
        super(modelName);
        this.staticAttribute = (AmmoLoaderSubsystemStaticAttr) getStaticAttr();
        this.ammoDiscoveryInputs = ammoDiscoveryInputs;
        this.ammoDiscoveryTargets = ammoDiscoveryTargets;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public AmmoLoaderSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new AmmoLoaderSubsystem(owner, name, this);
    }
}
