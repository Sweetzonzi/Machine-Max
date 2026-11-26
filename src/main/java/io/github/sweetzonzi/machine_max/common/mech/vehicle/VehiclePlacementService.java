package io.github.sweetzonzi.machine_max.common.mech.vehicle;

import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.body.CollisionGroups;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.objects.PhysicsGhostObject;
import com.jme3.math.Matrix3f;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.simsilica.mathd.Vec3d;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.part.PartRestoreMode;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.RegularPartData;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.SubPartData;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.VehicleData;
import io.github.sweetzonzi.machine_max.util.data.PosRotVelVel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.ObjIntConsumer;

/** 一份载具数据的放置几何及入世入口。实例只在所属世界的主线程使用。 */
public final class VehiclePlacementService {
    private static final double CONTACT_EPSILON = 1.0e-4;

    private final Level level;
    private final VehicleData source;
    private final Vector3f sourceRootPosition;
    private final Quaternion sourceToRoot;
    private final AABB rootLocalBounds;
    private Quaternion cachedRotation;
    private AABB cachedBounds;
    private Vec3 cachedRootOffset;

    /**
     * 计算并固定这份数据的参考根及其局部碰撞包围盒。
     *
     * @param level 解析部件定义及检查候选落点的世界
     * @param source 已校验的载具数据；复用本实例时须保持其结构、子零件位姿和部件定义不变
     */
    public VehiclePlacementService(Level level, VehicleData source) {
        this.level = level;
        this.source = source;
        PosRotVelVel root = source.findPlacementReferenceRoot(level).posRotVelVel;
        sourceRootPosition = root.position().clone();
        sourceToRoot = SparkMathKt.toBQuaternion(root.rotation()).inverse().normalizeLocal();
        rootLocalBounds = source.computeCollisionBounds(level, sourceRootPosition, sourceToRoot);
    }

    /**
     * 计算以世界原点为接地锚点时的碰撞 AABB。改变落点只需将返回值平移，无需重算形状。
     *
     * @param rootRotation 最大质量部件根子零件的目标世界朝向，须为单位四元数
     * @return 沿世界 XYZ 轴对齐的碰撞 AABB，最低 Y 为零
     */
    public AABB boundsAtOrigin(Quaternion rootRotation) {
        if (!rootRotation.equals(cachedRotation)) {
            AABB orientedBounds = Quaternion.IDENTITY.equals(rootRotation) ? rootLocalBounds
                    : source.computeCollisionBounds(level, sourceRootPosition, rootRotation.mult(sourceToRoot));
            Vec3 anchor = rootLocalBounds.getBottomCenter();
            Vector3f orientedAnchor = rootRotation.toRotationMatrix().mult(PhysicsHelperKt.toBVector3f(anchor), null);
            // 水平以固定局部底心为准，竖直以目标朝向下的最低碰撞边界为准。
            cachedRootOffset = new Vec3(-orientedAnchor.x, -orientedBounds.minY, -orientedAnchor.z);
            cachedBounds = orientedBounds.move(cachedRootOffset);
            cachedRotation = rootRotation.clone();
        }
        return cachedBounds;
    }

    /**
     * 求出目标朝向下参考根刚体应处的世界位姿。
     *
     * @param groundAnchor 世界接地锚点；XZ 对齐参考根局部 AABB 的底心投影，Y 对齐整车最低碰撞边界
     * @param rootRotation 参考根子零件的目标世界朝向，须为单位四元数
     * @return 只供本服务映射源子零件位姿使用的参考根落位
     */
    private Vec3 placedRootPosition(Vec3 groundAnchor, Quaternion rootRotation) {
        boundsAtOrigin(rootRotation);
        return groundAnchor.add(cachedRootOffset);
    }

    /**
     * 将各源子零件位姿映射到指定放置位姿，不读取上一帧结果。
     *
     * @param groundAnchor 世界接地锚点；XZ 对齐参考根局部 AABB 的底心投影，Y 对齐整车最低碰撞边界
     * @param rootRotation 参考根子零件的目标世界朝向，须为单位四元数
     * @param consumer 依次接收子零件刚体的绝对世界位姿及子零件 ID
     */
    public void forEachPlacedSubPart(Vec3 groundAnchor, Quaternion rootRotation, ObjIntConsumer<Transform> consumer) {
        Quaternion rotation = rootRotation.mult(sourceToRoot);
        Matrix3f axes = rotation.toRotationMatrix();
        Vec3 rootPosition = placedRootPosition(groundAnchor, rootRotation);
        for (RegularPartData part : source.parts.values()) {
            for (SubPartData subPart : part.subParts.values()) {
                consumer.accept(placeSubPart(subPart.posRotVelVel, rotation, axes, rootPosition), subPart.id);
            }
        }
    }

