package io.github.sweetzonzi.machine_max.client.render.post;

import cn.solarmoon.spark_core.util.SparkMathKt;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.explosion.ExplosionLightParams;
import io.github.sweetzonzi.machine_max.common.mech.explosion.ExplosionParams;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端爆炸火光状态：按维度持有火光列表，每条按自己的寿命独立结束。
 *
 * <p><b>它不能借爆炸波前条目当火光来源。</b>波前条目在波前抵达 {@code max_radius} 时被移除，
 * 而火光寿命与波前寿命互相独立（可以更短、也可以更长），因此这里独立保存一份：起爆包被接受后登记
 * 一次“起爆点 + 火光参数 + 登记时刻”，此后只按自己的寿命结束，不每帧从波前表重建。</p>
 *
 * <p><b>年龄用关卡的游戏时钟，不用墙钟。</b>登记记的是 {@link Level#getGameTime()}（20 tick/s），
 * 年龄 = {@code 当前游戏时间 - 登记时间}；游戏暂停时它不前进，火光因此冻结而不是在暂停界面里烧完。
 * 渲染侧再加上帧间插值量，得到与光栅化同节拍的连续年龄（见 {@link ExplosionLightSource#collect}）。</p>
 *
 * <p><b>年龄维护与是否发光无关</b>：开关关闭、容量拒绝、光源被剔除都只影响本帧提交，
 * 年龄由游戏时钟推导，照常推进——重新打开开关不会把已经结束的火光从头播放。</p>
 *
 * <p>线程：起爆包由 {@code MainThreadPayloadHandler} 处理、渲染采集也在主线程，因此列表用
 * {@link ArrayList} 即可；维度表用并发映射，与 {@code ExplosionManager} 的按维度注册同构。</p>
 *
 * <p>设计文档：{@code docs/plan/进行中/渲染-屏幕空间光照通用化设计.md} §8.3。</p>
 */
@EventBusSubscriber(modid = MachineMax.MOD_ID, value = Dist.CLIENT)
public final class ExplosionLightState {

    private static final Map<Level, ExplosionLightState> INSTANCES = new ConcurrentHashMap<>();

    /** 本维度的火光列表，登记顺序即遍历顺序 */
    private final List<Entry> entries = new ArrayList<>();
    /** 采集侧遍历用的只读视图，构造期建一次，避免逐帧包装 */
    private final List<Entry> readOnlyEntries = Collections.unmodifiableList(entries);

    private ExplosionLightState() {
    }

    /**
     * 取本维度的火光状态，不存在则创建。只应由起爆登记路径调用。
     */
    private static ExplosionLightState get(Level level) {
        return INSTANCES.computeIfAbsent(level, key -> new ExplosionLightState());
    }

    /**
     * 取已存在的火光状态；该维度还没有起爆过火光时返回 null，采集侧据此直接跳过。
     *
     * @param level 当前维度
     */
    @Nullable
    public static ExplosionLightState existing(Level level) {
        return INSTANCES.get(level);
    }

    /**
     * 起爆包登记入口：绑定给 {@code ExplosionManager#setLightSink} 的客户端实现。
     *
     * <p>主线程调用。{@code light.enabled()} 为 false 时不留下任何条目。</p>
     *
     * @param level  收到起爆包的维度
     * @param origin 起爆点（物理侧的 JME 向量）；本方法转成渲染侧使用的 JOML 向量后保存，
     *               调用方随后可以复用该实例
     * @param params 该次爆炸的参数集
     */
    public static void onDetonate(Level level, com.jme3.math.Vector3f origin, ExplosionParams params) {
        ExplosionLightParams light = params.light();
        if (!light.enabled()) return;
        ExplosionLightState state = get(level);
        long now = level.getGameTime();
        // 先清一遍：即使光照链长期不渲染（总开关关闭），列表长度也只与寿命窗口内的起爆数有关
        state.purge(now);
        state.entries.add(new Entry(SparkMathKt.toVector3f(origin), light, now));
    }

    /** @return 本维度火光列表的只读视图；采集侧逐帧遍历，不复制 */
    public List<Entry> entries() {
        return readOnlyEntries;
    }

    /**
     * 移除已结束的火光。
     *
     * @param gameTime 当前游戏时间（tick），与登记时取的是同一个时钟
     */
    public void purge(long gameTime) {
        entries.removeIf(entry -> isFinished(entry, gameTime));
    }

    /** @return 该条火光是否已达到自己的寿命 */
    public static boolean isFinished(Entry entry, long gameTime) {
        return gameTime - entry.startGameTime() >= entry.light().durationTicks();
    }

    /**
     * 退出世界或切换维度：丢掉该维度实例，避免把火光留在旧 {@link Level} 上。
     */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            INSTANCES.remove(level);
        }
    }

    /**
     * 一条已登记的火光。
     *
     * @param origin         起爆点（世界坐标，JOML）；本类自己持有，调用方不得修改
     * @param light          静态火光参数，来自服务端同步的值
     * @param startGameTime  登记时刻的游戏时间（tick）
     */
    public record Entry(Vector3f origin, ExplosionLightParams light, long startGameTime) {
    }
}
