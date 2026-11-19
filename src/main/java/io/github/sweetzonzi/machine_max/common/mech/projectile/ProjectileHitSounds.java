package io.github.sweetzonzi.machine_max.common.mech.projectile;

import cn.solarmoon.spark_core.sound.ISoundSpreader;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 弹种侧的一次性 FMOD 命中音：按"打中了什么"选事件，在命中点起一个 3D 实例。
 *
 * <p>两个调用点各占一半：击穿装甲取 {@code sounds.fmod.hit_armour}（{@code SubPartHitEffects}），
 * 命中地形取 {@code sounds.fmod.hit_ground}（{@code ProjectilesHitPayload}）。两者都是<b>分层</b>的
 * 一层——FMOD 接住了就不再播原有的 OGG，没接住由调用方回退同一层的原有音效。层与层之间的取舍读
 * {@link ProjectileType.FmodSounds} 的类型注释。</p>
 *
 * <p>两个调用点传的都是<b>投射物 id</b>而不是弹种注册键：两处本来就都有这个 id（地面命中是
 * {@code HitEntry.objId}，装甲命中由服务端随表现条目下发），弹种在客户端按 id 现查，
 * 线宽只有一两个字节。</p>
 *
 * <p>事件是 3D 单次事件，位置就是命中点，速度取零：命中点在世界里是静止的，多普勒在这里没有意义。
 * 事件结束后由 SparkCore 的播放器自行释放实例，本类不保留任何引用。</p>
 *
 * <p><b>调用线程：</b>客户端主线程（表现包的处理器）。服务端调用返回 {@code false}。</p>
 */
public final class ProjectileHitSounds {

    private ProjectileHitSounds() {
    }

    /**
     * 击穿装甲：播放弹种的 {@code hit_armour}。
     *
     * <p>音量类别取 {@link SoundSource#NEUTRAL}，与它所替代的材质命中音同档，玩家拉滑块时行为不变。
     *
     * @param level        维度（客户端）
     * @param projectileId 打出这一击的投射物 id；{@code 0} 或客户端查不到它时不播
     * @param position     命中点世界坐标
     * @return true 表示 FMOD 接下了这一层，调用方不要再播材质命中音
     */
    public static boolean playArmourHit(Level level, int projectileId, Vec3 position) {
        return play(level, projectileId, position, SoundSource.NEUTRAL, true);
    }

    /**
     * 命中地形：播放弹种的 {@code hit_ground}。
     *
     * <p>音量类别取 {@link SoundSource#BLOCKS}，与它所替代的方块 {@code SoundType} 命中音同档。
     *
     * @param level        维度（客户端）
     * @param projectileId 打出这一击的投射物 id；{@code 0} 或客户端查不到它时不播
     * @param position     命中点世界坐标
     * @return true 表示 FMOD 接下了这一层，调用方不要再播方块命中音
     */
    public static boolean playGroundHit(Level level, int projectileId, Vec3 position) {
        return play(level, projectileId, position, SoundSource.BLOCKS, false);
    }

    private static boolean play(Level level, int projectileId,
                                Vec3 position, SoundSource category, boolean armour) {
        if (!level.isClientSide()) return false;
        ProjectileType type = typeOf(level, projectileId);
        if (type == null) return false;
        Optional<ProjectileType.FmodSounds> fmod = type.getSounds().fmod();
        if (fmod.isEmpty()) return false;
        Optional<String> event = armour ? fmod.get().hitArmour() : fmod.get().hitGround();
        if (event.isEmpty()) return false;
        return new HitPointSource(level, position)
                .tryPlayFmodSound(fmod.get().bank(), event.get(), category, Map.of()) != null;
    }

    /**
     * 按投射物 id 查弹种，查不到返回 {@code null}。
     *
     * <p>查不到是正常情形而不是错误：这一发可能不在本机（超出同步范围、进入视野时已经飞过去了），
     * 也可能已经被清理掉。调用方据此回退同一层的原有音效，命中表现本身不受影响。</p>
     */
    @Nullable
    private static ProjectileType typeOf(Level level, int projectileId) {
        if (projectileId <= 0) return null;
        ProjectileManager manager = ObjectManager.levelProjectileManagers.get(level);
        if (manager == null) return null;
        DestroyableObject projectile = manager.getProjectile(projectileId);
        return projectile instanceof BallisticProjectile ballistic ? ballistic.getProjectileType() : null;
    }

    /**
     * 把命中点当成一个只读声源喂给 3D 事件。
     *
     * <p>位置恒为构造时给的点：实例一旦启动就只读它取位姿，因此不需要任何刷新。实例在播放器里
     * 持有的正是这个对象，播放结束即随实例一起释放。</p>
     */
    private record HitPointSource(Level level, Vec3 position) implements ISoundSpreader {

        @Override
        public @NotNull Level getLevel() {
            return level;
        }

        @Override
        public @NotNull Vec3 getPosition(UUID uuid) {
            return position;
        }

        /** 命中点贴在车体或地面上一动不动，多普勒的声源速度为零。 */
        @Override
        public @NotNull Vec3 getSpeed(UUID uuid) {
            return Vec3.ZERO;
        }

        /** 与接触音、引擎音共用同一条听者规则：第一人称正控制着某个子系统时用车内混音。 */
        @Override
        public boolean shouldApplyInteriorEffect(@Nullable Entity listener, boolean firstPerson) {
            return AbstractSubsystem.isInteriorListener(listener, firstPerson);
        }
    }
}
