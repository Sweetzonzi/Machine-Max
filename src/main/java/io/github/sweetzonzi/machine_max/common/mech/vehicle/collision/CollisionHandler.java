package io.github.sweetzonzi.machine_max.common.mech.vehicle.collision;

import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.PhysicsHost;
import cn.solarmoon.spark_core.physics.body.CollisionGroups;
import cn.solarmoon.spark_core.physics.body.CollisionObjectEntity;
import cn.solarmoon.spark_core.physics.body.ManifoldPoint;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.physics.terrain.PhysicsChunkSection;
import cn.solarmoon.spark_core.physics.terrain.SectionSnapshot;
import cn.solarmoon.spark_core.util.BlockCollisionUtil;
import cn.solarmoon.spark_core.util.PPhase;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.bullet.collision.*;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Matrix3f;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.compat.create.CreateCollisionResolver;
import io.github.sweetzonzi.machine_max.compat.create.CreateCompat;
import io.github.sweetzonzi.machine_max.common.MMServerConfig;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.registry.MMDamageTypes;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.MaterialAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.MMDamageExtensions;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.HitBox;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageApi;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageContext;
import io.github.sweetzonzi.machine_max.util.MMMath;
import io.github.sweetzonzi.machine_max.util.mechanic.DamageUtil;
import io.github.sweetzonzi.machine_max.util.mechanic.KnockbackUtil;
import io.github.sweetzonzi.machine_max.util.mechanic.MassUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import static io.github.sweetzonzi.machine_max.util.mechanic.DynamicUtil.calculateSlipScale;

/**
 * 碰撞处理器
 * <p>承载原先分散在 SubPart 中的碰撞物理逻辑，包括预碰撞判定、摩擦系数计算、
 * 方块破坏、部件伤害、实体击退等。与 CollisionEffectManager 平级协作，
 * 碰撞物理计算完成后将效果数据通过 effectManager 记录，供主线程消费。</p>
 *
 * <p>线程模型：所有方法均在物理线程调用。</p>
 */
public class CollisionHandler {

    private final SubPart subPart;
    public final CollisionEffectManager effectManager;

    /**
     * 承载面判据：地形指向零件的法线竖直分量不低于该值即视为能承担重量（约 45°）。
     *
     * <p>见 {@link #isSupportingTerrainContact(Vector3f)}。</p>
     */
    private static final float CLIMB_SUPPORT_NORMAL_Y = 0.7f;

    /**
     * 地面撞击判据：接触法线的竖直分量不低于该值（约 18°）时，撞击接近速度改取**刚体原点**的
     * 竖直分速，而不是接触点速度。见 {@link CollisionImpactModel#verticalApproachSpeed}。
     *
     * <p>实测依据：地形 section 交界与合并盒棱边上的接触拿到的是棱边角平分线，倾斜 5.2°–9.6°，
     * 上面挂着的轮子刚体是平动的（竖直分速 0.00–0.02 m/s），但接触点速度的竖直分量有 1.5–2.5 m/s
     * ——全部由轮胎滚动 {@code ω × r} 贡献。按接触点速度判，这些点会被读成 2–3 m/s 的撞击；
     * 按刚体分速判则是 0。整车真落地的点刚体竖直分速在 4 m/s 以上，两种口径都给过门。</p>
     *
     * <p>改口径还不够：悬挂对轮子的下压加速度（K17 合计约 260 m/s²）在一个 10 ms 子步里就是 2.6 m/s，
     * 轮子丢掉一个子步支撑再回来时，刚体竖直分速本身就压着这个量级。所以轮胎材质的
     * {@code impact_min_speed} 取 5.0 m/s，理由与实测见计划文档 §2.3。</p>
     *
     * <p>上限压到 18° 而不是 45°：30°–45° 的坡面按接触点速度判才对——车是斜着撞上去的，
     * 竖直分速本来就接近 0。</p>
     */
    private static final float GROUND_IMPACT_NORMAL_Y = 0.95f;

    //方向向量缓存，避免每 tick 分配
    private final Vector3f tmpFront = new Vector3f();
    private final Vector3f tmpSide = new Vector3f();

    public CollisionHandler(SubPart subPart) {
        this.subPart = subPart;
        this.effectManager = new CollisionEffectManager(subPart);
    }

    // ==================== 公共入口 ====================

    /**
     * 碰撞预处理，由物理引擎在接触产生时同步回调。
     * 替代原 SubPart.onPreContact。
     * <ul>
     *   <li>忽略客户端非激活部件、无效碰撞点</li>
     *   <li>对方为方块时执行攀爬辅助预判</li>
     * </ul>
     *
     * @return false 表示跳过本次碰撞
     */
    public boolean onPreContact(PhysicsCollisionObject o1, PhysicsCollisionObject o2,
                                ManifoldPoint point1, ManifoldPoint point2,
                                long manifoldPointId) {
        Level level = subPart.getLevel();
        if (level.isClientSide() && !subPart.isActive()) return false;
        PhysicsRigidBody other = (PhysicsRigidBody) o2;
        Vector3f normal = new Vector3f();
        int hitBoxIndex = point1.getTriangleIndex();
        Vector3f worldContactPoint = new Vector3f();
        point1.getPositionWorld(worldContactPoint);
        //无效接触点（超出轮胎宽度的碰撞等）直接忽略
        if (!subPart.isEffectiveContact(hitBoxIndex, worldContactPoint, true)) {
            disableContact(manifoldPointId);
            return false;
        }
        point2.getNormalWorld(normal);
        //对方为方块时执行预碰撞判定（攀爬辅助等）
        if (other.getCollisionGroup() == CollisionGroups.TERRAIN) {
            return preProcessBlockCollision(other, normal, worldContactPoint, hitBoxIndex);
        }
        return true;
    }

