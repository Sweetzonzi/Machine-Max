package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr;

import cn.solarmoon.spark_core.pack.diagnostic.PackContentException;
import com.mojang.serialization.MapCodec;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.AbstractSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.AmmoLoaderSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.AmmoRackSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.BasicSubsystemDynamicAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.BatterySubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.CameraSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.CarControllerSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.EngineSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.GearboxSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.ItemStorageSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.JointDriverSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.LauncherSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.LightingSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.MotorSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.MotorbikeControllerSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.RegenAmmoRackSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.ScriptableSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.SeatSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.SightSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.TrackControllerSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.TrackDriveSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.TransmissionSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.TurretDriverSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.WeaponControllerSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.WheelDriverSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.AbstractSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.AmmoLoaderSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.AmmoRackSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.BasicSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.BatterySubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.CameraSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.CarControllerSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.EngineSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.GearboxSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.ItemStorageSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.JointDriverSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.LauncherSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.LightingSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.MotorSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.MotorbikeControllerSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.RegenAmmoRackSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.ScriptableSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.SeatSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.SightSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.TrackControllerSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.TrackDriveSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.TransmissionSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.TurretDriverSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.WeaponControllerSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.WheelDriverSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.datagen.MMLanguageRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模组代码登记的子系统类型表，以及类型身份的唯一来源。
 *
 * <p>一条登记把某个类型的安装属性 Codec 与静态属性 Codec 绑在一起：部件 JSON 顶层的
 * {@code type} 分派到安装属性，子系统型号 JSON 的 {@code type} 分派到静态属性，两侧因此
 * 不可能出现"安装侧叫 A、静态侧叫 B"的错配。</p>
 *
 * <p>内建 21 条登记在静态初始化里完成；另有三个仍在开发中的类型（{@code joint_driver}、
 * {@code scriptable}、{@code battery}）的登记被注释掉，内容包无法引用它们，见静态初始化的说明。
 * 下游在自己的模组初始化阶段调用 {@link #register(ResourceLocation, Kind)}，必须早于内容包装载；
 * 内容包装载开始时 {@link #seal()} 封表，此后的登记立即失败。</p>
 *
 * <p>类型身份由本表派生：{@link #nameOf(MapCodec, Slot)} 按 Codec 实例身份反查命名空间 ID，
 * 属性类不声明第二份身份。表与 {@link SubsystemCodecs} 都不接触事件总线，纯解码路径不需要
 * 模组初始化。</p>
 */
public final class SubsystemKinds {

    /**
     * 一条子系统类型登记。
     *
     * <p>两个 Codec 都是该类型属性类持有的单例；解码后对象的 {@code codec()} 必须是同一实例，
     * 编码时也按实例身份反查类型 ID。</p>
     *
     * @param installCodec 安装属性（运行时动态属性）的 MapCodec
     * @param staticCodec  静态属性（型号 JSON）的 MapCodec
     */
    public record Kind(MapCodec<? extends AbstractSubsystemAttr> installCodec,
                       MapCodec<? extends AbstractSubsystemStaticAttr> staticCodec) {
        public Kind {
            if (installCodec == null || staticCodec == null)
                throw new IllegalArgumentException("Subsystem kind needs both install and static codecs");
        }
    }

    /** 一条登记里的两个 Codec 槽位，用于反查与诊断时指明查的是哪一侧。 */
    public enum Slot {
        INSTALL,
        STATIC;

        /** 诊断消息里的槽位名，对应翻译键的参数。 */
        public String label() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private static final Map<ResourceLocation, Kind> KINDS = new ConcurrentHashMap<>();

    private static volatile boolean sealed;

    static {
        register("basic", BasicSubsystemDynamicAttr.CODEC, BasicSubsystemStaticAttr.CODEC);
        register("seat", SeatSubsystemAttr.CODEC, SeatSubsystemStaticAttr.CODEC);
        register("item_storage", ItemStorageSubsystemAttr.CODEC, ItemStorageSubsystemStaticAttr.CODEC);
        register("engine", EngineSubsystemAttr.CODEC, EngineSubsystemStaticAttr.CODEC);
        register("motor", MotorSubsystemAttr.CODEC, MotorSubsystemStaticAttr.CODEC);
        register("car_controller", CarControllerSubsystemAttr.CODEC, CarControllerSubsystemStaticAttr.CODEC);
        register("motorbike_controller", MotorbikeControllerSubsystemAttr.CODEC, MotorbikeControllerSubsystemStaticAttr.CODEC);
        register("track_controller", TrackControllerSubsystemAttr.CODEC, TrackControllerSubsystemStaticAttr.CODEC);
        register("track_drive", TrackDriveSubsystemAttr.CODEC, TrackDriveSubsystemStaticAttr.CODEC);
        register("gearbox", GearboxSubsystemAttr.CODEC, GearboxSubsystemStaticAttr.CODEC);
        register("transmission", TransmissionSubsystemAttr.CODEC, TransmissionSubsystemStaticAttr.CODEC);
        // WIP：以下三个类型的字段契约尚未定稿（两侧属性类均标 @ApiStatus.Experimental）。
        // 登记一旦生效，内容包就能引用随时会变的字段，因此暂时不登记：解码遇到这些 ID 会按「未知类型」拒绝。
        // 契约定稿后取消注释即可，两侧 Codec 与登记结构都不用改。
        // register("joint_driver", JointDriverSubsystemAttr.CODEC, JointDriverSubsystemStaticAttr.CODEC);
        register("wheel_driver", WheelDriverSubsystemAttr.CODEC, WheelDriverSubsystemStaticAttr.CODEC);
        register("turret_driver", TurretDriverSubsystemAttr.CODEC, TurretDriverSubsystemStaticAttr.CODEC);
        // register("scriptable", ScriptableSubsystemAttr.CODEC, ScriptableSubsystemStaticAttr.CODEC);
        // register("battery", BatterySubsystemAttr.CODEC, BatterySubsystemStaticAttr.CODEC);
        register("lighting", LightingSubsystemAttr.CODEC, LightingSubsystemStaticAttr.CODEC);
        register("launcher", LauncherSubsystemAttr.CODEC, LauncherSubsystemStaticAttr.CODEC);
        register("ammo_loader", AmmoLoaderSubsystemAttr.CODEC, AmmoLoaderSubsystemStaticAttr.CODEC);
        register("ammo_rack", AmmoRackSubsystemAttr.CODEC, AmmoRackSubsystemStaticAttr.CODEC);
        register("weapon_controller", WeaponControllerSubsystemAttr.CODEC, WeaponControllerSubsystemStaticAttr.CODEC);
        register("camera", CameraSubsystemAttr.CODEC, CameraSubsystemStaticAttr.CODEC);
        register("sight", SightSubsystemAttr.CODEC, SightSubsystemStaticAttr.CODEC);
        register("regen_ammo_rack", RegenAmmoRackSubsystemAttr.CODEC, RegenAmmoRackSubsystemStaticAttr.CODEC);
    }

    private SubsystemKinds() {
    }

    /**
     * 登记一种子系统类型，同时给出安装与静态两个 Codec。
     *
     * <p>同一 ID 不能覆盖，同一 Codec 实例不能属于两条登记：类型 ID 靠 Codec 实例反查，
     * 共用实例会让 ID 不唯一。</p>
     *
     * @param id   带命名空间的类型 ID；扩展建议使用自己的模组命名空间
     * @param kind 该类型两侧 Codec 的登记项
     * @throws IllegalStateException    表已封表，或该 Codec 实例已属于另一条登记时抛出
     * @throws IllegalArgumentException 同一 ID 已登记时抛出
     */
    public static void register(ResourceLocation id, Kind kind) {
        if (sealed) throw new IllegalStateException(
                MMLanguageRegistry.ERROR_SUBSYSTEM_KIND_SEALED.component(id.toString()).getString());
        if (KINDS.putIfAbsent(id, kind) != null)
            throw new IllegalArgumentException("Duplicate subsystem type: " + id);
        for (Map.Entry<ResourceLocation, Kind> entry : KINDS.entrySet()) {
            if (entry.getKey().equals(id)) continue;
            if (sharesCodec(entry.getValue(), kind)) {
                KINDS.remove(id, kind);
                throw new IllegalStateException(
                        "Codec of subsystem type " + id + " is already registered as " + entry.getKey());
            }
        }
    }

    private static boolean sharesCodec(Kind a, Kind b) {
        Object aInstall = a.installCodec();
        Object aStatic = a.staticCodec();
        Object bInstall = b.installCodec();
        Object bStatic = b.staticCodec();
        return aInstall == bInstall || aInstall == bStatic || aStatic == bInstall || aStatic == bStatic;
    }

    /**
     * 查询已登记的类型。
     *
     * @param id   类型 ID，取自内容 JSON 的 {@code type}
     * @param slot 出错时用于指明是安装侧还是静态侧的 Codec
     * @return 该类型的登记项
     * @throws PackContentException 类型未登记时抛出；不回退到任何默认类型
     */
    public static Kind get(ResourceLocation id, Slot slot) {
        Kind kind = KINDS.get(id);
        if (kind == null) throw new PackContentException(
                MMLanguageRegistry.ERROR_SUBSYSTEM_TYPE_UNKNOWN.component(id.toString(), slot.label()));
        return kind;
    }

    /**
     * 按 Codec 实例反查类型 ID。
     *
     * <p>比较是引用比较而不是 {@code equals}：类型身份等于"登记时给出的那个 Codec 实例"，
     * 因此下游无法用等价克隆或覆写 {@code getType} 冒充另一种类型。</p>
     *
     * @param codec 属性类 {@code codec()} 返回的单例
     * @param slot  该 Codec 应处的槽位
     * @return 该 Codec 所属的登记 ID
     * @throws PackContentException Codec 不在该槽位的任何登记项里时抛出
     */
    public static ResourceLocation nameOf(MapCodec<?> codec, Slot slot) {
        for (Map.Entry<ResourceLocation, Kind> entry : KINDS.entrySet()) {
            Kind kind = entry.getValue();
            MapCodec<?> registered = slot == Slot.INSTALL ? kind.installCodec() : kind.staticCodec();
            if (registered == codec) return entry.getKey();
        }
        throw new PackContentException(
                MMLanguageRegistry.ERROR_SUBSYSTEM_CODEC_UNREGISTERED.component(slot.label()));
    }

    /**
     * 封表：内容包装载开始时调用，之后只读。
     *
     * <p>封表是确定登记窗口的手段——迟到登记让装载失败，而不是让排在后面的文件悄悄生效。
     * 表按进程保留，内容包 reload 不清表也不重新开放登记。重复调用无副作用。</p>
     */
    public static void seal() {
        sealed = true;
    }

    private static void register(String path, MapCodec<? extends AbstractSubsystemAttr> installCodec,
                                 MapCodec<? extends AbstractSubsystemStaticAttr> staticCodec) {
        register(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, path), new Kind(installCodec, staticCodec));
    }
}
