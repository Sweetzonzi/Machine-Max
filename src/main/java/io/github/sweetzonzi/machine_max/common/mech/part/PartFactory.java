package io.github.sweetzonzi.machine_max.common.mech.part;

import com.mojang.serialization.*;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPartType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.RegularPartData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import java.util.Objects;

/**
 * 部件实例与动态存档的统一入口。
 *
 * <p>实例创建只接受当前逻辑端已发布的定义；种类分派由 {@link PartKinds} 完成。
 * 定义 JSON 的 {@code type} 选择定义编解码器，动态存档则由 {@code registry_key}
 * 查到定义后选择存档编解码器，二者不能混用。</p>
 * <p>校验方法不分配刚体。创建和恢复回调会构造原生物理资源，调用者必须在部件尚未入世、
 * 且没有物理步并发访问它的构造阶段执行；实例的装配体归属、入世与失败后的批次清理由调用者管理。</p>
 */
public final class PartFactory {
    /**
     * 仅提供静态工厂操作，禁止实例化。
     */
    private PartFactory() {
    }

    /**
     * 查询指定逻辑端当前发布的部件定义。
     *
     * @param clientSide {@code true} 查询客户端定义；{@code false} 查询服务端定义
     * @param id 内容包注册名，不是运行时部件 UUID
     * @return 已通过本轮装载校验的定义
     * @throws IllegalArgumentException 定义缺失、被校验剔除或该端正在装载时抛出
     */
    public static AbstractPartType resolve(boolean clientSide, ResourceLocation id) {
        AbstractPartType type = PartDefinitions.get(clientSide, id);
        if (type == null) throw new IllegalArgumentException("Unknown or invalid part: " + id);
        return type;
    }

    /**
     * 按定义新建部件，并由所属种类决定初始状态。
     *
     * <p>定义必须是当前发布表中的同一个对象；跨重载保留的定义引用会被拒绝。
     * 本方法不设置装配体，也不将刚体加入物理世界。</p>
     *
     * @param type 当前逻辑端的已发布定义
     * @param variant 变体名；{@code null} 按 {@code default} 校验并传给种类回调处理
     * @param level 实例所在世界，同时决定查询哪一端的定义
     * @param context 构造路径授予的实例权限，不能为 {@code null}
     * @return 已完成种类构造流程的部件
     * @throws IllegalArgumentException 定义未发布、引用已过期或变体不存在时抛出
     */
    public static AbstractPart<?> create(AbstractPartType type, String variant, Level level, PartCreationContext context) {
        PartDefinitions.requireCurrent(level.isClientSide(), type);
        validateVariant(type, variant == null ? "default" : variant);
        return PartKinds.get(type.kindId()).create(type, variant, level, context);
    }

    /**
     * 校验存档后委托所属种类恢复部件。
     *
     * <p>恢复模式必须显式指定：收纳物品使用完整恢复，蓝图使用骨架恢复。
     * 回调应保留输入 DTO，复制可变的子系统 NBT 后再交给加载逻辑，保证同一份模板可以重复使用。</p>
     *
     * @param data 与定义所属种类匹配的动态存档
     * @param level 恢复目标世界
     * @param mode 完整状态或仅结构的恢复策略，不能为 {@code null}
     * @param context 本次恢复路径授予的权限；权限不从存档恢复
     * @return 尚未由本工厂加入装配体或物理世界的部件
     * @throws IllegalArgumentException 存档的注册名、变体、UUID、数据类或子零件名不合法时抛出
     */
    public static AbstractPart<?> restore(AbstractPartData data, Level level, PartRestoreMode mode, PartCreationContext context) {
        AbstractPartType type = validateRestore(data, level.isClientSide());
        return PartKinds.get(type.kindId()).restore(type, data, level, Objects.requireNonNull(mode), context);
    }

    /**
     * 对单个存档执行无物理资源分配的预检，供装配体批量恢复使用。
     *
     * <p>检查定义可查询、变体存在、UUID 可解析、DTO 属于注册的数据类，且保存的子零件名均在
     * 所选变体内。允许存档只包含变体的一部分子零件；连接引用、重复 UUID 等装配体级约束由调用者检查。</p>
     *
     * @param data 待恢复存档；本方法不修改其内容
     * @param clientSide 目标逻辑端
     * @return 预检使用的当前定义
     * @throws IllegalArgumentException 任一预检条件不满足时抛出
     */
    public static AbstractPartType validateRestore(AbstractPartData data, boolean clientSide) {
        AbstractPartType type = resolve(clientSide, data.registryKey);
        validateVariant(type, data.variant);
        java.util.UUID.fromString(data.uuid);
        if (!PartKinds.get(type.kindId()).dataClass().isInstance(data)) {
            throw new IllegalArgumentException("Saved state type mismatch: " + data.registryKey);
        }
        if (!type.getVariant(data.variant).subParts.keySet().containsAll(data.subParts.keySet())) {
            throw new IllegalArgumentException("Unknown saved SubPart in " + data.registryKey);
        }
        return type;
    }

