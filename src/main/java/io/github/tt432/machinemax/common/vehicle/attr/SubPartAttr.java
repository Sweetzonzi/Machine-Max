package io.github.tt432.machinemax.common.vehicle.attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Map;

public record SubPartAttr(
        String parent,
        float mass,
        String massCenterLocator,
        Map<String, ShapeAttr> collisionShapeAttr,
        Map<String, ConnectorAttr> connectors,
        DragAttr airDrag
) {

    public static final Codec<SubPartAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("parent", "").forGetter(SubPartAttr::parent),
            Codec.FLOAT.fieldOf("mass").forGetter(SubPartAttr::mass),
            Codec.STRING.optionalFieldOf("mass_center_bone", "").forGetter(SubPartAttr::massCenterLocator),
            ShapeAttr.MAP_CODEC.fieldOf("shapes").forGetter(SubPartAttr::collisionShapeAttr),
            ConnectorAttr.MAP_CODEC.fieldOf("connectors").forGetter(SubPartAttr::connectors),
            DragAttr.CODEC.fieldOf("air_drag").forGetter(SubPartAttr::airDrag)
    ).apply(instance, SubPartAttr::new));

    public static final Codec<Map<String, SubPartAttr>> MAP_CODEC = Codec.unboundedMap(
            Codec.STRING,//子部件名
            SubPartAttr.CODEC//子部件属性
    );
}
