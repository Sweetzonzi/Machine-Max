package io.github.sweetzonzi.machine_max.common.mech.ray;

import cn.solarmoon.spark_core.physics.PhysicsHost;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.physics.terrain.PhysicsChunkSection;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.HitBox;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.InteractBox;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.InteractBoxes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 公用射线解析核心：把已经执行的物理射线结果按 owner 解析成不同命中类型，
 * 完成子零件有效性过滤并准备各类型需要的数据。
 *
 * <p>射线本身由调用方选择实际世界或只读快照发射；本类只读 {@code rawHits}，
 * 不发射射线、不应用观察者排除、不写投射物字段、不触发伤害。</p>
 *
 * <p>每条命中都带世界坐标命中点与世界空间法线；目标身份由具体结果类型给出。
 * SubPart 的可拾取判定走 {@link SubPart#findPickableHitBox(int)}，交互判定区映射走
 * {@link InteractBoxes#findInteractBox(int)}；两者都严格校验子形状索引，不退回首个元素。</p>
 *
 * <p>{@code rawHits} 不包含完整射线起终点，只有沿本次射线的命中比例，因此
 * {@code from/to} 必须与这份列表来自同一次 rayTest。</p>
 *
 * <p>线程：在查询发起方所在线程执行，只读输入列表。</p>
 */
public final class RayHitResolver {

    private RayHitResolver() {
    }

    /**
     * 解析一份物理射线结果。
     *
     * @param rawHits 物理引擎返回的原始命中结果，调用期间只读
     * @param from    本次射线起点（世界坐标）
     * @param to      本次射线终点（世界坐标）
     * @return 按 fraction 升序排列的不可变结果列表；没有有效结果时返回空列表
     */
    public static List<RayHit> resolve(List<PhysicsRayTestResult> rawHits, Vec3 from, Vec3 to) {
        if (rawHits == null || rawHits.isEmpty() || from == null || to == null) {
            return List.of();
        }
        if (!isFinite(from) || !isFinite(to)) return List.of();
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        if (dx == 0.0 && dy == 0.0 && dz == 0.0) return List.of();

        List<RayHit> hits = new ArrayList<>(rawHits.size());
        for (PhysicsRayTestResult result : rawHits) {
            if (result == null) continue;
            double fraction = result.getHitFraction();
            if (!Double.isFinite(fraction) || fraction < 0.0 || fraction > 1.0) continue;

            PhysicsCollisionObject object = result.getCollisionObject();
            if (object == null) continue;
            PhysicsHost owner = PhysicsBodyExtensionKt.getOwner(object);
            if (owner == null) continue;

            Vec3 point = pointAt(from, dx, dy, dz, fraction);
            Vec3 normal = normalOf(result);
            if (point == null || normal == null) continue;

            if (owner instanceof SubPart subPart) {
                HitBox hitBox = subPart.findPickableHitBox(result.triangleIndex());
                // 无效形状直接跳过，不转成 OtherRayHit 绕过过滤
                if (hitBox == null) continue;
                hits.add(new SubPartRayHit(fraction, subPart, hitBox, point, normal));
            } else if (owner instanceof AbstractConnector connector) {
                hits.add(new ConnectorRayHit(fraction, connector, point, normal));
            } else if (owner instanceof InteractBoxes interactBoxes) {
                InteractBox interactBox = interactBoxes.findInteractBox(result.triangleIndex());
                if (interactBox == null) continue;
                hits.add(new InteractBoxRayHit(fraction, interactBox, interactBox.isEnabled(), point, normal));
            } else if (owner instanceof PhysicsChunkSection section) {
                hits.add(new TerrainRayHit(fraction, section, point, normal));
            } else if (owner instanceof Entity entity) {
                hits.add(new EntityRayHit(fraction, entity, point, normal));
            } else {
                hits.add(new OtherRayHit(fraction, owner, point, normal));
            }
        }
        if (hits.isEmpty()) return List.of();
        hits.sort(Comparator.comparingDouble(RayHit::fraction));
        return List.copyOf(hits);
    }

    /**
     * 按 fraction 计算世界坐标命中点。
     */
    @Nullable
    private static Vec3 pointAt(Vec3 from, double dx, double dy, double dz, double fraction) {
        double x = from.x + dx * fraction;
        double y = from.y + dy * fraction;
        double z = from.z + dz * fraction;
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return null;
        return new Vec3(x, y, z);
    }

    /**
     * 复制引擎结果中的世界空间法线。
     */
    @Nullable
    private static Vec3 normalOf(PhysicsRayTestResult result) {
        Vector3f normal = result.getHitNormalLocal(null);
        if (normal == null) return null;
        if (!Float.isFinite(normal.x) || !Float.isFinite(normal.y) || !Float.isFinite(normal.z)) return null;
        return new Vec3(normal.x, normal.y, normal.z);
    }

    private static boolean isFinite(Vec3 vec) {
        return Double.isFinite(vec.x) && Double.isFinite(vec.y) && Double.isFinite(vec.z);
    }
}
