package io.github.sweetzonzi.machine_max.common.mech.projectile.component.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import io.github.sweetzonzi.machine_max.common.registry.MMDataRegistries;

import java.util.function.Function;

/**
 * 通用世界效果 — 可注册、可由 JSON 分派的效果抽象。
 * <p>
 * 与 Spark-Core 的 {@code StateCondition} / {@code StateAction} 同构：
 * 注册表中的元素就是各效果类型的 {@link MapCodec}，因此不需要额外的
 * Serializer / Type 类，{@link #codec()} 返回自身类型的 Codec 即可。
 * <p>
 * 每个效果类型都是一个实现类，且通常只是其参数类型的薄包装
 * （如 {@link ExplosionWorldEffect} 包装 {@code ExplosionParams}），
 * 字段定义只在参数类型中给出，包装类不复制字段列表。
 * <p>
 * 设计出处：《武器系统-组件化投射物与类型体系设计》§4.4。
 */
public interface WorldEffect {

    /**
     * 本效果类型的 JSON Codec。
     * <p>
     * 仅提供 JSON {@code MapCodec}：战斗部不随投射物同步到客户端
     * （起爆由服务端发起，客户端只收起爆包建表现）。
     *
     * @return 本效果类型的 Codec
     */
    MapCodec<? extends WorldEffect> codec();

    /**
     * 执行本效果。
     * <p>
     * <b>调用线程：</b>主线程。所有依赖（维度、起点、归属）都来自 {@code context}，
     * 不再额外传参。效果自身不负责线程切换——调度由统一执行器
     * {@link EffectExecutor} 负责。
     *
     * @param context 效果执行上下文
     */
    void apply(EffectContext context);

    /**
     * 聚合 Codec：按 JSON 中的 {@code "type"} 字段从
     * {@link MMDataRegistries#getWORLD_EFFECT_CODEC() WORLD_EFFECT_CODEC} 注册表分派。
     * <p>
     * 惰性访问点：本字段在 {@link #codec()} 首次被读取时才触发接口初始化，
     * 因此不会早于 {@code MachineMax.REGISTER} 就绪。
     */
    Codec<WorldEffect> CODEC = MMDataRegistries.getWORLD_EFFECT_CODEC().byNameCodec()
            .dispatch(WorldEffect::codec, Function.identity());
}
