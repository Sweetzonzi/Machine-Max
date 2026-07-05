package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.RegenLoaderSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.RegenLoaderSubsystem;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 再生装弹机子系统动态属性。<br>
 * 定义弹药发现频道、弹链构成等运行时配置。
 */
@Getter
public class RegenLoaderSubsystemAttr extends BasicSubsystemDynamicAttr {
    public final RegenLoaderSubsystemStaticAttr staticAttribute;

    /** 弹药发现频道 → 目标名称列表（用于自动发现同载具内的 IAmmoConsumer） */
    public final Map<String, List<String>> discoveryOutputs;

    /** 原始弹链键值对（JSON 格式：弹种 → 数量），仅用于 CODEC 序列化 */
    private final Map<ResourceLocation, Integer> projectileTypesMap;

    /**
     * 展平后的弹链序列（不可变，构造时从 JSON Map 展平）。<br>
     * 例如 {SMK:3, AP:2, Tracer:1} → [SMK, SMK, SMK, AP, AP, Tracer]
     */
    public final List<ResourceLocation> projectileTypes;

    public static final MapCodec<RegenLoaderSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT)
                    .fieldOf("projectile_types")
                    .forGetter(RegenLoaderSubsystemAttr::getProjectileTypesMap),
            AbstractSubsystemAttr.SIGNAL_TARGETS_CODEC.optionalFieldOf("discovery_outputs", Map.of())
                    .forGetter(RegenLoaderSubsystemAttr::getDiscoveryOutputs)
    ).apply(instance, RegenLoaderSubsystemAttr::new));

    public RegenLoaderSubsystemAttr(
            ResourceLocation modelName,
            Map<ResourceLocation, Integer> projectileTypesMap,
            Map<String, List<String>> discoveryOutputs) {
        super(modelName);
        this.staticAttribute = (RegenLoaderSubsystemStaticAttr) getStaticAttr();
        this.projectileTypesMap = Map.copyOf(projectileTypesMap);

        // 展平弹链：{SMK:3, AP:2, Tracer:1} → [SMK, SMK, SMK, AP, AP, Tracer]
        List<ResourceLocation> flat = new ArrayList<>();
        for (var entry : projectileTypesMap.entrySet()) {
            for (int i = 0; i < entry.getValue(); i++)
                flat.add(entry.getKey());
        }
        this.projectileTypes = List.copyOf(flat);

        this.discoveryOutputs = discoveryOutputs;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.REGEN_LOADER;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new RegenLoaderSubsystem(owner, name, this);
    }
}
