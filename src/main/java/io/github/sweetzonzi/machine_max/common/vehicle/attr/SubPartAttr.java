package io.github.sweetzonzi.machine_max.common.vehicle.attr;

import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.animation.model.origin.OBone;
import cn.solarmoon.spark_core.animation.model.origin.OCube;
import cn.solarmoon.spark_core.animation.model.origin.OLocator;
import cn.solarmoon.spark_core.animation.model.origin.OModel;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.bullet.collision.shapes.*;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.dynamic_attr.AbstractSubsystemAttr;
import jme3utilities.math.MyMath;
import lombok.Getter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Getter
public class SubPartAttr {
    // 模型属性
    public final String startBone;
    public final List<String> endBones;
    // 物理属性
    public final float mass;
    public final Vec3 projectedArea;
    public final BlockCollisionType blockCollision;
    public final float stepHeight;
    public final boolean climbAssist;

    // 功能属性
    public final float durability;
    public final Map<String, HitBoxAttr> hitBoxes;
    public final Map<String, InteractBoxAttr> interactBoxes;
    public final Map<String, ConnectorAttr> connectors;
    public final Map<String, AbstractSubsystemAttr> subsystems;
    public final int hydroPriority;
    public final Map<String, HydrodynamicAttr> hydrodynamics;

    // 运行时缓存 - 按状态缓存
    public final Map<String, Map<String, OBone>> bones = new LinkedHashMap<>();
    public final ConcurrentMap<String, CompoundCollisionShape> hitBoxShape = new ConcurrentHashMap<>();
    public final ConcurrentMap<String, CompoundCollisionShape> interactBoxShape = new ConcurrentHashMap<>();
    public final ConcurrentMap<Long, String> interactBoxNames = new ConcurrentHashMap<>();
    public final ConcurrentMap<Long, String> hitBoxNames = new ConcurrentHashMap<>();
    public final ConcurrentMap<String, ConcurrentMap<String, Transform>> locatorTransforms = new ConcurrentHashMap<>();

    public enum BlockCollisionType {
        TRUE, FALSE, GROUND
    }

