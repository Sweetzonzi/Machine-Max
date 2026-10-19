package io.github.sweetzonzi.machine_max.client.render.post;

import net.minecraft.resources.ResourceLocation;

/**
 * 一类屏幕空间光源的来源：提供稳定的身份与优先级，并在每帧把自己的光源提交进光源表。
 *
 * <p>采集只读 {@link LightFrameContext} 与宿主状态，不操作 GL，也不得保存借用的上下文。
 * 来源 ID 在构造期创建一次，用于逐来源诊断；{@link #priority()} 必须显式声明，
 * 同一来源需要两种优先级时拆成两个实例。</p>
 *
 * <p>来源实例由渲染线程独占：{@link #collect} 只在渲染线程被调用，提交的记录不得由物理线程写入。
 * 复用内部 scratch 与单个 {@link ScreenSpaceLight} 载体是预期写法——每盏灯新建记录会让
 * 采集路径产生与光源数成正比的分配。</p>
 *
 * <p>设计文档：{@code docs/plan/进行中/渲染-屏幕空间光照通用化设计.md} §4.1。</p>
 */
public interface ScreenSpaceLightSource {

    /**
     * @return 来源身份，构造期创建一次；只用于诊断统计，不在逐光源路径上创建
     */
    ResourceLocation id();

    /**
     * @return 优先级，数值越小越优先；取 {@link ScreenSpaceLightPriority} 的常量
     */
    int priority();

    /**
     * 采集本帧的光源并提交进光源表。提交是复制语义：{@code out} 立即取走数值，
     * 调用方随后可以复用同一个载体。
     *
     * @param frame 本次主视图的只读上下文
     * @param out   本帧的光源表，调用方不需要自己判断容量
     */
    void collect(LightFrameContext frame, ScreenSpaceLightBuffer out);
}
