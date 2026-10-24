package io.github.sweetzonzi.machine_max.common.mech.part;

import cn.solarmoon.spark_core.pack.diagnostic.PackContentException;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.VariantAttr;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/**
 * 部件静态定义的公共基础，保存图标、变体及共享耐久配置。
 *
 * <p>具体种类继承此类，在自身 Codec 中声明专属字段，并由 {@link #kindId()} 对应到
 * {@link PartKinds} 的注册项。定义对象描述型号，不持有实例进度、库存或装配体归属。</p>
 * <p>构造时复制变体映射的结构；嵌套属性不作深复制。注册名在解码后设置一次，发布后定义及
 * 嵌套属性应保持只读。运行时查找必须经过 {@link PartDefinitions}，不能直接使用装载候选。</p>
 */
@Getter
public abstract class AbstractPartType {
    /**
     * 内容包提供的部件图标资源名；缺省图标是否需要回退由调用方决定。
     */
    public final ResourceLocation icon;
    /**
     * 是否由全部子零件共用一个耐久池；池上限为各子零件的共享耐久贡献之和。
     */
    public final boolean shareDurability;
    /**
     * 变体名到配置的只读映射；每个实例精确选择其中一个变体。
     */
    public final Map<String, VariantAttr> variants;
    /**
     * 普通部件物品使用的最大堆叠数；种类校验器负责检查其允许范围。
     */
    public final int maxStackSize;
    /**
     * 解码后由装载器赋值一次的注册名；与定义 JSON 的种类标识相互独立。
     */
    private ResourceLocation registryKey;

    /**
     * 兼容两种变体写法：单个配置视为 {@code default}，映射写法保留显式名称。
     *
     * <p>编码时，只有一个 {@code default} 变体的映射输出为单配置，其他情况输出名称映射。</p>
     */
    public static final Codec<Map<String, VariantAttr>> VARIANT_MAP_CODEC = Codec.either(
            VariantAttr.CODEC,
            Codec.unboundedMap(Codec.STRING, VariantAttr.CODEC)
    ).xmap(
            either -> either.map(
                    variant -> Map.of("default", variant),
                    map -> map
            ),
            map -> {
                if (map.size() == 1 && map.containsKey("default")) {
                    return Either.left(map.get("default"));
                } else {
                    return Either.right(map);
                }
            }
    );

    /**
     * 保存种类共用的静态字段并复制变体映射结构。
     *
     * <p>构造器不设置注册名，也不执行装载期校验。子类完成自身字段初始化后，由装载流程设置
     * 注册名、执行文件内检查，再进入整批候选的种类校验。</p>
     */
    protected AbstractPartType(ResourceLocation icon, boolean shareDurability, int maxStackSize,
                               Map<String, VariantAttr> variants) {
        this.icon = icon;
        this.shareDurability = shareDurability;
        this.maxStackSize = maxStackSize;
        this.variants = Collections.unmodifiableMap(new LinkedHashMap<>(variants));
    }

    /**
     * 返回在 {@link PartKinds} 注册的种类标识，用于选择定义 Codec、存档 Codec 和实例工厂。
     *
     * <p>它标识 Java 部件种类；具体型号的内容包注册名由 {@link #registryKey} 表示。</p>
     */
    public abstract String kindId();
    /**
     * 精确查询指定变体；名称未定义时返回 {@code null}，不自动选择默认变体。
     */
    public VariantAttr getVariant(String name) {
        return variants.get(name);
    }

    /**
     * 遍历构造时保存的变体名称顺序；只读映射的迭代器不支持删除。
     */
    public Iterator<String> getVariantIterator() {
        return variants.keySet().iterator();
    }

    /**
     * 返回定义声明的默认图标，供物品和预览入口使用。
     */
    public ResourceLocation getDefaultIcon() {
        return icon;
    }

    /**
     * 取得内容包型号注册名。
     *
     * @throws NullPointerException 装载器尚未设置注册名时抛出
     */
    public ResourceLocation getRegistryKey() {
        return Objects.requireNonNull(registryKey, "Unregistered part definition");
    }

    /**
     * 在解码完成后绑定型号注册名，只能赋值一次。
     *
     * @param id 该定义的内容包资源名，不能为 {@code null}
     * @throws IllegalStateException 已经绑定过注册名时抛出
     */
    public void setRegistryKey(ResourceLocation id) {
        if (registryKey != null) throw new IllegalStateException("Already registered: " + registryKey);
        registryKey = Objects.requireNonNull(id);
    }

    /**
     * 执行单文件内的模型、变体和子零件检查。
     *
     * <p>由装载器在解码并设置注册名后调用，使错误归属于当前文件。
     * {@link VariantAttr#validate(String)} 检查子零件集合并下发各项配置检查；
     * 跨部件必需引用则由 {@link PartDefinitions#validateAndPublish(boolean, Map)} 在候选收集完成后处理。</p>
     * <p>首个检查错误即中止当前定义，调用者不得将失败对象加入候选表。</p>
     *
     * @throws PackContentException 任一文件内配置检查失败时抛出
     */
    public final void validate() {
        for (Map.Entry<String, VariantAttr> variantEntry : variants.entrySet()) {
            variantEntry.getValue().validate(variantEntry.getKey());
        }
    }
}
