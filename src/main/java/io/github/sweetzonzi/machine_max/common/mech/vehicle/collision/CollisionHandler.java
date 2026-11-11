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
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.SubPartAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.MMDamageExtensions;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.HitBox;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageApi;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageContext;
import io.github.sweetzonzi.machine_max.util.MMMath;
import io.github.sweetzonzi.machine_max.util.mechanic.DamageUtil;
import io.github.sweetzonzi.machine_max.util.mechanic.KnockbackUtil;
import io.github.sweetzonzi.machine_max.util.mechanic.MassUtil;
import io.github.sweetzonzi.machine_max.util.terrain.LocalHeightField;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * <p>管理 SubPart 的碰撞物理逻辑，包括预碰撞判定、摩擦系数计算、
 * 方块破坏、部件伤害、实体击退等。与 CollisionEffectManager 平级协作，
 * 碰撞物理计算完成后将效果数据通过 effectManager 记录，供主线程消费。</p>
 *
 * <p>线程模型：所有方法均在物理线程调用。</p>
 */
public class CollisionHandler {

    private final SubPart subPart;
    public final CollisionEffectManager effectManager;

    /**
     * 地面撞击判据：接触法线的竖直分量不低于该值（约 18°）时，撞击接近速度改取**刚体原点**的
     * 竖直分速，而不是接触点速度。见 {@link CollisionImpactModel#verticalApproachSpeed}。
     *
     * <p>实测依据：地形 section 交界与合并盒棱边上的接触拿到的是棱边角平分线，倾斜 5.2°–9.6°，
     * 上面挂着的轮子刚体是平动的（竖直分速 0.00–0.02 m/s），但接触点速度的竖直分量有 1.5–2.5 m/s
     * ——全部由轮胎滚动 {@code ω × r} 贡献。按接触点速度判，这些点会被读成 2–3 m/s 的撞击；
     * 按刚体分速判则是 0。整车真落地的点刚体竖直分速在 4 m/s 以上，两种口径都给过门。
     * 等高接缝上的斜法线已在进入本判据之前处置（见 {@link #resolveInternalEdge}），
     * 真实棱边仍会以角平分线的形式到达。</p>
     *
     * <p>改口径还不够：悬挂对轮子的下压加速度（K17 合计约 260 m/s²）在一个 10 ms 子步里就是 2.6 m/s，
     * 轮子丢掉一个子步支撑再回来时，刚体竖直分速本身就压着这个量级。所以轮胎材质的
     * {@code impact_min_speed} 取 5.0 m/s，理由与实测见计划文档 §2.3。</p>
     *
     * <p>上限压到 18° 而不是 45°：30°–45° 的坡面按接触点速度判才对——车是斜着撞上去的，
     * 竖直分速本来就接近 0。</p>
     */
    private static final float GROUND_IMPACT_NORMAL_Y = 0.95f;

    /**
     * 承载面判据：地形指向零件的法线竖直分量不低于该值（约 45°）时，接触面能承担重量。
     * 见 {@link #isSupportingTerrainContact(Vector3f)}。
     */
    private static final float CLIMB_SUPPORT_NORMAL_Y = 0.7f;
    /** 轮面支撑进入与保持阈值，均取有符号的世界法线 Y 分量。 */
    private static final float WHEEL_SUPPORT_ENTER_Y = 0.25f;
    private static final float WHEEL_SUPPORT_EXIT_Y = 0.15f;
    /** 支撑接触允许的最大分离距离（米）。 */
    private static final float WHEEL_SUPPORT_DISTANCE = 0.01f;

    /**
     * 内部棱判据：地形给的法线水平倾斜不小于该值时才处理。
     * 更小的倾斜已经是竖直法线本身的分量，见 {@link #resolveInternalEdge}。
     */
    private static final float EDGE_DIAGONAL_MIN = 0.02f;

    /**
     * 内部棱判据的边界容差（米）：接触点离最近的方块边界超过它就判这个接触不落在接缝上。
     * 接缝上的接触点本来就落在边界上，台架实测偏 0–3 cm。
     */
    private static final float EDGE_BOUNDARY_TOLERANCE = 0.05f;