    /**
     * 碰撞处理主入口，由物理引擎在碰撞约束求解后同步回调。
     * 替代原 SubPart.onContactProcessed。
     * <p>执行流程：</p>
     * <ol>
     *   <li>获取碰撞参数并验证有效性</li>
     *   <li>计算相对速度与碰撞角</li>
     *   <li>根据碰撞箱重设摩擦系数</li>
     *   <li>按碰撞类型分发到对应处理方法</li>
     * </ol>
     */
    public void onContactProcessed(PhysicsCollisionObject o1, PhysicsCollisionObject o2,
                                   ManifoldPoint point1, ManifoldPoint point2,
                                   long manifoldPointId) {
        Level level = subPart.getLevel();
        if (level.isClientSide() && !subPart.isActive()) return;
        PhysicsRigidBody other = (PhysicsRigidBody) o2;
        var otherOwner = PhysicsBodyExtensionKt.getOwner(other);

        Vector3f normal = new Vector3f();
        int hitBoxIndex = point1.getTriangleIndex();
        int otherHitBoxIndex = point2.getTriangleIndex();
        Vector3f worldContactPoint = new Vector3f();
        Vector3f localContactPoint = new Vector3f();
        Vector3f otherLocalContactPoint = new Vector3f();
        point1.getPositionWorld(worldContactPoint);
        point1.getLocalPoint(localContactPoint);
        point2.getLocalPoint(otherLocalContactPoint);
        //有效性检验
        if (!subPart.isEffectiveContact(hitBoxIndex, worldContactPoint, true)) {
            disableContact(manifoldPointId);
            return;
        }
        point2.getNormalWorld(normal);

        //计算接触点相对速度
        Vector3f contactVel = MMMath.relPointWorldVel(
                localContactPoint,
                subPart.body.getPhysicsRotation(null),
                subPart.body.getLinearVelocity(null),
                subPart.body.getAngularVelocity(null));
        if (o2 instanceof PhysicsRigidBody && !other.isStatic())
            contactVel.subtractLocal(MMMath.relPointWorldVel(otherLocalContactPoint, other));

        //计算碰撞角（法线与速度的夹角）
        float impactAngle = (float) Math.toDegrees(Math.acos(normal.dot(contactVel.normalize())));
        if (Float.isNaN(impactAngle)) impactAngle = 0;

        HitBox hitBox = subPart.getHitBox(hitBoxIndex);
        //根据实际接触部位重设摩擦系数
        setupFriction(hitBox, hitBoxIndex);

        //按碰撞对象类型分发
        if (other.getCollisionGroup() == CollisionGroups.TERRAIN) {
            processBlockCollision(other, otherOwner, normal, worldContactPoint,
                    localContactPoint, otherLocalContactPoint, contactVel,
                    hitBoxIndex, otherHitBoxIndex, impactAngle,
                    point1, point2, manifoldPointId);
        } else if (other.getCollisionGroup() == CollisionGroups.PHYSICS_BODY) {
            processPartCollision(other, otherOwner, normal, worldContactPoint,
                    contactVel, hitBoxIndex, otherHitBoxIndex, impactAngle, manifoldPointId);
        } else if (other.getCollisionGroup() == CollisionGroups.PAWN) {
            if (otherOwner instanceof net.minecraft.world.entity.Entity contactEntity
                    && !(contactEntity instanceof CollisionObjectEntity)) {
                processEntityCollision(other, normal, worldContactPoint, localContactPoint,
                        otherLocalContactPoint, contactVel,
                        hitBoxIndex, otherHitBoxIndex, impactAngle, manifoldPointId);
            }
        }
    }

    // ==================== 摩擦设定 ====================

    /**
     * 根据碰撞箱属性重设刚体的摩擦系数。
     *
     * <p>履带轮面用履带材料的物理参数；非轮胎部位采用各向异性摩擦，轮胎部位另有滑移曲线逻辑。</p>
     */
    private void setupFriction(HitBox hitBox, int hitBoxIndex) {
        // 履带成员轮面：以履带材料替换 HitBox 材质参数（同一条履带下所有接触一致）
        if (subPart.isTrackContact(hitBoxIndex)) {
            MaterialAttr.PhysicsAttr physics = subPart.trackLink.attr().physics();
            applyFriction(true, PhysicsHelperKt.toBVector3f(physics.friction()), physics.rollingFriction(),
                    physics.spinningFriction(), physics.restitution());
            return;
        }
        Vector3f friction = PhysicsHelperKt.toBVector3f(hitBox.attr.friction());
        applyFriction(subPart.isWheel(hitBoxIndex), friction, hitBox.attr.rollingFriction(),
                hitBox.attr.spinningFriction(), hitBox.attr.restitution());
    }

    /** 方块破坏能量系数：履带轮面用履带材料，其余用碰撞箱材质。 */
    private float contactBlockDamageFactor(int hitBoxIndex, HitBox hitBox) {
        return subPart.contactMaterial(hitBoxIndex, hitBox).physics().blockDamageFactor();
    }

    private void applyFriction(boolean isWheel, Vector3f friction, float rolling, float spinning,
                               float restitution) {
        //非轮子部件重设各向异性摩擦，轮胎另行处理
        //(1,1,1) 表示各向同性，需关闭各向异性模式；否则仅在值变化时才更新逐轴摩擦缩放，
        //避免连续相同材质接触时每 tick 在"各向异性/各向同性"间翻转
        if (!isWheel) {
            if (friction.equals(Vector3f.UNIT_XYZ))
                subPart.body.setAnisotropicFriction(Vector3f.UNIT_XYZ, AfMode.none);
            else if (!friction.equals(subPart.body.getAnisotropicFriction(null)))
                subPart.body.setAnisotropicFriction(friction, AfMode.basic);
        } else if (!friction.equals(Vector3f.UNIT_XYZ)) {
            // 履带轮面也需要各向异性（带方向摩擦），按值变化更新
            if (!friction.equals(subPart.body.getAnisotropicFriction(null)))
                subPart.body.setAnisotropicFriction(friction, AfMode.basic);
        }
        if (rolling != subPart.body.getRollingFriction())
            subPart.body.setRollingFriction(rolling);
        if (spinning != subPart.body.getSpinningFriction())
            subPart.body.setSpinningFriction(spinning);
        if (restitution != subPart.body.getRestitution())
            subPart.body.setRestitution(restitution);
    }

    // ==================== 预碰撞-方块处理 ====================