    public static final Codec<SubPartAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("start_bone", "").forGetter(SubPartAttr::getStartBone),
            Codec.STRING.listOf().optionalFieldOf("end_bones", List.of()).forGetter(SubPartAttr::getEndBones),
            Codec.FLOAT.optionalFieldOf("durability", 20f).forGetter(SubPartAttr::getDurability),
            Codec.FLOAT.optionalFieldOf("mass", 25f).forGetter(SubPartAttr::getMass),
            Vec3.CODEC.optionalFieldOf("projected_area", Vec3.ZERO).forGetter(SubPartAttr::getProjectedArea),
            Codec.STRING.optionalFieldOf("block_collision", "true").forGetter(SubPartAttr::getBlockCollision),
            Codec.FLOAT.optionalFieldOf("collision_height", -1.0f).forGetter(SubPartAttr::getStepHeight),
            Codec.BOOL.optionalFieldOf("climb_assist", false).forGetter(SubPartAttr::isClimbAssist),
            HitBoxAttr.MAP_CODEC.optionalFieldOf("hit_boxes", Map.of()).forGetter(SubPartAttr::getHitBoxes),
            InteractBoxAttr.MAP_CODEC.optionalFieldOf("interact_boxes", Map.of()).forGetter(SubPartAttr::getInteractBoxes),
            ConnectorAttr.MAP_CODEC.optionalFieldOf("connectors", Map.of()).forGetter(SubPartAttr::getConnectors),
            AbstractSubsystemAttr.MAP_CODEC.optionalFieldOf("subsystems", Map.of()).forGetter(SubPartAttr::getSubsystems),
            Codec.INT.optionalFieldOf("hydro_priority", 0).forGetter(SubPartAttr::getHydroPriority),
            HydrodynamicAttr.MAP_CODEC.optionalFieldOf("hydrodynamics", Map.of("", HydrodynamicAttr.DEFAULT)).forGetter(SubPartAttr::getHydrodynamics)
    ).apply(instance, SubPartAttr::new));

    public static final Codec<Map<String, SubPartAttr>> MAP_CODEC = Codec.unboundedMap(
            Codec.STRING,
            SubPartAttr.CODEC
    );

    public SubPartAttr(
            String startBone,
            List<String> endBones,
            float durability,
            float mass,
            Vec3 projectedArea,
            String blockCollision,
            float stepHeight,
            boolean climbAssist,
            Map<String, HitBoxAttr> hitBoxes,
            Map<String, InteractBoxAttr> interactBoxes,
            Map<String, ConnectorAttr> connectors,
            Map<String, AbstractSubsystemAttr> subsystems,
            int hydroPriority,
            Map<String, HydrodynamicAttr> hydrodynamics
    ) {
        this.startBone = startBone;
        this.endBones = endBones;
        this.durability = durability;
        if (mass <= 0) throw new IllegalArgumentException("error.machine_max.subpart.zero_mass");
        this.mass = mass;
        this.projectedArea = projectedArea;
        this.blockCollision = BlockCollisionType.valueOf(blockCollision.toUpperCase());
        this.stepHeight = stepHeight;
        this.climbAssist = climbAssist;

        if (hitBoxes.isEmpty()) throw new IllegalArgumentException("error.machine_max.subpart.empty_hit_boxes");
        this.hitBoxes = hitBoxes;
        this.interactBoxes = interactBoxes;
        this.connectors = connectors;
        this.hydroPriority = hydroPriority;
        this.hydrodynamics = hydrodynamics;
        this.subsystems = subsystems;
    }

    /**
     * 获取子部件在指定状态的碰撞体积
     */
    public CompoundCollisionShape getCollisionShape(VariantAttr attr, String state) {
        return hitBoxShape.computeIfAbsent(state, s -> {
            ResourceLocation modelLocation = attr.getModel(state);
            var shape = new CompoundCollisionShape(1);
            // 加载模型骨骼
            Map<String, OBone> bones = filterBones(
                    OModel.getORIGINS().get(new ModelIndex("part", modelLocation)).getBones(),
                    startBone, endBones);
            if (bones.isEmpty()) throw new IllegalArgumentException(Component.translatable("error.machine_max.subpart.empty_collision_shape").getString());
            // 获取定位器
            LinkedHashMap<String, OLocator> locators = LinkedHashMap.newLinkedHashMap(1);
            for (OBone bone : bones.values()) locators.putAll(bone.getLocators());
            // 添加定位器
            for (Map.Entry<String, OLocator> entry : locators.entrySet()) {
                addLocator(state, entry.getKey(), locators);
            }

            for (Map.Entry<String, HitBoxAttr> hitBoxEntry : this.hitBoxes.entrySet()) {
                if (bones.get(hitBoxEntry.getKey()) != null) {
                    String hitBoxName = hitBoxEntry.getKey();
                    OBone bone = bones.get(hitBoxEntry.getKey());
                    Matrix4f pose = new Matrix4f();
                    bone.applyTransformWithParents(pose, bones.get(startBone));
                    switch (hitBoxEntry.getValue().shapeType()) {
                        case "box":
                            for (OCube cube : bone.getCubes()) {
                                org.joml.Vector3f size = cube.getSize().scale(0.5f).toVector3f();
                                BoxCollisionShape boxShape = new BoxCollisionShape(size.x, size.y, size.z);
                                // 存储所有状态的子形状对应关系
                                hitBoxNames.put(boxShape.nativeId(), hitBoxName);
                                shape.addChildShape(
                                        boxShape,
                                        PhysicsHelperKt.toBVector3f(cube.getTransformedCenter(pose)),
                                        SparkMathKt.toBQuaternion(cube.getTransformedRotation(pose)).toRotationMatrix());
                            }
                            break;
                        case "sphere":
                            for (OCube cube : bone.getCubes()) {
                                SphereCollisionShape ballShape = new SphereCollisionShape((float) (cube.getSize().x / 2));
                                hitBoxNames.put(ballShape.nativeId(), hitBoxName);
                                shape.addChildShape(
                                        ballShape,
                                        PhysicsHelperKt.toBVector3f(cube.getTransformedCenter(pose)));
                            }
                            break;
                        case "cylinder":
                            for (OCube cube : bone.getCubes()) {
                                Vector3f size = PhysicsHelperKt.toBVector3f(cube.getSize().scale(0.5f));
                                CylinderCollisionShape cylinderShape = new CylinderCollisionShape(size, 0);
                                hitBoxNames.put(cylinderShape.nativeId(), hitBoxName);
                                shape.addChildShape(
                                        cylinderShape,
                                        PhysicsHelperKt.toBVector3f(cube.getTransformedCenter(pose)),
                                        SparkMathKt.toBQuaternion(cube.getTransformedRotation(pose)).toRotationMatrix());
                            }
                            break;
//                        case "cone":
//                            // TODO: 创建锥形碰撞体积
//                        break;
                        case "capsule":
                            for (OCube cube : bone.getCubes()) {
                                //TODO: 检查尺寸方向是否正确
                                Vector3f size = PhysicsHelperKt.toBVector3f(cube.getSize().scale(0.5f));
                                CapsuleCollisionShape cylinderShape = new CapsuleCollisionShape(size.x, size.y, 0);
                                hitBoxNames.put(cylinderShape.nativeId(), hitBoxName);
                                shape.addChildShape(
                                        cylinderShape,
                                        PhysicsHelperKt.toBVector3f(cube.getTransformedCenter(pose)),
                                        SparkMathKt.toBQuaternion(cube.getTransformedRotation(pose)).toRotationMatrix());
                            }
                            break;
                        case "wheel":
                            for (OCube cube : bone.getCubes()) {
                                Vector3f size = PhysicsHelperKt.toBVector3f(cube.getSize().scale(0.5f));
                                SphereCollisionShape round = new SphereCollisionShape(size.x * 0.2f);
                                size = new Vector3f(size.x * 0.8f, size.y - size.x * 0.2f, size.z);
                                CylinderCollisionShape cylinderShape = new CylinderCollisionShape(size, 0);
                                MinkowskiSum collisionShape = new MinkowskiSum(round, cylinderShape);
                                hitBoxNames.put(collisionShape.nativeId(), hitBoxName);
                                shape.addChildShape(
                                        collisionShape,
                                        PhysicsHelperKt.toBVector3f(cube.getTransformedCenter(pose)),
                                        SparkMathKt.toBQuaternion(cube.getTransformedRotation(pose)).toRotationMatrix());
                            }
                            break;
                        default:
                            String error = hitBoxEntry.getKey() + "被指定为不支持的碰撞形状 " + hitBoxEntry.getValue();
                            error += "应为box, capsule, cylinder, wheel之一";
                            throw new IllegalArgumentException(error);
                    }
                } else {
                    String error = "未找到碰撞形状骨骼 " + hitBoxEntry.getKey();
                    throw new IllegalArgumentException(error);
                }
            }
            if (shape.countChildren() <= 0)
                throw new IllegalArgumentException(Component.translatable("error.machine_max.subpart.empty_collision_shape").getString());
            // 调整零件质心
            Transform massCenter = null;
            OLocator locator = locators.get("MassCenter");
            if (locator != null) {
                org.joml.Vector3f rotation = locator.getRotation().toVector3f();
                massCenter = new Transform(
                        PhysicsHelperKt.toBVector3f(locator.getOffset()),
                        SparkMathKt.toBQuaternion(new Quaternionf().rotationZYX(rotation.x, rotation.y, rotation.z))
                );
            } else if (!startBone.isEmpty()) {
                OBone startBoneInstance = bones.get(this.startBone);
                if (startBoneInstance != null) {
                    org.joml.Vector3f rotation = startBoneInstance.getRotation().toVector3f();
                    var offset = startBoneInstance.getPivot().toVector3f();
                    massCenter = new Transform(
                            PhysicsHelperKt.toBVector3f(offset),
                            SparkMathKt.toBQuaternion(new Quaternionf().rotationZYX(rotation.x, rotation.y, rotation.z))
                    );
                }
            }
            if (massCenter != null) {
                // 重新计算定位器相对质心的变换
                for (Map.Entry<String, Transform> locatorTransform : locatorTransforms.computeIfAbsent(state, v1 -> new ConcurrentHashMap<>()).entrySet()) {
                    String locatorName = locatorTransform.getKey();
                    Transform transform = locatorTransform.getValue();
                    MyMath.combine(massCenter.invert(), transform, transform);
                    locatorTransforms.get(state).put(locatorName, transform);
                }
                shape.correctAxes(massCenter);
            }
            return shape;
        });
    }

    /**
     * 获取子部件在指定状态的交互体积
     */
    public CompoundCollisionShape getInteractBoxShape(VariantAttr attr, String state) {
        return interactBoxShape.computeIfAbsent(state, s -> {
            ResourceLocation modelLocation = attr.getModel(state);
            var shape = new CompoundCollisionShape(1);
            // 加载模型骨骼
            Map<String, OBone> bones = filterBones(
                    OModel.getORIGINS().get(new ModelIndex("part", modelLocation)).getBones(),
                    startBone, endBones);
            // 获取定位器
            LinkedHashMap<String, OLocator> locators = LinkedHashMap.newLinkedHashMap(0);
            for (OBone bone : bones.values()) locators.putAll(bone.getLocators());

            for (Map.Entry<String, InteractBoxAttr> interactBoxEntry : this.interactBoxes.entrySet()) {
                String boneName = interactBoxEntry.getValue().getBoneName();
                if (bones.get(boneName) != null) {
                    String interactBoxName = interactBoxEntry.getKey();
                    OBone bone = bones.get(boneName);
                    Matrix4f pose = new Matrix4f();
                    bone.applyTransformWithParents(pose, bones.get(startBone));
                    for (OCube cube : bone.getCubes()) {
                        org.joml.Vector3f size = cube.getSize().scale(0.5f).toVector3f();
                        BoxCollisionShape boxShape = new BoxCollisionShape(size.x, size.y, size.z);
                        // 存储所有状态的子形状对应关系
                        interactBoxNames.put(boxShape.nativeId(), interactBoxName);
                        shape.addChildShape(
                                boxShape,
                                PhysicsHelperKt.toBVector3f(cube.getTransformedCenter(pose)),
                                SparkMathKt.toBQuaternion(cube.getTransformedRotation(pose)).toRotationMatrix());
                    }
                } else {
                    MachineMax.LOGGER.error("未找到对应的交互形状骨骼{}。", interactBoxEntry.getValue().getBoneName());
                }
            }

            Transform transform = null;
            OLocator locator = locators.get("MassCenter");
            if (locator != null) {
                org.joml.Vector3f rotation = locator.getRotation().toVector3f();
                transform = new Transform(
                        PhysicsHelperKt.toBVector3f(locator.getOffset()),
                        SparkMathKt.toBQuaternion(new Quaternionf().rotationZYX(rotation.x, rotation.y, rotation.z))
                );
            } else if (!startBone.isEmpty()) {
                OBone startBoneInstance = bones.get(this.startBone);
                if (startBoneInstance != null) {
                    org.joml.Vector3f rotation = startBoneInstance.getRotation().toVector3f();
                    var offset = startBoneInstance.getPivot().toVector3f();
                    transform = new Transform(
                            PhysicsHelperKt.toBVector3f(offset),
                            SparkMathKt.toBQuaternion(new Quaternionf().rotationZYX(rotation.x, rotation.y, rotation.z))
                    );
                }
            }
            if (transform != null)
                shape.correctAxes(transform);
            return shape;
        });
    }

    /**
     * 获取零件所需渲染的骨骼列表
     *
     * @param variant 变体属性，存储模型路径
     * @param state 状态名称
     * @return 骨骼列表
     */
    public Map<String, OBone> getBonesToRender(VariantAttr variant, String state) {
        return getBones().computeIfAbsent(state, v -> {
            ResourceLocation modelLocation = variant.getModel(state);
            // 加载模型骨骼
            return filterBones(
                    OModel.getORIGINS().get(new ModelIndex("part", modelLocation)).getBones(),
                    startBone, endBones);
        });
    }

    /**
     * 将骨骼列表过滤，只保留在指定骨骼之间的骨骼
     *
     * @param bones     骨骼列表
     * @param startBone 起始骨骼名称
     * @param endBones  结束骨骼名称列表
     * @return 过滤后的骨骼列表
     */
    public static Map<String, OBone> filterBones(LinkedHashMap<String, OBone> bones, String startBone, List<String> endBones) {
        if (startBone.isEmpty()) return bones;
        else {
            LinkedHashMap<String, OBone> filteredBones = new LinkedHashMap<>();
            for (Map.Entry<String, OBone> entry : bones.entrySet()) {
                String boneName = entry.getKey();
                OBone bone = entry.getValue();
                if (endBones.contains(boneName)) continue; // 跳过子骨骼
                if (boneName.equals(startBone)) {
                    filteredBones.put(boneName, bone);
                } else if (bone.isChildOf(startBone)) {
                    // 排除子骨骼
                    boolean isExcluded = false;
                    for (String excludedBone : endBones) {
                        if (bone.isChildOf(excludedBone)) {
                            isExcluded = true;
                            break;
                        }
                    }
                    if (!isExcluded) filteredBones.put(boneName, bone);
                }
            }
            return filteredBones;
        }
    }

    /**
     * 根据子形状ID获取命中框名称
     */
    public String getHitBoxName(long shapeId) {
        return hitBoxNames.get(shapeId);
    }

    /**
     * 根据子形状ID获取交互框名称
     */
    public String getInteractBoxName(long shapeId) {
        return interactBoxNames.get(shapeId);
    }

    private String getBlockCollision() {
        return blockCollision.toString().toLowerCase();
    }

    private void addLocator(String state, String locatorName, Map<String, OLocator> locators) {
        if (locatorName.isEmpty()) return;
        OLocator locator = locators.get(locatorName);
        org.joml.Vector3f rotation = locator.getRotation().toVector3f();
        Quaternionf quaternion = new Quaternionf().rotationXYZ(rotation.x, rotation.y, rotation.z);
        Transform transform = new Transform(
                PhysicsHelperKt.toBVector3f(locator.getOffset()),
                SparkMathKt.toBQuaternion(quaternion)
        );
        locatorTransforms.computeIfAbsent(state, v1 -> new ConcurrentHashMap<>()).put(locatorName, transform);
    }
}