package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import cn.solarmoon.spark_core.pack.diagnostic.PackContentException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemCodecs;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemKinds;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.AbstractSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.SubPartAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.VariantAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.connector.ConnectorAttr;
import io.github.sweetzonzi.machine_max.datagen.MMLanguageRegistry;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

@Getter
abstract public class AbstractSubsystemAttr {
    public final ResourceLocation modelName;//注册的子系统型号

    protected AbstractSubsystemAttr(ResourceLocation modelName) {
        this.modelName = modelName;
    }

    public abstract MapCodec<? extends AbstractSubsystemAttr> codec();

    /**
     * 类型 ID，由登记表按 {@link #codec()} 的实例身份反查。
     *
     * <p>属性类不另声明类型常量：类型身份等于登记时给出的那个 Codec 单例，因此等价克隆、
     * 覆写本方法或用同名 ID 都造不出第二种身份。解码前类型必须已登记，时序见
     * {@link SubsystemKinds#seal()}。</p>
     *
     * @return 该属性的命名空间类型 ID，例如 {@code machine_max:wheel_driver}
     * @throws PackContentException {@code codec()} 返回的实例未登记时抛出
     */
    public final ResourceLocation getType() {
        return SubsystemKinds.nameOf(codec(), SubsystemKinds.Slot.INSTALL);
    }

    public abstract AbstractSubsystem createSubsystem(ISubsystemHost owner, String name);

    public static final Codec<Map<String, List<String>>> SIGNAL_TARGETS_CODEC = Codec.unboundedMap(
            Codec.STRING,
            Codec.STRING.listOf()
    );

    /** 控制组预设的"空"哨兵，表示无预设 */
    public static final ResourceLocation NO_CONTROL_GROUP_PRESET = ResourceLocation.parse("machine_max:empty");

    /**
     * 获取控制组预设的 ResourceLocation。
     * 子类（如 SeatSubsystemAttr）如有预设则覆盖此方法返回实际 RL；
     * 无预设的子系统继承此默认实现返回 NO_CONTROL_GROUP_PRESET。
     */
    public ResourceLocation getControlGroupPresetRl() {
        return NO_CONTROL_GROUP_PRESET;
    }

    public static final Codec<AbstractSubsystemAttr> CODEC = SubsystemCodecs.INSTALL_CODEC;

    public static final Codec<Map<String, AbstractSubsystemAttr>> MAP_CODEC = Codec.unboundedMap(
            Codec.STRING,//子系统名称
            CODEC//子系统属性
    );

    /**
     * 取本安装项引用的型号定义，并完成类型绑定。
     *
     * <p>具体安装属性在解码构造器里调用本方法，因此型号缺失与"型号属于另一类子系统"都在
     * JSON 解码时暴露，不在出车或物理步才暴露。两端来源的选择口径是客户端型号表优先、
     * 服务端型号表兜底；两张表都没有该定义即内容错误。</p>
     *
     * <p>两侧类型 ID 都从 {@link SubsystemKinds} 派生后按值比较，属于内容级检查：登记级的
     * 两侧配对已由登记项本身保证。</p>
     *
     * @return 已确认类型的型号定义，具体属性据此强转成自己的静态属性类型
     * @throws PackContentException 型号不存在，或型号的类型与安装属性不同
     */
    protected AbstractSubsystemStaticAttr getStaticAttr() {
        var definition = getModelName();
        var staticAttr = MMDynamicRes.STATIC_SUBSYSTEM_ATTRS.get(definition);
        if (staticAttr == null)
            staticAttr = MMDynamicRes.SERVER_STATIC_SUBSYSTEM_ATTRS.get(definition);
        if (staticAttr == null) throw new PackContentException(
                MMLanguageRegistry.ERROR_SUBSYSTEM_DEFINITION_NOT_FOUND.component(
                        definition.toString(), getType().toString()));
        ResourceLocation expected = getType();
        ResourceLocation actual = staticAttr.getType();
        if (!expected.equals(actual)) throw new PackContentException(
                MMLanguageRegistry.ERROR_SUBSYSTEM_DEFINITION_TYPE_MISMATCH.component(
                        definition.toString(), expected.toString(), actual.toString()));
        return staticAttr;
    }

