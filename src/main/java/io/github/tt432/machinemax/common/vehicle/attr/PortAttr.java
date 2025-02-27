package io.github.tt432.machinemax.common.vehicle.attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;

import java.util.*;

@Getter
public class PortAttr {
    public final String name;
    public final String target;//目标端口名称，特别地，“VEHICLE_BUS”将会把内容传输给载具本身
    public final Set<String> requiredSignals = new HashSet<>();//端口需求的信号类型
    public final Set<String> providedSignals = new HashSet<>();//端口提供的信号类型
    public final Set<String> requiredResources = new HashSet<>();//端口需求的资源类型
    public final Set<String> providedResources = new HashSet<>();//端口提供的资源类型
    public static final Codec<PortAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(PortAttr::getName),
            Codec.STRING.optionalFieldOf("target","").forGetter(PortAttr::getTarget),
            Codec.STRING.listOf().optionalFieldOf("required_signals", List.of()).forGetter((p) -> p.requiredSignals.stream().toList()),
            Codec.STRING.listOf().optionalFieldOf("provided_signals", List.of()).forGetter((p) -> p.providedSignals.stream().toList()),
            Codec.STRING.listOf().optionalFieldOf("required_resources", List.of()).forGetter((p) -> p.requiredResources.stream().toList()),
            Codec.STRING.listOf().optionalFieldOf("provided_resources", List.of()).forGetter((p) -> p.providedResources.stream().toList())
    ).apply(instance, PortAttr::new));

    public static final Codec<Map<String, PortAttr>> MAP_CODEC = Codec.unboundedMap(
            Codec.STRING,
            CODEC
    );

    public PortAttr(String name,
                    String target,
                    List<String> requiredSignals,
                    List<String> providedSignals,
                    List<String> requiredResources,
                    List<String> providedResources) {
        this.name = name;
        this.target = target;
        this.requiredSignals.addAll(requiredSignals);
        this.providedSignals.addAll(providedSignals);
        this.requiredResources.addAll(requiredResources);
        this.providedResources.addAll(providedResources);
    }
}
