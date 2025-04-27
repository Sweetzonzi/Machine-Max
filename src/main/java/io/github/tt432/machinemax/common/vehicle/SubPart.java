package io.github.tt432.machinemax.common.vehicle;

import cn.solarmoon.spark_core.event.NeedsCollisionEvent;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.SparkMathKt;
import cn.solarmoon.spark_core.physics.collision.CollisionCallback;
import cn.solarmoon.spark_core.physics.collision.PhysicsCollisionObjectTicker;
import cn.solarmoon.spark_core.physics.host.PhysicsHost;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.vehicle.attr.SubPartAttr;
import io.github.tt432.machinemax.common.vehicle.connector.AbstractConnector;
import io.github.tt432.machinemax.util.MMMath;
import io.github.tt432.machinemax.util.ShapeHelper;
import io.github.tt432.machinemax.util.formula.Dynamic;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;

@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME)
public class SubPart implements PhysicsHost, CollisionCallback, PhysicsCollisionObjectTicker {
    public final Part part;
    public String name;
    public final SubPartAttr attr;
    public SubPart parent;
    public Transform massCenterTransform = new Transform();
    public final HashMap<String, AbstractConnector> connectors = HashMap.newHashMap(1);
    public final PhysicsRigidBody body;
    public final CompoundCollisionShape collisionShape;
    public final boolean GROUND_COLLISION_ONLY;//是否仅和零件之下的地面方块碰撞
    public final float stepHeight;
    //流体动力相关参数
    private final boolean ENABLE_FLUID_DYNAMIC_SWEEP_TEST = false;//TODO:true时，检测流体遮挡效果时将使用球形扫掠而非射线检测
    public Vec3 projectedArea;

