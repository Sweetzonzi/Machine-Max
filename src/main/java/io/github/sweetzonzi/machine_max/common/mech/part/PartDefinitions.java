package io.github.sweetzonzi.machine_max.common.mech.part;

import cn.solarmoon.spark_core.pack.diagnostic.PackContentException;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPartType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/**
 * 按逻辑端管理部件定义的装载与发布快照。
 *
 * <p>装载入口先调用 {@link #begin(boolean)} 清空该端可见定义，再在私有候选表收集文件。
 * {@link #validateAndPublish(boolean, Map)} 校验整批候选并剔除引用了失效定义的定义，
 * 最后一次性发布全部有效定义与普通部件索引。候选对象不会通过运行时查询暴露。</p>
 * <p>每端通过一个 {@code volatile} 快照引用发布，查询只读集合结构；定义对象及其嵌套属性
 * 不是深复制的，发布后调用者不得修改。装载流程必须保证同一逻辑端的批次不并发交错；
 * 本类不提供跨多次查询的事务，也不负责现存运行时部件的迁移。</p>
 */
public final class PartDefinitions {
    /**
     * 同一轮发布的全部定义、普通部件子集及就绪标志。
     *
     * <p>{@code ready=false} 表示尚未发布或正在装载；{@code ready=true} 允许有效定义数为零。
     * 制造与研究索引应据此区分“等待装载”与“装载完成但没有可用部件”。</p>
     */
    private record Snapshot(Map<ResourceLocation, AbstractPartType> all, Map<ResourceLocation, RegularPartType> regular, boolean ready) {
    }
    private static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of(), false);
    private static volatile Snapshot client = EMPTY;
    private static volatile Snapshot server = EMPTY;
    /**
     * 仅提供按逻辑端共享的静态定义表。
     */
    private PartDefinitions() {
    }

    /**
     * 开始指定逻辑端的新装载批次，立即清空可查询定义并置为未就绪。
     *
     * <p>读取线程不会在本轮校验期间取得上一轮定义；已有运行时对象的销毁由其宿主管理。</p>
     */
    public static void begin(boolean clientSide) {
        publish(clientSide, EMPTY);
    }

    /**
     * 判断该端是否已完成一轮发布；返回真不代表定义表非空。
     */
    public static boolean isReady(boolean clientSide) {
        return snapshot(clientSide).ready;
    }

    /**
     * 取得调用时的全部定义快照，集合结构只读。
     *
     * <p>保存此返回值会固定在该轮快照；需要跟随重载的普通部件查询使用 {@link #regularView(boolean)}。</p>
     */
    public static Map<ResourceLocation, AbstractPartType> all(boolean clientSide) {
        return snapshot(clientSide).all;
    }

    /**
     * 按注册名查询当前发布定义；缺失、被剔除或装载中返回 {@code null}。
     */
    public static AbstractPartType get(boolean clientSide, ResourceLocation id) {
        return all(clientSide).get(id);
    }

    /**
     * 取得调用时的普通部件只读快照，不包含扩展种类。
     */
    public static Map<ResourceLocation, RegularPartType> regular(boolean clientSide) {
        return snapshot(clientSide).regular;
    }

    /**
     * 创建跟随重载的普通部件只读视图，供长期持有映射引用的兼容入口使用。
     *
     * <p>每次查询或取得条目集合时读取当前快照；已经取得的条目集合与迭代器继续属于取得时那一轮。
     * 跨多次调用需要一致快照时应保存 {@link #regular(boolean)} 的结果。</p>
     */
    public static Map<ResourceLocation, RegularPartType> regularView(boolean clientSide) {
        return Collections.unmodifiableMap(new AbstractMap<>() {
            @Override
            public Set<Entry<ResourceLocation, RegularPartType>> entrySet() {
                return regular(clientSide).entrySet();
            }
            @Override
            public RegularPartType get(Object id) {
                return regular(clientSide).get(id);
            }
        });
    }

    /**
     * 校验整批候选，传播失效引用，然后发布有效子集。
     *
     * <p>本方法先清空该端发布表，再复制输入映射，然后反复执行 {@link AbstractPartType#validate(Map)}：
     * 每轮把本轮判定失败的候选从幸存集中去掉，再拿缩小后的幸存集重新校验其余候选，直到不再出现
     * 新的失败项。因此引用了失效定义的定义会被连带剔除，完整且有效的循环引用可以通过。</p>
     * <p>单个定义抛出的 {@link RuntimeException} 会转为诊断，其他候选继续处理；若异常本身携带
     * 消息组件（如 {@link PackContentException}），组件原样保留，供调用方按玩家语言渲染。
     * 若输入复制等批次级操作异常中止，发布表保持未就绪的空表，运行时工厂无法取得本轮候选。</p>
     *
     * @param clientSide 发布目标逻辑端
     * @param input 完整候选表；键必须与定义注册名一致，本方法不修改输入映射
     * @return 被剔除定义注册名到诊断消息的只读映射；空映射表示全部候选通过
     */
    public static Map<ResourceLocation, Component> validateAndPublish(boolean clientSide, Map<ResourceLocation, AbstractPartType> input) {
        begin(clientSide);
        Map<ResourceLocation, AbstractPartType> candidates = Map.copyOf(input);
        Map<ResourceLocation, Component> invalid = new LinkedHashMap<>();
        boolean changed;
        do {
            changed = false;
            Map<ResourceLocation, AbstractPartType> survivors = new LinkedHashMap<>(candidates);
            invalid.keySet().forEach(survivors::remove);
            Map<ResourceLocation, AbstractPartType> view = Collections.unmodifiableMap(survivors);
            for (var entry : survivors.entrySet()) {
                try {
                    entry.getValue().validate(view);
                } catch (RuntimeException error) {
                    invalid.put(entry.getKey(), messageOf(error));
                    changed = true;
                }
            }
        } while (changed);
        Map<ResourceLocation, AbstractPartType> valid = new LinkedHashMap<>(candidates);
        invalid.keySet().forEach(valid::remove);
        Map<ResourceLocation, RegularPartType> regular = new LinkedHashMap<>();
        valid.forEach((id, type) -> { if (type instanceof RegularPartType value) regular.put(id, value); });
        publish(clientSide, new Snapshot(Map.copyOf(valid), Map.copyOf(regular), true));
        return Collections.unmodifiableMap(new LinkedHashMap<>(invalid));
    }

    /**
     * 把校验失败转成上报用的消息组件。
     *
     * <p>内容错误自带可翻译组件，直接沿用；其他运行时异常只可能是代码缺陷，退化为异常消息。</p>
     */
    private static Component messageOf(Throwable error) {
        if (error instanceof PackContentException content) return content.getUserMessage();
        String message = error.getMessage();
        return Component.literal(message != null ? message : error.getClass().getSimpleName());
    }

    /**
     * 校验定义对象确实属于当前发布轮次。
     *
     * <p>使用对象身份而非注册名判等；相同注册名的上一轮对象也会被拒绝，防止工厂绕过重载后的校验结果。</p>
     *
     * @param clientSide 将要使用定义的逻辑端
     * @param type 待校验定义
     * @param <T> 定义的具体类型
     * @return 传入的同一对象
     * @throws IllegalArgumentException 对象未发布、已被剔除或所属轮次已失效时抛出
     */
    public static <T extends AbstractPartType> T requireCurrent(boolean clientSide, T type) {
        if (get(clientSide, type.getRegistryKey()) != type) {
            throw new IllegalArgumentException("Unpublished or stale part definition: " + type.getRegistryKey());
        }
        return type;
    }

    /**
     * 一次读取指定逻辑端的快照引用，供单次查询使用。
     */
    private static Snapshot snapshot(boolean clientSide) {
        return clientSide ? client : server;
    }

    /**
     * 以一次 volatile 引用写入发布该端的完整索引与就绪标志。
     */
    private static void publish(boolean clientSide, Snapshot value) {
        if (clientSide) client = value;
        else server = value;
    }
}
