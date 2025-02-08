package io.github.tt432.machinemax.common.vehicle.attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.*;

public record SubPartAttr(
        String parent,
        float mass,
        String massCenterLocatorName,
        Map<String, ShapeAttr> shapeAndMaterials,
        Map<String, ConnectorAttr> connectors,
        DragAttr airDrag
) {

    public static final Codec<SubPartAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("parent", "").forGetter(SubPartAttr::parent),
            Codec.FLOAT.fieldOf("mass").forGetter(SubPartAttr::mass),
            Codec.STRING.optionalFieldOf("mass_center_locator", "").forGetter(SubPartAttr::massCenterLocatorName),
            ShapeAttr.MAP_CODEC.fieldOf("shape_and_materials").forGetter(SubPartAttr::shapeAndMaterials),
            ConnectorAttr.MAP_CODEC.fieldOf("connectors").forGetter(SubPartAttr::connectors),
            DragAttr.CODEC.fieldOf("air_drag").forGetter(SubPartAttr::airDrag)
    ).apply(instance, SubPartAttr::new));

    public static final Codec<Map<String, SubPartAttr>> MAP_CODEC = Codec.unboundedMap(
            Codec.STRING,//子部件名
            SubPartAttr.CODEC//子部件属性
    );
}
