package io.github.tt432.machinemax.common.vehicle.attr;

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
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.vehicle.PartType;
import lombok.Getter;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
public class SubPartAttr {

    public final String parent;
    public final float mass;
    public final Vec3 projectedArea;
    public final String massCenterLocator;
    public final Vec3 friction;
    public final float slipAdaptation;
    public final float rollingFriction;
    public final String blockCollision;
    public final float stepHeight;
    public final boolean climbAssist;
    public final Map<String, HitBoxAttr> collisionShapeAttr;
    public final Map<String, ConnectorAttr> connectors;
    public final DragAttr aeroDynamic;

    public final Map<String, CompoundCollisionShape> collisionShapes = new HashMap<>();
    public final Map<String, Transform> massCenterTransforms = new HashMap<>();

    public static final Codec<SubPartAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("parent", "").forGetter(SubPartAttr::getParent),
            Codec.FLOAT.fieldOf("mass").forGetter(SubPartAttr::getMass),
            Vec3.CODEC.optionalFieldOf("projected_area", Vec3.ZERO).forGetter(SubPartAttr::getProjectedArea),
            Codec.STRING.optionalFieldOf("mass_center", "").forGetter(SubPartAttr::getMassCenterLocator),
            Vec3.CODEC.optionalFieldOf("friction", new Vec3(0.9, 1.5, 0.9)).forGetter(SubPartAttr::getFriction),
            Codec.FLOAT.optionalFieldOf("slip_adaptation", 0.5f).forGetter(SubPartAttr::getSlipAdaptation),
            Codec.FLOAT.optionalFieldOf("rolling_friction", 0.01f).forGetter(SubPartAttr::getRollingFriction),
            Codec.STRING.optionalFieldOf("block_collision", "true").forGetter(SubPartAttr::getBlockCollision),
            Codec.FLOAT.optionalFieldOf("collision_height", -1.0f).forGetter(SubPartAttr::getStepHeight),
            Codec.BOOL.optionalFieldOf("climb_assist", false).forGetter(SubPartAttr::isClimbAssist),
            HitBoxAttr.MAP_CODEC.fieldOf("hit_boxes").forGetter(SubPartAttr::getCollisionShapeAttr),
            ConnectorAttr.MAP_CODEC.fieldOf("connectors").forGetter(SubPartAttr::getConnectors),
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
            Vec3 friction,
            float slipAdaptation,
            float rollingFriction,
            String blockCollision,
            float stepHeight,
            boolean climbAssist,
            Map<String, HitBoxAttr> collisionShapeAttr,
            Map<String, ConnectorAttr> connectors,
            DragAttr aeroDynamic
    ) {
        this.parent = parent;
        this.mass = mass;
        this.projectedArea = projectedArea;
        this.massCenterLocator = massCenterLocator;
        this.friction = friction;
        this.slipAdaptation = slipAdaptation;
        this.rollingFriction = rollingFriction;
        this.blockCollision = blockCollision;
        this.stepHeight = stepHeight;
        this.climbAssist = climbAssist;
        this.collisionShapeAttr = collisionShapeAttr;
        this.connectors = connectors;
        this.aeroDynamic = aeroDynamic;
    }

    /**
     * 获取子部件的碰撞体积 Get collision shape of sub-part<p>
     * 若碰撞体积不存在，则创建并缓存 If collision shape does not exist, create and cache it.<p>
     * 实际创建部件时再生成碰撞体积以避免模型数据未加载的情形 Create collision shape of sub-part when actually creating the part to avoid situations where model data is not loaded.
     * @param variant 部件变体名
     * @param type 部件类型
     * @return 碰撞体积
     */
    public CompoundCollisionShape getCollisionShape(String variant, PartType type) {
        return collisionShapes.computeIfAbsent(variant, v -> {
            //创建碰撞体积 Create collision shape for sub-part
            var shape = new CompoundCollisionShape(1);
            ModelIndex modelIndex = new ModelIndex(
                    type.variants.getOrDefault(variant, type.variants.get("default")),//获取部件模型路径
                    type.animation,//获取部件动画路径
                    type.textures.getFirst());//获取部件第一个可用纹理作为默认纹理
            LinkedHashMap<String, OBone> bones = modelIndex.getModel().getBones();//从模型获取所有骨骼
            LinkedHashMap<String, OLocator> locators = LinkedHashMap.newLinkedHashMap(0);
            for (OBone bone : bones.values()) locators.putAll(bone.getLocators());//从模型获取所有定位器
            int i = 0;
            for (Map.Entry<String, HitBoxAttr> hitBoxEntry : this.collisionShapeAttr.entrySet()) {
                if (bones.get(hitBoxEntry.getKey()) != null) {//若找到了对应的碰撞形状骨骼
                    String hitBoxName = hitBoxEntry.getValue().hitBoxName();
                    float rha = hitBoxEntry.getValue().RHA();
                    float damageReduction = hitBoxEntry.getValue().damageReduction();
                    float damageMultiplier = hitBoxEntry.getValue().damageMultiplier();
                    OBone bone = bones.get(hitBoxEntry.getKey());
                    switch (hitBoxEntry.getValue().shapeType()) {
                        case "box"://与方块的尺寸匹配
                            for (OCube cube : bone.getCubes()) {
                                org.joml.Vector3f size = cube.getSize().scale(0.5f).toVector3f();
                                BoxCollisionShape boxShape = new BoxCollisionShape(size.x, size.y, size.z);
                                org.joml.Vector3f rotation = cube.getRotation().toVector3f();
                                Quaternionf quaternion = new Quaternionf().rotationXYZ(rotation.x, rotation.y, rotation.z);
                                type.hitBoxes.put(boxShape.nativeId(), hitBoxName);
                                type.thickness.put(boxShape.nativeId(), rha);
                                type.damageReduction.put(boxShape.nativeId(), damageReduction);
                                type.damageMultiplier.put(boxShape.nativeId(), damageMultiplier);
                                shape.addChildShape(
                                        boxShape,
                                        PhysicsHelperKt.toBVector3f(cube.getTransformedCenter(new Matrix4f())),
                                        SparkMathKt.toBQuaternion(quaternion).toRotationMatrix());
                            }
                            break;
                        case "sphere"://取方块的x轴尺寸作为球直径
                            for (OCube cube : bone.getCubes()) {
                                SphereCollisionShape ballShape = new SphereCollisionShape((float) (cube.getSize().x / 2));
                                type.hitBoxes.put(ballShape.nativeId(), hitBoxName);
                                type.thickness.put(ballShape.nativeId(), rha);
                                type.damageReduction.put(ballShape.nativeId(), damageReduction);
                                type.damageMultiplier.put(ballShape.nativeId(), damageMultiplier);
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
                                type.hitBoxes.put(cylinderShape.nativeId(), hitBoxName);
                                type.thickness.put(cylinderShape.nativeId(), rha);
                                type.damageReduction.put(cylinderShape.nativeId(), damageReduction);
                                type.damageMultiplier.put(cylinderShape.nativeId(), damageMultiplier);
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
                            continue;
                    }
                    i++;
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
                } else {
                    MachineMax.LOGGER.error("在部件{}中未找到质心定位点{}。", type.name, this.massCenterLocator);
                }
                shape.correctAxes(massCenter);//调整碰撞体位置，使模型原点对齐质心(必须在创建完成碰撞体积后进行！)
                //TODO:重新计算并调节转动惯量
            }
            //将结果存入零件属性中 Store result in sub-part attributes
            this.massCenterTransforms.put(variant, massCenter);
            return shape;
        });
    }
}
