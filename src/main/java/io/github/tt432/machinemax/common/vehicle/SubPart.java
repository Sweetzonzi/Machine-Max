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
import io.github.tt432.machinemax.common.vehicle.attr.SubPartAttr;
import io.github.tt432.machinemax.common.vehicle.connector.AbstractConnector;
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
    public final boolean isWheel;
    public final float wheelRadius;
    public final float stepHeight;
    public PhysicsRigidBody terrainsUnder;

    public SubPart(String name, Part part, SubPartAttr attr) {
        this.part = part;
        this.name = name;
        this.attr = attr;
        this.body = new PhysicsRigidBody(name, this, collisionShape, attr.mass());
        this.wheelRadius = attr.radius();
        this.stepHeight = attr.stepHeight();
        this.isWheel = attr.radius() > 0;
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
        //轮胎零件的爬坡辅助功能
        PhysicsRigidBody terrain;
        PhysicsRigidBody wheel;
        if (event.getPcoA() instanceof PhysicsRigidBody pcoA && event.getPcoB() instanceof PhysicsRigidBody pcoB) {
            if (pcoA.getOwner() instanceof SubPart && pcoB.name.equals("terrain")) {
                terrain = pcoB;
                wheel = pcoA;
            } else if (pcoB.getOwner() instanceof SubPart && pcoA.name.equals("terrain")) {
                terrain = pcoA;
                wheel = pcoB;
            } else return;
        } else return;
        if (wheel.getOwner() instanceof SubPart subPart && subPart.isWheel) {//轮胎零件遭遇地形方块时
            float terrainHeight = terrain.boundingBox(null).getMax(null).y;
            if (terrainHeight < subPart.body.getPhysicsLocation(null).y - 0.95 * subPart.wheelRadius) {
                //若地形方块位于车轮之下，则不修改碰撞检测结果
            } else {
                float y0 = subPart.body.getPhysicsLocation(null).y - subPart.wheelRadius;
                float height = terrain.boundingBox(null).getMax(null).y - y0;
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
    public void physicsTick(@NotNull PhysicsCollisionObject pco, @NotNull PhysicsLevel physicsLevel) {
        //TODO:阻力处理
        if (this.isWheel) {//轮胎攀爬辅助处理
            var start = this.body.getPhysicsLocation(null).subtract(0f, 1 + this.wheelRadius, 0f);
            var end = start.add(0f, 1 + stepHeight, 0f);
            var test = getPhysicsLevel().getWorld().rayTest(start, end);
            terrainsUnder = null;//清空先前记录的地面碰撞体
            float y0 = this.body.getPhysicsLocation(null).y - this.wheelRadius;
            float height = -1;
            for (var hit : test) {//寻找轮子下最低的方块
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
                //不知为何，施加力量的效果无效
//                float impulse = -0.02f * getPhysicsLevel().getWorld().getGravity(null).y * height * part.vehicle.totalMass;
//                body.applyCentralImpulse(new Vector3f(0, impulse, 0));
                var vel = this.body.getLinearVelocity(null);
                var horizonVel = Math.sqrt(vel.x * vel.x + vel.z * vel.z);//根据水平速度决定赋予的额外垂直速度
                var ang = Math.atan2(height, 1);
                float extraVel = (float) Math.max(Math.sin(ang)*horizonVel, 1.5);
                body.setLinearVelocity(new Vector3f(vel.x, vel.y + extraVel, vel.z));
            }
        }
    }

    @Override
    public void mcTick(@NotNull PhysicsCollisionObject physicsCollisionObject, @NotNull Level level) {

    }
}