    /**
     * 检查世界边界、已加载区块、方块碰撞和玩家／载具占位（主线程）。
     *
     * @param bounds 指定落点及朝向下的整车世界碰撞 AABB
     * @return 候选范围完整加载且没有占位冲突时为 {@code true}
     */
    public boolean canPlace(AABB bounds) {
        var border = level.getWorldBorder();
        if (bounds.minX < border.getMinX() || bounds.maxX > border.getMaxX()
                || bounds.minZ < border.getMinZ() || bounds.maxZ > border.getMaxZ()
                || bounds.minY < level.getMinBuildHeight() || bounds.maxY > level.getMaxBuildHeight()) return false;

        // 各轴只收缩极薄一层，以区分相贴与穿透，同时避免把恰好贴着的区块算进去。
        AABB interior = bounds.deflate(Math.min(CONTACT_EPSILON, bounds.getXsize() * 0.25),
                Math.min(CONTACT_EPSILON, bounds.getYsize() * 0.25),
                Math.min(CONTACT_EPSILON, bounds.getZsize() * 0.25));
        if (!level.hasChunksAt(Mth.floor(interior.minX), Mth.floor(interior.minY), Mth.floor(interior.minZ),
                Mth.floor(interior.maxX), Mth.floor(interior.maxY), Mth.floor(interior.maxZ))
                || !level.noBlockCollision(null, interior)) return false;

        var query = new PhysicsGhostObject(new BoxCollisionShape(new Vector3f((float) interior.getXsize() * 0.5f,
                (float) interior.getYsize() * 0.5f, (float) interior.getZsize() * 0.5f)));
        Vec3 center = interior.getCenter();
        query.setPhysicsLocationDp(new Vec3d(center.x, center.y, center.z));
        query.setCollisionGroup(CollisionGroups.PAWN);
        query.setCollideWithGroups(CollisionGroups.PAWN | CollisionGroups.PHYSICS_BODY);
        return SparkLevel.getPhysicsLevel(level).getWorld().getWorldSnapshot().contactTest(query, null) == 0;
    }

    /**
     * 检查占位并构造载具，经 {@link ObjectManager#addVehicle} 正式入世（服务端主线程）。
     * 源数据保持原样；新实例使用独立 UUID。
     *
     * @param restoreMode 部件恢复模式；初始状态可预先写入传给构造器的载具数据副本
     * @param groundAnchor 目标世界接地锚点；XZ 对齐参考根局部 AABB 的底心投影，Y 指定接地高度
     * @param rootRotation 参考根子零件的目标世界朝向，须为单位四元数
     * @return 已登记的载具；边界、区块或占位校验失败时为 {@code null}
     * @throws IllegalArgumentException 如果在客户端世界调用
     */
    public @Nullable VehicleCore instantiateAndAdd(PartRestoreMode restoreMode, Vec3 groundAnchor,
                                                     Quaternion rootRotation) {
        if (level.isClientSide()) throw new IllegalArgumentException("载具只能在服务端创建");
        AABB bounds = boundsAtOrigin(rootRotation).move(groundAnchor);
        if (!canPlace(bounds)) return null;
        VehicleCore vehicle = new VehicleCore(level, createInstanceData(groundAnchor, rootRotation, bounds), restoreMode);
        ObjectManager.addVehicle(vehicle);
        return vehicle;
    }

    private Vector3f placePosition(Vector3f position, Matrix3f axes, Vec3 rootPosition) {
        Vector3f local = axes.mult(position.subtract(sourceRootPosition), null);
        return local.set((float) (rootPosition.x + local.x), (float) (rootPosition.y + local.y),
                (float) (rootPosition.z + local.z));
    }

    private Transform placeSubPart(PosRotVelVel pose, Quaternion rotation, Matrix3f axes, Vec3 rootPosition) {
        return new Transform(placePosition(pose.position(), axes, rootPosition),
                rotation.mult(SparkMathKt.toBQuaternion(pose.rotation())));
    }

    private VehicleData createInstanceData(Vec3 groundAnchor, Quaternion rootRotation, AABB bounds) {
        Quaternion rotation = rootRotation.mult(sourceToRoot);
        Matrix3f axes = rotation.toRotationMatrix();
        Vec3 rootPosition = placedRootPosition(groundAnchor, rootRotation);
        Map<String, RegularPartData> parts = LinkedHashMap.newLinkedHashMap(source.parts.size());
        for (var partEntry : source.parts.entrySet()) {
            RegularPartData part = partEntry.getValue();
            Map<String, SubPartData> subParts = HashMap.newHashMap(part.subParts.size());
            for (var subEntry : part.subParts.entrySet()) {
                SubPartData subPart = subEntry.getValue();
                PosRotVelVel pose = subPart.posRotVelVel;
                Transform placed = placeSubPart(pose, rotation, axes, rootPosition);
                // 姿态和速度使用同一次整体旋转；只有位置需要平移。
                PosRotVelVel placedPose = new PosRotVelVel(placed.getTranslation(),
                        SparkMathKt.toQuaternionf(placed.getRotation()),
                        axes.mult(pose.linearVel(), null), axes.mult(pose.angularVel(), null));
                subParts.put(subEntry.getKey(), new SubPartData(subPart.id, subPart.durabilityRatio, placedPose,
                        new HashMap<>(subPart.connectorData), new HashMap<>(subPart.subsystemData)));
            }
            parts.put(partEntry.getKey(), new RegularPartData(part.registryKey, part.name, part.uuid, part.variant,
                    part.assemblingProgress, part.sharedDurabilityRatio, part.materialAssemblingProgress,
                    part.assembling, part.destroyed, part.destroyTime, part.textureName, subParts, part.staleness));
        }
        Vec3 pos = SparkMathKt.toVec3(placePosition(PhysicsHelperKt.toBVector3f(source.pos), axes, rootPosition));
        return new VehicleData(source.name, UUID.randomUUID(), pos, bounds.getMinPosition().subtract(pos),
                bounds.getMaxPosition().subtract(pos), source.hpRatio, parts, source.connections, source.trackLinks, source.meta);
    }
}
