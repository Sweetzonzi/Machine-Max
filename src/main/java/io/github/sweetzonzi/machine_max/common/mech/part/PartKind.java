package io.github.sweetzonzi.machine_max.common.mech.part;

import com.mojang.serialization.Codec;
import net.minecraft.world.level.Level;

/**
 * 一类部件的定义、动态存档与实例构造契约。
 *
 * <p>由模组在 {@link PartKinds#register(String, PartKind)} 注册。定义 Codec 只描述静态配置，
 * 存档 Codec 只描述实例状态；两个 Java 类供运行时分派时校验和收窄泛型。
 * 所有回调应非空，且必须使用这一组配套类型。</p>
 *
 * @param definitionClass 静态定义的 Java 类
 * @param definitionCodec 定义 JSON 的 Codec
 * @param dataClass 动态存档 DTO 的 Java 类
 * @param dataCodec 动态存档的 Codec
 * @param creator 新建实例回调；负责完成子类初始化和结构构造
 * @param restorer 存档恢复回调；必须遵守传入的恢复模式和实例权限
 * @param validator 全候选就绪后的校验器；不得构造刚体或修改发布表
 * @param <T> 该种类的定义类型
 * @param <D> 该种类的动态存档类型
 */
public record PartKind<T extends AbstractPartType, D extends AbstractPartData>(
        Class<T> definitionClass, Codec<T> definitionCodec,
        Class<D> dataClass, Codec<D> dataCodec,
        Creator<T> creator, Restorer<T, D> restorer, Validator<T> validator) {
    /**
     * 新建实例回调；返回部件前应完成结构初始化，入世和装配体注册由外层调用者负责。
     */
    @FunctionalInterface
    public interface Creator<T> {
        /**
         * 按已发布定义与指定变体构造实例；变体可为 {@code null}，表示 {@code default}。
         *
         * <p>构造失败时须释放本次分配的物理资源；不得把部分初始化的实例交给调用者。</p>
         */
        AbstractPart<?> create(T type, String variant, Level level, PartCreationContext context);
    }

    /**
     * 动态存档恢复回调；DTO、恢复模式和构造权限必须分别处理。
     */
    @FunctionalInterface
    public interface Restorer<T, D> {
        /**
         * 按模式恢复实例；骨架模式只采用结构信息，完整模式才加载保存的运行状态。
         *
         * <p>不得修改输入 DTO。可变 NBT 应先复制，构造失败时应清理已分配资源。</p>
         */
        AbstractPart<?> restore(T type, D data, Level level, PartRestoreMode mode, PartCreationContext context);
    }

    /**
     * 装载期校验回调；需要的其他部件必须通过校验上下文登记为依赖。
     */
    @FunctionalInterface
    public interface Validator<T> {
        /**
         * 检查种类约束与必需引用；失败抛出运行时异常，由发布流程记录诊断并剔除依赖者。
         */
        void validate(T type, PartValidationContext context);
    }

    /**
     * 将候选定义收窄到注册类并执行校验；类不匹配也视作该定义的校验失败。
     */
    void validate(AbstractPartType type, PartValidationContext context) {
        validator.validate(definitionClass.cast(type), context);
    }

    /**
     * 在统一工厂完成发布轮次和变体预检后，向种类新建回调传递具体定义。
     */
    AbstractPart<?> create(AbstractPartType type, String variant, Level level, PartCreationContext context) {
        return creator.create(definitionClass.cast(type), variant, level, context);
    }

    /**
     * 将定义和存档收窄到配套类型，向恢复回调原样传递恢复模式及本次权限。
     */
    AbstractPart<?> restore(AbstractPartType type, AbstractPartData data, Level level,
                            PartRestoreMode mode, PartCreationContext context) {
        return restorer.restore(definitionClass.cast(type), dataClass.cast(data), level, mode, context);
    }
}