    /**
     * 内部棱判据的等高容差（米）：边界两侧柱顶高度差在这以内算同高。
     */
    private static final float EDGE_EQUAL_TOP_TOLERANCE = 1e-3f;

    /**
     * 攀爬垫的固定侵入深度（米），也是可攀爬方块上任何一条接触的深度上限。
     *
     * <p>立面接触把法线换成竖直方向后，这个深度就是求解器每步要消掉的量，抬升速度约
     * {@code CLIMB_LIFT_PENETRATION × ERP / 子步时长}（ERP 0.2、子步 10 ms、垫高 0.049 m 时
     * 约 0.98 m/s）。承载接触与高度场接触的深度超过它时同样钳到它：轮子被台阶立面托进方块内部、
     * 或者压进台阶边那张平滑面时，报出的侵入是几何量（0.2 m 以上很常见），越过求解器分速度/
     * 位置修正的深度阈值后不再产生速度、只把位置每步挪 {@code ERP × 深度}，部件被弹射出去；
     * 钳到一个垫高以后，这条接触退化成和立面一样“每步抬一个垫”的速度约束，支撑与贴合都不受
     * 影响。</p>
     *
     * <p>取值不得大于 {@code splitImpulsePenetrationThreshold}：超过它时穿透修正改走位置修正、
     * 不再产生速度。本工程在世界初始化时把该阈值设为 0.05 m（见
     * {@code ObjectManager#loadVehicleData}），Bullet 默认 0.04 m。</p>
     *
     * <p>取常数而不沿用方块给出的侵入深度：后者随车速与接触角在一个数量级内浮动，抬升速度跟着抖；
     * 取小值而不取整段侧面侵入：一次消掉几十厘米会把部件弹飞。</p>
     */
    private static final float CLIMB_LIFT_PENETRATION = 0.049f;

    /**
     * 抬升接触点从轮底（球心正下方一个半径）插值到 Bullet 报的真实接触点的比例。
     *
     * <p>0 表示冲量作用在轮底：竖直方向没有力臂，抬升几乎全部变成线速度、自转为 0；1 表示用
     * Bullet 报的侧面接触点：力臂等于半径，竖直冲量主要被自转吃掉，抬升趋近 0（实测侧面点时
     * 轮心几乎不动、自转恒定）。取中间值，轮子沿立面往上时保留一段力臂——轮子仍在立面上打滑、
     * 有拖拽阻力矩，但抬升不会被自转吃掉。</p>
     */
    private static final float CLIMB_LIFT_CONTACT_BLEND = 0.3f;

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

    /** 物理线程复用的支撑接触法线与点位缓冲。 */
    private final Vector3f wheelSupportPoint = new Vector3f();

    /**
     * 判断碰撞处理后的接触是否为轮面提供向上支撑。判据使用接触几何，与求解冲量无关。
     *
     * @param other 接触对方的物理宿主
     * @param wheelPoint 轮侧接触点
     * @param otherPoint 对方接触点，供读取指向轮面的世界法线
     * @param previouslySupported 上个完成物理步的支撑状态，用于选择迟滞阈值
     */
    public boolean isWheelSupport(Object other, ManifoldPoint wheelPoint, ManifoldPoint otherPoint,
                                  boolean previouslySupported) {
        if (other instanceof SubPart part) {
            if (subPart.part.assembly != null && subPart.part.assembly == part.part.assembly) return false;
        } else if (!(other instanceof PhysicsChunkSection)
                && !(other instanceof PhysicsHost host && CreateCompat.isLoaded()
                && CreateCollisionResolver.isCreateOwner(host))) {
            return false;
        }
        // 被碰撞处理丢弃的点带大正距离，同样被分离距离上限排除。
        float distance = wheelPoint.getDistance();
        if (!Float.isFinite(distance) || distance > WHEEL_SUPPORT_DISTANCE) return false;
        int shape = wheelPoint.getTriangleIndex();
        if (!subPart.isWheelSurface(shape)) return false;
        // 取对方指向轮面的法线，保留正负以排除顶面接触。
        float up = otherPoint.getNormalWorld(wheelSupportPoint).y;
        if (!Float.isFinite(up) || up < (previouslySupported ? WHEEL_SUPPORT_EXIT_Y : WHEEL_SUPPORT_ENTER_Y)) {
            return false;
        }
        return subPart.isEffectiveContact(shape, wheelPoint.getPositionWorld(wheelSupportPoint), true);
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
        //轮胎（轮类碰撞箱）的方向性由滑移曲线的摩擦椭圆承担：applyWheelFriction 同时写入合成摩擦与摩擦方向。
        //刚体的各向异性缩放按部件局部轴逐分量计算，轮子转动时它既改变大小（0°~90° 在 1.0 与 1.5 之间）
        //又让摩擦方向偏离滑移方向（45° 时约 11°），会凭空产生侧向力，所以轮胎必须保持各向同性。
        boolean tyre = subPart.isWheel(hitBoxIndex);
        applyFriction(!tyre, friction, hitBox.attr.rollingFriction(),
                hitBox.attr.spinningFriction(), hitBox.attr.restitution());
    }

