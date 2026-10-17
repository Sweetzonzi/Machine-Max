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
 * <p><b>遮挡策略取 {@code NONE}</b>：光心就在枪口，紧贴射手自己的炮管与车体，而且往往正对着
 * 枪口方向看——S2（在光心屏幕投影处采一次深度）会在相机位于炮管后方时把整盏光判死，可实际上
 * 这一发应该照亮炮管侧面、车体前部与地面。代价与爆炸火光相同：可能照亮被遮挡的表面，
 * 属于本阶段的已知近似。</p>
 *
 * <p>本来源不做额外剔除：它没有"光心在相机背后/投影半径过小"这类特有门禁（枪口焰永远落在
 * 枪口，投影半径过小的情形交给光源表的公共影响体剔除）。</p>
 *
 * <p>提交的最终强度 = 内容强度 × 时间包络 × 类别倍率（本类乘） × 全局倍率（光源表统一乘一次），
 * 每项各乘一次。</p>
 *
 * <p>设计文档：{@code docs/plan/进行中/渲染-屏幕空间光照通用化设计.md} §2.4、§8.6。</p>
 */
public class MuzzleFlashLightSource implements ScreenSpaceLightSource {

    private static final ResourceLocation SOURCE_ID =
            ResourceLocation.parse("machine_max:muzzle_flash");

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
                    ScreenSpaceLight.OcclusionMode.NONE, 0f, 0f);
            out.submit(light);
        }
    }
}
