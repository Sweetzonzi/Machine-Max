package io.github.sweetzonzi.machine_max.common.mech.vehicle;

import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.part.PartRestoreMode;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.SubPartAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.VariantAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.RegularPartData;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.SubPartData;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.VehicleData;
import io.github.sweetzonzi.machine_max.util.data.PosRotVelVel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;

/**
 * 无玩家上下文的服务端载具构造与登记入口，供玩家放置和后续世界生成复用。
 */
public final class VehiclePlacementService {
    private VehiclePlacementService() {
    }

    /**
     * 不依赖玩家或物品创建并登记载具。输入数据只读使用，生成的新实例会分配独立 UUID。
     * 目标变换为空时保留数据中的初始位姿。
     */
    @Nullable
    public static VehicleCore instantiateAndAdd(
            @NotNull Level level,
            @NotNull VehicleData source,
            @NotNull PartRestoreMode restoreMode,
            @Nullable Transform targetTransform
    ) {
        if (level.isClientSide()) throw new IllegalArgumentException("Vehicle placement is server-only");
        VehicleData instanceData = applyWorldTransform(level, source, targetTransform)
                .withNewUUID(UUID.randomUUID());
        VehicleCore vehicle = new VehicleCore(level, instanceData, restoreMode);
        ObjectManager.addVehicle(vehicle);
        return vehicle;
    }

    /** 以质量最大部件的根子零件为锚点，将整车刚体变换写入新的数据副本。 */
    private static VehicleData applyWorldTransform(Level level, VehicleData source, @Nullable Transform target) {
        if (target == null) return source;
        RegularPartData anchorPart = null;
        String anchorName = null;
        float maxMass = Float.NEGATIVE_INFINITY;
        for (RegularPartData part : source.parts.values()) {
            RegularPartType type = RegularPartType.get(level, part.registryKey);
            VariantAttr variant = type == null ? null : type.getVariant(part.variant);
            if (variant == null) continue;
            float mass = 0f;
            for (SubPartAttr attr : variant.getSubParts().values()) mass += attr.getMass();
            if (mass > maxMass) {
                maxMass = mass;
                anchorPart = part;
                anchorName = variant.getRootSubPartName();
            }
        }
        if (anchorPart == null || anchorName == null || !anchorPart.subParts.containsKey(anchorName)) {
            throw new IllegalArgumentException("找不到有效的载具放置参考锚点");
        }

        Vector3f anchorPosition = anchorPart.subParts.get(anchorName).posRotVelVel.position();
        Quaternion sourceRotation = SparkMathKt.toBQuaternion(
                anchorPart.subParts.get(anchorName).posRotVelVel.rotation());
        Quaternion rotationDelta = target.getRotation().mult(sourceRotation.inverse());
        Vector3f targetPosition = target.getTranslation();
        Map<String, RegularPartData> transformedParts = LinkedHashMap.newLinkedHashMap(source.parts.size());
        for (Map.Entry<String, RegularPartData> partEntry : source.parts.entrySet()) {
            RegularPartData part = partEntry.getValue();
            Map<String, SubPartData> transformedSubParts = new HashMap<>(part.subParts.size());
            for (Map.Entry<String, SubPartData> subEntry : part.subParts.entrySet()) {
                SubPartData sub = subEntry.getValue();
                PosRotVelVel pose = sub.posRotVelVel;
                Vector3f position = rotate(rotationDelta, pose.position().subtract(anchorPosition))
                        .addLocal(targetPosition);
                Quaternion rotation = rotationDelta.mult(SparkMathKt.toBQuaternion(pose.rotation()));
                Vector3f linearVelocity = rotate(rotationDelta, pose.linearVel());
                Vector3f angularVelocity = rotate(rotationDelta, pose.angularVel());
                transformedSubParts.put(subEntry.getKey(), new SubPartData(sub.id, sub.durabilityRatio,
                        new PosRotVelVel(position, SparkMathKt.toQuaternionf(rotation), linearVelocity, angularVelocity),
                        new HashMap<>(sub.connectorData), new HashMap<>(sub.subsystemData)));
            }
            transformedParts.put(partEntry.getKey(), new RegularPartData(part.registryKey, part.name, part.uuid,
                    part.variant, part.assemblingProgress, part.sharedDurabilityRatio,
                    part.materialAssemblingProgress, part.assembling, part.destroyed, part.destroyTime,
                    part.textureName, transformedSubParts));
        }
        Vec3 transformedPos = transformPoint(source.pos, anchorPosition, targetPosition, rotationDelta);
        Vec3[] bounds = transformBounds(source.min, source.max, anchorPosition, targetPosition, rotationDelta);
        return new VehicleData(source.name, source.uuid, transformedPos, bounds[0], bounds[1], source.hpRatio,
                transformedParts, source.connections, source.trackLinks, source.meta);
    }

    private static Vec3 transformPoint(Vec3 point, Vector3f anchor, Vector3f target, Quaternion delta) {
        Vector3f local = new Vector3f((float) point.x, (float) point.y, (float) point.z).subtractLocal(anchor);
        Vector3f world = rotate(delta, local).addLocal(target);
        return new Vec3(world.x, world.y, world.z);
    }

    private static Vec3[] transformBounds(Vec3 min, Vec3 max, Vector3f anchor, Vector3f target, Quaternion delta) {
        Vector3f outMin = new Vector3f(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
        Vector3f outMax = new Vector3f(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) {
            Vector3f corner = new Vector3f((float) (x == 0 ? min.x : max.x),
                    (float) (y == 0 ? min.y : max.y), (float) (z == 0 ? min.z : max.z));
            Vector3f world = rotate(delta, corner.subtractLocal(anchor)).addLocal(target);
            outMin.set(Math.min(outMin.x, world.x), Math.min(outMin.y, world.y), Math.min(outMin.z, world.z));
            outMax.set(Math.max(outMax.x, world.x), Math.max(outMax.y, world.y), Math.max(outMax.z, world.z));
        }
        return new Vec3[]{new Vec3(outMin.x, outMin.y, outMin.z), new Vec3(outMax.x, outMax.y, outMax.z)};
    }

    private static Vector3f rotate(Quaternion rotation, Vector3f vector) {
        return rotation.toRotationMatrix().mult(vector, new Vector3f());
    }
}
