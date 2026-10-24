package io.github.sweetzonzi.machine_max.common.mech.part;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPartType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.RegularPartData;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模组代码注册的部件种类表，以及定义 JSON 的分派 Codec。
 *
 * <p>种类把定义类、动态存档类、两个 Codec 与构造回调绑定在一起。注册应在内容包装载前完成，
 * 表按进程保留，重载内容包不会清除种类。内容包只能选择已注册种类，不能自行注册 Java 工厂。</p>
 * <p>内建 {@code regular} 对应普通部件；扩展种类建议使用带模组命名空间的资源名。
 * {@link #CODEC} 只负责定义的格式分派，跨定义引用的校验和发布由 {@link PartDefinitions} 承担。</p>
 */
public final class PartKinds {
    private static final Map<String, PartKind<?, ?>> KINDS = new ConcurrentHashMap<>();
    static {
        register("regular", new PartKind<>(RegularPartType.class, RegularPartType.CODEC,
                RegularPartData.class, RegularPartData.CODEC, RegularPart::new,
                (type, data, level, mode, context) -> new RegularPart(data, type, level, mode, context),
                (type, context) -> {
                    if (type.variants.isEmpty()) throw new IllegalArgumentException("Part needs at least one variant");
                    if (type.maxStackSize < 1 || type.maxStackSize > 99) throw new IllegalArgumentException("Invalid stack size");
                }));
    }

    /**
     * 种类表按进程共享，禁止实例化。
     */
    private PartKinds() {
    }

    /**
     * 注册一种部件的类型、编解码器和回调；同名种类不能覆盖。
     *
     * @param id {@code regular} 或可被资源名解析器接受的种类标识；扩展应使用命名空间
     * @param kind 类型配套且回调非空的种类描述
     * @throws IllegalArgumentException 标识不合法时抛出
     * @throws IllegalStateException 同一标识已注册时抛出
     */
    public static void register(String id, PartKind<?, ?> kind) {
        if (!id.equals("regular") && net.minecraft.resources.ResourceLocation.tryParse(id) == null) {
            throw new IllegalArgumentException("Invalid part kind: " + id);
        }
        if (KINDS.putIfAbsent(id, kind) != null) throw new IllegalStateException("Duplicate part kind: " + id);
    }

    /**
     * 查询已注册种类。
     *
     * @param id 定义的 {@code kindId()} 或 JSON 的 {@code type}
     * @return 对应种类描述
     * @throws IllegalArgumentException 种类未注册时抛出；不会退回普通部件
     */
    public static PartKind<?, ?> get(String id) {
        PartKind<?, ?> result = KINDS.get(id);
        if (result == null) throw new IllegalArgumentException("Unknown part type: " + id);
        return result;
    }

    /**
     * 定义 JSON 的严格类型分派入口。
     *
     * <p>只有完全缺少 {@code type} 字段才默认选 {@code regular}；显式空值、非字符串和未知种类
     * 均返回解码错误。选定种类后把完整对象交给其定义 Codec，不尝试其他种类。</p>
     * <p>编码时核对定义 Java 类与注册项匹配。普通部件使用自身 Codec 的字段布局，
     * 扩展种类则写入 {@code kindId()}，覆盖其 Codec 可能产出的同名 {@code type} 字段。</p>
     */
    public static final Codec<AbstractPartType> CODEC = new Codec<>() {
        /**
         * 按字段是否存在决定默认种类，再委托所选定义 Codec 解码。
         */
        @Override
        public <N> DataResult<Pair<AbstractPartType, N>> decode(DynamicOps<N> ops, N input) {
            return ops.getMap(input).flatMap(map -> {
                boolean present = map.entries().anyMatch(entry -> ops.getStringValue(entry.getFirst())
                        .result().filter("type"::equals).isPresent());
                DataResult<String> id = present ? ops.getStringValue(map.get("type")) : DataResult.success("regular");
                return id.flatMap(name -> {
                    PartKind<?, ?> kind = KINDS.get(name);
                    if (kind == null) return DataResult.error(() -> "Unknown part type: " + name);
                    return kind.definitionCodec().decode(ops, input).map(pair -> Pair.of(pair.getFirst(), pair.getSecond()));
                });
            });
        }

        /**
         * 检查定义类型，并在扩展种类编码结果中写入权威种类标识。
         */
        @Override
        public <N> DataResult<N> encode(AbstractPartType value, DynamicOps<N> ops, N prefix) {
            PartKind<?, ?> kind = KINDS.get(value.kindId());
            if (kind == null || !kind.definitionClass().isInstance(value)) {
                return DataResult.error(() -> "Unregistered definition class for " + value.kindId());
            }
            return encodeDefinition(kind, value, ops).flatMap(encoded -> {
                if (value.kindId().equals("regular")) return ops.mergeToMap(prefix, ops.getMap(encoded).getOrThrow());
                return ops.getMap(encoded).flatMap(map -> {
                    var builder = ops.mapBuilder();
                    map.entries().filter(entry -> !entry.getFirst().equals(ops.createString("type")))
                            .forEach(entry -> builder.add(entry.getFirst(), entry.getSecond()));
                    return builder.add("type", ops.createString(value.kindId())).build(prefix);
                });
            });
        }
    };

    /**
     * 将已核对类型的定义收窄为注册类，交由该种类的定义 Codec 编码。
     */
    private static <T extends AbstractPartType, N> DataResult<N> encodeDefinition(
            PartKind<T, ?> kind, AbstractPartType value, DynamicOps<N> ops) {
        return kind.definitionCodec().encodeStart(ops, kind.definitionClass().cast(value));
    }
}
