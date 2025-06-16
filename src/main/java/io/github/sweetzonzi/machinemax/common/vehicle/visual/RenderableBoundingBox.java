package io.github.sweetzonzi.machinemax.common.vehicle.visual;

import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.SparkMathKt;
import com.jme3.bounding.BoundingBox;
import com.jme3.math.Vector3f;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.awt.*;

@Getter
public class RenderableBoundingBox extends BoundingBox {
    private Vec3 oldMin;
    private Vec3 oldMax;
    @Setter
    private Color color = Color.RED;

    public RenderableBoundingBox(Vector3f min, Vector3f max) {
        super(min, max);
        this.oldMin = new Vec3(min.x, min.y, min.z);
        this.oldMax = new Vec3(max.x, max.y, max.z);
    }

    public RenderableBoundingBox(Vec3 min, Vec3 max) {
        super(PhysicsHelperKt.toBVector3f(min), PhysicsHelperKt.toBVector3f(max));
        this.oldMin = min;
        this.oldMax = max;
    }

    public void updateShape(Vector3f min, Vector3f max) {
        this.oldMin = new Vec3(min.x, min.y, min.z);
        this.oldMax = new Vec3(max.x, max.y, max.z);
        this.setMinMax(min, max);
    }

    public Matrix4f getTransformMatrix(float partialTick) {
        Vec3 oldCenter = oldMax.add(oldMin).scale(0.5);
        Vec3 newCenter = SparkMathKt.toVec3(this.getCenter(null));
        Vec3 center = newCenter.scale(partialTick).add(oldCenter.scale(1 - partialTick));
        return new Matrix4f().setTranslation(center.toVector3f());
    }
}
