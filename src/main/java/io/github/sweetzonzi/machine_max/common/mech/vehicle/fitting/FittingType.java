package io.github.sweetzonzi.machine_max.common.mech.vehicle.fitting;

import com.mojang.serialization.Codec;
import io.github.sweetzonzi.machine_max.MachineMax;
import net.minecraft.resources.ResourceLocation;

/**
 * 载具挂件型号的基类。
 *
 * <p>挂件是挂在载具上的硬件：首版只有履带（{@link TrackType}），后续的装甲板、装饰物
 * 也走同一个注册表与同一份 {@code fittings/} 目录。基类只承载所有挂件共有的外观
 * （{@link FittingVisual}）与注册键；各自的数据字段留在子类，基类不预设履带字段，
 * 也不承担运行时职责。</p>
 *
 * <p>{@link #CODEC} 用 JSON 的 {@code "type"} 分派到具体型号的 {@code MapCodec}；
 * 首版只有 {@code "track"}，未知取值抛 {@link IllegalArgumentException}，由内容包加载器
 * 记为内容包错误。</p>
 */
public abstract class FittingType {
    /**
     * 缺省型号 id：内容包 fixture {@code fittings/default.json}。
     *
     * <p>物品缺少 {@code machine_max:fitting_type} 组件、旧存档缺少型号字段时都回落到它。</p>
     */
    public static final ResourceLocation DEFAULT_ID =
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "default");

    private final FittingVisual visual;
    private ResourceLocation registryKey;

    protected FittingType(FittingVisual visual) {
        this.visual = visual;
    }

    /** JSON {@code "type"} 的取值，也是 dispatch 的路由键。 */
    public abstract String getSerializedName();

    /**
     * 型号 id 对应的名称翻译键。
     *
     * <p>格式是 {@code <命名空间>.fitting.<型号名>}，例如 {@code machine_max:2s38_track}
     * 对应 {@code machine_max.fitting.2s38_track}。内容包在自己的 {@code lang/} 里提供，
     * 物品名与创造栏条目都取这个键。</p>
     *
     * @param id 型号 id
     * @return 翻译键
     */
    public static String nameKey(ResourceLocation id) {
        return id.getNamespace() + ".fitting." + id.getPath().replace('/', '.');
    }

    /** 本型号的名称翻译键。 */
    public String nameKey() {
        return nameKey(getRegistryKey());
    }

    public FittingVisual visual() {
        return visual;
    }

    /**
     * 内容包加载时写入的注册键（型号 id）。
     *
     * @throws IllegalStateException 型号尚未注册
     */
    public ResourceLocation getRegistryKey() {
        if (registryKey == null) throw new IllegalStateException("FittingType not registered");
        return registryKey;
    }

    /**
     * 内容包加载时写入注册键；同一个对象重复写入是代码错误。
     *
     * @throws UnsupportedOperationException 该型号已经注册过
     */
    public void setRegistryKey(ResourceLocation key) {
        if (this.registryKey != null) {
            throw new UnsupportedOperationException("FittingType already registered as " + this.registryKey);
        }
        this.registryKey = key;
    }

    public static final Codec<FittingType> CODEC = Codec.STRING.dispatch(
            "type",
            FittingType::getSerializedName,
            name -> switch (name) {
                case "track" -> TrackType.MAP_CODEC;
                default -> throw new IllegalArgumentException("未知载具配件类型: " + name);
            }
    );
}
