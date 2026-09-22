package io.github.sweetzonzi.machine_max.common.mech.grab;

import cn.solarmoon.spark_core.event.PhysicsLevelTickEvent;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.attachment.LivingEntityEyesightAttachment;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.IPartAssembly;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 抓取会话管理器：每 Level 单例，持有抓取登记表，负责目标解析与每 tick 驱动，
 * 并且是释放的唯一执行者。
 * <p>
 * 分工纪律（接口设计文档 §1.4）：判据、玩家输入、玩家状态变化、目标失效四条来源都只是"请求结束"，
 * 任何其他路径都不得自行移除约束，否则会出现"约束已从世界移除但登记表仍有记录"这类不一致。
 * <p>
 * 线程分工（接口设计文档 §五）：
 * <ul>
 *   <li>主线程：目标解析、参数读取、眼位与视线快照、玩家状态判定；</li>
 *   <li>物理线程：{@link GrabConstraint#physicsStep()} 在物理步的步进前阶段评估判据并写零力点，
 *       约束的建立与移除、冲量施加都以物理任务提交。</li>
 * </ul>
 */
@EventBusSubscriber(modid = MachineMax.MOD_ID)
public class GrabManager {

    /** 每 Level 单例。 */
    private static final Map<Level, GrabManager> INSTANCES = new ConcurrentHashMap<>();

    private final Level level;
    /** 抓取登记表：按玩家 UUID 索引，每个玩家至多一条（详细设计文档 §6.4 的单槽状态机）。 */
    private final ConcurrentHashMap<UUID, GrabConstraint> grabs = new ConcurrentHashMap<>();

    private GrabManager(Level level) {
        this.level = level;
    }

    /**
     * 取得该维度的抓取会话管理器，不存在则创建。
     *
     * @param level 世界
     * @return 该维度的管理器
     */
    public static GrabManager get(Level level) {
        return INSTANCES.computeIfAbsent(level, GrabManager::new);
    }

    /**
     * 取得该维度已存在的抓取会话管理器。
     *
     * @param level 世界
     * @return 管理器，尚未创建时为 null
     */
    @Nullable
    public static GrabManager getIfPresent(Level level) {
        return INSTANCES.get(level);
    }

    // ==================== 事件驱动 ====================

    /**
     * 主线程每 tick 驱动：处理结束请求、按玩家状态判定释放、覆写物理线程要读的快照。
     * <p>
     * 优先级取 HIGHEST，先于物理层的步进请求（{@code PhysicsLevelApplier} 用的 HIGH），
     * 使本 tick 的快照与物理任务先就位。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLevelTickPre(LevelTickEvent.Pre event) {
        if (event.getLevel().isClientSide()) return;
        GrabManager manager = getIfPresent(event.getLevel());
        if (manager != null) manager.tick();
    }

    /**
     * 物理步的步进前阶段：逐个评估释放判据并写零力点。
     * <p>
     * 判据与刚体同线程，且每个物理步评估一次（详细设计文档 §6.2、§8.1）。
     */
    @SubscribeEvent
    public static void onPhysicsTickPre(PhysicsLevelTickEvent.Pre event) {
        GrabManager manager = getIfPresent(event.getLevel().getMcLevel());
        if (manager == null || manager.grabs.isEmpty()) return;
        for (GrabConstraint constraint : manager.grabs.values()) {
            constraint.physicsStep();
        }
    }

    /** 世界卸载时丢弃登记表：物理世界随之关闭，约束不再存在。 */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level mcLevel) INSTANCES.remove(mcLevel);
    }

    // ==================== 对外动作 ====================

    /**
     * 开始抓取（主线程，服务端）。
     * <p>
     * 载荷只表达"开始"这一意图，目标、抓取点、方向与全部受力参数都在这里用玩家真实的视线与属性
     * 重算（详细设计文档 §8.5）。
     *
     * @param player 发起抓取的玩家
     */
    public void startGrab(ServerPlayer player) {
        // 已有抓取则先释放（接口设计文档 §2.1）
        releaseGrab(player, GrabConstraint.ReleaseReason.INPUT);
        // 服务端复核形态门禁：徒手形态要求主手为空
        if (!player.getMainHandItem().isEmpty()) return;
        if (!player.hasData(MMAttachments.getENTITY_EYESIGHT())) return;
        LivingEntityEyesightAttachment eyesight = player.getData(MMAttachments.getENTITY_EYESIGHT());
        // 目标解析：复用玩家身上的视线拾取结果，按 §3.7 的归属规则挑出零件自身刚体的那条命中，
        // 连接点判定体与交互框刚体（常态运动学模式）被跳过
        SubPart target = eyesight.getSubPart();
        if (target == null) return;
        PhysicsRayTestResult hit = eyesight.getTargetBodyCache().get(target.getBody());
        if (hit == null) return;
        // 动力学断言：被抓刚体在抓取期间必须是动力学模式（详细设计文档 P3、§8.2）
        if (!target.getBody().isDynamic()) return;
        float range = (float) player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        if (range <= 0) return;
        Vector3f eye = PhysicsHelperKt.toBVector3f(player.getEyePosition());
        Vector3f forward = PhysicsHelperKt.toBVector3f(player.getForward());
        // 命中距离 d：射线能命中它的前提就是长度不超过 D，因此 d ≤ D，脱手判据不会贴边误触
        float hitDistance = range * hit.getHitFraction();
        Vector3f hitPoint = forward.mult(hitDistance, null).addLocal(eye);
        // frame A 原点：约束创建时写入的世界侧端点，取"眼位 + 视线 × 射线长度"
        Vector3f anchorOrigin = forward.mult(range, null).addLocal(eye);
        float mass = target.getBody().getMass();
        double strength = GrabPhysics.grabStrength(player);
        // 刚度与阻尼在抓取时写死，之后不再随力量变化（详细设计文档 §8.3）
        float stiffness = GrabPhysics.stiffness(mass, strength);
        float damping = GrabPhysics.damping(stiffness, mass);
        GrabConstraint constraint = new GrabConstraint(player, target, hitPoint, anchorOrigin,
                hitDistance, stiffness, damping);
        // 先写入快照再提交建立任务，避免创建完成后的第一个物理步读到空快照
        constraint.updateSnapshot(eye, forward, range);
        grabs.put(player.getUUID(), constraint);
        constraint.submitCreate();
    }

    /**
     * 释放抓取（主线程）。四条结束来源的统一执行入口（接口设计文档 §1.4）。
     *
     * @param player 归属玩家
     * @param reason 结束原因
     */
    public void releaseGrab(ServerPlayer player, GrabConstraint.ReleaseReason reason) {
        GrabConstraint constraint = grabs.remove(player.getUUID());
        if (constraint != null) constraint.destroy();
    }

    /**
     * 丢出（主线程）。释放当前抓取，并在抓取点上施加冲量（详细设计文档 §7）。
     *
     * @param player 归属玩家
     */
    public void throwGrab(ServerPlayer player) {
        GrabConstraint constraint = grabs.remove(player.getUUID());
        if (constraint == null) return;
        SubPart target = constraint.getTarget();
        // 有效质量取被抓装配体的总质量；未装配的零件即自身质量（详细设计文档 §7.1）
        IPartAssembly assembly = target.getPart().getAssembly();
        float effectiveMass = assembly != null ? assembly.getTotalMass() : target.getBody().getMass();
        double strength = GrabPhysics.grabStrength(player);
        float speedFactor = GrabPhysics.movementSpeedFactor(player);
        float impulse = GrabPhysics.throwImpulse(effectiveMass, strength, speedFactor);
        // TODO: 冲量目前只作用于被抓刚体本身，是否需要在装配体各刚体之间按质量分摊，待 V7 实测结论
        constraint.throwWith(impulse, PhysicsHelperKt.toBVector3f(player.getForward()));
    }

    // ==================== 主线程每 tick 驱动 ====================

    private void tick() {
        if (grabs.isEmpty()) return;
        for (Map.Entry<UUID, GrabConstraint> entry : grabs.entrySet()) {
            UUID uuid = entry.getKey();
            GrabConstraint constraint = entry.getValue();
            // 以玩家列表为准取当前实例：跨维度时玩家实体被重建，旧引用不再是权威
            ServerPlayer player = ((ServerLevel) level).getServer().getPlayerList().getPlayer(uuid);
            if (player == null || player != constraint.getPlayer() || player.level() != level) {
                releaseGrab(constraint.getPlayer(), GrabConstraint.ReleaseReason.PLAYER_STATE);
                continue;
            }
            tickConstraint(player, constraint);
        }
    }

    private void tickConstraint(ServerPlayer player, GrabConstraint constraint) {
        // 物理线程判定出的结束原因（脱手判据或目标转为运动学/被摧毁）
        GrabConstraint.ReleaseReason pending = constraint.pollPendingRelease();
        if (pending != null) {
            releaseGrab(player, pending);
            return;
        }
        // 玩家状态变化：死亡、断线、徒手形态下主手不再为空
        if (player.isRemoved() || !player.isAlive() || !player.getMainHandItem().isEmpty()) {
            releaseGrab(player, GrabConstraint.ReleaseReason.PLAYER_STATE);
            return;
        }
        // 目标状态与结构：被摧毁、脱落、转运动学、装配体分裂
        if (constraint.isTargetInvalid()) {
            releaseGrab(player, GrabConstraint.ReleaseReason.TARGET_INVALID);
            return;
        }
        // 覆写快照：眼位、视线与脱手阈值都由服务端用玩家真实状态算出（详细设计文档 §3.4）
        float range = (float) player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        constraint.updateSnapshot(PhysicsHelperKt.toBVector3f(player.getEyePosition()),
                PhysicsHelperKt.toBVector3f(player.getForward()), range);
    }
}
