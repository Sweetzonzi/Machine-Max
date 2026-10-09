package io.github.sweetzonzi.machine_max.common.registry

import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoSupplier
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.syncher.EntityDataSerializer
import net.minecraft.resources.ResourceLocation
import java.util.Optional

/**
 * 自定义 `SynchedEntityData` 序列化器。
 *
 * 序列化器必须注册进 NeoForge 的受管注册表 `NeoForgeRegistries.ENTITY_DATA_SERIALIZERS`，
 * 而不是原版表：原版入口 `EntityDataSerializers.registerSerializer` 对 mod 直接抛异常，
 * 因为那张表是普通的自增身份表、两端 id 没有保障。NeoForge 注册表是 `sync(true)` 的受管注册表，
 * 两端内容一致，线号由 `256 + 注册表内 id` 换算得出（原版序列化器占用 0~255）。
 *
 * **实例与注册项必须是同一个对象**：`SynchedEntityData.defineId` 在子系统类的静态初始化里执行，
 * 那一刻 `DeferredHolder` 尚未绑定，所以下面先建好单例、注册时用 `factory` 把它交出去。
 * 注册表按实例身份查 id，两端取到不同实例就会写出 -1 并抛 `Unregistered serializer`。
 *
 * 注册时机：由 [MachineMax] 构造器调用 [register]（与其它注册文件并列），早于任何子系统实例的创建。
 */
object MMEntityDataSerializers {

    /** 触发本 object 初始化，注册随之发生。 */
    @JvmStatic
    fun register() {
    }

    /**
     * 供给者指认：宿主 Part uuid + SubPart 名 + 子系统名。
     *
     * 传输开销为 16 字节 uuid + 两个 UTF-8 名；空值用 [IAmmoSupplier.SupplierRef.NONE] 表达。
     * 换用这三项而不是 SubPart 的 int id，是因为 int id 由会话内计数器分配、不进存档，
     * 而这个值要同时进存档（后进玩家靠它拿到当前指认），必须重启后仍然有效。
     */
    @JvmStatic
    val SUPPLIER_REF: EntityDataSerializer<IAmmoSupplier.SupplierRef> =
        EntityDataSerializer.forValueType(IAmmoSupplier.SupplierRef.STREAM_CODEC)

    /**
     * 可空的资源地址（`Optional<ResourceLocation>`）。
     *
     * 空值用 [Optional.empty] 表达，而不是在资源地址的键空间里占一个哨兵名字：
     * 资源地址没有 nil uuid 那样的"内容包造不出来"的值——`ResourceLocation.fromNamespaceAndPath`
     * 会校验命名空间与路径字符，哨兵只能是一个**合法**的名字，内容包真定义了同名条目就会撞车，
     * 而且取值处每个入口都得先判一次哨兵。原版表示可空同步值用的也是这套写法
     * （`EntityDataSerializers.OPTIONAL_COMPONENT` 一族）。
     *
     * 线号开销是 1 字节存在标志加字符串本身（[ResourceLocation.STREAM_CODEC] 编的就是 UTF-8 字符串）。
     * 与具体字段无关，凡是可空的资源地址都可以复用它。
     */
    @JvmStatic
    val OPTIONAL_RESOURCE_LOCATION: EntityDataSerializer<Optional<ResourceLocation>> =
        EntityDataSerializer.forValueType(ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC))

    init {
        MachineMax.REGISTER.entityDataSerializer<IAmmoSupplier.SupplierRef> {
            id = "supplier_ref"
            factory = { SUPPLIER_REF }
        }
        MachineMax.REGISTER.entityDataSerializer<Optional<ResourceLocation>> {
            id = "optional_resource_location"
            factory = { OPTIONAL_RESOURCE_LOCATION }
        }
    }
}
