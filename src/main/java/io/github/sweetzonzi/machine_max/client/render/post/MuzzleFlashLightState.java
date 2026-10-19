package io.github.sweetzonzi.machine_max.client.render.post;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.projectile.MuzzleFlashLight;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
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
 * 客户端枪口火光状态：按维度持有尚未燃尽的枪口焰，每条按自己的寿命独立结束。
 *
 * <p><b>年龄用关卡的游戏时钟，不用墙钟。</b>登记记的是 {@link Level#getGameTime()}（20 tick/s），
 * 年龄 = {@code 当前游戏时间 - 登记时间}；游戏暂停时它不前进，枪口焰因此冻结而不是在暂停界面里烧完。
 * 渲染侧再加上帧间插值量，得到与光栅化同节拍的连续年龄（见 {@link MuzzleFlashLightSource#collect}）。</p>
 *
 * <p><b>年龄维护与是否发光无关</b>：开关关闭、容量拒绝、光源被剔除都只影响本帧提交，
 * 年龄由游戏时钟推导，照常推进——重新打开开关不会把已经结束的枪口焰从头播放。</p>
 *
 * <p><b>为什么独立于爆炸火光状态。</b>两者的寿命来源不同：火光寿命来自服务端同步的
 * {@code duration_ticks}，枪口焰寿命由本机按弹种推导（不进网络）；末尾的包络也不同
 * （火光先亮住再收，枪口焰瞬间衰减）。合成一个通用"瞬态点光"容器需要同时装两种参数与两种包络，
 * 不如各自保留一份一百多行的状态类。</p>
 *
 * <p>线程：开火登记发生在主线程（发射器 {@code onTick} 的客户端分支、调试物品的 {@code use}），
 * 渲染采集也在主线程，因此列表用 {@link ArrayList} 即可；维度表用并发映射，与
 * {@code ExplosionManager} 的按维度注册同构。</p>
 *
 * <p>设计文档：{@code docs/plan/进行中/渲染-屏幕空间光照通用化设计.md} §2.4、§8.6。</p>
 */
@EventBusSubscriber(modid = MachineMax.MOD_ID, value = Dist.CLIENT)
public final class MuzzleFlashLightState {

    /**
     * 单维度同时保留的枪口焰上限。
     *
     * <p>正常负载远低于此值：每台发射器每 tick 至多一条，而寿命最长 40 tick、典型 2 tick。
     * 上限只用于兜住异常输入（大量发射器同帧开火、调试物品连点），超出时丢最旧的一条——
     * 枪口焰是可丢弃的短命表现，丢旧的比拒绝新的更符合"最近开火最该被看见"。</p>
     */
    private static final int MAX_ENTRIES = 128;

    private static final Map<Level, MuzzleFlashLightState> INSTANCES = new ConcurrentHashMap<>();

    /** 本维度的枪口焰列表，登记顺序即遍历顺序 */
    private final List<Entry> entries = new ArrayList<>();
    /** 采集侧遍历用的只读视图，构造期建一次，避免逐帧包装 */
    private final List<Entry> readOnlyEntries = Collections.unmodifiableList(entries);

    private MuzzleFlashLightState() {
    }

    /**
     * 取本维度的枪口焰状态，不存在则创建。只应由开火登记路径调用。
     */
    private static MuzzleFlashLightState get(Level level) {
        return INSTANCES.computeIfAbsent(level, key -> new MuzzleFlashLightState());
    }

    /**
     * 取已存在的枪口焰状态；该维度还没开过火时返回 null，采集侧据此直接跳过。
     *
     * @param level 当前维度
     */
    @Nullable
    public static MuzzleFlashLightState existing(Level level) {
        return INSTANCES.get(level);
    }

    /**
     * 开火登记入口：绑定给 {@link MuzzleFlashLight#setSink} 的客户端实现。
     *
     * <p>主线程调用。参数已由 {@link MuzzleFlashLight#emit} 按弹种配置与口径求值，
     * 本方法只保存这些数值，不读内容包。</p>
     *
     * @param level    开火所在的维度
     * @param position 枪口世界坐标；本方法复制进自己的 JOML 向量后保存，调用方随后可以复用该实例
     * @param params   这一发的火光参数
     */
    public static void onMuzzleFlash(Level level, Vec3 position, MuzzleFlashLight.Params params) {
        if (!level.isClientSide()) return;
        MuzzleFlashLightState state = get(level);
        long now = level.getGameTime();
        // 先清一遍：即使光照链长期不渲染（总开关关闭），列表长度也只与寿命窗口内的开火数有关
        state.purge(now);
        if (state.entries.size() >= MAX_ENTRIES) {
            state.entries.remove(0);
        }
        state.entries.add(new Entry(
            new Vector3f((float) position.x, (float) position.y, (float) position.z),
            params, now));
    }

    /** @return 本维度枪口焰列表的只读视图；采集侧逐帧遍历，不复制 */
    public List<Entry> entries() {
        return readOnlyEntries;
    }

    /**
     * 移除已燃尽的枪口焰。
     *
     * @param gameTime 当前游戏时间（tick），与登记时取的是同一个时钟
     */
    public void purge(long gameTime) {
        entries.removeIf(entry -> isFinished(entry, gameTime));
    }

    /** @return 该条枪口焰是否已达到自己的寿命 */
    public static boolean isFinished(Entry entry, long gameTime) {
        return gameTime - entry.startGameTime() >= entry.params().durationTicks();
    }

    /**
     * 退出世界或切换维度：丢掉该维度实例，避免把枪口焰留在旧 {@link Level} 上。
     */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            INSTANCES.remove(level);
        }
    }

    /**
     * 一条已登记的枪口火光。
     *
     * @param origin        枪口世界坐标（JOML）；本类自己持有，调用方不得修改
     * @param params        这一发的火光参数（半径、颜色、强度、寿命）
     * @param startGameTime 登记时刻的游戏时间（tick）
     */
    public record Entry(Vector3f origin, MuzzleFlashLight.Params params, long startGameTime) {
    }
}
