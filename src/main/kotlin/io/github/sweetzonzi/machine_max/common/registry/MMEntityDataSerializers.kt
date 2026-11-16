package io.github.sweetzonzi.machine_max.common.registry

import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.mech.ammo.AmmoBelt
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AmmoRef
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ReloadJobProgress
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ReloadJobResults
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ReloadJobSpec
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
     * 弹药端身份：宿主 SubPart 的 int ID + 子系统在该子零件声明序中的位置。
     *
     * 传输开销为两个变长整数；空值用 [AmmoRef.NONE] 表达。
     * 这个值只在一次会话内指认同一台机器（服务端创建载体时分配 SubPart ID，随载具数据下发），
     * 来源关系本身不持久化，因此不需要重启后仍然有效。
     */
    @JvmStatic
    val AMMO_REF: EntityDataSerializer<AmmoRef> =
        EntityDataSerializer.forValueType(AmmoRef.STREAM_CODEC)

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

    /**
     * 可空的弹药内容（模式、长度、余量与回充配置/运行态）。
     *
     * 空值表示没有已装供弹数据，用 [Optional.empty] 表达而不是造一份零发 Belt。
     * 发射器用它同步已装 Belt；值是快照而不是持有者正在推进的实例——同值写入不会广播，
     * 所以写入方必须先复制（[AmmoBelt.syncCopy]）。
     */
    @JvmStatic
    val OPTIONAL_AMMO_BELT: EntityDataSerializer<Optional<AmmoBelt>> =
        EntityDataSerializer.forValueType(ByteBufCodecs.optional(AmmoBelt.STREAM_CODEC))

    /**
     * 可空的作业冻结分配（[ReloadJobSpec]：编号、运输者、提供者、冻结规格与预留份额）。
     *
     * 空值表示这一 tick 没有作业，用 [Optional.empty] 表达而不是在编号里占一个哨兵：编号是自增的
     * 恒正量，而时长为 0 是合法配置（没有机械段的机构），写成"编号 0 表示无作业"会把这两件事绑在一起。
     * 这五项在作业存续期间不变，因此只在受理、收尾与全量补推时下发。
     */
    @JvmStatic
    val OPTIONAL_RELOAD_JOB_SPEC: EntityDataSerializer<Optional<ReloadJobSpec>> =
        EntityDataSerializer.forValueType(ByteBufCodecs.optional(ReloadJobSpec.STREAM_CODEC))

    /**
     * 可空的作业动态样本（[ReloadJobProgress]：编号、进度基线、本阶段时长与阶段）。
     *
     * 与冻结分配分开：换段与换人重定时只改这四项，重发整份交付规格是白字节开销。
     * 编号是两支样本的关联键，客户端拿它把两份配成一条作业。
     */
    @JvmStatic
    val OPTIONAL_RELOAD_JOB_PROGRESS: EntityDataSerializer<Optional<ReloadJobProgress>> =
        EntityDataSerializer.forValueType(ByteBufCodecs.optional(ReloadJobProgress.STREAM_CODEC))

    /**
     * 按发生顺序排列的作业结束结果（[ReloadJobResults]：编号与是否已交付）。
     *
     * 一轮可以结束不止一条作业（零时长作业连续受理、交付），标量字段会丢掉前一条的终态。
     * 只保留最近若干条：这是同步值而不是日志，客户端用编号水位忽略已应用过的条目。
     */
    @JvmStatic
    val RELOAD_JOB_RESULTS: EntityDataSerializer<ReloadJobResults> =
        EntityDataSerializer.forValueType(ReloadJobResults.STREAM_CODEC)

    init {
        MachineMax.REGISTER.entityDataSerializer<AmmoRef> {
            id = "ammo_ref"
            factory = { AMMO_REF }
        }
        MachineMax.REGISTER.entityDataSerializer<Optional<ResourceLocation>> {
            id = "optional_resource_location"
            factory = { OPTIONAL_RESOURCE_LOCATION }
        }
        MachineMax.REGISTER.entityDataSerializer<Optional<AmmoBelt>> {
            id = "optional_ammo_belt"
            factory = { OPTIONAL_AMMO_BELT }
        }
        MachineMax.REGISTER.entityDataSerializer<Optional<ReloadJobSpec>> {
            id = "optional_reload_job_spec"
            factory = { OPTIONAL_RELOAD_JOB_SPEC }
        }
        MachineMax.REGISTER.entityDataSerializer<Optional<ReloadJobProgress>> {
            id = "optional_reload_job_progress"
            factory = { OPTIONAL_RELOAD_JOB_PROGRESS }
        }
        MachineMax.REGISTER.entityDataSerializer<ReloadJobResults> {
            id = "reload_job_results"
            factory = { RELOAD_JOB_RESULTS }
        }
    }
}

