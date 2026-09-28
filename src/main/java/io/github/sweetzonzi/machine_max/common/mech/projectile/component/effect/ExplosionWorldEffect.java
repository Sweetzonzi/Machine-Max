package io.github.sweetzonzi.machine_max.common.mech.projectile.component.effect;

import com.jme3.math.Vector3f;
import com.mojang.serialization.MapCodec;
import io.github.sweetzonzi.machine_max.common.mech.explosion.ExplosionManager;
import io.github.sweetzonzi.machine_max.common.mech.explosion.ExplosionParams;
import io.github.sweetzonzi.machine_max.common.registry.MMDamageTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 爆炸战斗部 — 参数化爆炸效果的薄适配（注册名 {@code machine_max:blast}）。
 * <p>
 * 爆炸不是投射物的子模块，而是独立服务（需同时服务于载具殉爆、子系统摧毁与
 * 指令爆炸）；本类只把 {@link EffectContext} 翻译成一次 {@link ExplosionManager#detonate}
 * 调用，起爆的跨 tick 推进、强度折算与伤害施加全部由该服务驱动。
 * <p>
 * <b>字段定义只在 {@link ExplosionParams} 一处</b>，本包装类不复制字段列表。
 * <p>
 * {@code apply} 调用返回时不代表伤害已经产生——起爆与结算之间是异步的。
 * <p>
 * 设计出处：《武器系统-组件化投射物与类型体系设计》§4.4、
 * 《武器系统-爆炸系统接口设计》§2.2。
 *
 * @param params 爆炸参数集（复用 {@link ExplosionParams}）
 */
public record ExplosionWorldEffect(ExplosionParams params) implements WorldEffect {

    /** 由 {@link ExplosionParams} 的 codec 经 xmap 派生；不重复字段列表 */
    public static final MapCodec<ExplosionWorldEffect> CODEC =
            ExplosionParams.MAP_CODEC.xmap(ExplosionWorldEffect::new, ExplosionWorldEffect::params);

    @Override
    public MapCodec<? extends WorldEffect> codec() {
        return CODEC;
    }

    /**
     * 起爆：把上下文起点翻译为一次 {@link ExplosionManager#detonate}。
     * <p>
     * 归属固定为 {@link MMDamageTypes#BLAST}，不带 owner（owner 归属属后续阶段）。
     * 种子由服务端生成并随起爆包同步，保证双端一致。
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
                level.damageSources().source(MMDamageTypes.BLAST),
                level.random.nextLong());
    }
}