    public SubPart(String name, Part part, SubPartAttr attr) {
        this.part = part;
        this.name = name;
        this.attr = attr;
        this.collisionShape = attr.getCollisionShape(part.variant, part.type);
        this.body = new PhysicsRigidBody(name, this, this.collisionShape, attr.mass);
        if (attr.blockCollision.equals("true") || attr.blockCollision.equals("True"))
            GROUND_COLLISION_ONLY = false;
        else if (attr.blockCollision.equals("ground") || attr.blockCollision.equals("Ground"))
            GROUND_COLLISION_ONLY = true;
        else {
            GROUND_COLLISION_ONLY = false;
            body.removeCollideWithGroup(PhysicsCollisionObject.COLLISION_GROUP_03);
        }
        this.stepHeight = attr.stepHeight;
        this.projectedArea = attr.projectedArea;
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
                    body.setFriction(2.5f);
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
        if (partBody.getOwner() instanceof SubPart subPart && subPart.GROUND_COLLISION_ONLY) {//仅与地面方块碰撞的零件遭遇方块时
            float terrainHeight = terrain.boundingBox(null).getMax(null).y;
            float y0 = ShapeHelper.getShapeMinY(partBody, 0.1f) + 0.1f;//计算部件最低点高度
            if (terrainHeight <= y0) {
                //若地形方块位于部件最低位置以下，则不修改碰撞检测结果
            } else {
                float height = terrainHeight - y0;//部件最低点与地形的高度差
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
        Vector3f vel = this.body.getLinearVelocity(null);
        Vector3f localVel = MMMath.relPointLocalVel(PhysicsHelperKt.toBVector3f(attr.aeroDynamic.center()), this.body);
        Vector3f pos = MMMath.relPointWorldPos(PhysicsHelperKt.toBVector3f(attr.aeroDynamic.center()), this.body);
        //仅在有速度时应用流体动力
        if (vel.length() > 0.1f) {
            //遮挡关系处理
            float xOcclusion = 1;//遮挡系数，1为无遮挡，应用全部流体动力；0为完全被遮挡，不应用流体动力
            float yOcclusion = 1;
            float zOcclusion = 1;
            if (Math.abs(localVel.x) > 0.1f && attr.aeroDynamic.effectiveRange().x > 0) {
                Vector3f target = pos.add(MMMath.localVectorToWorldVector(new Vector3f(Math.signum(localVel.x), 0, 0), this.body).mult((float) attr.aeroDynamic.effectiveRange().x));
                if (!target.equals(pos)) {
                    List<PhysicsRayTestResult> result = getPhysicsLevel().getWorld().rayTest(pos, target);
                    for (PhysicsRayTestResult ray : result) {
                        var hit = ray.getCollisionObject();
                        if (hit == this.body || !(hit.getOwner() instanceof SubPart)) continue;
                        if ((hit.getOwner() instanceof SubPart sp && sp.part.vehicle == this.part.vehicle)) {
                            if (sp.attr.aeroDynamic.priority() > this.attr.aeroDynamic.priority()) {
                                float tempOcclusion = ray.getHitFraction();//距离越近，遮挡效果越大
                                if (tempOcclusion < xOcclusion) xOcclusion = Math.max(0, tempOcclusion);
                                if (xOcclusion <= 0) break;
                            }
                        }
                    }
                }
            }
            if (Math.abs(localVel.y) > 0.1f && attr.aeroDynamic.effectiveRange().y > 0) {
                Vector3f target = pos.add(MMMath.localVectorToWorldVector(new Vector3f(0, Math.signum(localVel.y), 0), this.body).mult((float) attr.aeroDynamic.effectiveRange().y));
                if (!target.equals(pos)) {
                    List<PhysicsRayTestResult> result = getPhysicsLevel().getWorld().rayTest(pos, target);
                    for (PhysicsRayTestResult ray : result) {
                        var hit = ray.getCollisionObject();
                        if (hit == this.body || !(hit.getOwner() instanceof SubPart)) continue;
                        if ((hit.getOwner() instanceof SubPart sp && sp.part.vehicle == this.part.vehicle)) {
                            if (sp.attr.aeroDynamic.priority() > this.attr.aeroDynamic.priority()) {
                                float tempOcclusion = ray.getHitFraction();//距离越近，遮挡效果越大
                                if (tempOcclusion < yOcclusion) yOcclusion = Math.max(0, tempOcclusion);
                                if (yOcclusion <= 0) break;
                            }
                        }
                    }
                }

            }
            if (Math.abs(localVel.z) > 0.1f && attr.aeroDynamic.effectiveRange().z > 0) {
                Vector3f target = pos.add(MMMath.localVectorToWorldVector(new Vector3f(0, 0, Math.signum(localVel.z)), this.body).mult((float) attr.aeroDynamic.effectiveRange().z));
                if (!target.equals(pos)) {
                    List<PhysicsRayTestResult> result = getPhysicsLevel().getWorld().rayTest(pos, target);
                    for (PhysicsRayTestResult ray : result) {
                        var hit = ray.getCollisionObject();
                        if (hit == this.body || !(hit.getOwner() instanceof SubPart)) continue;
                        if ((hit.getOwner() instanceof SubPart sp && sp.part.vehicle == this.part.vehicle)) {
                            if (sp.attr.aeroDynamic.priority() > this.attr.aeroDynamic.priority()) {
                                float tempOcclusion = ray.getHitFraction();//距离越近，遮挡效果越大
                                if (tempOcclusion < zOcclusion) zOcclusion = Math.max(0, tempOcclusion);
                                if (zOcclusion <= 0) break;
                            }
                        }
                    }
                }
            }
            //流体动力计算
            Vector3f localAeroForce = Dynamic.aeroDynamicForce(
                    1.29f,//kg/m^3 流体密度
                    this.projectedArea,
                    attr.aeroDynamic,
                    localVel).mult(xOcclusion, yOcclusion, zOcclusion);
            this.body.applyForce(//应用流体动力
                    MMMath.localVectorToWorldVector(localAeroForce, this.body),
                    MMMath.localVectorToWorldVector(PhysicsHelperKt.toBVector3f(attr.aeroDynamic.center()), this.body));
        }
        //攀爬辅助处理
        if (this.GROUND_COLLISION_ONLY && stepHeight > 0 && attr.climbAssist) {
            float y0 = ShapeHelper.getShapeMinY(this.body, 0.1f);
            Vector3f start = this.body.getPhysicsLocation(null);
            start.set(1, y0 - 1);
            var end = start.add(0f, 1 + stepHeight, 0f);
            if (start.equals(end)) {
                MachineMax.LOGGER.error("Same start and end position for climb assist ray test, canceling climb assist.");
                //TODO:治标不治本，需要排查原因
                return;
            }
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
                var horizonVel = Math.sqrt(vel.x * vel.x + vel.z * vel.z);//根据水平速度决定赋予的额外垂直速度
                var ang = Math.atan2(height, 1);
                float extraVel = (float) Math.max(Math.sin(ang) * horizonVel, 1.75f);
                float horizontalVelScale = (float) Math.cos(ang);
                body.setLinearVelocity(new Vector3f(horizontalVelScale * vel.x, (float) (0.7 * vel.y + extraVel), horizontalVelScale * vel.z));
            }
        }
    }

    public void mcTick(@NotNull PhysicsCollisionObject physicsCollisionObject, @NotNull Level level) {

    }
}
