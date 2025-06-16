package io.github.sweetzonzi.machinemax.common.vehicle.visual;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.anim.play.AnimController;
import cn.solarmoon.spark_core.animation.anim.play.BoneGroup;
import cn.solarmoon.spark_core.animation.anim.play.ModelIndex;
import cn.solarmoon.spark_core.molang.core.storage.IForeignVariableStorage;
import cn.solarmoon.spark_core.molang.core.storage.IScopedVariableStorage;
import cn.solarmoon.spark_core.molang.core.storage.ITempVariableStorage;
import cn.solarmoon.spark_core.molang.core.storage.VariableStorage;
import cn.solarmoon.spark_core.physics.SparkMathKt;
import cn.solarmoon.spark_core.sync.SyncData;
import cn.solarmoon.spark_core.sync.SyncerType;
import com.jme3.math.Transform;
import io.github.sweetzonzi.machinemax.common.vehicle.PartType;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.awt.*;

@Getter
public class PartProjection implements IAnimatable<PartProjection> {
    public final PartType type;
    public final Level level;
    public String variant;
    public ModelIndex modelIndex;
    private Transform oldTransform;
    private Transform transform;
    @Setter
    private BoneGroup bones;
    public Color color = new Color(255, 255, 255, 64);

    public PartProjection(PartType partType, Level level, String variant, Transform transform) {
        this.type = partType;
        this.level = level;
        this.setTransform(transform);
        this.setVariant(variant);
    }

    public PartProjection setTransform(Transform transform) {
        this.oldTransform = this.transform;
        this.transform = transform;
        return this;
    }

    public PartProjection setVariant(String variant) {
        this.variant = variant;
        this.setModelIndex(new ModelIndex(
                type.variants.getOrDefault(variant, type.variants.get("default")),
                type.animation,
                type.textures.getFirst()));
        return this;
    }

    public PartProjection setColor(Color color) {
        this.color = color;
        return this;
    }

    @Override
    public PartProjection getAnimatable() {
        return this;
    }

    @Override
    public @NotNull Level getAnimLevel() {
        return this.level;
    }

    @NotNull
    @Override
    public AnimController getAnimController() {
        return new AnimController(this);
    }

    @NotNull
    @Override
    public ModelIndex getModelIndex() {
        return this.modelIndex;
    }

    @Override
    public void setModelIndex(@NotNull ModelIndex modelIndex) {
        this.modelIndex = modelIndex;
        this.setBones(new BoneGroup(this));
    }

    public @NotNull BoneGroup getBones() {
        if (this.bones == null) bones = new BoneGroup(this);
        return this.bones;
    }

    @NotNull
    @Override
    public ITempVariableStorage getTempStorage() {
        return new VariableStorage();
    }

    @NotNull
    @Override
    public IScopedVariableStorage getScopedStorage() {
        return new VariableStorage();
    }

    @NotNull
    @Override
    public IForeignVariableStorage getForeignStorage() {
        return new VariableStorage();
    }

    @NotNull
    @Override
    public Vec3 getWorldPosition(float partialTick) {
        if (oldTransform == null)
            return SparkMathKt.toVec3(transform.getTranslation());
        else {
            return SparkMathKt.toVec3(SparkMathKt.lerp(oldTransform, transform, partialTick).getTranslation());
        }
    }

    @Override
    public Matrix4f getWorldPositionMatrix(float partialTick) {
        if (oldTransform == null)
            return SparkMathKt.toMatrix4f(transform.toTransformMatrix());
        else {
            return SparkMathKt.toMatrix4f(SparkMathKt.lerp(oldTransform, transform, partialTick).toTransformMatrix());
        }
    }

    @Override
    public float getRootYRot(float v) {
        return 0;
    }

    @NotNull
    @Override
    public SyncerType getSyncerType() {
        return null;
    }

    @NotNull
    @Override
    public SyncData getSyncData() {
        return null;
    }
}
