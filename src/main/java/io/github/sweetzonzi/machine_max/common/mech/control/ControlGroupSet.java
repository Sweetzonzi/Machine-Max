package io.github.sweetzonzi.machine_max.common.mech.control;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import lombok.Getter;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 控制组集合，作为 AbstractControllableSubsystem 的一个字段存在。<br>
 * 包含一个始终激活的 baseGroup 和一个可切换的子控制组列表。<br>
 * 激活切换时自动合并 baseGroup 和当前子组的绑定与输出目标。
 */
@Getter
public class ControlGroupSet {

    /** 始终激活的基础控制组，由 UGC 作者预设，玩家不可移除 */
    public final ControlGroup baseGroup;

    /** 所有可切换的子控制组 */
    public final List<ControlGroup> groups;

    /** 当前激活的子控制组索引，-1 表示无子组激活 */
    private int activeIndex;

    /** GUI 交互元素列表，始终可见，不占物理按键。运行时状态（TOGGLE/SLIDER）保存在元素自身 */
    private final List<AbstractGuiAction> guiActions = new ArrayList<>();

    public ControlGroupSet(ControlGroup baseGroup, List<ControlGroup> groups, List<AbstractGuiAction> guiActions, int activeIndex) {
        this.baseGroup = baseGroup;
        this.groups = groups != null ? List.copyOf(groups) : Collections.emptyList();
        this.guiActions.addAll(guiActions != null ? guiActions : Collections.emptyList());
        this.activeIndex = activeIndex;
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
     * 获取当前有效的控制模式。<br>
     * 优先使用当前激活子组（若不为 INHERIT），否则回退到 baseGroup 的模式。
     */
    public ControlMode getEffectiveControlMode() {
        ControlGroup active = getActiveGroup();
        if (active != null && active.controlMode != ControlMode.INHERIT) {
            return active.controlMode;
        }
        return baseGroup.controlMode;
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
     * 获取当前有效的移动输入输出目标：以 baseGroup 为基准，激活子组同频道覆盖合并。
     * 用于实际信号发送。
     */
    public Map<String, List<String>> getMergedMoveTargets() {
        ControlGroup active = getActiveGroup();
        if (active == null || active.moveTargets.isEmpty()) {
            return baseGroup.moveTargets;
        }
        Map<String, List<String>> result = new HashMap<>(baseGroup.moveTargets);
        result.putAll(active.moveTargets);
        return result;
    }

    /**
     * 获取当前有效的视角输入输出目标：以 baseGroup 为基准，激活子组同频道覆盖合并。
     * 用于实际信号发送。
     */
    public Map<String, List<String>> getMergedViewTargets() {
        ControlGroup active = getActiveGroup();
        if (active == null || active.viewTargets.isEmpty()) {
            return baseGroup.viewTargets;
        }
        Map<String, List<String>> result = new HashMap<>(baseGroup.viewTargets);
        result.putAll(active.viewTargets);
        return result;
    }

    /**
     * 获取当前有效的常规按键输入输出目标：以 baseGroup 为基准，激活子组同频道覆盖合并。
     * 用于实际信号发送。
     */
    public Map<String, List<String>> getMergedRegularTargets() {
        ControlGroup active = getActiveGroup();
        if (active == null || active.regularTargets.isEmpty()) {
            return baseGroup.regularTargets;
        }
        Map<String, List<String>> result = new HashMap<>(baseGroup.regularTargets);
        result.putAll(active.regularTargets);
        return result;
    }

    /**
     * 获取所有控制组的移动输出目标并集（baseGroup + 全部子组）。
     * 用于注册和显示所有可能的目标名称，实际发送信号时应使用 getMergedMoveTargets()。
     */
    public Map<String, List<String>> getAllMoveTargets() {
        Map<String, List<String>> result = new HashMap<>(baseGroup.moveTargets);
        for (ControlGroup group : groups) {
            for (Map.Entry<String, List<String>> entry : group.moveTargets.entrySet()) {
                result.merge(entry.getKey(), entry.getValue(), (a, b) -> {
                    List<String> merged = new ArrayList<>(a);
                    for (String s : b) {
                        if (!merged.contains(s)) merged.add(s);
                    }
                    return merged;
                });
            }
        }
        return result;
    }

    /**
     * 获取所有控制组的视角输出目标并集（baseGroup + 全部子组）。
     * 用于注册和显示所有可能的目标名称，实际发送信号时应使用 getMergedViewTargets()。
     */
    public Map<String, List<String>> getAllViewTargets() {
        Map<String, List<String>> result = new HashMap<>(baseGroup.viewTargets);
        for (ControlGroup group : groups) {
            for (Map.Entry<String, List<String>> entry : group.viewTargets.entrySet()) {
                result.merge(entry.getKey(), entry.getValue(), (a, b) -> {
                    List<String> merged = new ArrayList<>(a);
                    for (String s : b) {
                        if (!merged.contains(s)) merged.add(s);
                    }
                    return merged;
                });
            }
        }
        return result;
    }

    /**
     * 获取当前有效的主武器控制输出目标：以 baseGroup 为基准，激活子组同频道覆盖合并。
     * 用于实际信号发送。
     */
    public Map<String, List<String>> getMergedMainWeaponTargets() {
        ControlGroup active = getActiveGroup();
        if (active == null || active.mainWeaponTargets.isEmpty()) {
            return baseGroup.mainWeaponTargets;
        }
        Map<String, List<String>> result = new HashMap<>(baseGroup.mainWeaponTargets);
        result.putAll(active.mainWeaponTargets);
        return result;
    }

    /**
     * 获取当前有效的副武器控制输出目标：以 baseGroup 为基准，激活子组同频道覆盖合并。
     * 用于实际信号发送。
     */
    public Map<String, List<String>> getMergedSecondaryWeaponTargets() {
        ControlGroup active = getActiveGroup();
        if (active == null || active.secondaryWeaponTargets.isEmpty()) {
            return baseGroup.secondaryWeaponTargets;
        }
        Map<String, List<String>> result = new HashMap<>(baseGroup.secondaryWeaponTargets);
        result.putAll(active.secondaryWeaponTargets);
        return result;
    }

    /**
     * 获取所有控制组的常规按键输出目标并集（baseGroup + 全部子组）。
     * 用于注册和显示所有可能的目标名称，实际发送信号时应使用 getMergedRegularTargets()。
     */
    public Map<String, List<String>> getAllRegularTargets() {
        Map<String, List<String>> result = new HashMap<>(baseGroup.regularTargets);
        for (ControlGroup group : groups) {
            for (Map.Entry<String, List<String>> entry : group.regularTargets.entrySet()) {
                result.merge(entry.getKey(), entry.getValue(), (a, b) -> {
                    List<String> merged = new ArrayList<>(a);
                    for (String s : b) {
                        if (!merged.contains(s)) merged.add(s);
                    }
                    return merged;
                });
            }
        }
        return result;
    }

    /**
     * 获取所有控制组的主武器控制输出目标并集（baseGroup + 全部子组）。
     * 用于注册和显示所有可能的目标名称，实际发送信号时应使用 getMergedMainWeaponTargets()。
     */
    public Map<String, List<String>> getAllMainWeaponTargets() {
        Map<String, List<String>> result = new HashMap<>(baseGroup.mainWeaponTargets);
        for (ControlGroup group : groups) {
            for (Map.Entry<String, List<String>> entry : group.mainWeaponTargets.entrySet()) {
                result.merge(entry.getKey(), entry.getValue(), (a, b) -> {
                    List<String> merged = new ArrayList<>(a);
                    for (String s : b) {
                        if (!merged.contains(s)) merged.add(s);
                    }
                    return merged;
                });
            }
        }
        return result;
    }

    /**
     * 获取所有控制组的副武器控制输出目标并集（baseGroup + 全部子组）。
     * 用于注册和显示所有可能的目标名称，实际发送信号时应使用 getMergedSecondaryWeaponTargets()。
     */
    public Map<String, List<String>> getAllSecondaryWeaponTargets() {
        Map<String, List<String>> result = new HashMap<>(baseGroup.secondaryWeaponTargets);
        for (ControlGroup group : groups) {
            for (Map.Entry<String, List<String>> entry : group.secondaryWeaponTargets.entrySet()) {
                result.merge(entry.getKey(), entry.getValue(), (a, b) -> {
                    List<String> merged = new ArrayList<>(a);
                    for (String s : b) {
                        if (!merged.contains(s)) merged.add(s);
                    }
                    return merged;
                });
            }
        }
        return result;
    }

    /**
     * 获取所有控制组（baseGroup + 全部子组）中所有 ControlBinding 的目标并集 {频道 -> [目标名列表]}。
     * 用于注册和显示所有可能的目标名称，实际发送信号时应使用 getMergedBindings() 按激活态发送。
     */
    public Map<String, List<String>> getAllBindingTargets() {
        Map<String, List<String>> result = new HashMap<>();
        for (ControlGroup group : getAllGroups()) {
            for (ControlBinding binding : group.bindings) {
                result.merge(binding.channel, binding.targets, (a, b) -> {
                    List<String> merged = new ArrayList<>(a);
                    for (String s : b) {
                        if (!merged.contains(s)) merged.add(s);
                    }
                    return merged;
                });
            }
        }
        return result;
    }

    /**
     * 获取所有 GUI 交互元素的目标并集 {频道 -> [目标名列表]}。
     * 用于注册和显示所有可能的目标名称。
     */
    public Map<String, List<String>> getAllGuiActionTargets() {
        Map<String, List<String>> result = new HashMap<>();
        for (AbstractGuiAction action : guiActions) {
            result.merge(action.channel, action.targets, (a, b) -> {
                List<String> merged = new ArrayList<>(a);
                for (String s : b) {
                    if (!merged.contains(s)) merged.add(s);
                }
                return merged;
            });
        }
        return result;
    }

    /**
     * 获取 baseGroup + 全部子组的列表，用于遍历所有控制组。
     */
    private List<ControlGroup> getAllGroups() {
        List<ControlGroup> all = new ArrayList<>(1 + groups.size());
        all.add(baseGroup);
        all.addAll(groups);
        return all;
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
                    Collections.emptyMap(), Collections.emptyMap(),
                    Collections.emptyMap(), Collections.emptyList()),
            Collections.emptyList(),
            Collections.emptyList(),
            -1
    );

    /**
     * 判断此控制组集合是否为空（baseGroup 无任何输出目标和绑定，且无子组）。
     */
    public boolean isEmpty() {
        return groups.isEmpty()
                && baseGroup.moveTargets.isEmpty()
                && baseGroup.viewTargets.isEmpty()
                && baseGroup.regularTargets.isEmpty()
                && baseGroup.mainWeaponTargets.isEmpty()
                && baseGroup.secondaryWeaponTargets.isEmpty()
                && baseGroup.bindings.isEmpty();
    }

    public static final Codec<ControlGroupSet> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ControlGroup.CODEC.fieldOf("base_group").forGetter(s -> s.baseGroup),
            ControlGroup.CODEC.listOf().optionalFieldOf("groups", Collections.emptyList()).forGetter(s -> s.groups),
            AbstractGuiAction.CODEC.listOf().optionalFieldOf("gui_actions", Collections.emptyList()).forGetter(s -> s.guiActions),
            Codec.INT.optionalFieldOf("active_index", -1).forGetter(s -> s.activeIndex)
    ).apply(instance, ControlGroupSet::new));

    public static final StreamCodec<ByteBuf, ControlGroupSet> STREAM_CODEC = StreamCodec.composite(
            ControlGroup.STREAM_CODEC, s -> s.baseGroup,
            ControlGroup.STREAM_CODEC.apply(ByteBufCodecs.list()), s -> s.groups,
            AbstractGuiAction.STREAM_CODEC.apply(ByteBufCodecs.list()), s -> s.guiActions,
            ByteBufCodecs.VAR_INT, s -> s.activeIndex,
            ControlGroupSet::new
    );
}
