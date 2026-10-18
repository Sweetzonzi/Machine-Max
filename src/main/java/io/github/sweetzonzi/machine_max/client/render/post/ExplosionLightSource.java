package io.github.sweetzonzi.machine_max.client.render.post;

import io.github.sweetzonzi.machine_max.client.MMClientConfig;
import io.github.sweetzonzi.machine_max.common.mech.explosion.ExplosionLightParams;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

import java.util.List;

/**
 * 爆炸火光光源来源：把 {@link ExplosionLightState} 里尚未结束的每一次起爆提交进通用光源表。
 *
 * <p><b>强度包络</b>：设连续年龄 {@code t}、寿命 {@code T}、{@code u = t/T}，则
 * {@code I(t) = 配置强度 × (1 - u²(3 - 2u))}——起爆瞬间立即到峰值，结束时一阶导为零地平滑归零，
 * 中途不退回到峰值。半径固定为配置值：不随波前扩张，也就不需要在年龄为 0 时做除法。</p>
 *
 * <p><b>强度口径</b>：{@code light.intensity} 是光源在 1 m 处的辐照度（设计文档 §4.1），不是叠加倍率；
 * 提交的最终强度 = 内容强度 × 时间包络 × 类别倍率（本类乘） × 全局倍率（光源表统一乘一次），
 * 每项各乘一次。半径负责剔除范围与窗宽，不参与亮度。</p>
 *
 * <p><b>遮挡策略取 {@code NONE}</b>（设计文档 §8.5）：起爆点可能不在视野内、也可能被自己掀起的
 * 粒子与烟尘挡住，用“光心屏幕深度”判定会整盏误杀；这一阶段的代价是可能穿墙照亮。距离、近裁面与
 * 视锥三条几何剔除仍由光源表按影响球执行，本类不重复。</p>
 *
 * <p>设计文档：{@code docs/plan/进行中/渲染-屏幕空间光照通用化设计.md} §8.2、§8.3；
 * 算子与强度语义：{@code docs/plan/进行中/渲染-屏幕空间光照物理化设计.md} §3、§4.1。</p>
 */
public class ExplosionLightSource implements ScreenSpaceLightSource {

    /**
     * {@code 1/d²} 的奇点截断距离（m）。
     *
     * <p>爆炸火光的光心通常离地面与车体几米以上，取比枪口与曳光更大的值，让爆心的过曝范围
     * 不至于缩成一个点；真正的“爆心很亮”来自内容强度，不是来自把 {@code d_min} 调小。</p>
     */
    private static final float MIN_DISTANCE_M = 0.75f;

    private static final ResourceLocation SOURCE_ID =
            ResourceLocation.parse("machine_max:explosion_light");

    /** 本帧待提交的光源载体与颜色 scratch，采集路径不产生与光源数成正比的分配 */
    private final ScreenSpaceLight light = new ScreenSpaceLight();
    private final Vector3f viewPos = new Vector3f();
    private final Vector3f colorScratch = new Vector3f();

    @Override
    public ResourceLocation id() {
        return SOURCE_ID;
    }

    @Override
    public int priority() {
        return ScreenSpaceLightPriority.EXPLOSION_LIGHT;
    }

    /**
     * 渲染线程：遍历本维度的火光列表，逐条按年龄求值并提交。
     *
     * <p>类别开关关闭时不提交任何条目，但列表的年龄照常推进：这里不改变状态，只读取。</p>
     */
    @Override
    public void collect(LightFrameContext frame, ScreenSpaceLightBuffer out) {
        if (!MMClientConfig.isExplosionLightEnabled()) return;

        Level level = frame.level();
        if (level == null) return;
        ExplosionLightState state = ExplosionLightState.existing(level);
        if (state == null) return;

        long gameTime = level.getGameTime();
        state.purge(gameTime);
        List<ExplosionLightState.Entry> entries = state.entries();
        if (entries.isEmpty()) return;

        float partialTick = frame.partialTick();
        float categoryIntensity = MMClientConfig.getExplosionLightIntensity();

        for (int i = 0; i < entries.size(); i++) {
            ExplosionLightState.Entry entry = entries.get(i);
            ExplosionLightParams lightParams = entry.light();
            float duration = lightParams.durationTicks();

            // 连续年龄：整数游戏时间差 + 本帧插值量，与光栅化同节拍
            float age = (gameTime - entry.startGameTime()) + partialTick;
            if (age < 0f) age = 0f;                       // 换维度等情况下游戏时间可能回退
            if (age >= duration) continue;                // 已结束：等下一次 purge 移除

            float u = age / duration;
            float envelope = 1f - u * u * (3f - 2f * u);
            float intensity = lightParams.intensity() * envelope * categoryIntensity;
            if (intensity <= 0f) continue;                // 末尾帧与倍率 0：不产生候选

            BlastProjectionUtil.toViewSpace(frame.modelViewMatrix(), frame.cameraPos(), entry.origin(), viewPos);
            colorScratch.set(lightParams.red(), lightParams.green(), lightParams.blue());
            light.setPoint(viewPos, lightParams.radius(), colorScratch, intensity,
                    ScreenSpaceLight.OcclusionMode.NONE, 0f, 0f);
            light.setMinDistance(MIN_DISTANCE_M);
            // 光晕半径用内容强度（不含包络）派生：闪光在收尾时变暗，尺寸不该跟着缩小
            light.setGlow(lightParams.intensity(), lightParams.glow());
            out.submit(light);
        }
    }
}
