package io.github.sweetzonzi.machine_max.common.mech.projectile;

import cn.solarmoon.spark_core.api.SpreadingSoundHelper;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

/**
 * 弹种侧的一次性 FMOD 命中音：按"打中了什么"选事件，在命中点起一个定点 3D 实例。
 *
 * <p>两个调用点各占一层：击穿装甲取 {@code sounds.fmod.hit_armour}（{@code SubPartHitEffects}），
 * 命中地形取 {@code sounds.fmod.hit_ground}（{@code ProjectilesHitPayload}）。这一层与"被击中方"
 * 那一层（装甲按材质的 {@code sounds}、地形按方块的 {@code SoundType}）是<b>叠加</b>关系：
 * 同一个命中点同时响两个发声体的声音。弹种这一层静默（没配事件、bank 未加载、客户端查不到这一发）
 * 时，被击中方那一层照旧。</p>
 *
 * <p>两个调用点传的都是<b>投射物 id</b>而不是弹种注册键：两处本来就都有这个 id（地面命中是
 * {@code HitEntry.objId}，装甲命中由服务端随表现条目下发），弹种在客户端按 id 现查，
 * 线宽只有一两个字节。</p>
 *
 * <p>事件是 3D 单次事件，位置就是命中点，速度取零：命中点在世界里是静止的，多普勒在这里没有意义。
 * 定点入口不需要声源对象，实例播完由 SparkCore 的播放器自行释放，本类不保留任何引用。</p>
 *
 * <p><b>调用线程：</b>客户端主线程（表现包的处理器）。服务端调用直接返回。</p>
 */
public final class ProjectileHitSounds {

    private ProjectileHitSounds() {
    }

    /**
     * 击穿装甲：播放弹种的 {@code hit_armour}。
     *
     * <p>音量类别取 {@link SoundSource#NEUTRAL}，与同一命中点上的材质命中音同档，玩家拉滑块时行为不变。
     *
     * @param level        维度（客户端）
     * @param projectileId 打出这一击的投射物 id；{@code 0} 或客户端查不到它时不播
     * @param position     命中点世界坐标
     */
    public static void playArmourHit(Level level, int projectileId, Vec3 position) {
        play(level, projectileId, position, SoundSource.NEUTRAL, true);
    }

    /**
     * 命中地形：播放弹种的 {@code hit_ground}。
     *
     * <p>音量类别取 {@link SoundSource#BLOCKS}，与同一命中点上的方块 {@code SoundType} 命中音同档。
     *
     * @param level        维度（客户端）
     * @param projectileId 打出这一击的投射物 id；{@code 0} 或客户端查不到它时不播
     * @param position     命中点世界坐标
     */
    public static void playGroundHit(Level level, int projectileId, Vec3 position) {
        play(level, projectileId, position, SoundSource.BLOCKS, false);
    }

    private static void play(Level level, int projectileId,
                             Vec3 position, SoundSource category, boolean armour) {
        if (!level.isClientSide()) return;
        ProjectileType type = typeOf(level, projectileId);
        if (type == null) return;
        Optional<ProjectileType.FmodSounds> fmod = type.getSounds().fmod();
        if (fmod.isEmpty()) return;
        Optional<String> event = armour ? fmod.get().hitArmour() : fmod.get().hitGround();
        if (event.isEmpty()) return;
        SpreadingSoundHelper.tryPlayFmodSound(level, fmod.get().bank(), event.get(),
                category, Map.of(), position, Vec3.ZERO);
    }

    /**
     * 按投射物 id 查弹种，查不到返回 {@code null}。
     *
     * <p>查不到是正常情形而不是错误：这一发可能不在本机（超出同步范围、进入视野时已经飞过去了），
     * 也可能已经被清理掉。调用方不因此改变被击中方那一层的表现。</p>
     */
    @Nullable
    private static ProjectileType typeOf(Level level, int projectileId) {
        if (projectileId <= 0) return null;
        ProjectileManager manager = ObjectManager.levelProjectileManagers.get(level);
        if (manager == null) return null;
        DestroyableObject projectile = manager.getProjectile(projectileId);
        return projectile instanceof BallisticProjectile ballistic ? ballistic.getProjectileType() : null;
    }
}
