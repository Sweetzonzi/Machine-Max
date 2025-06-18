package io.github.sweetzonzi.machinemax.common.vehicle.attr;

import cn.solarmoon.spark_core.animation.anim.play.ModelIndex;
import cn.solarmoon.spark_core.animation.model.origin.OBone;
import cn.solarmoon.spark_core.animation.model.origin.OCube;
import cn.solarmoon.spark_core.animation.model.origin.OLocator;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.SparkMathKt;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.bullet.collision.shapes.CylinderCollisionShape;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.common.vehicle.PartType;
import jme3utilities.math.MyMath;
import lombok.Getter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Getter
public class SubPartAttr {
    public final String parent;
    public final float mass;
    public final Vec3 projectedArea;
    public final String massCenterLocator;
    public final BlockCollisionType blockCollision;
    public final float stepHeight;
    public final boolean climbAssist;
    public final Map<String, HitBoxAttr> hitBoxes;
    public final Map<String, InteractBoxAttr> interactBoxes;
    public final Map<String, ConnectorAttr> connectors;
    public final DragAttr aeroDynamic;

    public final ConcurrentMap<String, CompoundCollisionShape> hitBoxShape = new ConcurrentHashMap<>();//不同变体零件模型的碰撞体积
    public final ConcurrentMap<String, CompoundCollisionShape> interactBoxShape = new ConcurrentHashMap<>();//不同变体零件模型的交互体积
    public final ConcurrentMap<Long, String> interactBoxNames = new ConcurrentHashMap<>();//碰撞体子形状id对应的交互判定区名称
    public final ConcurrentMap<Long, String> hitBoxNames = new ConcurrentHashMap<>();//碰撞体子形状id对应的碰撞判定区名称
    public final ConcurrentMap<String, ConcurrentMap<String, Transform>> locatorTransforms = new ConcurrentHashMap<>();//定位器相对质心的变换
    public final Map<String, Transform> massCenterTransforms = new HashMap<>();//不同变体部件质心定位器的变换

    public enum BlockCollisionType {
        TRUE, FALSE, GROUND
    }

