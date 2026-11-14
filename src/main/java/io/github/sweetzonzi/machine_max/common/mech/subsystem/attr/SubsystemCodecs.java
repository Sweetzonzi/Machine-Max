package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr;

import cn.solarmoon.spark_core.pack.diagnostic.PackContentException;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.AbstractSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.AbstractSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.datagen.MMLanguageRegistry;
import net.minecraft.resources.ResourceLocation;

/**
 * 子系统属性的分派 Codec：按 JSON 顶层的 {@code type} 到 {@link SubsystemKinds} 选登记项，
 * 再把整个对象交给该登记项对应一侧的 Codec。
 *
 * <p>两个 Codec 都只在方法体内引用 {@link SubsystemKinds}，静态初始化只构造 Codec 对象。
 * 这条方向保证类初始化不成环：属性类的静态初始化引用本类，登记表的静态初始化引用属性类，
 * 两者不会互相等待。</p>
 *
 * <p>未知类型在这里以 {@link PackContentException} 拒绝，消息可翻译并携带未知 ID 与槽位；
 * 不落到 DFU 的英文 literal，也不回退到任何默认类型。子系统没有默认类型：{@code type}
 * 缺失、显式 null、空串与非字符串同样失败。</p>
 */
public final class SubsystemCodecs {

    /** 部件 JSON 里子系统安装项的 Codec，对应 {@link AbstractSubsystemAttr}。 */
    public static final Codec<AbstractSubsystemAttr> INSTALL_CODEC = new Codec<>() {
        @Override
        public <N> DataResult<Pair<AbstractSubsystemAttr, N>> decode(DynamicOps<N> ops, N input) {
            ResourceLocation id = readType(ops, input);
            SubsystemKinds.Kind kind = SubsystemKinds.get(id, SubsystemKinds.Slot.INSTALL);
            DataResult<Pair<AbstractSubsystemAttr, N>> decoded = decodeWith(kind.installCodec(), ops, input);
            return decoded.flatMap(pair -> {
                if (pair.getFirst().codec() != kind.installCodec())
                    return DataResult.error(() -> "Subsystem type " + id
                            + " is registered with a different install codec than the decoded attribute reports");
                return DataResult.success(pair);
            });
        }

        @Override
        public <N> DataResult<N> encode(AbstractSubsystemAttr value, DynamicOps<N> ops, N prefix) {
            ResourceLocation id = SubsystemKinds.nameOf(value.codec(), SubsystemKinds.Slot.INSTALL);
            return encodeWith(SubsystemKinds.get(id, SubsystemKinds.Slot.INSTALL).installCodec(),
                    value, id, ops, prefix);
        }
    };

    /** 子系统型号 JSON 的 Codec，对应 {@link AbstractSubsystemStaticAttr}。 */
    public static final Codec<AbstractSubsystemStaticAttr> STATIC_CODEC = new Codec<>() {
        @Override
        public <N> DataResult<Pair<AbstractSubsystemStaticAttr, N>> decode(DynamicOps<N> ops, N input) {
            ResourceLocation id = readType(ops, input);
            SubsystemKinds.Kind kind = SubsystemKinds.get(id, SubsystemKinds.Slot.STATIC);
            DataResult<Pair<AbstractSubsystemStaticAttr, N>> decoded = decodeWith(kind.staticCodec(), ops, input);
            return decoded.flatMap(pair -> {
                if (pair.getFirst().codec() != kind.staticCodec())
                    return DataResult.error(() -> "Subsystem type " + id
                            + " is registered with a different static codec than the decoded attribute reports");
                return DataResult.success(pair);
            });
        }

        @Override
        public <N> DataResult<N> encode(AbstractSubsystemStaticAttr value, DynamicOps<N> ops, N prefix) {
            ResourceLocation id = SubsystemKinds.nameOf(value.codec(), SubsystemKinds.Slot.STATIC);
            return encodeWith(SubsystemKinds.get(id, SubsystemKinds.Slot.STATIC).staticCodec(),
                    value, id, ops, prefix);
        }
    };

    private SubsystemCodecs() {
    }

    /**
     * 读取并校验 JSON 顶层的 {@code type}。
     *
     * @return 可解析的命名空间 ID；类型登记与否由调用方查表
     * @throws PackContentException 缺失、非字符串、空串或不是合法资源名时抛出
     */
    private static <N> ResourceLocation readType(DynamicOps<N> ops, N input) {
        MapLike<N> map = ops.getMap(input).result().orElse(null);
        if (map == null) throw invalidType("<not an object>");
        N raw = map.get("type");
        if (raw == null) throw invalidType("<missing>");
        String text = ops.getStringValue(raw).result().orElse(null);
        if (text == null || text.isEmpty()) throw invalidType(raw.toString());
        ResourceLocation id = ResourceLocation.tryParse(text);
        if (id == null) throw invalidType(text);
        return id;
    }

    private static PackContentException invalidType(String shown) {
        return new PackContentException(MMLanguageRegistry.ERROR_SUBSYSTEM_TYPE_INVALID.component(shown));
    }

    @SuppressWarnings("unchecked")
    private static <T, N> DataResult<Pair<T, N>> decodeWith(
            MapCodec<? extends T> codec, DynamicOps<N> ops, N input) {
        return ((Codec<T>) codec.codec()).decode(ops, input);
    }

    /**
     * 用登记项一侧的 Codec 编码，并把权威类型 ID 写回 {@code type}。
     *
     * <p>类型 ID 取自 {@link SubsystemKinds#nameOf} 的实例反查而不是对象自述，因此编码结果
     * 不会写出对象自称的另一种类型。</p>
     */
    @SuppressWarnings("unchecked")
    private static <T, N> DataResult<N> encodeWith(MapCodec<? extends T> codec, T value, ResourceLocation id,
                                                   DynamicOps<N> ops, N prefix) {
        return ((Codec<T>) codec.codec()).encode(value, ops, ops.empty())
                .flatMap(fields -> ops.getMap(fields))
                .flatMap(fields -> {
                    var builder = ops.mapBuilder();
                    fields.entries().forEach(entry -> builder.add(entry.getFirst(), entry.getSecond()));
                    return builder.add("type", ops.createString(id.toString())).build(prefix);
                });
    }
}
