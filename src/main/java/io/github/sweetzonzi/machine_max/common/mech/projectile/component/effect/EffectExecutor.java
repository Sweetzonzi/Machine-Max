package io.github.sweetzonzi.machine_max.common.mech.projectile.component.effect;

import io.github.sweetzonzi.machine_max.MachineMax;

import java.util.List;

/**
 * 效果执行器 — 统一调度一批 {@link WorldEffect} 的执行。
 * <p>
 * 集中承担两件事：
 * <ul>
 *   <li><b>顺序</b>：严格按 {@code warheads} 列表顺序逐个 {@code apply}；</li>
 *   <li><b>错误处理</b>：单个效果抛异常不阻断同批其余效果，避免一个配置错误
 *       让整次命中失去全部战斗部效果。</li>
 * </ul>
 * <p>
 * <b>调用线程：</b>主线程（由 {@code ProjectileManager.postTick()} 调用）。
 */
public final class EffectExecutor {

    private EffectExecutor() {
    }

    /**
     * 按列表顺序执行全部世界效果。
     *
     * @param effects 效果列表（空列表或 null 时不做任何事）
     * @param context 效果执行上下文
     */
    public static void executeAll(List<WorldEffect> effects, EffectContext context) {
        if (effects == null || effects.isEmpty()) return;
        for (WorldEffect effect : effects) {
            if (effect == null) continue;
            try {
                effect.apply(context);
            } catch (Exception e) {
                MachineMax.LOGGER.error("执行世界效果 {} 失败 @ {}",
                        effect.getClass().getSimpleName(), context.origin(), e);
            }
        }
    }
}