    /**
     * 方块碰撞预处理，主要用于攀爬辅助的碰撞法线修正预判。
     * 若接触方块已在攀爬辅助列表中，使用局部高度场计算虚拟碰撞面。
     *
     * @return true 表示保留本次碰撞，false 表示跳过
     */
    private boolean preProcessBlockCollision(PhysicsRigidBody other, Vector3f normal,
                                             Vector3f worldContactPoint, int hitBoxIndex) {
        var otherOwner = PhysicsBodyExtensionKt.getOwner(other);
        if (otherOwner instanceof PhysicsChunkSection terrain) {
            other.shouldShowDebugBoxWhenNonColldeWith = true;
            BlockPos blockPos = terrain.getBlockPosFromContactPoint(worldContactPoint, normal, 0);
            //若该方块属于攀爬辅助列表，使用高度场进行碰撞修正
            if (subPart.climbableBlocks.contains(blockPos)) {
                Vector3f pos = subPart.body.getPhysicsLocation(null);
                float radius = 0;
                if (!subPart.attr.climbAssist)
                    pos.set(worldContactPoint);
                else if (subPart.isWheelSurface(hitBoxIndex))
                    radius = subPart.getWheelRadius(hitBoxIndex);
                var result = subPart.heightField.solveContact(pos, radius, worldContactPoint);
                //高度场只能用来决定“要不要改写这个接触”，不能用来决定“有没有接触”：
                //两者相差 1 µm 时高度场读数就已经是未侵入，而支撑力恰恰是靠这段近接触间隙
                //传递的（容差见 Spark-Core/tools/native/README.md）。所以承载面一律放行，
                //只有非承载面（撞在台阶立面上）才交给攀爬辅助跳过。
                if (isSupportingTerrainContact(normal)) return true;
                return !Float.isFinite(result.penetration()) || result.penetration() > 0;
            } else return true;
        } else if (CreateCompat.isLoaded() && CreateCollisionResolver.isCreateOwner(otherOwner)) {
            return true;
        } else return false;
    }

    // ==================== 方块碰撞处理（合并Terrain+CreateTerrain） ====================

    /**
     * 方块碰撞分发，同时处理原版区块和 Create 模组装置的碰撞。
     * 合并了原 SubPart.onCollideWithTerrain 和 onCollideWithCreateTerrain。
     */
    private void processBlockCollision(PhysicsRigidBody other, PhysicsHost otherOwner,
                                       Vector3f normal, Vector3f worldContactPoint,
                                       Vector3f localContactPoint, Vector3f otherLocalContactPoint,
                                       Vector3f contactVel, int hitBoxIndex, int otherHitBoxIndex,
                                       float impactAngle, ManifoldPoint point1, ManifoldPoint point2,
                                       long manifoldPointId) {
        if (otherOwner instanceof PhysicsChunkSection terrain) {
            handleTerrainCollision(other, terrain, normal, worldContactPoint, localContactPoint,
                    otherLocalContactPoint, contactVel, hitBoxIndex, otherHitBoxIndex,
                    impactAngle, point1, point2, manifoldPointId);
        } else if (CreateCompat.isLoaded() && CreateCollisionResolver.isCreateOwner(otherOwner)) {
            handleCreateTerrainCollision(other, otherOwner, normal, worldContactPoint, localContactPoint,
                    contactVel, hitBoxIndex, otherHitBoxIndex, impactAngle, manifoldPointId);
        }
    }

    /**
     * 与原版区块方块碰撞处理。
     * <p>包含完整的摩擦修正（含轮胎滑移曲线）、攀爬辅助、方块破坏与伤害计算、效果记录。</p>
     */
    private void handleTerrainCollision(PhysicsRigidBody other, PhysicsChunkSection terrain,
                                        Vector3f normal, Vector3f worldContactPoint,
                                        Vector3f localContactPoint, Vector3f otherLocalContactPoint,
                                        Vector3f contactVel, int hitBoxIndex, int otherHitBoxIndex,
                                        float impactAngle, ManifoldPoint point1, ManifoldPoint point2,
                                        long manifoldPointId) {
        Level level = subPart.getLevel();
        other.shouldShowDebugBoxWhenNonColldeWith = true;

        var hitBox = subPart.getHitBox(hitBoxIndex);
        BlockPos blockPos = resolveContactBlock(terrain, worldContactPoint, normal);
        SectionSnapshot.BlockSnapshot block = blockPos == null ? null : terrain.getBlockSnapshot(blockPos);
        if (block == null) {
            // 接触点落在 section 之外或没有快照：保留接触，只跳过与方块有关的统计与破坏
            return;
        }
        if (terrain.isRemoved(blockPos)) {
            disableContact(manifoldPointId);
            return;
        }
        //获取方块物理属性
        BlockState blockState = block.getState();
        float blockFriction = block.getFriction();
        float blockRollingFriction = block.getRollingFriction();
        float blockRestitution = block.getRestitution();
        float blockSlip = block.getSlip(); // 湿滑系数，0~1

        //保存原始接触法线（攀爬分支内会将其覆写为 UNIT_Y / 高度场法线）
        Vector3f rawNormal = new Vector3f(normal);

        //攀爬辅助处理
        if (subPart.climbableBlocks.contains(blockPos)) {
            Vector3f pos = subPart.body.getPhysicsLocation(null);
            float radius = 0;
            if (!subPart.attr.climbAssist)
                pos.set(worldContactPoint);
            else if (subPart.isWheelSurface(hitBoxIndex))
                radius = subPart.getWheelRadius(hitBoxIndex);
            var result = subPart.heightField.solveContact(pos, radius, worldContactPoint);
            // 高度场在区块交界、相邻 section 缺快照、空列处会给出 ±∞ 的侵入深度。
            // 只有真正要接管这个接触（滑上坡面、或高度场判定未接触）时才改写它的法线、
            // 接触点与缓存冲量；平地上高度场与 Bullet 一致，什么都不改，
            // 尤其不要清 warm-start 冲量——否则求解器每个子步都要从零重建支撑力。
            if (Float.isFinite(result.penetration())
                    && result.penetration() > 0 && result.normal().y < 0.999f) {
                //修正碰撞法线和接触点，使车辆能爬上高度场 (JME负深度代表侵入)
                ManifoldPoints.setAppliedImpulse(manifoldPointId, 0f);
                ManifoldPoints.setAppliedImpulseLateral1(manifoldPointId, 0f);
                ManifoldPoints.setAppliedImpulseLateral2(manifoldPointId, 0f);
                ManifoldPoints.setDistance1(manifoldPointId, Math.min(-result.penetration(), -0.01f));
                ManifoldPoints.setNormalWorldOnB(manifoldPointId,
                        point1.getIndex() == 0 ? result.normal() : result.normal().negate());
                ManifoldPoints.setPositionWorldOnA(manifoldPointId, result.contact());
                ManifoldPoints.setPositionWorldOnB(manifoldPointId, result.contact());
                ManifoldPoints.setCombinedRestitution(manifoldPointId, 0f);
                //用坡面法线重算滑移摩擦，保证摩擦方向/大小与修正后的接触面自洽
                setupContactFriction(manifoldPointId, result.normal(), contactVel, localContactPoint,
                        hitBox, hitBoxIndex, blockFriction, blockRollingFriction, blockSlip);
                return;
            } else if (Float.isFinite(result.penetration()) && result.penetration() <= 0
                    && !isSupportingTerrainContact(normal)) {
                //尚未接触高度场，且接触面不是承载面：这是撞在台阶立面上，跳过接触让车爬上去。
                //承载面不能走这条：高度场未侵入只说明间隙为正，正间隙的近接触仍要承重。
                ManifoldPoints.setAppliedImpulse(manifoldPointId, 0f);
                ManifoldPoints.setAppliedImpulseLateral1(manifoldPointId, 0f);
                ManifoldPoints.setAppliedImpulseLateral2(manifoldPointId, 0f);
                ManifoldPoints.setDistance1(manifoldPointId, 5000f);
                ManifoldPoints.setCombinedRestitution(manifoldPointId, 0f);
                ManifoldPoints.setCombinedFriction(manifoldPointId, 0f);
                return;
            }
        }

        //普通路径：用接触法线计算并写入摩擦（滑移率供效果/伤害记录使用）
        float slipRatio = setupContactFriction(manifoldPointId, rawNormal, contactVel, localContactPoint,
                hitBox, hitBoxIndex, blockFriction, blockRollingFriction, blockSlip);

        //调用子系统碰撞回调
        if (hitBox.subsystem != null) {
            hitBox.subsystem.onCollideWithBlock(
                    subPart.body, other, blockPos, blockState, contactVel, normal,
                    worldContactPoint, impactAngle, hitBox, manifoldPointId);
            if (terrain.isRemoved(blockPos)) {
                disableContact(manifoldPointId);
                recordEffect(hitBoxIndex, blockState, contactVel, normal, worldContactPoint, slipRatio);
                return;
            }
        }

        //撞击判定用的法线口径："由对方指向零件"。这是 approachSpeed、resolveVanillaBlockPos
        //与 supportedDurabilityAt 的共同入参约定，也与 Post 阶段（collectTerrainPoint）同源。
        //point1 恒为零件侧，其 getNormalWorld() 是外法线（零件→对方），方向相反。
        Vector3f normalOnPart = resolveNormalOnPart(manifoldPointId, point1.getIndex(), new Vector3f());

        //求解前恢复系数：与是否允许破坏无关，但沿用原有作用范围（允许破坏的接触才写入方块弹性）
        boolean destructible = MMServerConfig.shouldDestroyBlocks()
                && contactBlockDamageFactor(hitBoxIndex, hitBox) > 0
                && blockState.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) >= 0;
        boolean instantBroken = false;
        //撞击判定只认够深的侵入：section 棱边上的擦碰会在 0.1 mm 侵入上把横向车速读成接近速度。
        boolean penetrating = isImpactContact(manifoldPointId);
        if (destructible) {
            float restitution = Math.clamp(subPart.body.getRestitution() * blockRestitution, 0f, 1f);
            ManifoldPoints.setCombinedRestitution(manifoldPointId, restitution);
            //单点即时撞碎：预测足以破坏目标时本步禁用接触并让方块失效（§5.3）
            instantBroken = penetrating && tryInstantBreak(terrain, hitBox, hitBoxIndex, normalOnPart, worldContactPoint,
                    contactVel, restitution, manifoldPointId);
        }

