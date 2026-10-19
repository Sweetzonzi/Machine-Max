package io.github.sweetzonzi.machine_max.client.render.post;

import io.github.sweetzonzi.machine_max.client.MMClientConfig;
import io.github.sweetzonzi.machine_max.common.mech.projectile.MuzzleFlashLight;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

import java.util.List;

/**
 * 枪口火光光源来源：把 {@link MuzzleFlashLightState} 里尚未燃尽的每一次开火提交进通用光源表。
 *
 * <p><b>强度包络</b>：设连续年龄 {@code t}、寿命 {@code T}、{@code u = t/T}，则
 * {@code I(t) = 配置强度 × (1 - u)²}——开火瞬间到峰值，随后二次衰减。它与爆炸火光的
 * {@code 1 - u²(3 - 2u)} 刻意不同：枪口焰是"炸一下就散"，爆光是"先亮住再收"；
 * 两者都只靠一个可配的 {@code duration_ticks} 表达时长。</p>
 *
 * <p><b>遮挡策略取 {@code SOURCE_DEPTH}</b>：光心就在枪口，紧贴射手自己的炮管与车体，所以容差取得比
 * 曳光大（{@link #OCCLUSION_TOLERANCE_M}）——炮管侧面与炮口制退器会比枪口本身近几十厘米，容差太小
 * 会在自己开火时把这一发判死。车体或墙挡在光心前面时，表面光照与星芒整盏跳过：这时它照亮的是看不到
 * 的那一侧，留着就是"隔着自己的车体发光"。**光晕不受这条判定影响**，它按着色器的逐像素 {@code t_hit}
 * 截断，光源被挡住但光晕球露出一半时仍然只画露出来的那一半。</p>
 *
 * <p>本来源不做额外剔除：它没有"光心在相机背后/投影半径过小"这类特有门禁（枪口焰永远落在
 * 枪口，投影半径过小的情形交给光源表的公共影响体剔除）。</p>
 *
 * <p><b>强度口径</b>：{@code muzzle_flash.intensity} 是光源在 1 m 处的辐照度（设计文档 §4.1），
 * 不是叠加倍率；半径只决定“照到多远”与窗宽，不参与亮度。提交前乘好的因子是时间包络与类别倍率
 * （{@code MMClientConfig} 的枪口火光强度），全局倍率由光源表统一乘一次，每项各乘一次。</p>
 *
 * <p>{@code 1/d²} 的奇点截断沿用 {@link ScreenSpaceLight#DEFAULT_MIN_DISTANCE_M}：光心就在炮口，
 * 相机贴到炮管旁边是常态。</p>
 *
 * <p>设计文档：{@code docs/plan/进行中/渲染-屏幕空间光照通用化设计.md} §2.4、§8.6；
 * 算子与强度语义：{@code docs/plan/进行中/渲染-屏幕空间光照物理化设计.md} §3、§4.1。</p>
 */
public class MuzzleFlashLightSource implements ScreenSpaceLightSource {

    private static final ResourceLocation SOURCE_ID =
            ResourceLocation.parse("machine_max:muzzle_flash");

    /**
     * 光源级遮挡判定的绝对容差（m）。
     *
     * <p>判据是"光心投影处的场景深度 < 光心深度 − 容差 → 光源自身不可见"。枪口贴着自己的炮管与
     * 炮口制退器，沿视线看过去它们比枪口近几十厘米是常态，容差因此取得比曳光大；车体或墙挡在
     * 光心前面时深度差是米级，不会被这条容差放过。相对分量由链统一乘（{@code OcclusionTolRel}）。</p>
     */
    private static final float OCCLUSION_TOLERANCE_M = 0.75f;

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
        return ScreenSpaceLightPriority.MUZZLE_FLASH;
    }

    /**
     * 渲染线程：遍历本维度的枪口焰列表，逐条按年龄求值并提交。
     *
     * <p>类别开关关闭时不提交任何条目，但列表的年龄照常推进：这里不改变状态，只读取。</p>
     */
    @Override
    public void collect(LightFrameContext frame, ScreenSpaceLightBuffer out) {
        if (!MMClientConfig.isMuzzleFlashLightEnabled()) return;

        Level level = frame.level();
        if (level == null) return;
        MuzzleFlashLightState state = MuzzleFlashLightState.existing(level);
        if (state == null) return;

        long gameTime = level.getGameTime();
        state.purge(gameTime);
        List<MuzzleFlashLightState.Entry> entries = state.entries();
        if (entries.isEmpty()) return;

        float partialTick = frame.partialTick();
        float categoryIntensity = MMClientConfig.getMuzzleFlashLightIntensity();

        for (int i = 0; i < entries.size(); i++) {
            MuzzleFlashLightState.Entry entry = entries.get(i);
            MuzzleFlashLight.Params params = entry.params();
            float duration = params.durationTicks();

            // 连续年龄：整数游戏时间差 + 本帧插值量，与光栅化同节拍
            float age = (gameTime - entry.startGameTime()) + partialTick;
            if (age < 0f) age = 0f;                       // 换维度等情况下游戏时间可能回退
            if (age >= duration) continue;                // 已燃尽：等下一次 purge 移除

            float u = age / duration;
            float envelope = (1f - u) * (1f - u);
            float intensity = params.intensity() * envelope * categoryIntensity;
            if (intensity <= 0f) continue;                // 类别倍率为 0：不产生候选

            BlastProjectionUtil.toViewSpace(frame.modelViewMatrix(), frame.cameraPos(), entry.origin(), viewPos);
            colorScratch.set(params.red(), params.green(), params.blue());
            light.setPoint(viewPos, params.radius(), colorScratch, intensity,
                    ScreenSpaceLight.OcclusionMode.SOURCE_DEPTH, OCCLUSION_TOLERANCE_M, 0f);
            // 光晕半径用内容强度（不含包络）派生：包络每帧都在缩小，拿它算半径会让光晕往回缩
            light.setGlow(params.intensity(), params.glow());
            light.setGlare(params.glare());
            out.submit(light);
        }
    }
}