    /**
     * 使用普通部件默认权限创建实例。
     *
     * <p>适用于 MM 普通物品与载具路径，默认允许撬棍拆卸；需要限制拆卸的外部装配体应调用
     * {@link #create(AbstractPartType, String, Level, PartCreationContext)} 显式传入权限。</p>
     */
    public static RegularPart createRegular(RegularPartType type, String variant, Level level) {
        return (RegularPart) create(type, variant, level, PartCreationContext.REGULAR);
    }

    /**
     * 使用普通部件权限恢复存档，并在分配实例前拒绝其他种类的定义。
     *
     * @param data 普通部件存档
     * @param level 恢复目标世界
     * @param mode 调用路径要求的状态恢复策略
     * @return 普通部件实例
     * @throws IllegalArgumentException 注册名对应的定义不是普通部件或存档预检失败时抛出
     */
    public static RegularPart restoreRegular(RegularPartData data, Level level, PartRestoreMode mode) {
        if (!(resolve(level.isClientSide(), data.registryKey) instanceof RegularPartType)) {
            throw new IllegalArgumentException("Vehicle requires regular part: " + data.registryKey);
        }
        return (RegularPart) restore(data, level, mode, PartCreationContext.REGULAR);
    }

    /**
     * 按存档的 {@code registry_key} 选择专用 Codec 并解码 DTO。
     *
     * <p>只解码数据，不构造部件，也不执行 {@link #validateRestore(AbstractPartData, boolean)}
     * 的完整预检。定义必须已发布，因此该入口不能用于在部件定义装载前读取实例。</p>
     *
     * @param clientSide 存档所引用定义的逻辑端
     * @param ops JSON、NBT 等数据格式的操作实现
     * @param input 含 {@code registry_key} 的数据对象
     * @param <N> 数据格式的节点类型
     * @return 种类专用 DTO；字段解析或定义查询失败通过 {@link DataResult} 返回
     */
    public static <N> DataResult<? extends AbstractPartData> decodeData(boolean clientSide, DynamicOps<N> ops, N input) {
        return ops.getMap(input).flatMap(map -> ResourceLocation.CODEC.parse(ops, map.get("registry_key")))
                .flatMap(id -> {
                    AbstractPartType type = PartDefinitions.get(clientSide, id);
                    if (type == null) return DataResult.error(() -> "Unknown or invalid part: " + id);
                    return PartKinds.get(type.kindId()).dataCodec().parse(ops, input).map(data -> (AbstractPartData) data);
                });
    }

    /**
     * 按当前定义所属种类编码动态存档。
     *
     * <p>编码前核对 DTO 的 Java 类型，避免以普通部件 Codec 写出扩展部件的数据。
     * 输出字段由种类的存档 Codec 决定，本方法不追加定义 JSON 使用的 {@code type} 字段。</p>
     *
     * @param clientSide 定义所在逻辑端
     * @param ops 目标数据格式的操作实现
     * @param data 待编码的种类专用 DTO
     * @param <N> 数据格式的节点类型
     * @return 编码结果；定义缺失或 DTO 类型不匹配时返回错误
     */
    public static <N> DataResult<N> encodeData(boolean clientSide, DynamicOps<N> ops, AbstractPartData data) {
        AbstractPartType type = PartDefinitions.get(clientSide, data.registryKey);
        if (type == null) return DataResult.error(() -> "Unknown or invalid part: " + data.registryKey);
        return encode(PartKinds.get(type.kindId()), data, ops);
    }

    /**
     * 核对数据类后收窄泛型，调用该种类的存档 Codec；类型不匹配通过结果返回。
     */
    private static <D extends AbstractPartData, N> DataResult<N> encode(PartKind<?, D> kind, AbstractPartData data, DynamicOps<N> ops) {
        if (!kind.dataClass().isInstance(data)) return DataResult.error(() -> "Saved state type mismatch");
        return kind.dataCodec().encodeStart(ops, kind.dataClass().cast(data));
    }

    /**
     * 校验变体名能精确命中定义；未知名称直接拒绝，不选择其他变体。
     */
    private static void validateVariant(AbstractPartType type, String variant) {
        if (type.getVariant(variant) == null) throw new IllegalArgumentException("Unknown variant " + type.getRegistryKey() + "/" + variant);
    }
}
