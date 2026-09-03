package io.github.sweetzonzi.machine_max.common.mech.vehicle.attr;

import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.animation.model.origin.OBone;
import cn.solarmoon.spark_core.animation.model.origin.OCube;
import cn.solarmoon.spark_core.animation.model.origin.OLocator;
import cn.solarmoon.spark_core.animation.model.origin.OModel;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.bullet.collision.shapes.*;
import com.jme3.bullet.collision.shapes.infos.ChildCollisionShape;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.connector.ConnectorAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.AbstractSubsystemAttr;
import jme3utilities.math.MyMath;
import lombok.AccessLevel;
import lombok.Getter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Getter
public class SubPartAttr {
    // 模型属性
    public final String startBone;
    public final List<String> endBones;
    // 物理属性
    public final float mass;
    public final String massCenterName;
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

    // 运行时缓存
    @Getter(value = AccessLevel.PRIVATE)
    private Map<String, OBone> bonesCache = null;
    /**
     * 运行时缓存：自动排除骨骼列表。
     * 由 VariantAttr 装配时根据模型骨骼层级计算，内容为本子部件所有"后代子部件"的 start_bone。
     * 用于在骨骼过滤时自动剔除，避免嵌套子部件的骨骼被子部件重复宣称。
     */
    @Getter(value = AccessLevel.PRIVATE)
    private List<String> autoEndBones = List.of();
    public CompoundCollisionShape hitBoxShape = null;
    public CompoundCollisionShape interactBoxShape = null;
    public final ConcurrentMap<Long, String> interactBoxNames = new ConcurrentHashMap<>();
    public final ConcurrentMap<Long, String> hitBoxNames = new ConcurrentHashMap<>();
    public final ConcurrentMap<Long, Float> wheelHalfWidths = new ConcurrentHashMap<>();
    public final ConcurrentMap<Long, Float> wheelRadius = new ConcurrentHashMap<>();
    public final ConcurrentMap<Long, Boolean> isWheelSurface = new ConcurrentHashMap<>();
    public final ConcurrentMap<String, Transform> locatorTransforms = new ConcurrentHashMap<>();
    public final ConcurrentMap<String, Set<String>> hydrodynamicLocators = new ConcurrentHashMap<>();
    public final Transform massCenterTransform = new Transform();

    public enum BlockCollisionType {
        TRUE, FALSE, GROUND
    }

