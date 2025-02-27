package io.github.tt432.machinemax.common.vehicle;

import cn.solarmoon.spark_core.physics.collision.BodyPhysicsTicker;
import cn.solarmoon.spark_core.physics.collision.CollisionCallback;
import cn.solarmoon.spark_core.physics.host.PhysicsHost;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Transform;
import io.github.tt432.machinemax.common.vehicle.attr.SubPartAttr;
import io.github.tt432.machinemax.common.vehicle.connector.AbstractConnector;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class SubPart implements PhysicsHost, CollisionCallback, BodyPhysicsTicker {
    public String name;
    public final Part part;
    public SubPart parent;
    public Transform massCenterTransform = new Transform();
    public final HashMap<String, AbstractConnector> connectors = HashMap.newHashMap(1);
    public final PhysicsRigidBody body;
    //TODO:建Shape在SubPartAttr中进行，节约内存
    CompoundCollisionShape collisionShape = new CompoundCollisionShape(1);
    public final Map<Integer, String> materials = new HashMap<>(2);//碰撞体积各部分的材料类型
    public final Map<Integer, Float> thicknesses = new HashMap<>(2);//碰撞体积各部分的厚度
    public final Map<Integer, Float> frictionCoeffs = new HashMap<>(2);//碰撞体积各部分的粗糙度修正系数

    public SubPart(String name, Part part, SubPartAttr attr) {
        this.part = part;
        this.name = name;
        this.body = new PhysicsRigidBody(name, this, collisionShape, attr.mass());
    }

    public void addToLevel() {
        this.bindBody(body, part.level.getPhysicsLevel(), true,
                (body -> {
                    body.addCollisionCallback(this);//TODO:写接触规则
                    body.addPhysicsTicker(this);
                    body.activate();
                    return null;
                }));
    }

    public void destroy() {
        for (AbstractConnector connector : connectors.values()) {
            connector.destroy();
        }
        if (body.isInWorld()) this.removeAllBodies();
    }

    @NotNull
    @Override
    public PhysicsLevel getPhysicsLevel() {
        return part.level.getPhysicsLevel();
    }

    /**
     * Invoked immediately after a contact manifold is removed.
     *
     * @param manifoldId the native ID of the {@code btPersistentManifold} (not
     *                   zero)
     */
    @Override
    public void onEnded(PhysicsCollisionObject pcoA, PhysicsCollisionObject pcoB, long manifoldId) {
        this.part.vehicle.activate();
    }

    /**
     * Invoked immediately after a contact point is refreshed without being
     * removed. Skipped for Sphere-Sphere contacts.
     *
     * @param pcoA            the first involved object (not null)
     * @param pcoB            the 2nd involved object (not null)
     * @param manifoldPointId the native ID of the {@code btManifoldPoint} (not
     *                        zero)
     */
    @Override
    public void onProcessed(PhysicsCollisionObject pcoA, PhysicsCollisionObject pcoB, long manifoldPointId) {

    }

    /**
     * Invoked immediately after a contact manifold is created.
     *
     * @param manifoldId the native ID of the {@code btPersistentManifold} (not
     *                   zero)
     */
    @Override
    public void onStarted(PhysicsCollisionObject pcoA, PhysicsCollisionObject pcoB,long manifoldId) {

    }

    @Override
    public void physicsTick(@NotNull PhysicsCollisionObject pco, @NotNull PhysicsLevel physicsLevel) {

    }

    @Override
    public void mcTick(@NotNull PhysicsCollisionObject physicsCollisionObject, @NotNull Level level) {

    }
}
