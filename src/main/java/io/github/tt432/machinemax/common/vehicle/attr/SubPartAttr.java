package io.github.tt432.machinemax.common.vehicle.attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.*;

public record SubPartAttr(
        String parent,
        float mass,
        String material,//使用材料
        float thickness,//等效厚度
        String massCenterLocator,
        Map<String, String> collisionShape,
        Map<String, ConnectorAttr> connectors,
        DragAttr airDrag
) {
    public static final Codec<Map<String, String>> COLLISION_SHAPE_CODEC = Codec.unboundedMap(
            Codec.STRING,//形状骨骼名称
            Codec.STRING//形状类型，支持："box", "sphere", "cylinder", "capsule", "cone"
    );

    public static final Codec<SubPartAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("parent", "").forGetter(SubPartAttr::parent),
            Codec.FLOAT.fieldOf("mass").forGetter(SubPartAttr::mass),
            Codec.STRING.fieldOf("material").forGetter(SubPartAttr::material),
            Codec.FLOAT.fieldOf("thickness").forGetter(SubPartAttr::thickness),
            Codec.STRING.optionalFieldOf("mass_center_bone", "").forGetter(SubPartAttr::massCenterLocator),
            COLLISION_SHAPE_CODEC.fieldOf("shapes").forGetter(SubPartAttr::collisionShape),
            ConnectorAttr.MAP_CODEC.fieldOf("connectors").forGetter(SubPartAttr::connectors),
            DragAttr.CODEC.fieldOf("air_drag").forGetter(SubPartAttr::airDrag)
    ).apply(instance, SubPartAttr::new));

    public static final Codec<Map<String, SubPartAttr>> MAP_CODEC = Codec.unboundedMap(
            Codec.STRING,//子部件名
            SubPartAttr.CODEC//子部件属性
    );
}