        //撞击资格登记（材质门），由 Post 阶段统一聚合与投递
        if (!instantBroken && penetrating) {
            registerTerrainImpact(other, terrain, hitBox, hitBoxIndex, normalOnPart, worldContactPoint, contactVel);
        }

        //记录碰撞效果供主线程 EffectManager 消费
        recordEffect(hitBoxIndex, blockState, contactVel, normal, worldContactPoint, slipRatio);
    }

    /**
     * 轮胎滑移曲线摩擦计算。
     * 根据纵向和侧向滑移率查滑移曲线获取等效摩擦系数（摩擦椭圆），
     * 并将摩擦方向设为椭圆合成后的最终方向（切平面内归一化滑移方向）。
     * <p>调用线程：物理线程。</p>
     */
    private void applyWheelFriction(HitBox hitBox, float slipAngle, float slipRatio,
                                    Vector3f slipVel,
                                    long manifoldPointId, float blockFriction, float wetFactor) {
        var slipCurve = hitBox.attr.getEffectiveMaterial().physics().slipCurve();
        var longitudinalCurve = slipCurve.longitudinal();
        var lateralCurve = slipCurve.lateral();
        float angleDeg = (float) Math.toDegrees(Math.abs(slipAngle));
        //纵向摩擦：基于滑移率查曲线
        double muFront = hitBox.getMuFront()
                * calculateSlipScale(
                Math.abs(slipRatio),
                longitudinalCurve.peakSlipRatio(),
                longitudinalCurve.baseScale(),
                longitudinalCurve.peakScale(),
                longitudinalCurve.kineticScale()
        );
        //侧向摩擦：基于滑移角查曲线
        double muSide = hitBox.getMuSide()
                * calculateSlipScale(
                angleDeg / 90,
                lateralCurve.peakAngleDeg(),
                lateralCurve.kineticAngleDeg(),
                lateralCurve.baseScale(),
                lateralCurve.peakScale(),
                lateralCurve.kineticScale()
        );
        //摩擦椭圆：考虑双轴摩擦耦合，斜向滑移时总抓地力按椭圆半径衰减（漂移物理基础）
        var vz = slipVel.dot(tmpFront);
        var vx = slipVel.dot(tmpSide);
        double theta = Math.atan2(vx, vz);
        var muEff = (float) (muFront * muSide / Math.sqrt(muFront * muFront * Math.sin(theta) * Math.sin(theta) + muSide * muSide * Math.cos(theta) * Math.cos(theta)));
        //摩擦方向 = 椭圆合成后的最终方向（切平面内归一化滑移方向）。
        //必须设置 LATERAL_FRICTION 标志，求解器（CacheDirection 模式）才会采用该方向
        float slipLen = slipVel.length();
        if (slipLen > 0.01f) {
            ManifoldPoints.setLateralFrictionDir1(manifoldPointId, slipVel.mult(1f / slipLen));
            ManifoldPoints.setFlags(manifoldPointId,
                    ManifoldPoints.getFlags(manifoldPointId) | ContactPointFlag.LATERAL_FRICTION);
        } else {
            //无显著滑移（纯滚动）：清标志，让求解器回退 plane-space，避免退化方向抖动
            ManifoldPoints.setFlags(manifoldPointId,
                    ManifoldPoints.getFlags(manifoldPointId) & ~ContactPointFlag.LATERAL_FRICTION);
        }
        ManifoldPoints.setCombinedFriction(manifoldPointId,
                Math.max(0.001f, subPart.body.getFriction() * muEff * blockFriction * wetFactor));
    }

    /**
     * 计算并写入某接触点的滑移摩擦与滚动摩擦（服务端执行）。
     * <p>平地、高度场坡面、Create 地形三路共用：法线不同时传各自切平面法线
     * （高度场路径传高度场法线，其余传接触法线），滑移/椭圆/方向均以该法线为准。</p>
     * <p>调用线程：物理线程。</p>
     *
     * @param manifoldPointId      接触点ID
     * @param normal               接触切平面法线
     * @param contactVel           接触点相对速度（世界坐标）
     * @param localContactPoint    接触点在部件本地坐标的位置（计算轮面旋转速度用）
     * @param hitBox               当前碰撞箱
     * @param hitBoxIndex          接触点对应的子形状索引
     * @param blockFriction        方块摩擦系数
     * @param blockRollingFriction 方块滚动摩擦
     * @param blockSlip            方块湿滑系数 0~1
     * @return 滑移率（供调用方记录效果/方块破坏使用）
     */
    private float setupContactFriction(long manifoldPointId, Vector3f normal,
                                       Vector3f contactVel, Vector3f localContactPoint,
                                       HitBox hitBox, int hitBoxIndex,
                                       float blockFriction, float blockRollingFriction, float blockSlip) {
        //切向滑移速度（相对传入的接触平面法线）
        float normalContactVel = contactVel.dot(normal);
        Vector3f slipVel = contactVel.subtract(normal.mult(normalContactVel));
        //轮面旋转引起的表面速度（仅角速度贡献）
        Vector3f wheelVel = MMMath.relPointExtraVelFromAngularVel(localContactPoint,
                subPart.body.getPhysicsRotation(null), subPart.body.getAngularVelocity(null));
        //切平面前向/侧向基
        normal.cross(subPart.getRightVector(), tmpFront);
        tmpFront.cross(normal, tmpSide);
        float slipAngle = (float) Math.atan2(tmpSide.dot(slipVel), tmpFront.dot(slipVel));
        //滑移率与滑移速度
        float moveVelLen = subPart.body.getLinearVelocity(null).length();
        float wheelVelLen = Math.abs(wheelVel.dot(tmpFront));
        float slipRatio = Math.abs(moveVelLen - wheelVelLen) / (Math.max(moveVelLen, wheelVelLen) + 0.1f);

        //服务端才写入摩擦（客户端物理仅供表现）
        if (!subPart.getLevel().isClientSide()) {
            //湿滑衰减：履带轮面读履带材料的 slip_adaptation，其余读碰撞箱材质
            MaterialAttr.PhysicsAttr contactPhysics =
                    subPart.contactMaterial(hitBoxIndex, hitBox).physics();
            float effectiveSlip = blockSlip * (1f - contactPhysics.slipAdaptation());
            float wetFactor = (1f - effectiveSlip) * (1f - effectiveSlip * Math.abs(slipRatio) * 0.7f);
            if (subPart.isTrackContact(hitBoxIndex)) {
                //履带：前向/侧向摩擦由 body 的各向异性摩擦承担，这里只写合成摩擦标量
                ManifoldPoints.setCombinedFriction(manifoldPointId,
                        Math.max(0.001f, subPart.body.getFriction() * blockFriction * wetFactor));
                ManifoldPoints.setFlags(manifoldPointId,
                        ManifoldPoints.getFlags(manifoldPointId) & ~ContactPointFlag.LATERAL_FRICTION);
            } else if (subPart.isWheel(hitBoxIndex) && subPart.isWheelSurface(hitBoxIndex)) {
                //轮胎：滑移曲线 + 摩擦椭圆 + 单一合成方向
                applyWheelFriction(hitBox, slipAngle, slipRatio, slipVel,
                        manifoldPointId, blockFriction, wetFactor);
            } else {
                //非轮胎：各向同性，不设方向，清除可能残留的摩擦方向标志
                ManifoldPoints.setCombinedFriction(manifoldPointId,
                        Math.max(0.001f, subPart.body.getFriction() * blockFriction * wetFactor));
                ManifoldPoints.setFlags(manifoldPointId,
                        ManifoldPoints.getFlags(manifoldPointId) & ~ContactPointFlag.LATERAL_FRICTION);
            }
            ManifoldPoints.setCombinedRollingFriction(manifoldPointId,
                    Math.max(0f, subPart.body.getRollingFriction() * blockRollingFriction));
        }
        return slipRatio;
    }

    /**
     * 取“由对方指向零件”的接触法线，供撞击判定使用。
     *
     * <p>Bullet 的 {@code m_normalWorldOnB} 由 B 指向 A（{@code addContactPoint} 把 A 侧点算成
     * {@code pointB + n·depth}，depth 为负表示侵入），而事件里的 {@code o1Point} 恒为零件侧：
     * 零件是 A 时它已经是本方向，零件是 B 时需要取反。{@link ManifoldPoint#getNormalWorld()}
     * 返回的是外法线（零件→对方），与本方向相反，只能用于需要外法线的场合。</p>
     *
     * <p>调用线程：物理线程。</p>
     */
    private static Vector3f resolveNormalOnPart(long manifoldPointId, int partIndex, Vector3f store) {
        ManifoldPoints.getNormalWorldOnB(manifoldPointId, store);
        return CollisionImpactModel.normalOnPart(partIndex == 0, store, store);
    }

    /**
     * 按材质门登记本步的地形撞击组。
     *
     * <p>只有侵入够深（{@code distance1 <= -0.002}）的点才会走到本方法，判据见 {@link #isImpactContact(long)}。
     * 接近速度分两套口径：近竖直的地面接触取刚体原点的竖直分速
     * （{@link CollisionImpactModel#verticalApproachSpeed}，理由见 {@link #GROUND_IMPACT_NORMAL_Y}），
     * 立面接触取含 {@code ω × r} 的接触点相对速度投射到“由对方指向零件”的法线上。
     * 门为 {@code max(approachEpsilon, 材质 impact_min_speed)}。同组后续点不再重复判门，
     * 由 Post 阶段一次性收集本步冲量。</p>
     *
     * <p>调用线程：物理线程。</p>
     */
    private void registerTerrainImpact(PhysicsRigidBody other, PhysicsChunkSection terrain, HitBox hitBox,
                                       int hitBoxIndex, Vector3f normalOnPart, Vector3f worldContactPoint,
                                       Vector3f contactVel) {
        Level level = subPart.getLevel();
        if (level.isClientSide()) return;
        float gate = CollisionImpactModel.impactGate(
                MMServerConfig.collisionApproachEpsilon(),
                subPart.contactMaterial(hitBoxIndex, hitBox).physics().impactMinSpeed());
        float pointApproach = CollisionImpactModel.approachSpeed(contactVel, normalOnPart);
        Vector3f bodyVel = subPart.body.getLinearVelocity(null);
        // 地面接触按刚体原点的竖直分速判（口径见 GROUND_IMPACT_NORMAL_Y）；立面接触仍按接触点
        // 速度判——那里的横向分量就是真实迎击速度，不能丢。
        float approach = isGroundImpactContact(normalOnPart)
                ? CollisionImpactModel.verticalApproachSpeed(bodyVel, normalOnPart)
                : pointApproach;
        if (!CollisionImpactModel.reachesGate(approach, gate)) {
            return;
        }
        CollisionPostSettlement.TargetBlockKey target = CollisionPostSettlement.resolveVanillaTarget(
                other.nativeId(), terrain, worldContactPoint, normalOnPart);
        if (target == null) return;
        ObjectManager.collisionSettlement(level)
                .registerTerrainImpact(subPart.body.nativeId(), hitBoxIndex, target);
    }

    /**
     * 单点即时撞碎：求解前按方向有效质量预测冲量，够破坏目标就本步禁用接触。
     *
     * <p>本分支不受材质门限制，只要求 {@code u > approachEpsilon}；预测不足时保留正常求解，
     * 由 Post 按实际冲量聚合判定。破坏成功时对部件默认零伤害：约束已被禁用，
     * 不能拿“假如没碎会承受的冲量”伤害自身。</p>
     *
     * <p>{@code normalOnPart} 必须是“由对方指向零件”，破块目标与背撑都靠它反向压入几何。
     * 调用线程：物理线程。</p>
     */
    private boolean tryInstantBreak(PhysicsChunkSection terrain, HitBox hitBox, int hitBoxIndex,
                                    Vector3f normalOnPart, Vector3f worldContactPoint, Vector3f contactVel,
                                    float restitution, long manifoldPointId) {
        Level level = subPart.getLevel();
        float approach = CollisionImpactModel.approachSpeed(contactVel, normalOnPart);
        if (!CollisionImpactModel.exceedsEpsilon(approach, MMServerConfig.collisionApproachEpsilon())) {
            return false;
        }
        BlockPos blockPos = CollisionPostSettlement.resolveVanillaBlockPos(terrain, worldContactPoint, normalOnPart);
        if (blockPos == null) return false;
        SectionSnapshot.BlockSnapshot snapshot = terrain.getBlockSnapshot(blockPos);
        if (snapshot == null) return false;
        BlockState blockState = snapshot.getState();

        Vector3f lever = worldContactPoint.subtract(subPart.body.getPhysicsLocation(null));
        float effectiveMass = CollisionImpactModel.normalEffectiveMass(
                subPart.body.getMass(), lever, normalOnPart,
                subPart.body.getInverseInertiaWorld(new Matrix3f()));
        float predicted = CollisionImpactModel.predictedImpulse(effectiveMass, approach, restitution);
        float share = CollisionImpactModel.blockShare(subPart.getRHA(hitBox),
                CollisionPostSettlement.blockArmorOf(blockState));
        float blockImpulse = CollisionImpactModel.blockContribution(
                contactBlockDamageFactor(hitBoxIndex, hitBox), share, predicted);
        float durability = CollisionPostSettlement.supportedDurabilityAt(
                level, blockPos, normalOnPart, CollisionPostSettlement.blockDurabilityOf(blockState));
        float threshold = CollisionImpactModel.breakImpulse(
                MMServerConfig.collisionImpulsePerDurability(), durability);
        if (!CollisionImpactModel.meetsBreakThreshold(blockImpulse, threshold)) return false;

        // 目标在本步求解中立即失效，并同步禁用本点；后续同块回调在入口处按已移除处理
        terrain.markRemoved(blockPos);
        disableContact(manifoldPointId);
        CollisionPostSettlement.scheduleBlockRemoval(level, blockPos, blockState,
                CollisionPostSettlement.dropChance(blockImpulse, threshold));
        return true;
    }

    /**
     * 接触面是否在承重（法线竖直分量足够大）。
     *
     * <p>判据是法线的竖直分量 {@code |n.y| >= 0.7}（约 45°），对符号不敏感：法线的口径在
     * 两侧交换时会整体取反，这里只关心它是否竖直。攀爬辅助只该丢弃撞在台阶立面上的接触
     * （{@code n.y ≈ 0}）；承载面的接触即使尚未侵入也必须保留，它是近接触承重的唯一来源。</p>
     *
     * @param normal 接触法线，方向可以是“地形→零件”或反向
     */
    private static boolean isSupportingTerrainContact(Vector3f normal) {
        return normal != null && Math.abs(normal.y) >= CLIMB_SUPPORT_NORMAL_Y;
    }

    /**
     * 这个接触算不算“地面接触”（法线接近竖直），判据见 {@link #GROUND_IMPACT_NORMAL_Y}。
     *
     * @param normal 接触法线，方向可以是“地形→零件”或反向
     */
    private static boolean isGroundImpactContact(Vector3f normal) {
        return normal != null && Math.abs(normal.y) >= GROUND_IMPACT_NORMAL_Y;
    }

    /**
     * 把接触点解析成方块位置，必要时沿法线反方向退入地形后再取一次。
     *
     * <p>接触点位于零件表面上：轮子略微离开地面（接触距离为正）时，它的 y 会高于地形顶面，
     * 直接取整会落到上方相邻 section，令接触被判成本 section 之外。这里先按原位置取，
     * 落到 section 外就朝地形内部退 2 cm、6 cm 再取；仍取不到时返回 null，
     * 调用方跳过与方块有关的统计，但保留接触本身。</p>
     *
     * <p>调用线程：物理线程。</p>
     */
    private static BlockPos resolveContactBlock(PhysicsChunkSection terrain, Vector3f point, Vector3f normal) {
        for (float depth : new float[]{0f, -0.02f, -0.06f}) {
            BlockPos pos = terrain.getBlockPosFromContactPoint(point, normal, depth);
            BlockPos rel = pos.subtract(terrain.getSectionPos().origin());
            if (rel.getX() >= 0 && rel.getY() >= 0 && rel.getZ() >= 0
                    && rel.getX() <= 15 && rel.getY() <= 15 && rel.getZ() <= 15) {
                return pos;
            }
        }
        return null;
    }

    /**
     * 统一禁用接触点：写入距离哨兵并清零法向与两个切向缓存冲量。
     *
     * <p>调用线程：物理线程。</p>
     */
    private static void disableContact(long manifoldPointId) {
        ManifoldPoints.setDistance1(manifoldPointId, CollisionPostSettlement.DISABLED_DISTANCE);
        ManifoldPoints.setAppliedImpulse(manifoldPointId, 0f);
        ManifoldPoints.setAppliedImpulseLateral1(manifoldPointId, 0f);
        ManifoldPoints.setAppliedImpulseLateral2(manifoldPointId, 0f);
    }

    /**
     * 判撞击所需的侵入深度下限（米）。
     *
     * <p>实测三档：地形 section 棱边上的擦碰 {@code distance1 ≈ -0.0001}（0.1 mm）、
     * 轮子承重时的稳态侵入 {@code -0.006 … -0.009}（6–9 mm，但速度门读数为 0）、
     * 真正撞上 0.3 m 台阶时 {@code -0.018}（10 m/s）与 {@code -0.054}（15 m/s）。
     * 2 mm 把擦碰挡在外面，同时离真实撞击还差一个数量级。</p>
     */
    private static final float MIN_PENETRATION = 0.002f;

    /**
     * 这个 manifold 点算不算一次撞击：侵入至少要达到 {@link #MIN_PENETRATION}。
     *
     * <p>Bullet 会把"还差一点"的点也留在 manifold 里（{@code distance1} 为正，最大到接触破裂阈值）。
     * 这类点在地形 section 交界处给出的是相邻盒顶面棱边的斜法线，横向车速被投成接近速度
     * {@code u = v·sinθ}：台架上 10 m/s 滚过接缝读 2.07 m/s、15 m/s 读 3.62 m/s，
     * 游戏里 19 m/s 在 0.1 mm 侵入上读 2.43 m/s，而轮心是平稳滚过去的。
     * 撞击判定因此要求真侵入，且深到不是棱边擦碰。</p>
     *
     * <p>调用线程：物理线程。</p>
     */
    private static boolean isImpactContact(long manifoldPointId) {
        return ManifoldPoints.getDistance1(manifoldPointId) <= -MIN_PENETRATION;
    }

    /**
     * Create 模组装置方块碰撞处理。
     * 与原版方块碰撞的逻辑基本相同，但通过 CreateCollisionResolver 获取方块属性。
     */
    private void handleCreateTerrainCollision(PhysicsRigidBody other, PhysicsHost otherOwner,
                                              Vector3f normal, Vector3f worldContactPoint,
                                              Vector3f localContactPoint, Vector3f contactVel,
                                              int hitBoxIndex, int otherHitBoxIndex, float impactAngle,
                                              long manifoldPointId) {
        Level level = subPart.getLevel();
        var info = CreateCollisionResolver.resolve(otherOwner, otherHitBoxIndex);
        BlockState blockState = info.blockState();
        if (!info.create() || blockState == null) return;

        other.shouldShowDebugBoxWhenNonColldeWith = true;
        var hitBox = subPart.getHitBox(hitBoxIndex);
        BlockPos worldBlockPos = BlockPos.containing(worldContactPoint.x, worldContactPoint.y, worldContactPoint.z);
        float blockFriction = BlockCollisionUtil.getBlockFriction(blockState);
        float blockRollingFriction = BlockCollisionUtil.getBlockRollingFriction(blockState);
        ChunkAccess chunk = level.getChunkAt(worldBlockPos);
        float blockSlip = BlockCollisionUtil.getSlip(chunk, blockState, worldBlockPos);

        //摩擦计算（与 handleTerrainCollision 共用同一方法）
        float slipRatio = setupContactFriction(manifoldPointId, normal, contactVel, localContactPoint,
                hitBox, hitBoxIndex, blockFriction, blockRollingFriction, blockSlip);

        //子系统碰撞回调
        if (hitBox.subsystem != null) {
            hitBox.subsystem.onCollideWithBlock(
                    subPart.body, other, worldBlockPos, blockState, contactVel, normal,
                    worldContactPoint, impactAngle, hitBox, manifoldPointId);
        }

        //记录碰撞效果
        recordEffect(hitBoxIndex, blockState, contactVel, normal, worldContactPoint, slipRatio);
    }

    // ==================== 零件间碰撞处理 ====================

    /**
     * 两个 SubPart 之间的碰撞处理。
     * <p>调用子系统回调，计算恢复系数，基于碰撞能量对对方部件造成伤害。</p>
     */
    private void processPartCollision(PhysicsRigidBody other, PhysicsHost otherOwner,
                                      Vector3f normal, Vector3f worldContactPoint,
                                      Vector3f contactVel, int hitBoxIndex, int otherHitBoxIndex,
                                      float impactAngle, long manifoldPointId) {
        Level level = subPart.getLevel();
        if (!(otherOwner instanceof SubPart otherSubPart)) return;

        var hitBox = subPart.getHitBox(hitBoxIndex);
        HitBox otherHitBox = otherSubPart.getHitBox(otherHitBoxIndex);

        //调用子系统碰撞回调
        if (hitBox.subsystem != null) {
            hitBox.subsystem.onCollideWithPart(
                    subPart.body, other, contactVel, normal, worldContactPoint,
                    impactAngle, hitBox, otherHitBox, manifoldPointId);
        }

        if (contactVel.length() < 2f) return;

        //计算恢复系数和速度变化
        float contactNormalVel = contactVel.dot(normal);
        float restitution = (float) Math.sqrt(
                subPart.body.getRestitution() * other.getRestitution());
        ManifoldPoints.setCombinedRestitution(manifoldPointId, restitution);

        //未达到侵入深度的点不结算伤害（口径见 isImpactContact）
        if (!isImpactContact(manifoldPointId)) return;

        float deltaVel = (1 + restitution) * contactNormalVel * subPart.getEquivalentMass()
                / (subPart.getEquivalentMass() + otherSubPart.getEquivalentMass());
        //基于能量对对方部件造成伤害，伤害仅作用于对方
        float partDamage = 0.0005f * deltaVel * deltaVel * subPart.body.getMass();
        DamageSource source = level.damageSources().source(MMDamageTypes.PART_COLLISION);
        if (otherHitBox.modifyDamage(source, partDamage) > 1) {
            BFDamageContext ctx = BFDamageContext.builder()
                    .source(source)
                    .baseDamage(partDamage)
                    .hitVelocity(SparkMathKt.toVec3(contactVel))
                    .hitPoint(SparkMathKt.toVec3(worldContactPoint))
                    .hitNormal(SparkMathKt.toVec3(normal))
                    // 伤害落在 otherSubPart 上，命中框必须是对方的：装甲、材质修正与子系统路由都以它为准
                    .penetration(partDamage)
                    .build();
            ctx.extensions().set(MMDamageExtensions.HIT_BOX, otherHitBox);
            BFDamageApi.hurt(otherSubPart, ctx);
        }
    }

    // ==================== 实体碰撞处理 ====================

    /**
     * 与生物实体的碰撞处理。
     * <p>计算碰撞能量在实体和部件之间的分配，造成伤害和击退（含多段连接质量影响）。</p>
     */
    private void processEntityCollision(PhysicsRigidBody other, Vector3f normal,
                                        Vector3f worldContactPoint, Vector3f localContactPoint,
                                        Vector3f otherLocalContactPoint, Vector3f contactVel,
                                        int hitBoxIndex, int otherHitBoxIndex, float impactAngle,
                                        long manifoldPointId) {
        Level level = subPart.getLevel();
        var otherOwner = PhysicsBodyExtensionKt.getOwner(other);
        //过滤无效实体（已死亡、载具乘客、已有冲量等）
        if (!(otherOwner instanceof LivingEntity livingEntity)
                || livingEntity.isRemoved()
                || livingEntity.isDeadOrDying()
                || livingEntity.hasImpulse
                || (livingEntity.getVehicle() instanceof MMPartEntity)) return;

        var hitBox = subPart.getHitBox(hitBoxIndex);
        var vel = subPart.getLinearVelocity();

        //子系统碰撞回调
        if (hitBox.subsystem != null) {
            hitBox.subsystem.onCollideWithEntity(
                    subPart.body, other, contactVel, normal, worldContactPoint,
                    impactAngle, hitBox, manifoldPointId);
        }

        //忽略相对速度过小的碰撞
        if (contactVel.subtract(PhysicsHelperKt.toBVector3f(livingEntity.getDeltaMovement().scale(20)))
                .length() < 2f) return;

        //未达到侵入深度的点不结算撞击（口径见 isImpactContact）
        if (!isImpactContact(manifoldPointId)) return;

        float contactNormalSpeed = vel.dot(normal);
        //计算等效质量（含连接部件影响）
        double entityMass = MassUtil.getEntityMass(livingEntity);
        double partMass = subPart.body.getMass();
        for (var connector : subPart.connectors.values()) {
            if (connector.hasPart())
                partMass += 0.3 * connector.attachedConnector.subPart.body.getMass();
        }
        partMass += 0.05 * (subPart.part.assembly.getTotalMass() - subPart.body.getMass());
        float restitution = (float) Math.sqrt(subPart.body.getRestitution());
        double miu = (entityMass * partMass / (partMass + entityMass));
        double contactEnergy = 0.5 * miu * contactNormalSpeed * contactNormalSpeed
                * (1 - restitution * restitution);
        float impulse = (float) miu * (1 + restitution) * contactNormalSpeed;
        Vector3f impulseVec = normal.mult(impulse);

        //对部件造成伤害
        float partDamage = (float) (0.2 * contactEnergy * miu / (250 * partMass));
        DamageSource source = level.damageSources().flyIntoWall();
        if (hitBox.modifyDamage(source, partDamage) > 1) {
            BFDamageContext ctx = BFDamageContext.builder()
                    .source(level.damageSources().source(DamageTypes.FLY_INTO_WALL, livingEntity))
                    .baseDamage(partDamage)
                    .hitVelocity(SparkMathKt.toVec3(vel))
                    .hitPoint(SparkMathKt.toVec3(worldContactPoint))
                    .hitNormal(SparkMathKt.toVec3(normal))
                    .penetration(partDamage)
                    .build();
            ctx.extensions().set(MMDamageExtensions.HIT_BOX, hitBox);
            BFDamageApi.hurt(subPart, ctx);
        }

        //部件减速反冲
        subPart.getPhysicsLevel().submitDeduplicatedTask(
                subPart.part.uuid + "_" + subPart.name + "_entity_impulse", PPhase.PRE, () -> {
                    subPart.body.applyImpulse(impulseVec.mult(-0.3f),
                            worldContactPoint.subtract(subPart.body.getPhysicsLocation(null)));
                    return null;
                });

        //实体击退与伤害
        other.setLinearVelocity(other.getLinearVelocity(null)
                .add(impulseVec.mult((float) (1f / entityMass))));
        SparkLevel.submitDeduplicatedTask(level,
                livingEntity.getUUID() + "_entity_knockback", PPhase.PRE, () -> {
                    float damage = (float) (contactEnergy * miu / (250 * entityMass));
                    if (damage > 1) {
                        if (!level.isClientSide()) {
                            livingEntity.hurt(level.damageSources()
                                    .source(DamageTypes.FLY_INTO_WALL, subPart.getEntity()), damage);
                        }
                        level.playSound(null, worldContactPoint.x, worldContactPoint.y, worldContactPoint.z,
                                net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_KNOCKBACK,
                                net.minecraft.sounds.SoundSource.AMBIENT, 1f, 1f);
                    }
                    // 沿接触法线把实体推离部件（与上面给实体刚体的冲量同向）：
                    // 质量折算、m/s → 方块/tick 换算与击退抗性都在 KnockbackUtil 内
                    KnockbackUtil.applyImpulse(livingEntity, impulse, SparkMathKt.toVec3(normal));
                });
    }

    // ==================== 效果记录 ====================

    /**
     * 将本次碰撞的效果数据记录到 CollisionEffectManager。
     * 调用于物理线程末尾，EffectManager 在主线程 tick 中消费。
     */
    private void recordEffect(int hitBoxIndex, BlockState blockState,
                              Vector3f contactVel, Vector3f normal,
                              Vector3f worldContactPoint, float slipRatio) {
        boolean isWheel = subPart.isWheel(hitBoxIndex);
        effectManager.recordLatestEffect(isWheel, blockState, contactVel,
                normal, worldContactPoint, slipRatio);
    }
}
