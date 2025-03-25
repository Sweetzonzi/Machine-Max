package io.github.tt432.machinemax.common.vehicle;

import cn.solarmoon.spark_core.event.NeedsCollisionEvent;
import cn.solarmoon.spark_core.physics.collision.CollisionCallback;
import cn.solarmoon.spark_core.physics.collision.PhysicsCollisionObjectTicker;
import cn.solarmoon.spark_core.physics.host.PhysicsHost;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.vehicle.attr.SubPartAttr;
import io.github.tt432.machinemax.common.vehicle.connector.AbstractConnector;
import io.github.tt432.machinemax.util.ShapeHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME)
public class SubPart implements PhysicsHost, CollisionCallback, PhysicsCollisionObjectTicker {
    public final Part part;
    public String name;
    public final SubPartAttr attr;
    public SubPart parent;
    public Transform massCenterTransform = new Transform();
    public final HashMap<String, AbstractConnector> connectors = HashMap.newHashMap(1);
    public final PhysicsRigidBody body;
    //TODO:建Shape在SubPartAttr中进行，节约内存
    CompoundCollisionShape collisionShape = new CompoundCollisionShape(1);
    public final Map<Integer, String> materials = new HashMap<>(2);//碰撞体积各部分的材料类型
    public final Map<Integer, Float> thicknesses = new HashMap<>(2);//碰撞体积各部分的厚度
    public final Map<Integer, Float> frictionCoeffs = new HashMap<>(2);//碰撞体积各部分的粗糙度修正系数
    public final boolean GROUND_COLLISION;
    public final float stepHeight;

    public SubPart(String name, Part part, SubPartAttr attr) {
        this.part = part;
        this.name = name;
        this.attr = attr;
        this.body = new PhysicsRigidBody(name, this, collisionShape, attr.mass());
        if (attr.blockCollision().equals("true")) GROUND_COLLISION = false;
        else if (attr.blockCollision().equals("ground")) GROUND_COLLISION = true;
        else {
            GROUND_COLLISION = false;
            body.removeCollideWithGroup(PhysicsCollisionObject.COLLISION_GROUP_03);
        }
        this.stepHeight = attr.stepHeight();
    }

    public void addToLevel() {
        this.bindBody(body, part.level.getPhysicsLevel(), true,
                (body -> {
                    body.addCollisionCallback(this);//TODO:写接触规则
                    body.addPhysicsTicker(this);
                    body.setProtectGravity(true);
                    body.setSleepingThresholds(0.05f, 0.05f);
                    body.setDamping(0.01f, 0.01f);
                    body.setRestitution(0.2f);
                    body.setFriction(2f);
                    body.setRollingFriction(0.2f);
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
        if (pcoA.getOwner() instanceof SubPart subPartA && pcoB.getOwner() instanceof SubPart subPartB) {

        }
    }

    /**
     * Invoked immediately after a contact manifold is created.
     *
     * @param manifoldId the native ID of the {@code btPersistentManifold} (not
     *                   zero)
     */
    @Override
    public void onStarted(PhysicsCollisionObject pcoA, PhysicsCollisionObject pcoB, long manifoldId) {

    }

    @SubscribeEvent
    public static void onPreCollision(NeedsCollisionEvent event) {
        //同载具部件不发生碰撞
        if (event.getPcoA().getOwner() instanceof SubPart subPartA && event.getPcoB().getOwner() instanceof SubPart subPartB) {
            if (subPartA.part.vehicle instanceof VehicleCore vehicleA && subPartB.part.vehicle instanceof VehicleCore vehicleB) {
                if (vehicleA == vehicleB) {
                    event.setShouldCollide(false);
                    return;
                }
            }
        }
        //特殊碰撞模式的处理
        PhysicsRigidBody terrain;
        PhysicsRigidBody partBody;
        if (event.getPcoA() instanceof PhysicsRigidBody pcoA && event.getPcoB() instanceof PhysicsRigidBody pcoB) {
            if (pcoA.getOwner() instanceof SubPart && pcoB.name.equals("terrain")) {
                terrain = pcoB;
                partBody = pcoA;
            } else if (pcoB.getOwner() instanceof SubPart && pcoA.name.equals("terrain")) {
                terrain = pcoA;
                partBody = pcoB;
            } else return;
        } else return;
        if (partBody.getOwner() instanceof SubPart subPart && subPart.GROUND_COLLISION) {//仅与地面方块碰撞的零件遭遇方块时
            float terrainHeight = terrain.boundingBox(null).getMax(null).y;
            if (terrainHeight < ShapeHelper.getShapeMinY(partBody, 0.1f) + 0.1f) {
                //若地形方块位于车轮之下，则不修改碰撞检测结果
            } else {
                float y0 = ShapeHelper.getShapeMinY(partBody, 0.1f) + 0.1f;
                float height = terrainHeight - y0;
                BlockPos highestBlockPos = terrain.blockPos.above();
                while (height <= subPart.stepHeight && subPart.getPhysicsLevel().getTerrainBlockBodies().containsKey(highestBlockPos)) {
                    height = subPart.getPhysicsLevel().getTerrainBlockBodies().get(highestBlockPos).boundingBox(null).getMax(null).y - y0;
                    highestBlockPos = highestBlockPos.above();
                }
                if (height <= subPart.stepHeight) {
                    event.setShouldCollide(false);
                }
            }
        }
    }

    @Override
    public void prePhysicsTick(@NotNull PhysicsCollisionObject pco, @NotNull PhysicsLevel physicsLevel) {
        //TODO:阻力处理
        if (this.GROUND_COLLISION && stepHeight > 0) {//攀爬辅助处理
            float y0 = ShapeHelper.getShapeMinY(this.body, 0.1f);
            Vector3f start = this.body.getPhysicsLocation(null);
            start.set(1, y0 - 1);
            var end = start.add(0f, 1 + stepHeight, 0f);
            var test = getPhysicsLevel().getWorld().rayTest(start, end);
            PhysicsRigidBody terrainsUnder = null;//清空先前记录的地面碰撞体
            float height = -1;
            for (var hit : test) {//寻找部件下最低的方块
                if (hit.getCollisionObject() instanceof PhysicsRigidBody terrain && terrain.name.equals("terrain")) {
                    terrainsUnder = terrain;
                    height = Math.max(terrain.boundingBox(null).getMax(null).y - y0, height);
                    break;
                }
            }
            if (terrainsUnder != null) {//若接地/轮子质心竖直投影方向有方块，寻找投影方向连续方块的最高点
                BlockPos highestBlockPos = terrainsUnder.blockPos.above();
                while (height <= stepHeight && getPhysicsLevel().getTerrainBlockBodies().containsKey(highestBlockPos)) {
                    height = Math.max(getPhysicsLevel().getTerrainBlockBodies().get(highestBlockPos).boundingBox(null).getMax(null).y - y0, height);
                    highestBlockPos = highestBlockPos.above();
                }
            }
            if (height > 0 && height <= stepHeight) {//若最高点小于容许高度，则额外为车轮赋予速度
                var vel = this.body.getLinearVelocity(null);
                var horizonVel = Math.sqrt(vel.x * vel.x + vel.z * vel.z);//根据水平速度决定赋予的额外垂直速度
                var ang = Math.atan2(height, 1);
                float extraVel = (float) Math.max(Math.sin(ang) * horizonVel, 1.5);
                body.setLinearVelocity(new Vector3f(vel.x, vel.y + extraVel, vel.z));
            }
        }
    }

    @Override
    public void mcTick(@NotNull PhysicsCollisionObject physicsCollisionObject, @NotNull Level level) {

    }
}
