package io.github.sweetzonzi.machine_max.common.mech.explosion;

import cn.solarmoon.spark_core.event.PhysicsSnapshotReadyEvent;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.MMServerConfig;
import io.github.sweetzonzi.machine_max.network.payload.explosion.ExplosionDetonatePayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每 Level 单例的调度器。<b>双端各一份</b>。
 *
 * <p>服务端只做三件事：接收请求、订阅快照就绪事件、按顺序驱动活跃实例；
 * 客户端不建实例、不结算，只维护表现条目。</p>
 *
 * <p>推进必须挂在快照刷新之后，即订阅 {@link PhysicsSnapshotReadyEvent}——
 * "事件即一步"，不需要待排队步数计数器，也不需要新鲜度标志。</p>
 */
@EventBusSubscriber(modid = MachineMax.MOD_ID)
public final class ExplosionManager {

    private static final Map<Level, ExplosionManager> INSTANCES = new ConcurrentHashMap<>();

    private final Level level;

    /** 服务端：活跃实例表（只读视图对外暴露） */
    private final List<ExplosionInstance> active = new ArrayList<>();

    /** 双端：表现条目表 */
    private final List<BlastFrontVisual> visuals = new ArrayList<>();

    private ExplosionManager(Level level) {
        this.level = level;
    }

    /**
     * 取某 Level 的实例，不存在则创建。实例的存放方式沿用项目既有的"按维度注册"模式。
     */
    public static ExplosionManager get(Level level) {
        return INSTANCES.computeIfAbsent(level, ExplosionManager::new);
    }

    /**
     * 三种调用方共用的唯一入口。主线程调用。
     *
     * <p>内部：广播起爆包 → 纳入活跃表（新实例的年龄从 0 起算）。
     * 起爆包只含"起爆点 + 种子 + 参数集"，不带实例标识。</p>
     *
     * <p>仅服务端生效：客户端通过起爆包建立表现条目。</p>
     *
     * @param origin 起爆点
     * @param params 参数集
     * @param source 伤害来源（应为 {@code machine_max:blast}）
     * @param seed   起爆种子，双端必须一致
     */
    public void detonate(Vector3f origin, ExplosionParams params, DamageSource source, long seed) {
        if (level.isClientSide()) return;
        // 殉爆环路保护：限制每维度同时活跃的实例数
        if (active.size() >= MMServerConfig.explosionMaxActiveInstances()) {
            MachineMax.LOGGER.warn("爆炸实例数已达上限 {}，忽略本次起爆 @ {}",
                    MMServerConfig.explosionMaxActiveInstances(), origin);
            return;
        }
        PacketDistributor.sendToPlayersInDimension((ServerLevel) level,
                new ExplosionDetonatePayload(level.dimension(), origin, seed, params));
        active.add(new ExplosionInstance(level, origin, params, source, seed));
    }

    /** 客户端：登记一个表现条目（由起爆包处理器调用）。 */
    public void addVisual(BlastFrontVisual visual) {
        visuals.add(visual);
    }

    /** 活跃实例的只读视图，供调试与测试；调用方不应持有其中的实例。 */
    public List<ExplosionInstance> activeInstances() {
        return Collections.unmodifiableList(active);
    }

    /**
     * 表现条目的只读视图，供客户端渲染读取（无活跃爆炸时为空表，渲染侧据此不提交后处理 pass）。
     */
    public List<BlastFrontVisual> visuals() {
        return Collections.unmodifiableList(visuals);
    }

    /**
     * 快照就绪事件的订阅点。只处理本 Level 的事件。
     *
     * <p>三个方法按固定顺序调用，不可合并：顺序本身承担确定性、
     * "每去重键每 tick 至多一次"、摧毁与世界修改解耦三项保证。</p>
     */
    @SubscribeEvent
    public static void onSnapshotReady(PhysicsSnapshotReadyEvent event) {
        ExplosionManager manager = INSTANCES.get(event.getPhysicsLevel().getMcLevel());
        if (manager != null) {
            manager.tick();
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            ExplosionManager manager = INSTANCES.remove(level);
            if (manager != null) {
                manager.active.clear();
                manager.visuals.clear();
            }
        }
    }

    /**
     * 每次快照就绪推进一步。
     *
     * <p>活跃表的重入处理：先取一份快照再遍历，因此 settle/execute 内触发殉爆新增的实例
     * 不在本 tick 内处理，留到下一次事件。</p>
     */
    private void tick() {
        if (!active.isEmpty()) {
            List<ExplosionInstance> snapshot = new ArrayList<>(active);
            for (ExplosionInstance instance : snapshot) {
                instance.advance();  // 推进期：DDA + 粗筛 + rayTest + 实体 AABB → 登记
                instance.settle();   // 结算期与施加：逐去重键一次 hurt
                instance.execute();  // 执行期：统一摧毁本 tick 标记的方块
                if (instance.isFinished()) {
                    active.remove(instance);
                }
            }
        }
        if (!visuals.isEmpty()) {
            Iterator<BlastFrontVisual> it = visuals.iterator();
            while (it.hasNext()) {
                BlastFrontVisual visual = it.next();
                visual.tick();
                if (visual.isFinished()) {
                    it.remove();
                }
            }
        }
    }
}
