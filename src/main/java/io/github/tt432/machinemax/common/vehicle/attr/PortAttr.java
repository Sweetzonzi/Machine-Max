package io.github.tt432.machinemax.common.vehicle.attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;

import java.util.*;

/**
 * @param signalTargets  信号传输目标与信号的识别名，特别地，当目标为part时将会令信号在部件内共享，当目标为vehicle时将会令信号在载具内共享
 * @param acceptableSignals 接受的信号识别名列表，不在此列表中的信号将不会被传输给连接的部件对接口，留空时接受所有类型的信号
 */

public record PortAttr(
        Map<String, List<String>> signalTargets,//信号名与传输目标列表
        List<String> acceptableSignals) {
    public static final Codec<Map<String, List<String>>> TARGET_CODEC = Codec.unboundedMap(
            Codec.STRING,
            Codec.STRING.listOf()
    );
    public static final Codec<PortAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            TARGET_CODEC.optionalFieldOf("signal_targets", Map.of()).forGetter(PortAttr::signalTargets),
            Codec.STRING.listOf().optionalFieldOf("acceptable_signals", List.of()).forGetter(PortAttr::acceptableSignals)
    ).apply(instance, PortAttr::new));

}