    public float getBasicDurability() {
        return getStaticAttr().basicDurability;
    }

    /**
     * 内容包装载期检查：本安装位引用的模型资源是否落在宿主子部件的区间内。
     *
     * <p>子系统属性在变体解码过程中构造，此时同级 {@code model} 字段尚未解码、子部件区间也未计算，
     * 因此这类检查不能在构造器里做，而由部件类型自己的
     * {@link io.github.sweetzonzi.machine_max.common.mech.part.AbstractPartType#validate()}
     * 在部件定义解码完成后统一调用。</p>
     *
     * <p>默认不检查；需要校验安装挂点的子系统覆盖本方法。</p>
     *
     * @param variant       子系统所属变体，提供模型与子部件表
     * @param subPartName   宿主子部件名称
     * @param subsystemName 本子系统名称，仅用于错误信息
     */
    public void validate(VariantAttr variant, String subPartName, String subsystemName) {
    }

    /**
     * 判断定位器是否属于宿主子部件的区间。
     *
     * <p>空名称表示"不引用模型"（使用宿主刚体原点与坐标轴），一律返回 {@code false}。</p>
     *
     * @param variant     子系统所属变体
     * @param subPartName 宿主子部件名称
     * @param locator     定位器名称
     * @return 定位器落在本子部件区间内时返回 true
     */
    protected static boolean hasLocator(VariantAttr variant, String subPartName, String locator) {
        if (locator == null || locator.isEmpty()) return false;
        SubPartAttr subPart = variant.getSubParts().get(subPartName);
        return subPart != null && subPart.getLocatorTransforms().containsKey(locator);
    }

    /**
     * 定位器不在宿主子部件区间内时的内容包异常。
     *
     * @param variant       子系统所属变体
     * @param subPartName   宿主子部件名称
     * @param subsystemName 本子系统名称
     * @param locator       声明但找不到的定位器名称；空名称按空串显示
     * @return 由调用方抛出的内容包异常
     */
    protected static PackContentException locatorNotFound(
            VariantAttr variant, String subPartName, String subsystemName, String locator) {
        String shown = locator == null || locator.isEmpty() ? "\"\"" : locator;
        return new PackContentException(
                MMLanguageRegistry.ERROR_PART_SUBSYSTEM_LOCATOR_NOT_FOUND.component(
                        variant.getModel().toString(), subPartName, subsystemName, shown));
    }

    /**
     * 要求受控连接点存在且为高级连接点。
     *
     * <p>轮驱、炮塔驱动、履带驱动与关节驱动都在宿主子部件的连接点表里按名称取
     * {@code AdvancedConnector}；名字写错或误用简单连接点时，它们在构造／握手阶段只打一条
     * error 日志然后静默失效，因此在这里提前拦成内容错误。</p>
     *
     * @param variant       子系统所属变体
     * @param subPartName   宿主子部件名称
     * @param subsystemName 本子系统名称
     * @param connectorName 声明受控的连接点名称
     */
    protected static void requireAdvancedConnector(
            VariantAttr variant, String subPartName, String subsystemName, String connectorName) {
        SubPartAttr subPart = variant.getSubParts().get(subPartName);
        if (subPart == null) return;
        ConnectorAttr connector = subPart.getConnectors().get(connectorName);
        if (connector == null) throw new PackContentException(
                MMLanguageRegistry.ERROR_PART_SUBSYSTEM_CONNECTOR_NOT_FOUND.component(
                        variant.getModel().toString(), subPartName, subsystemName, connectorName));
        if (connector.isSimpleConnector()) throw new PackContentException(
                MMLanguageRegistry.ERROR_PART_SUBSYSTEM_CONNECTOR_NOT_ADVANCED.component(
                        variant.getModel().toString(), subPartName, subsystemName, connectorName));
    }
}
