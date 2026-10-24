package io.github.sweetzonzi.machine_max.common.mech.part;

import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.Set;

/**
 * 只在装载校验阶段使用的候选查询与必需依赖收集器。
 *
 * <p>每个定义的校验器独占一个上下文；候选表属于同一装载批次，依赖集合由发布流程收集。
 * 查询命中只说明候选存在，不说明它最终有效。发布流程会继续传播依赖目标的校验失败。</p>
 * <p>校验器应在当前调用中使用它，不能缓存到运行时，也不能把候选对象作为已发布定义创建实例。</p>
 */
public final class PartValidationContext {
    private final Map<ResourceLocation, AbstractPartType> candidates;
    private final Set<ResourceLocation> required;

    /**
     * 绑定本轮候选表与当前定义的依赖集合；由发布流程按定义创建，不复制集合。
     */
    PartValidationContext(Map<ResourceLocation, AbstractPartType> candidates, Set<ResourceLocation> required) {
        this.candidates = candidates;
        this.required = required;
    }

    /**
     * 登记当前定义对目标定义的必需引用，并查询同批次候选。
     *
     * <p>先登记再查询；即使调用者捕获缺失异常，发布流程也能沿依赖边剔除当前定义。</p>
     *
     * @param id 必须存在且最终有效的部件注册名
     * @return 目标候选定义，尚未承诺会被发布
     * @throws IllegalArgumentException 本批次不存在该候选时抛出
     */
    public AbstractPartType require(ResourceLocation id) {
        required.add(id);
        AbstractPartType result = candidates.get(id);
        if (result == null) throw new IllegalArgumentException("Missing required part: " + id);
        return result;
    }

    /**
     * 登记必需部件依赖，并逐层校验目标变体、子零件和连接点的定义路径。
     *
     * <p>只检查静态配置路径，不构造连接器，也不检查运行时连接条件或几何对齐。</p>
     *
     * @param id 目标部件注册名
     * @param variant 目标变体名
     * @param subPart 该变体内的子零件名
     * @param connector 该子零件内的连接点名
     * @throws IllegalArgumentException 路径任一层缺失时抛出
     */
    public void requireConnector(ResourceLocation id, String variant, String subPart, String connector) {
        var definition = require(id);
        var selected = definition.getVariant(variant);
        if (selected == null) throw new IllegalArgumentException(id + ": unknown variant " + variant);
        var child = selected.subParts.get(subPart);
        if (child == null || !child.connectors.containsKey(connector)) {
            throw new IllegalArgumentException(id + ": unknown connector " + variant + "/" + subPart + "/" + connector);
        }
    }
}
