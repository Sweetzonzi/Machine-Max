package io.github.sweetzonzi.machine_max.common.mech.control;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 控制组集合，作为 AbstractControllableSubsystem 的一个字段存在。<br>
 * 包含一个始终激活的 baseGroup 和一个可切换的子控制组列表。<br>
 * 激活切换时自动合并 baseGroup 和当前子组的绑定与输出目标。
 */
public class ControlGroupSet {

    /** 始终激活的基础控制组，由 UGC 作者预设，玩家不可移除 */
    public final ControlGroup baseGroup;

    /** 所有可切换的子控制组 */
    public final List<ControlGroup> groups;

    /** 当前激活的子控制组索引，-1 表示无子组激活 */
    private int activeIndex = -1;

    /** GUI 交互元素列表，始终可见，不占物理按键。运行时状态（TOGGLE/SLIDER）保存在元素自身 */
    private final List<AbstractGuiAction> guiActions = new ArrayList<>();

    public ControlGroupSet(ControlGroup baseGroup, List<ControlGroup> groups) {
        this.baseGroup = baseGroup;
        this.groups = groups != null ? List.copyOf(groups) : Collections.emptyList();
    }

    /**
     * 获取当前激活的子控制组（若存在）。
     */
    @Nullable
    public ControlGroup getActiveGroup() {
        if (activeIndex >= 0 && activeIndex < groups.size()) {
            return groups.get(activeIndex);
        }
        return null;
    }

    /**
     * 获取当前激活的子控制组索引。
     */
    public int getActiveIndex() {
        return activeIndex;
    }

    /**
     * 切换到指定索引的子控制组。
     *
     * @param index 子控制组索引，-1 表示不激活任何子组
     * @return true 表示切换成功（索引有效且与之前不同），false 表示无变化或索引无效
     */
    public boolean activate(int index) {
        if (index == activeIndex) return false;
        if (index < -1 || index >= groups.size()) return false;
        this.activeIndex = index;
        return true;
    }

    /**
     * 获取当前所有有效的按键绑定（baseGroup + 当前子组）。
     */
    public List<ControlBinding> getMergedBindings() {
        List<ControlBinding> result = new ArrayList<>(baseGroup.bindings);
        ControlGroup active = getActiveGroup();
        if (active != null) {
            result.addAll(active.bindings);
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * 获取当前有效的移动输入输出目标。
     */
    public Map<String, List<String>> getMergedMoveTargets() {
        ControlGroup active = getActiveGroup();
        if (active != null && !active.moveTargets.isEmpty()) {
            return active.moveTargets;
        }
        return baseGroup.moveTargets;
    }

    /**
     * 获取当前有效的视角输入输出目标。
     */
    public Map<String, List<String>> getMergedViewTargets() {
        ControlGroup active = getActiveGroup();
        if (active != null && !active.viewTargets.isEmpty()) {
            return active.viewTargets;
        }
        return baseGroup.viewTargets;
    }

    /**
     * 获取当前有效的常规按键输入输出目标。
     */
    public Map<String, List<String>> getMergedRegularTargets() {
        ControlGroup active = getActiveGroup();
        if (active != null && !active.regularTargets.isEmpty()) {
            return active.regularTargets;
        }
        return baseGroup.regularTargets;
    }

    /** 获取 GUI 交互元素列表（不可变视图） */
    public List<AbstractGuiAction> getGuiActions() {
        return Collections.unmodifiableList(guiActions);
    }

    /**
     * 根据按键名查找第一个匹配的绑定（优先查当前子组，再查 baseGroup）。
     */
    @Nullable
    public ControlBinding findBindingByTrigger(String trigger) {
        ControlGroup active = getActiveGroup();
        if (active != null) {
            for (ControlBinding b : active.bindings) {
                if (b.trigger.equals(trigger)) return b;
            }
        }
        for (ControlBinding b : baseGroup.bindings) {
            if (b.trigger.equals(trigger)) return b;
        }
        return null;
    }

    /**
     * 根据索引查找绑定（baseGroup 索引即其列表索引，子组索引偏移 baseGroup 列表长度）。
     */
    @Nullable
    public ControlBinding findBindingByIndex(int index) {
        List<ControlBinding> merged = getMergedBindings();
        if (index >= 0 && index < merged.size()) {
            return merged.get(index);
        }
        return null;
    }

    /** 空控制组集合常量，无任何输出目标和绑定 */
    public static final ControlGroupSet EMPTY = new ControlGroupSet(
            new ControlGroup("base", ControlMode.INHERIT,
                    Collections.emptyMap(), Collections.emptyMap(),
                    Collections.emptyMap(), Collections.emptyList()),
            Collections.emptyList()
    );

    /**
     * 判断此控制组集合是否为空（baseGroup 无任何输出目标和绑定，且无子组）。
     */
    public boolean isEmpty() {
        return groups.isEmpty()
                && baseGroup.moveTargets.isEmpty()
                && baseGroup.viewTargets.isEmpty()
                && baseGroup.regularTargets.isEmpty()
                && baseGroup.bindings.isEmpty();
    }

    public static final Codec<ControlGroupSet> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ControlGroup.CODEC.fieldOf("base_group").forGetter(s -> s.baseGroup),
            ControlGroup.CODEC.listOf().optionalFieldOf("groups", Collections.emptyList()).forGetter(s -> s.groups),
            Codec.INT.optionalFieldOf("active_index", -1).forGetter(s -> s.activeIndex),
            AbstractGuiAction.CODEC.listOf().optionalFieldOf("gui_actions", Collections.emptyList()).forGetter(s -> s.guiActions)
    ).apply(instance, (baseGroup, groups, activeIndex, guiActions) -> {
        ControlGroupSet set = new ControlGroupSet(baseGroup, groups);
        set.activeIndex = activeIndex;
        set.guiActions.addAll(guiActions);
        return set;
    }));

    public static final StreamCodec<ByteBuf, ControlGroupSet> STREAM_CODEC = StreamCodec.composite(
            ControlGroup.STREAM_CODEC, s -> s.baseGroup,
            ControlGroup.STREAM_CODEC.apply(ByteBufCodecs.list()), s -> s.groups,
            ByteBufCodecs.VAR_INT, s -> s.activeIndex,
            AbstractGuiAction.STREAM_CODEC.apply(ByteBufCodecs.list()), s -> s.guiActions,
            (baseGroup, groups, activeIndex, guiActions) -> {
                ControlGroupSet set = new ControlGroupSet(baseGroup, groups);
                set.activeIndex = activeIndex;
                set.guiActions.addAll(guiActions);
                return set;
            }
    );
}
