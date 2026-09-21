package io.github.sweetzonzi.machine_max.mixin;

import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * 暴露 {@link PostChain} 的 pass 列表。
 *
 * <p>后处理的数组 uniform（如爆炸波前折射的 {@code BlastView[32]} / {@code BlastShape[32]}）
 * 无法经 {@link PostChain#setUniform(String, float)} 写入——那条路只支持单发标量，
 * 且 int uniform 在该路径上会因 {@code floatValues} 为 null 而抛 NPE。
 * 数组必须拿到 {@link PostPass} 的 uniform 引用后全量 {@code set(float[])}，
 * 而 pass 列表在 {@code PostChain} 中是私有字段，故用 Accessor 暴露只读访问。</p>
 */
@Mixin(PostChain.class)
public interface PostChainAccessor {

    /**
     * @return 链内全部 pass，顺序与链 JSON 的声明顺序一致
     */
    @Accessor("passes")
    List<PostPass> machineMax$getPasses();
}
