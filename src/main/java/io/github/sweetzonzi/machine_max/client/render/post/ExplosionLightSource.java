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
 * <p><b>遮挡策略取 {@code SOURCE_DEPTH}</b>：光心在爆点，容差取得较大（{@link #OCCLUSION_TOLERANCE_M}），
 * 因为起爆点常常贴着地面、贴着墙角，而火球与烟尘本身就在光心附近。墙或车体明显挡在光心前面时，
 * 表面光照与星芒整盏跳过——这时它照亮的是看不到的那一侧。**光晕不受这条判定影响**，它按着色器的
 * 逐像素 {@code t_hit} 截断，爆心被挡住而光晕球露出一半时只画露出来的那一半。距离、近裁面与视锥
 * 三条几何剔除仍由光源表按影响球执行，本类不重复。</p>
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

    /**
     * 光源级遮挡判定的绝对容差（m）。
     *
     * <p>起爆点常常贴地、贴墙，火球与烟尘也在光心附近；容差太小会把"爆心正好落在自己的火球里"
     * 或"爆心贴着地面"判成被遮挡、整盏消失。墙或车体挡在光心前面时深度差是米级以上，不会被放过。
     * 相对分量由链统一乘（{@code OcclusionTolRel}）。</p>
     */
    private static final float OCCLUSION_TOLERANCE_M = 1.0f;

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
                    ScreenSpaceLight.OcclusionMode.SOURCE_DEPTH, OCCLUSION_TOLERANCE_M, 0f);
            light.setMinDistance(MIN_DISTANCE_M);
            // 光晕半径用内容强度（不含包络）派生：闪光在收尾时变暗，尺寸不该跟着缩小
            light.setGlow(lightParams.intensity(), lightParams.glow());
            light.setGlare(lightParams.glare());
            out.submit(light);
        }
    }
}