    public static final Codec<SubPartAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("start_bone", "").forGetter(SubPartAttr::getStartBone),
            Codec.STRING.listOf().optionalFieldOf("end_bones", List.of()).forGetter(SubPartAttr::getEndBones),
            Codec.FLOAT.optionalFieldOf("durability", 20f).forGetter(SubPartAttr::getDurability),
            Codec.FLOAT.optionalFieldOf("mass", 25f).forGetter(SubPartAttr::getMass),
            Codec.STRING.optionalFieldOf("mass_center", "mass_center").forGetter(SubPartAttr::getMassCenterName),
            Vec3.CODEC.optionalFieldOf("projected_area", Vec3.ZERO).forGetter(SubPartAttr::getProjectedArea),
            Codec.STRING.optionalFieldOf("block_collision", "true").forGetter(SubPartAttr::getBlockCollision),
            Codec.FLOAT.optionalFieldOf("collision_height", -1.0f).forGetter(SubPartAttr::getStepHeight),
            Codec.BOOL.optionalFieldOf("climb_assist", false).forGetter(SubPartAttr::isClimbAssist),
            HitBoxAttr.MAP_CODEC.fieldOf("hit_boxes").forGetter(SubPartAttr::getHitBoxes),
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
            String massCenterName,
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
        this.massCenterName = massCenterName;
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
    public CompoundCollisionShape getCollisionShape(VariantAttr attr) {
        if (hitBoxShape == null){
            ResourceLocation modelLocation = attr.getModel();
            var shape = new CompoundCollisionShape(1);
            // 加载模型骨骼
            Map<String, OBone> bones = filterBones(
                    OModel.getORIGINS().get(new ModelIndex("part", modelLocation)).getBones(),
                    startBone, getEffectiveEndBones());
            if (bones.isEmpty()) throw new IllegalArgumentException(Component.translatable("error.machine_max.subpart.empty_collision_shape").getString());
            // 获取并储存定位器
            LinkedHashMap<String, OLocator> locators = LinkedHashMap.newLinkedHashMap(1);
            for (OBone bone : bones.values()) locators.putAll(bone.getLocators());
            addLocator(locators, bones.get(startBone));

            for (Map.Entry<String, HitBoxAttr> hitBoxEntry : this.hitBoxes.entrySet()) {
                if (bones.get(hitBoxEntry.getKey()) != null) {
                    String hitBoxName = hitBoxEntry.getKey();
                    OBone bone = bones.get(hitBoxName);
                    Matrix4f pose = new Matrix4f();
                    bone.applyTransformToLocal(pose, bones.get(startBone));
                    switch (hitBoxEntry.getValue().shapeType) {
                        case "box":
                            for (OCube cube : bone.getCubes()) {
                                org.joml.Vector3f size = cube.getSize().scale(0.5f).toVector3f();
                                BoxCollisionShape boxShape = new BoxCollisionShape(
                                        Math.max(Math.abs(size.x), 0.01f),
                                        Math.max(Math.abs(size.y), 0.01f),
                                        Math.max(Math.abs(size.z), 0.01f));
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
                                MultiSphere ballShape = new MultiSphere((float) (cube.getSize().y / 2));
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
                                CapsuleCollisionShape cylinderShape = new CapsuleCollisionShape(
                                        Math.max(Math.abs(size.x), 0.01f),
                                        Math.max(Math.abs(size.y), 0.01f),
                                        0
                                );
                                hitBoxNames.put(cylinderShape.nativeId(), hitBoxName);
                                shape.addChildShape(
                                        cylinderShape,
                                        PhysicsHelperKt.toBVector3f(cube.getTransformedCenter(pose)),
                                        SparkMathKt.toBQuaternion(cube.getTransformedRotation(pose)).toRotationMatrix());
                            }
                            break;
                        case "wheel":
                            for (OCube cube : bone.getCubes()) {
                                float radius = (float) (Math.max(Math.abs(cube.getSize().y), 0.01f) / 2);
                                float halfWidth = (float) (Math.max(Math.abs(cube.getSize().x), 0.01f) / 2);
                                // 地形接触用的球体
                                SphereCollisionShape ballShape = new SphereCollisionShape(radius);
                                hitBoxNames.put(ballShape.nativeId(), hitBoxName);
                                wheelHalfWidths.put(ballShape.nativeId(), halfWidth);
                                wheelRadius.put(ballShape.nativeId(), radius);
                                isWheelSurface.put(ballShape.nativeId(), true);
                                shape.addChildShape(
                                        ballShape,
                                        PhysicsHelperKt.toBVector3f(cube.getTransformedCenter(pose)));
                                // 一般判定用的圆柱体
                                float cylinderRadius = (float) Math.sqrt(radius * radius - halfWidth * halfWidth);
                                Vector3f cylinderSize = new Vector3f(halfWidth + 0.01f, cylinderRadius, cylinderRadius);
                                CylinderCollisionShape cylinderShape = new CylinderCollisionShape(cylinderSize, 0);
                                hitBoxNames.put(cylinderShape.nativeId(), hitBoxName);
                                isWheelSurface.put(cylinderShape.nativeId(), false);
                                shape.addChildShape(
                                        cylinderShape,
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
            Transform massCenter;
            OLocator locator = locators.get(this.massCenterName);
            if (locator != null) {
                org.joml.Vector3f rotation = locator.getRotation().toVector3f();
                massCenter = new Transform(
                        PhysicsHelperKt.toBVector3f(locator.getOffset()),
                        SparkMathKt.toBQuaternion(new Quaternionf().rotationZYX(rotation.x, rotation.y, rotation.z))
                );
            } else { // 未指定质心则取所有子形状的平均位置
                Vector3f center = new Vector3f();
                for (ChildCollisionShape child : shape.listChildren()) {
                    center.addLocal(child.copyOffset(null));
                }
                center.multLocal(1f / shape.countChildren());
                massCenter = new Transform(center, Quaternion.IDENTITY);
            }
            // 重新计算定位器相对质心的变换
            for (Map.Entry<String, Transform> locatorTransform : locatorTransforms.entrySet()) {
                String locatorName = locatorTransform.getKey();
                Transform transform = locatorTransform.getValue();
                MyMath.combine(transform, massCenter.invert(), transform);
                locatorTransforms.put(locatorName, transform);
            }
            shape.correctAxes(massCenter);
            // 缓存质心位置
            this.massCenterTransform.fromTransformMatrix(massCenter.toTransformMatrix());

            // 构建气动计算点缓存
            buildHydrodynamicLocatorsCache(bones);
            
            hitBoxShape = shape;
        }
        return hitBoxShape;
    }

    /**
     * 获取子部件在指定状态的交互体积，必须在getCollisionShape之后调用
     */
    public CompoundCollisionShape getInteractBoxShape(VariantAttr attr) {
        if (interactBoxShape == null) {
            ResourceLocation modelLocation = attr.getModel();
            var shape = new CompoundCollisionShape(1);
            // 加载模型骨骼
            Map<String, OBone> bones = filterBones(
                    OModel.getORIGINS().get(new ModelIndex("part", modelLocation)).getBones(),
                    startBone, getEffectiveEndBones());
            // 获取定位器
            LinkedHashMap<String, OLocator> locators = LinkedHashMap.newLinkedHashMap(0);
            for (OBone bone : bones.values()) locators.putAll(bone.getLocators());

            for (Map.Entry<String, InteractBoxAttr> interactBoxEntry : this.interactBoxes.entrySet()) {
                String boneName = interactBoxEntry.getValue().getBoneName();
                if (bones.get(boneName) != null) {
                    String interactBoxName = interactBoxEntry.getKey();
                    OBone bone = bones.get(boneName);
                    Matrix4f pose = new Matrix4f();
                    bone.applyTransformToLocal(pose, bones.get(startBone));
                    for (OCube cube : bone.getCubes()) {
                        org.joml.Vector3f size = cube.getSize().scale(0.5f).toVector3f();
                        BoxCollisionShape boxShape = new BoxCollisionShape(
                                Math.max(Math.abs(size.x), 0.01f),
                                Math.max(Math.abs(size.y), 0.01f),
                                Math.max(Math.abs(size.z), 0.01f));
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
            shape.correctAxes(this.massCenterTransform);
            interactBoxShape = shape;
        }
        return interactBoxShape;
    }

    /**
     * 获取零件所需渲染的骨骼列表
     *
     * @param variant 变体属性，存储模型路径
     * @return 骨骼列表
     */
    public Map<String, OBone> getBones(VariantAttr variant) {
        if (bonesCache == null) {
            bonesCache = filterBones(
                    OModel.getORIGINS().get(new ModelIndex("part", variant.getModel())).getBones(),
                    startBone, getEffectiveEndBones());
        }
        return bonesCache;
    }

    /**
     * 设置自动排除骨骼列表（由 VariantAttr 装配时调用）。
     * @param autoEndBones 本子部件所有后代子部件的 start_bone 列表
     */
    void setAutoEndBones(List<String> autoEndBones) {
        this.autoEndBones = autoEndBones == null ? List.of() : autoEndBones;
    }

    /**
     * 获取真正用于骨骼过滤的结束骨骼列表：配置的 end_bones 与运行时自动排除骨骼（后代子部件 start_bone）的并集。
     * 在过滤骨骼时使用本方法，确保嵌套子部件的骨骼不会被多个子部件重复宣称。
     * @return 合并去重后的结束骨骼列表
     */
    public List<String> getEffectiveEndBones() {
        if (autoEndBones.isEmpty()) return endBones;
        if (endBones.isEmpty()) return autoEndBones;
        LinkedHashSet<String> merged = new LinkedHashSet<>(endBones);
        merged.addAll(autoEndBones);
        return List.copyOf(merged);
    }

    /**
     * 将骨骼列表过滤，只保留在指定骨骼之间的骨骼。
     * <p>规则：</p>
     * <ul>
     *   <li>起始骨骼本身无条件保留；</li>
     *   <li>起始骨骼范围（start_bone 为空表示整棵模型）内的骨骼，若落在任一 end_bones 骨骼的子树内则被排除；</li>
     *   <li>start_bone 为空时同样会递归排除 end_bones 的后代，而不是仅跳过结束骨骼本身。</li>
     * </ul>
     *
     * @param bones     骨骼列表
     * @param startBone 起始骨骼名称
     * @param endBones  结束骨骼名称列表
     * @return 过滤后的骨骼列表
     */
    public static Map<String, OBone> filterBones(LinkedHashMap<String, OBone> bones, String startBone, List<String> endBones) {
        if (startBone.isEmpty() && endBones.isEmpty()) return bones;
        LinkedHashMap<String, OBone> filteredBones = new LinkedHashMap<>();
        for (Map.Entry<String, OBone> entry : bones.entrySet()) {
            String boneName = entry.getKey();
            OBone bone = entry.getValue();
            if (endBones.contains(boneName)) continue; // 跳过结束骨骼本身
            // 判断是否落在起始范围之外（空 start_bone 表示整棵模型都算在内）
            if (!boneName.equals(startBone) && !startBone.isEmpty() && !bone.isChildOf(startBone)) continue;
            // 起始骨骼本身无条件保留；其余骨骼需排除落在结束骨骼子树内的部分
            if (!boneName.equals(startBone)) {
                boolean isExcluded = false;
                for (String excludedBone : endBones) {
                    if (bone.isChildOf(excludedBone)) {
                        isExcluded = true;
                        break;
                    }
                }
                if (isExcluded) continue;
            }
            filteredBones.put(boneName, bone);
        }
        return filteredBones;
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

    /**
     * 构建气动计算点缓存
     * 将hydrodynamics配置中的定位器名称映射到实际的定位器名称集合
     */
    private void buildHydrodynamicLocatorsCache(Map<String, OBone> bones) {
        hydrodynamicLocators.clear();
        
        for (Map.Entry<String, HydrodynamicAttr> entry : hydrodynamics.entrySet()) {
            String hydrodynamicName = entry.getKey();
            Set<String> locatorNames = new HashSet<>();
            
            // 如果配置的定位器名称在骨骼中存在，则使用该骨骼下的所有定位器
            if (bones.containsKey(hydrodynamicName)) {
                OBone bone = bones.get(hydrodynamicName);
                locatorNames.addAll(bone.getLocators().keySet());
            } else {
                // 否则直接使用配置的定位器名称
                locatorNames.add(hydrodynamicName);
            }
            
            hydrodynamicLocators.put(hydrodynamicName, locatorNames);
        }
    }

    private void addLocator(Map<String, OLocator> locators, OBone startBone) {
        Matrix4f pose = new Matrix4f();
        Quaternionf quaternion = new Quaternionf();
        org.joml.Vector3f translation = new org.joml.Vector3f();
        for (Map.Entry<String, OLocator> entry : locators.entrySet()) {
            String locatorName = entry.getKey();
            OLocator locator = entry.getValue();

            pose.identity().setTranslation(locator.getOffset().toVector3f()).rotateZYX(locator.getRotation().toVector3f());

            locator.getBone().applyTransformToLocal(pose, startBone);

            Transform transform = new Transform(
                    PhysicsHelperKt.toBVector3f(pose.getTranslation(translation)),
                    SparkMathKt.toBQuaternion(pose.getNormalizedRotation(quaternion))
            );
            locatorTransforms.put(locatorName, transform);
        }
    }
}
