package io.github.sweetzonzi.machine_max.common.mech.projectile.component.effect;

import com.jme3.math.Vector3f;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.explosion.CookoffContribution;
import io.github.sweetzonzi.machine_max.common.mech.explosion.ExplosionManager;
import io.github.sweetzonzi.machine_max.common.mech.explosion.ExplosionParams;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 爆炸战斗部 — 参数化爆炸效果的薄适配（注册名 {@code machine_max:blast}）。
 * <p>
 * 爆炸不是投射物的子模块，而是独立服务（需同时服务于载具殉爆、子系统摧毁与
 * 指令爆炸）；本类只把 {@link EffectContext} 翻译成一次 {@link ExplosionManager#detonate}
 * 调用，起爆的跨 tick 推进、强度折算与伤害施加全部由该服务驱动。
 * <p>
 * <b>正常引爆的字段定义只在 {@link ExplosionParams} 一处</b>，本包装类不复制字段列表；
 * 本类只额外承载一个 {@link #cookoff()}，描述"该战斗部被诱爆时"的贡献。两组参数分别求值：
 * 正常引信执行 {@link #params()} 描述的世界效果，被诱爆时只按 {@link #cookoff()} 参与
 * {@code CookoffComposition} 合成，不执行正常效果。
 * <p>
 * {@code apply} 调用返回时不代表伤害已经产生——起爆与结算之间是异步的。
 * <p>
 * 设计出处：《武器系统-组件化投射物与类型体系设计》§4.4、
 * 《武器系统-爆炸系统接口设计》§2.2。
 *
 * @param params  正常引爆的爆炸参数集（复用 {@link ExplosionParams}）
 * @param cookoff 该战斗部被诱爆时的一份贡献；JSON 字段 {@code cookoff}，整块可选，缺省零贡献
 */
public record ExplosionWorldEffect(ExplosionParams params, CookoffContribution cookoff) implements WorldEffect {

    /**
     * 由 {@link ExplosionParams#MAP_CODEC} 的平铺字段加一个可选的 {@code cookoff} 组成；
     * 正常爆炸的字段列表仍只在 {@link ExplosionParams} 一处维护。
     */
    public static final MapCodec<ExplosionWorldEffect> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ExplosionParams.MAP_CODEC.forGetter(ExplosionWorldEffect::params),
            CookoffContribution.CODEC.optionalFieldOf("cookoff", CookoffContribution.ZERO)
                    .forGetter(ExplosionWorldEffect::cookoff)
    ).apply(instance, ExplosionWorldEffect::new));

    /**
     * 紧凑构造器：{@code cookoff} 缺省为零贡献，使代码内构造无需显式传零。
     */
    public ExplosionWorldEffect {
        cookoff = cookoff == null ? CookoffContribution.ZERO : cookoff;
    }

    @Override
    public MapCodec<? extends WorldEffect> codec() {
        return CODEC;
    }

    /**
     * 正常起爆：把上下文起点翻译为一次 {@link ExplosionManager#detonate}。
     * <p>
     * 伤害归属取自 {@link ExplosionParams#damageType()}（默认 {@code machine_max:blast}），
     * 不带 owner（owner 归属属后续阶段）。
     * 种子由服务端生成并随起爆包同步，保证双端一致。
     * <p>
     * 被诱爆不走本方法：受伤摧毁的弹药只把 {@link #cookoff()} 计入库存/战斗部合成，
     * 由 {@code CookoffComposition} 生成一份新的普通爆炸参数。
     * <p>
     * <b>调用线程：</b>主线程（{@code detonate} 内含发包与非线程安全的活跃表）。
     */
    @Override
    public void apply(EffectContext context) {
        Level level = context.level();
        if (level.isClientSide()) return;

        Vec3 origin = context.origin();
        ExplosionManager.get(level).detonate(
                new Vector3f((float) origin.x, (float) origin.y, (float) origin.z),
                params,
                level.random.nextLong());
    }
}
