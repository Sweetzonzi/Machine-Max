package io.github.tt432.machinemax.common.vehicle.attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public record SubPartAttr(
        String parent,
        float mass,
        Vec3 projectedArea,
        String massCenterLocator,
        String blockCollision,
        float stepHeight,
        Map<String, ShapeAttr> collisionShapeAttr,
        Map<String, ConnectorAttr> connectors,
        DragAttr aeroDynamic
) {

    public static final Codec<SubPartAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("parent", "").forGetter(SubPartAttr::parent),
            Codec.FLOAT.fieldOf("mass").forGetter(SubPartAttr::mass),
            Vec3.CODEC.optionalFieldOf("projected_area",Vec3.ZERO).forGetter(SubPartAttr::projectedArea),
            Codec.STRING.optionalFieldOf("mass_center", "").forGetter(SubPartAttr::massCenterLocator),
            Codec.STRING.optionalFieldOf("block_collision", "true").forGetter(SubPartAttr::blockCollision),
            Codec.FLOAT.optionalFieldOf("step_height", 0.0f).forGetter(SubPartAttr::stepHeight),
            ShapeAttr.MAP_CODEC.fieldOf("shapes").forGetter(SubPartAttr::collisionShapeAttr),
            ConnectorAttr.MAP_CODEC.fieldOf("connectors").forGetter(SubPartAttr::connectors),
            DragAttr.CODEC.fieldOf("aero_dynamic").forGetter(SubPartAttr::aeroDynamic)
    ).apply(instance, SubPartAttr::new));

    public static final Codec<Map<String, SubPartAttr>> MAP_CODEC = Codec.unboundedMap(
            Codec.STRING,//子部件名
            SubPartAttr.CODEC//子部件属性
    );
}