    public static final Codec<SubPartAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("parent", "").forGetter(SubPartAttr::getParent),
            Codec.FLOAT.optionalFieldOf("mass", -1f).forGetter(SubPartAttr::getMass),
            Vec3.CODEC.optionalFieldOf("projected_area", Vec3.ZERO).forGetter(SubPartAttr::getProjectedArea),
            Codec.STRING.optionalFieldOf("mass_center", "").forGetter(SubPartAttr::getMassCenterLocator),
            Codec.STRING.optionalFieldOf("block_collision", "true").forGetter(SubPartAttr::getBlockCollision),
            Codec.FLOAT.optionalFieldOf("collision_height", -1.0f).forGetter(SubPartAttr::getStepHeight),
            Codec.BOOL.optionalFieldOf("climb_assist", false).forGetter(SubPartAttr::isClimbAssist),
            HitBoxAttr.MAP_CODEC.optionalFieldOf("hit_boxes", Map.of()).forGetter(SubPartAttr::getHitBoxes),
            InteractBoxAttr.MAP_CODEC.optionalFieldOf("interact_boxes", Map.of()).forGetter(SubPartAttr::getInteractBoxes),
            ConnectorAttr.MAP_CODEC.optionalFieldOf("connectors", Map.of()).forGetter(SubPartAttr::getConnectors),
            DragAttr.CODEC.fieldOf("aero_dynamic").forGetter(SubPartAttr::getAeroDynamic)
    ).apply(instance, SubPartAttr::new));

    public static final Codec<Map<String, SubPartAttr>> MAP_CODEC = Codec.unboundedMap(
            Codec.STRING,//子部件名
            SubPartAttr.CODEC//子部件属性
    );

    public SubPartAttr(
            String parent,
            float mass,
            Vec3 projectedArea,
            String massCenterLocator,
            String blockCollision,
            float stepHeight,
            boolean climbAssist,
            Map<String, HitBoxAttr> hitBoxes,
            Map<String, InteractBoxAttr> interactBoxes,
            Map<String, ConnectorAttr> connectors,
            DragAttr aeroDynamic
    ) {
        this.parent = parent;
        if (mass <= 0) throw new IllegalArgumentException("error.machine_max.subpart.zero_mass");
        this.mass = mass;
        this.projectedArea = projectedArea;
        this.massCenterLocator = massCenterLocator;
        this.blockCollision = BlockCollisionType.valueOf(blockCollision.toUpperCase());
        this.stepHeight = stepHeight;
        this.climbAssist = climbAssist;
        if (hitBoxes.isEmpty()) throw new IllegalArgumentException("error.machine_max.subpart.empty_hit_boxes");
        this.hitBoxes = hitBoxes;
        this.interactBoxes = interactBoxes;
        this.connectors = connectors;
        this.aeroDynamic = aeroDynamic;
    }

    /**
     * 获取子部件的碰撞体积 Get collision shape of sub-part<p>
     * 若碰撞体积不存在，则创建并缓存 If collision shape does not exist, create and cache it.<p>
     * 实际创建部件时再生成碰撞体积以避免模型数据未加载的情形 Create collision shape of sub-part when actually creating the part to avoid situations where model data is not loaded.
     *
     * @param variant 部件变体名
     * @param type    部件类型
     * @return 碰撞体积
     */
    public CompoundCollisionShape getCollisionShape(String variant, PartType type) {
        return hitBoxShape.computeIfAbsent(variant, v -> {
            //创建碰撞体积 Create collision shape for sub-part
            var shape = new CompoundCollisionShape(1);
            ModelIndex modelIndex = new ModelIndex(
                    type.variants.getOrDefault(variant, type.variants.get("default")),//获取部件模型路径
                    type.textures.getFirst());//获取部件第一个可用纹理作为默认纹理
            LinkedHashMap<String, OBone> bones = modelIndex.getModel().getBones();//从模型获取所有骨骼
            LinkedHashMap<String, OLocator> locators = LinkedHashMap.newLinkedHashMap(1);
            for (OBone bone : bones.values()) locators.putAll(bone.getLocators());//从模型获取所有定位器
            //将表示部件连接点的定位器添加进定位器列表
            for (ConnectorAttr connectorAttr : connectors.values()) {
                String locatorName = connectorAttr.locatorName();
                addLocator(variant, locatorName, locators);
            }
            //TODO:从AeroDynamic中提取气动中心定位点
            for (Map.Entry<String, HitBoxAttr> hitBoxEntry : this.hitBoxes.entrySet()) {
                if (bones.get(hitBoxEntry.getKey()) != null) {//若找到了对应的碰撞形状骨骼
                    String hitBoxName = hitBoxEntry.getValue().hitBoxName();
                    OBone bone = bones.get(hitBoxEntry.getKey());
                    for (String locatorName : bone.getLocators().keySet()) {//获取碰撞骨骼中的定位器
                        addLocator(variant, locatorName, locators);
                    }
                    switch (hitBoxEntry.getValue().shapeType()) {
                        case "box"://与方块的尺寸匹配
                            for (OCube cube : bone.getCubes()) {
                                org.joml.Vector3f size = cube.getSize().scale(0.5f).toVector3f();
                                BoxCollisionShape boxShape = new BoxCollisionShape(size.x, size.y, size.z);
                                org.joml.Vector3f rotation = cube.getRotation().toVector3f();
                                Quaternionf quaternion = new Quaternionf().rotationXYZ(rotation.x, rotation.y, rotation.z);
                                hitBoxNames.put(boxShape.nativeId(), hitBoxName);
                                shape.addChildShape(
                                        boxShape,
                                        PhysicsHelperKt.toBVector3f(cube.getTransformedCenter(new Matrix4f())),
                                        SparkMathKt.toBQuaternion(quaternion).toRotationMatrix());
                            }
                            break;
                        case "sphere"://取方块的x轴尺寸作为球直径
                            for (OCube cube : bone.getCubes()) {
                                SphereCollisionShape ballShape = new SphereCollisionShape((float) (cube.getSize().x / 2));
                                hitBoxNames.put(ballShape.nativeId(), hitBoxName);
                                shape.addChildShape(
                                        ballShape,
                                        PhysicsHelperKt.toBVector3f(cube.getTransformedCenter(new Matrix4f()).sub(bone.getPivot().toVector3f())));
                            }
                            break;
                        case "cylinder"://取方块的z轴尺寸为直径，x轴尺寸为圆柱高，圆柱默认方向为X轴(横躺)
                            for (OCube cube : bone.getCubes()) {
                                Vector3f size = PhysicsHelperKt.toBVector3f(cube.getSize().scale(0.5f));
                                org.joml.Vector3f rotation = cube.getRotation().toVector3f();
                                Quaternionf quaternion = new Quaternionf().rotationXYZ(rotation.x, rotation.y, rotation.z);
                                CylinderCollisionShape cylinderShape = new CylinderCollisionShape(size, 0);
                                hitBoxNames.put(cylinderShape.nativeId(), hitBoxName);
                                shape.addChildShape(
                                        cylinderShape,
                                        PhysicsHelperKt.toBVector3f(cube.getTransformedCenter(new Matrix4f()).sub(bone.getPivot().toVector3f())),
                                        SparkMathKt.toBQuaternion(quaternion).toRotationMatrix());
                            }
                            break;
                        case "cone":
                            //TODO:创建碰撞体积
                            break;
                        case "capsule":
                            //TODO:创建碰撞体积
                            break;
                        default:
                            MachineMax.LOGGER.error("在部件{}中发现不支持的碰撞形状类型{}。", type.name, hitBoxEntry.getValue());
                    }
                } else
                    MachineMax.LOGGER.error("在部件{}中未找到对应的碰撞形状骨骼{}。", type.name, hitBoxEntry.getKey());
            }
            //调整零件质心 Adjust sub-part mass center
            Transform massCenter = new Transform();
            if (!this.massCenterLocator.isEmpty()) {//若零件制定了质心定位点
                OLocator locator = locators.get(this.massCenterLocator);
                if (locator != null) {
                    org.joml.Vector3f rotation = locator.getRotation().toVector3f();
                    massCenter = new Transform(
                            PhysicsHelperKt.toBVector3f(locator.getOffset()),
                            SparkMathKt.toBQuaternion(new Quaternionf().rotationZYX(rotation.x, rotation.y, rotation.z))
                    );
                    //重新计算并调节定位器相对质心的变换 Recalculate and adjust locator transforms relative to mass center
                    for (Map.Entry<String, Transform> locatorTransform : locatorTransforms.computeIfAbsent(variant, v1 -> new ConcurrentHashMap<>()).entrySet()) {
                        String locatorName = locatorTransform.getKey();
                        Transform transform = locatorTransform.getValue();
                        MyMath.combine(massCenter.invert(), transform, transform);
                        locatorTransforms.get(variant).put(locatorName, transform);
                    }
                } else {
                    MachineMax.LOGGER.warn("在部件{}中未找到质心定位点{}，未对子部件的碰撞体积进行偏移调整。", type.name, this.massCenterLocator);
                }
                shape.correctAxes(massCenter);//调整碰撞体位置，使模型原点对齐质心(必须在创建完成碰撞体积后进行！)
            }
            //将结果存入零件属性中 Store result in sub-part attributes
            this.massCenterTransforms.put(variant, massCenter);
            return shape;
        });
    }

    public CompoundCollisionShape getInteractBoxShape(String variant, PartType type) {
        return interactBoxShape.computeIfAbsent(variant, v -> {
            //创建交互体积 Create interact box shape for sub-part
            var shape = new CompoundCollisionShape(1);
            ModelIndex modelIndex = new ModelIndex(
                    type.variants.getOrDefault(variant, type.variants.get("default")),//获取部件模型路径
                    type.textures.getFirst());//获取部件第一个可用纹理作为默认纹理
            LinkedHashMap<String, OBone> bones = modelIndex.getModel().getBones();//从模型获取所有骨骼
            LinkedHashMap<String, OLocator> locators = LinkedHashMap.newLinkedHashMap(0);
            for (OBone bone : bones.values()) locators.putAll(bone.getLocators());//从模型获取所有定位器
            for (Map.Entry<String, InteractBoxAttr> interactBoxEntry : this.interactBoxes.entrySet()) {
                String boneName = interactBoxEntry.getValue().boneName();
                if (bones.get(boneName) != null) {//若找到了对应的碰撞形状骨骼
                    String interactBoxName = interactBoxEntry.getKey();
                    OBone bone = bones.get(boneName);
                    for (OCube cube : bone.getCubes()) {
                        org.joml.Vector3f size = cube.getSize().scale(0.5f).toVector3f();
                        BoxCollisionShape boxShape = new BoxCollisionShape(size.x, size.y, size.z);
                        org.joml.Vector3f rotation = cube.getRotation().toVector3f();
                        Quaternionf quaternion = new Quaternionf().rotationXYZ(rotation.x, rotation.y, rotation.z);
                        interactBoxNames.put(boxShape.nativeId(), interactBoxName);
                        shape.addChildShape(
                                boxShape,
                                PhysicsHelperKt.toBVector3f(cube.getTransformedCenter(new Matrix4f())),
                                SparkMathKt.toBQuaternion(quaternion).toRotationMatrix());
                    }
                } else
                    MachineMax.LOGGER.error("在部件{}中未找到对应的交互形状骨骼{}。", type.name, interactBoxEntry.getValue().boneName());
            }
            //调整交互体积偏移 Adjust interact box shape offset
            Transform massCenter = new Transform();
            if (!this.massCenterLocator.isEmpty()) {//若零件制定了质心定位点
                OLocator locator = locators.get(this.massCenterLocator);
                if (locator != null) {
                    org.joml.Vector3f rotation = locator.getRotation().toVector3f();
                    massCenter = new Transform(
                            PhysicsHelperKt.toBVector3f(locator.getOffset()),
                            SparkMathKt.toBQuaternion(new Quaternionf().rotationZYX(rotation.x, rotation.y, rotation.z))
                    );
                } else {
                    MachineMax.LOGGER.warn("在部件{}的模型中未找到质心定位点{}，未对子部件的交互体积进行偏移调整。", type.name, this.massCenterLocator);
                }
                shape.correctAxes(massCenter);//调整碰撞体位置，使模型原点对齐质心(必须在创建完成碰撞体积后进行！)
                //TODO:重新计算并调节转动惯量
            }
            return shape;
        });
    }

    private String getBlockCollision() {
        return blockCollision.toString().toLowerCase();
    }

    private void addLocator(String variant, String locatorName, Map<String, OLocator> locators) {
        OLocator locator = locators.get(locatorName);
        org.joml.Vector3f rotation = locator.getRotation().toVector3f();
        Quaternionf quaternion = new Quaternionf().rotationXYZ(rotation.x, rotation.y, rotation.z);
        Transform transform = new Transform(
                PhysicsHelperKt.toBVector3f(locator.getOffset()),
                SparkMathKt.toBQuaternion(quaternion)
        );
        locatorTransforms.computeIfAbsent(variant, v1 -> new ConcurrentHashMap<>()).put(locatorName, transform);
    }
}