    /** 方块破坏能量系数：履带轮面用履带材料，其余用碰撞箱材质。 */
    private float contactBlockDamageFactor(int hitBoxIndex, HitBox hitBox) {
        return subPart.contactMaterial(hitBoxIndex, hitBox).physics().blockDamageFactor();
    }

    /**
     * 把接触材质对应的摩擦参数写进刚体。
     *
     * @param anisotropic 是否借刚体的各向异性摩擦表达方向性。履带轮面与非轮胎部件走这条；
     *                    轮胎由摩擦椭圆表达方向，必须保持各向同性——逐分量缩放按部件局部轴计算，
     *                    轮子一转，缩放系数与摩擦方向都跟着变，见 {@link #setupFriction}
     */
    private void applyFriction(boolean anisotropic, Vector3f friction, float rolling, float spinning,
                               float restitution) {
        //履带与其它部件用各向异性摩擦表达方向性；轮胎不设，保持各向同性
        //(1,1,1) 表示各向同性，需关闭各向异性模式；否则仅在值变化时才更新逐轴摩擦缩放，
        //避免连续相同材质接触时每 tick 在"各向异性/各向同性"间翻转
        if (!anisotropic || friction.equals(Vector3f.UNIT_XYZ)) {
            if (!Vector3f.UNIT_XYZ.equals(subPart.body.getAnisotropicFriction(null)))
                subPart.body.setAnisotropicFriction(Vector3f.UNIT_XYZ, AfMode.none);
        } else if (!friction.equals(subPart.body.getAnisotropicFriction(null))) {
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
     * 若接触方块已在攀爬辅助列表中，使用局部高度场计算虚拟碰撞面；
     * 开启 {@code climb_assist} 的部件，接触全部由 {@link #handleTerrainCollision} 改写，
     * 这里一律保留——把接触跳掉等于把立面从求解器里拿掉。
     *
     * @return true 表示保留本次碰撞，false 表示跳过
     */
    private boolean preProcessBlockCollision(PhysicsRigidBody other, Vector3f normal,
                                             Vector3f worldContactPoint, int hitBoxIndex) {
        var otherOwner = PhysicsBodyExtensionKt.getOwner(other);
        if (otherOwner instanceof PhysicsChunkSection terrain) {
            //轮胎的圆柱是拾取/命中判定用的辅助形状：它整颗都在同一 hitbox 的球体内，只在侧壁
            //突出约 1 cm。地形接触由球体负责，圆柱的地形接触只会给出侧壁方向的额外法线，直接跳过。
            if (subPart.isWheel(hitBoxIndex) && !subPart.isWheelSurface(hitBoxIndex)) return false;
            other.shouldShowDebugBoxWhenNonColldeWith = true;
            BlockPos blockPos = terrain.getBlockPosFromContactPoint(worldContactPoint, normal, 0);
            //若该方块属于攀爬辅助列表，使用高度场进行碰撞修正
            if (subPart.climbableBlocks.contains(blockPos)) {
                //开启攀爬辅助时接触一条都不放行：平滑面与“抬零件”两级改写都在
                //handleTerrainCollision 里分派，跳掉接触等于把这条面从求解器里拿掉。
                if (subPart.attr.climbAssist) return true;
                var result = sampleHeightField(hitBoxIndex, worldContactPoint);
                //只按高度场的侵入量判定，避免原始接触法线（台阶立面水平、承载面竖直）
                //把同一个台阶判成相反结论。
                return !Float.isFinite(result.penetration()) || result.penetration() > 0;
            } else return true;
        } else if (CreateCompat.isLoaded() && CreateCollisionResolver.isCreateOwner(otherOwner)) {
            return true;
        } else return false;
    }

    /**
     * 用部件当前位置查询局部高度场。
     *
     * <p>轮面按轮心与轮半径求球面与坡面的真实接触；其余碰撞箱只把接触点映射到高度场上，
     * 半径按 0 计。</p>
     */
    private LocalHeightField.ContactResult sampleHeightField(int hitBoxIndex, Vector3f worldContactPoint) {
        Vector3f pos = subPart.body.getPhysicsLocation(null);
        float radius = 0f;
        if (subPart.isWheelSurface(hitBoxIndex))
            radius = subPart.getWheelRadius(hitBoxIndex);
        else
            pos.set(worldContactPoint);
        return subPart.heightField.solveContact(pos, radius, worldContactPoint);
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

        //内部棱处置：两侧等高的方块接缝在几何上不是棱边，法线本应竖直，Bullet 给的却是棱线指向
        //球心的方向（判据与实测见 resolveInternalEdge）。放在最前面，是因为后面每一处读法线的地方
        //——方块定位、承载判据、摩擦切平面、撞击口径、子系统回调——都该看到同一条竖直法线；
        //真实棱边（另一侧不同高）不命中，攀爬分支的高度场接管时也会整条覆写。
        if (resolveInternalEdge(point2, normal, manifoldPointId)) {
            ManifoldPoints.setNormalWorldOnB(manifoldPointId,
                    point1.getIndex() == 0 ? Vector3f.UNIT_Y : Vector3f.UNIT_Y.negate());
        }

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
            //第一级：局部高度场。轮面用轮半径按球面求解，其余碰撞箱按接触点映射——它决定这张
            //平滑面在哪里、朝哪、被压进多深。接触点、法线与深度都换成高度场的结果，部件因此
            //沿平滑面走。
            var field = sampleHeightField(hitBoxIndex, worldContactPoint);
            if (Float.isFinite(field.penetration()) && field.penetration() > 0f) {
                //法线被换成坡面或竖直方向时，旧法线上的缓存冲量已经无效，需要清零；切向没变
                //（平地、法线仍是竖直）时保留，热启动不受影响。
                boolean sloped = field.normal().y < 0.999f;
                if (sloped || Math.abs(rawNormal.x) + Math.abs(rawNormal.z) > 0.01f) {
                    ManifoldPoints.setAppliedImpulse(manifoldPointId, 0f);
                    ManifoldPoints.setAppliedImpulseLateral1(manifoldPointId, 0f);
                    ManifoldPoints.setAppliedImpulseLateral2(manifoldPointId, 0f);
                }
                ManifoldPoints.setNormalWorldOnB(manifoldPointId,
                        point1.getIndex() == 0 ? field.normal() : field.normal().negate());
                //深度以抬升垫封顶：高度场报的是几何侵入（台阶边能到 0.2 m 以上），越过求解器
                //分速度/位置修正的深度阈值后每步只挪 ERP×深度 的位置、不产生速度，部件被弹射
                //出去。封顶以后这条接触只负责“贴住面”，抬升由斜法线的不穿透约束给。
                ManifoldPoints.setDistance1(manifoldPointId,
                        -Math.min(field.penetration(), CLIMB_LIFT_PENETRATION));
                ManifoldPoints.setPositionWorldOnA(manifoldPointId, field.contact());
                ManifoldPoints.setPositionWorldOnB(manifoldPointId, field.contact());
                ManifoldPoints.setCombinedRestitution(manifoldPointId, 0f);
                setupContactFriction(manifoldPointId, field.normal(), contactVel, localContactPoint,
                        hitBox, hitBoxIndex, blockFriction, blockRollingFriction, blockSlip);
                return;
            }
            //第二级：侵入方块，但高度场判定没碰到那张平滑面。开启 climb_assist 时抬零件：承载
            //面照原值传递支撑（深度封顶），立面换成竖直法线加一个固定的抬升垫；没开启时这条
            //接触直接放行，零件继续前行。可爬列表里的方块来自部件底面下方那根连续实心柱（见
            //SubPart 的 climbableBlocks 构建），所以这里的非承载接触必然是柱子的侧面。
            if (subPart.attr.climbAssist) {
                if (!isSupportingTerrainContact(rawNormal)
                        && ManifoldPoints.getDistance1(manifoldPointId) < 0f) {
                    //立面接触：法线换成竖直方向、深度换成一个固定的垫，侧面侵入就变成每步
                    //固定量的向上位移（约 CLIMB_LIFT_PENETRATION × ERP × 物理频率），部件沿
                    //立面升到上表面。不提供侧向阻挡正是这条接触的用途：车停不停由车体自己的
                    //碰撞决定，轮子不被 1 m 的台阶立面顶回去。摩擦按接触点速度在水平切平面上
                    //重算，轮胎在立面上打滑而不是被钉住。法线换向前的缓存冲量沿旧法线，
                    //竖直方向上没有可复用的分量，按高度场分支的同一约定清零。
                    ManifoldPoints.setAppliedImpulse(manifoldPointId, 0f);
                    ManifoldPoints.setAppliedImpulseLateral1(manifoldPointId, 0f);
                    ManifoldPoints.setAppliedImpulseLateral2(manifoldPointId, 0f);
                    ManifoldPoints.setNormalWorldOnB(manifoldPointId,
                            point1.getIndex() == 0 ? Vector3f.UNIT_Y : Vector3f.UNIT_Y.negate());
                    ManifoldPoints.setDistance1(manifoldPointId, -CLIMB_LIFT_PENETRATION);
                    ManifoldPoints.setCombinedRestitution(manifoldPointId, 0f);
                    //作用点：轮底到真实接触点之间插值。轮底没有力臂，抬升 1:1 变成线速度；
                    //真实接触点在轮子侧面，力臂等于半径，竖直冲量主要被自转吃掉。插值以后
                    //轮子仍有一点拖拽阻力矩，抬升却不被自转吃掉。非轮面碰撞箱没有“轮底”，
                    //保持 Bullet 给的作用点。
                    if (subPart.isWheelSurface(hitBoxIndex)) {
                        Vector3f point = subPart.body.getPhysicsLocation(null);
                        point.y -= subPart.getWheelRadius(hitBoxIndex);
                        point.x += (worldContactPoint.x - point.x) * CLIMB_LIFT_CONTACT_BLEND;
                        point.y += (worldContactPoint.y - point.y) * CLIMB_LIFT_CONTACT_BLEND;
                        point.z += (worldContactPoint.z - point.z) * CLIMB_LIFT_CONTACT_BLEND;
                        ManifoldPoints.setPositionWorldOnA(manifoldPointId, point);
                        ManifoldPoints.setPositionWorldOnB(manifoldPointId, point);
                    }
                    setupContactFriction(manifoldPointId, Vector3f.UNIT_Y, contactVel, localContactPoint,
                            hitBox, hitBoxIndex, blockFriction, blockRollingFriction, blockSlip);
                    return;
                }
                //承载面：深度、法线与缓存冲量都保持 Bullet 的结果，支撑力正是靠这些真实接触
                //传递的，改写它们部件会陷进地形。唯一的上限是抬升垫——轮子被台阶立面托进方块
                //内部时，Bullet 报的侵入能到 0.2 m 以上，深过求解器分速度/位置修正的阈值后
                //每步只挪 ERP×深度 的位置，部件被弹射出去；钳到一个垫高以后这条接触退化成和
                //立面一样“每步抬一个垫”的速度约束，支撑力不受影响。
                if (ManifoldPoints.getDistance1(manifoldPointId) < -CLIMB_LIFT_PENETRATION) {
                    ManifoldPoints.setAppliedImpulse(manifoldPointId, 0f);
                    ManifoldPoints.setAppliedImpulseLateral1(manifoldPointId, 0f);
                    ManifoldPoints.setAppliedImpulseLateral2(manifoldPointId, 0f);
                    ManifoldPoints.setDistance1(manifoldPointId, -CLIMB_LIFT_PENETRATION);
                }
            } else if (Float.isFinite(field.penetration()) && field.penetration() <= 0f) {
                //未开启攀爬辅助：这条接触不是平滑面上的接触（台阶立面、棱边），跳过它让车继续
                //前行，由平滑面接管。
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

        // 求解前恢复系数只在允许破坏的接触上写入方块弹性。
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

        //滚阻力臂：轮面半径；非轮面为 0，表示该接触不产生滚阻
        float rollingLever = subPart.getWheelRadius(hitBoxIndex);

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
            //滚动摩擦的求解器上限是角冲量（N·m·s），而材质里的 rolling_friction 是常规滚阻系数 C_rr。
            //按 τ = C_rr·N·r 反推每步的角冲量上限：J = C_rr·J_n·r，J_n 取同一接触点本步的法向冲量。
            //求解器把上限钳成 min(J·J_n, J)，载具的 J_n 远大于 1，第二项恒成立，于是
            //τ = J/Δt = C_rr·(J_n/Δt)·r = C_rr·N·r —— 与载荷成正比、与步长无关，即常规定义。
            //力臂取轮面半径；非轮面没有滚动半径，不产生滚阻（车体蹭地由摩擦系数负责）。
            if (rollingLever > 0f) {
                ManifoldPoints.setCombinedRollingFriction(manifoldPointId,
                        Math.max(0f, subPart.body.getRollingFriction() * blockRollingFriction)
                                * ManifoldPoints.getAppliedImpulse(manifoldPointId)
                                * rollingLever);
            } else {
                ManifoldPoints.setCombinedRollingFriction(manifoldPointId, 0f);
            }
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
     * 接触面是否在承载（法线竖直分量足够大，判据见 {@link #CLIMB_SUPPORT_NORMAL_Y}）。
     *
     * <p>攀爬辅助用它区分两类接触：竖直分量小的台阶立面（抬升或放行）与竖直分量大的承载面
     * （近接触也要保留，否则部件会陷进平滑面）。没有开启攀爬辅助时这条判据不参与，接触是否
     * 保留由高度场的侵入量决定。</p>
     *
     * @param normal 接触法线，方向可以是“地形→零件”或反向
     */
    private static boolean isSupportingTerrainContact(Vector3f normal) {
        return normal != null && Math.abs(normal.y) >= CLIMB_SUPPORT_NORMAL_Y;
    }

    /**
     * 判这条接触是不是落在「内部棱」上，并按判据处置它：穿透点法线归轴，分离点挪到轮心正下方。
     *
     * <p>内部棱指两侧柱顶等高的方块接缝：地形 section 之间的拼接，以及同一平面上相邻的同高方块。
     * 这种接缝在几何上不是棱边——两侧是同一个平面，法线本应竖直。Bullet 把每个方块当成独立凸体，
     * 接缝上球面最近的几何特征不是脚下那张面，而是邻块的顶棱线：报出来的接触点落在棱线上，离
     * 轮心 5–12 cm，法线是棱线指向球心的方向（台架与线上实测倾斜 2.8°–16°），接触距离常常为正
     * ——球面根本没碰到那个盒子，它只是还落在成对接触阈值的缓存窗口里。</p>
     *
     * <p>两种情形都要处置，方式不同：</p>
     * <ul>
     *   <li><b>穿透</b>（{@code distance1 < 0}）：斜法线把水平车速投成竖直分速，轮子被抬起。
     *       归轴成竖直方向即可——两侧等高，脚下确实是那张平面，这条接触仍是承载面，只是不该带
     *       水平分量。</li>
     *   <li><b>分离</b>（{@code distance1 ≥ 0}）：保留这条约束，但把作用点挪到轮心正下方的竖轴上。
     *       求解器对正距离点并不跳过，只是打折——{@code btSequentialImpulseConstraintSolver} 的
     *       {@code convertContact} 按 {@code distance1 ≤ contactProcessingThreshold} 收点，
     *       {@code setupContactConstraint} 里 {@code penetration = distance1 + linearSlop > 0} 时
     *       把位置误差置零、只从目标速度里扣掉 {@code distance1 / dt}，因此它仍会消掉任何快过
     *       {@code distance1 / dt} 的接近速度（8 mm 距离、10 ms 子步时门槛仅 0.8 m/s）。这个折扣
     *       正是 native 补丁保住的东西：支撑靠「差点接触」传递，禁用整条会让轮子丢一个子步的支撑、
     *       掉一个预载子步的距离（台架实测 26 mm）。问题出在作用点：它落在邻块的棱线上，离轮心
     *       5–12 cm 的水平力臂，而轮子自转在它上面的速度 {@code ω × r} 有竖直分量
     *       {@code avz·rx − avx·rz}——实测自转 176 rad/s、力臂 0.12 m 时为 21 m/s，与线上单子步
     *       竖直速度突增 +4…+16 m/s 逐个对应（{@code Δv_y ≈ −ω·r_x}，相关系数 −0.875；36 次突增
     *       全部落在 {@code 16k} 边界 0.7 m 内）。两侧等高时这个点描述的就是脚下那张平面，把作用点
     *       挪到轮心正下方的竖轴上等价于把该平面当成一张无限平面取接触点：法向约束与承载都保留，
     *       力臂归零，{@code ω × r} 不再有竖直分量。</li>
     * </ul>
     *
     * <p>真实棱边（台阶、悬崖、洞沿）两侧不同高，角平分线是对的——上台阶时正是它把轮子往斜上方
     * 推。判据因此只比较边界两侧柱子在接触点附近的最高点：等高才处置，不等高保持原样。接触点的
     * 高度只用来决定"附近"是哪三层，不参与比较。判据不需要知道方块属于哪个 section / chunk，
     * 只看几何。</p>
     *
     * <p>只处理朝上的承载面：地形指向零件的法线竖直分量不低于 {@link #CLIMB_SUPPORT_NORMAL_Y}，
     * 且水平倾斜不低于 {@link #EDGE_DIAGONAL_MIN}。判据带符号——天花板、洞顶的接触法线朝下，
     * 归到 +Y 会把法线指反。法线本来就竖直的点（正下方那张面的真接触）不受影响。</p>
     *
     * <p>归轴后不清法向缓存冲量：倾斜只有几度到十几度，旧方向上的冲量在新方向上仍是很好的初值，
     * 清零反而让这条接触丢一个子步的支撑。攀爬辅助的立面分支改法线时会清零，那里的倾斜可以到 90°，
     * 两者不是一回事。</p>
     *
     * <p>调用线程：物理线程。</p>
     *
     * @param terrainPoint    地形侧的接触点
     * @param normal          地形指向零件的法线，命中内部棱时被就地改成竖直方向
     * @param manifoldPointId 接触点 ID，分离的内部棱用它改写作用点
     * @return 是否命中内部棱（命中即已处置：归轴或挪点）
     */
    private boolean resolveInternalEdge(ManifoldPoint terrainPoint, Vector3f normal, long manifoldPointId) {
        //只处理朝上的承载面（地形指向零件的法线竖直向上）：天花板、洞顶的接触法线朝下，
        //归到 +Y 会把法线指反，判据必须是带符号的。
        if (normal == null || normal.y < CLIMB_SUPPORT_NORMAL_Y) {
            return false;
        }
        float horizontalX = Math.abs(normal.x);
        float horizontalZ = Math.abs(normal.z);
        if (horizontalX + horizontalZ < EDGE_DIAGONAL_MIN) {
            return false;
        }
        Vector3f point = new Vector3f();
        terrainPoint.getPositionWorld(point);
        //倾斜的主轴决定接缝朝哪个方向：边界在接触点所在坐标的最近整数处，两侧各一格
        boolean xDominant = horizontalX >= horizontalZ;
        float coordinate = xDominant ? point.x : point.z;
        int boundary = Math.round(coordinate);
        if (Math.abs(coordinate - boundary) > EDGE_BOUNDARY_TOLERANCE) {
            return false;
        }
        int referenceY = (int) Math.floor(point.y);
        int planeX = (int) Math.floor(point.x);
        int planeZ = (int) Math.floor(point.z);
        float lowerTop = nearbyColumnTop(xDominant ? boundary - 1 : planeX, referenceY,
                xDominant ? planeZ : boundary - 1);
        float upperTop = nearbyColumnTop(xDominant ? boundary : planeX, referenceY,
                xDominant ? planeZ : boundary);
        if (!Float.isFinite(lowerTop) || !Float.isFinite(upperTop)) {
            return false;
        }
        if (Math.abs(lowerTop - upperTop) >= EDGE_EQUAL_TOP_TOLERANCE) {
            return false;
        }
        //分离：两侧等高，这个点描述的就是脚下那张平面，把作用点挪到轮心正下方的竖轴上（见本方法说明）
        if (ManifoldPoints.getDistance1(manifoldPointId) >= 0f) {
            Vector3f centre = subPart.body.getPhysicsLocation(null);
            Vector3f projected = new Vector3f(centre.x, point.y, centre.z);
            ManifoldPoints.setPositionWorldOnA(manifoldPointId, projected);
            ManifoldPoints.setPositionWorldOnB(manifoldPointId, projected);
        }
        normal.set(Vector3f.UNIT_Y);
        return true;
    }

    /**
     * 某一列在接触点高度附近的最高点（顶面世界高度）；附近取不到实心方块时返回 {@link Float#NaN}。
     *
     * <p>只扫接触点所在层的上、本、下三层：接触点与地表的距离不超过一个接触阈值，这一列的地表
     * 方块必然落在这三层里。取这三层里最高的顶面，而不是"从上往下第一个实心方块"——两者的值
     * 在方块高度都不超过 1 格时相同，但方块形状可以比一格高（栅栏 1.5 格），取最高不必依赖
     * 那条前提。</p>
     *
     * <p>一个方块都取不到说明这列的地表比接触点低两层以上，那它一定比另一侧矮，与"取到但更低"
     * 结论相同，所以返回 NaN，由调用方一并判成不等高。取方块走 {@code terrainManager}，不用当前
     * section：接缝另一侧的柱子就是相邻 section，而 {@link PhysicsChunkSection#getBlockSnapshot}
     * 对越界坐标是夹取语义，会静默读到本侧的方块。空气与未加载的 section 都取不到快照
     * （快照只存实心方块），一律继续往下找。</p>
     *
     * <p>调用线程：物理线程。</p>
     */
    private float nearbyColumnTop(int x, int referenceY, int z) {
        float top = Float.NaN;
        for (int dy = 1; dy >= -1; dy--) {
            BlockPos pos = new BlockPos(x, referenceY + dy, z);
            SectionSnapshot.BlockSnapshot snapshot =
                    subPart.getPhysicsLevel().getTerrainManager().getBlockSnapshotAt(pos);
            if (snapshot == null) continue;
            float height = (float) snapshot.getState()
                    .getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                    .max(Direction.Axis.Y);
            float worldTop = pos.getY() + height;
            if (Float.isNaN(top) || worldTop > top) top = worldTop;
        }
        return top;
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
        if (otherHitBox.modifyDamage(source, partDamage) > CollisionImpactModel.MIN_DELIVERED_DAMAGE) {
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
        if (hitBox.modifyDamage(source, partDamage) > CollisionImpactModel.MIN_DELIVERED_DAMAGE) {
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
        effectManager.recordLatestEffect(hitBoxIndex, blockState, contactVel,
                normal, worldContactPoint, slipRatio);
    }
}
