package io.github.sweetzonzi.machine_max.common.mech.ray;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每个投射物 tick 的原版求交覆盖标记。
 *
 * <p>统一路径（{@code ProjectileUtil.getEntityHitResult}）由 Mixin 标记“本 tick 已覆盖”，
 * 或在实际命中事件中标记“本 tick 已产生原版命中”；补充查询只对两者都不成立的投射物执行，
 * 避免同一次碰撞出现两条伤害分发。</p>
 *
 * <p>标记以投射物身份 + tick 为作用域：Pre 建立本 tick 状态并记录运动段起点，Post 取出后
 * 立即移除，不做跨 tick 复用。</p>
 *
 * <p>线程：投射物 tick 与求交都在主线程，客户端与服务端各自维护自己的投射物集合。</p>
 */
public final class ProjectileHitCoverage {

    private ProjectileHitCoverage() {
    }

    /**
     * 单个投射物本 tick 的求交状态。
     */
    public static final class TickState {
        private final int tick;
        private final Vec3 start;
        private boolean covered;
        private boolean impacted;

        private TickState(int tick, Vec3 start) {
            this.tick = tick;
            this.start = start;
        }

        /**
         * @return Pre 时记录的 tick 计数
         */
        public int tick() {
            return tick;
        }

        /**
         * @return 本 tick 运动段起点（投射物 tick 前的位置）
         */
        public Vec3 start() {
            return start;
        }

        /**
         * @return 本 tick 是否进入过统一求交
         */
        public boolean covered() {
            return covered;
        }

        /**
         * @return 本 tick 是否产生过原版投射物命中事件
         */
        public boolean impacted() {
            return impacted;
        }
    }

    private static final Map<Projectile, TickState> STATES = new ConcurrentHashMap<>();

    /**
     * 开始一个投射物 tick，记录其运动段起点。
     *
     * @param projectile 正在 tick 的投射物
     */
    public static void begin(Projectile projectile) {
        if (STATES.size() > 256) purge();
        STATES.put(projectile, new TickState(projectile.tickCount, projectile.position()));
    }

    /**
     * 标记某个实体发起的原版查询已进入统一求交路径。非投射物来源忽略。
     *
     * @param source 原版查询的 shooter / projectile
     */
    public static void markCovered(@Nullable Entity source) {
        if (!(source instanceof Projectile projectile)) return;
        TickState state = STATES.get(projectile);
        if (state == null) {
            state = new TickState(projectile.tickCount, projectile.position());
            STATES.put(projectile, state);
        }
        state.covered = true;
    }

    /**
     * 标记本 tick 已产生原版投射物命中事件。
     *
     * @param projectile 产生命中事件的投射物
     */
    public static void markImpacted(Projectile projectile) {
        TickState state = STATES.get(projectile);
        if (state == null) {
            state = new TickState(projectile.tickCount, projectile.position());
            STATES.put(projectile, state);
        }
        state.impacted = true;
    }

    /**
     * 结束一个投射物 tick 并取出本 tick 状态。
     *
     * @param projectile 刚 tick 完的投射物
     * @return 本 tick 状态；Pre 未建立（例如实体本 tick 未 tick）时返回 null
     */
    @Nullable
    public static TickState end(Projectile projectile) {
        return STATES.remove(projectile);
    }

    private static void purge() {
        STATES.entrySet().removeIf(entry -> {
            Projectile projectile = entry.getKey();
            return projectile.isRemoved() || projectile.tickCount - entry.getValue().tick > 2;
        });
    }
}
