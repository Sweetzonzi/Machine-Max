package io.github.sweetzonzi.machine_max.common.mech.vehicle.interact;

import cn.solarmoon.spark_core.physics.PhysicsHost;
import cn.solarmoon.spark_core.physics.body.CollisionGroups;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.util.PPhase;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.bullet.collision.shapes.infos.ChildCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.InteractBoxAttr;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class InteractBoxes extends ConcurrentHashMap<String, InteractBox> implements PhysicsHost {

    public final SubPart subPart;
    public final CompoundCollisionShape interactBoxShape;
    @Nullable
    public volatile PhysicsRigidBody body;
    private final Map<String, PhysicsCollisionObject> allPhysicsBodies = new HashMap<>();

    public InteractBoxes(SubPart subPart, Map<String, InteractBoxAttr> boxes, CompoundCollisionShape interactBoxShape) {
        this.subPart = subPart;
        this.interactBoxShape = interactBoxShape;
        for (Map.Entry<String, InteractBoxAttr> entry : boxes.entrySet()) {
            String name = entry.getKey();
            InteractBox interactBox = new InteractBox(subPart, name, entry.getValue());
            this.put(name, interactBox);
        }
    }

    /** 物理线程。在所属子零件入世后创建交互刚体，并以当前物理位姿放置。 */
    public void addToLevel() {
        if (body != null || subPart.isRemoved() || !subPart.body.isInWorld()) return;
        var point = new PhysicsRigidBody(interactBoxShape, 0);
        PhysicsBodyExtensionKt.setOwner(point, this);
        point.setContactResponse(false);
        point.setKinematic(true); // 保持运动学模式，供射线检测选取交互区。
        point.setCollisionGroup(CollisionGroups.TRIGGER);
        point.setCollideWithGroups(CollisionGroups.NONE);
        point.shouldShowDebugBoxWhenNonColldeWith = true;
        point.setPhysicsLocation(subPart.body.getPhysicsLocation(null));
        point.setPhysicsRotation(subPart.body.getPhysicsRotation(null));
        getPhysicsLevel().getWorld().addCollisionObject(point);
        body = point;
    }

    /**
     * 更新交互判定区的位置和姿态，使其与零件统一。
     * <p>
     * 取<b>子零件的刚体</b>（{@code subPart.body}）：客户端那个刚体是运动学镜像，由
     * {@link io.github.sweetzonzi.machine_max.common.mech.DestroyableRigidObject#onPresentationPoseAdvanced()}
     * 每客户端 tick 写成呈现位姿，与模型同一条时间轴；服务端则就是模拟中的刚体本身。
     * 若改取 {@code getPosition()}（网络真值），客户端的判定区会抢在模型前面一至两个 tick
     * （车在走、判定区已经到位）。
     * <p>
     * <b>调用方必须每 tick 调一次、且早于 {@code PhysicsLevel#requestStep}</b>：这个刚体的位姿由
     * Spark 的 {@code stateOf(...)} 在那次请求里快照一次，调试框读的就是快照。晚于快照写，
     * 判定区就会永久晚一 tick；只在同步包到达时写，则会连上「按包更新」的抖动。
     * 见 {@code SubPart#preTick()}。
     */
    public void updatePose() {
        var point = body;
        if (point == null) return;
        point.setPhysicsLocation(subPart.body.getPhysicsLocation(null));
        point.setPhysicsRotation(subPart.body.getPhysicsRotation(null));
    }

    public InteractBox getInteractBox(long childShapeId) {
        String name = this.subPart.attr.interactBoxNames.get(childShapeId);
        return this.get(name);
    }

    public InteractBox getInteractBox(int contactPointIndex) {
        try {
            long childShapeId = this.interactBoxShape.listChildren()[contactPointIndex].getShape().nativeId();
            return getInteractBox(childShapeId);
        } catch (IndexOutOfBoundsException e) {
            MachineMax.LOGGER.error("No matching child shape of interact box {}-{} found for contact point id: {}", subPart.part.name, subPart.name, contactPointIndex);
            return this.values().iterator().next();
        }
    }

    /**
     * 按子形状索引查找交互判定区。
     *
     * <p>严格按索引映射，不退回首个元素：索引越界或该形状未登记交互判定区时返回
     * {@code null}。启用状态由调用方从返回的交互判定区读取。</p>
     *
     * @param childIndex 子形状索引（物理射线结果的 {@code triangleIndex}）
     * @return 对应的交互判定区；映射无效时返回 null
     */
    @Nullable
    public InteractBox findInteractBox(int childIndex) {
        if (childIndex < 0) return null;
        ChildCollisionShape[] children = interactBoxShape.listChildren();
        if (childIndex >= children.length) return null;
        return getInteractBox(children[childIndex].getShape().nativeId());
    }

    /** 主线程。清理信号容器，交由物理线程移除已建立的刚体。 */
    public void destroy() {
        this.clear();
        getPhysicsLevel().submitImmediateTask(PPhase.ALL, () -> {
            var point = body;
            if (point == null) return null;
            body = null;
            PhysicsBodyExtensionKt.setOwner(point, null);
            if (point.isInWorld()) getPhysicsLevel().getWorld().removeCollisionObject(point);
            return null;
        });
    }

    @NotNull
    @Override
    public PhysicsLevel getPhysicsLevel() {
        return subPart.getPhysicsLevel();
    }
}
