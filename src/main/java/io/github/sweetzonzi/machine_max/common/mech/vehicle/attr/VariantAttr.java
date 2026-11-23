package io.github.sweetzonzi.machine_max.common.mech.vehicle.attr;

import cn.solarmoon.spark_core.animation.AnimResourceKey;
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet;
import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.animation.model.origin.OBone;
import cn.solarmoon.spark_core.animation.model.origin.OModel;
import cn.solarmoon.spark_core.animation.state.origin.OAnimStateMachineSet;
import cn.solarmoon.spark_core.pack.diagnostic.PackContentException;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.connector.ConnectorAttr;
import io.github.sweetzonzi.machine_max.datagen.MMLanguageRegistry;
import lombok.Getter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@Getter
public class VariantAttr {
    public final List<ResourceLocation> tags; //部件标签
    @Getter
    public final ResourceLocation model; // 模型路径
    public final Map<String, ResourceLocation> textures; // 纹理名 -> 纹理
    /**
     * 显式声明的动画集；空表示与 {@link #model} 同名。
     * <p>显式写 {@link #EMPTY_ANIM} 表示这一变体没有动画集，不报告缺失。</p>
     */
    public final Optional<ResourceLocation> animations;
    /**
     * 显式声明的控制器集；空表示与 {@link #model} 同名。
     * <p>选中一整套控制器，不是文件名、单个控制器名或事件名。</p>
     */
    public final Optional<ResourceLocation> controllers;
    public final Map<String, SubPartAttr> subParts; //子部件名称-子部件属性
    /**
     * 根子部件名称：质量最大的子部件；质量相同时取 {@code sub_parts} 声明顺序中的第一个。
     * <p>作为部件刚体初始布放与预览动画体（PartAnimatable）的绝对锚点，两者必须一致。</p>
     */
    private final String rootSubPartName;

    public static final ResourceLocation EMPTY_TEXTURE = ResourceLocation.withDefaultNamespace("missingno");
    public static final ResourceLocation EMPTY_ANIM = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "empty");

    /** 信号保留地址（{@code "local"} = RegularPart、{@code "global"} = 装配体）：不得用作连接点 / 子系统 / 交互区名，可作为 signal_targets 的目标 */
    public static final Set<String> RESERVED_SIGNAL_NAMES = Set.of("local", "global");

    public static final Codec<Map<String, ResourceLocation>> TEXTURES_CODEC = Codec.either(
            ResourceLocation.CODEC,
            Codec.unboundedMap(Codec.STRING, ResourceLocation.CODEC)
    ).xmap(
            either -> either.map(
                    texture -> Map.of("default", texture),
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
     * 零件映射编解码器，支持两种格式：
     * <ul>
     *   <li><b>简写</b>（仅有一个零件时）：直接写 SubPartAttr 对象，自动使用默认零件名 {@code "sub_part.machine_max.main"}</li>
     *   <li><b>完整</b>（多个零件时）：{@code Map<String, SubPartAttr>} 键值对形式</li>
     * </ul>
     */
    public static final Codec<Map<String, SubPartAttr>> SUB_PART_MAP_CODEC = Codec.either(
            SubPartAttr.CODEC,
            SubPartAttr.MAP_CODEC
    ).xmap(
            either -> either.map(
                    subPart -> Map.of("sub_part.machine_max.main", subPart),
                    map -> map
            ),
            map -> {
                if (map.size() == 1 && map.containsKey("sub_part.machine_max.main")) {
                    return Either.left(map.get("sub_part.machine_max.main"));
                } else {
                    return Either.right(map);
                }
            }
    );

    public static final Codec<VariantAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.listOf().optionalFieldOf("tags", List.of()).forGetter(VariantAttr::getTags),
            ResourceLocation.CODEC.fieldOf("model").forGetter(VariantAttr::getModel),
            TEXTURES_CODEC.optionalFieldOf("textures", Map.of("default", EMPTY_TEXTURE)).forGetter(VariantAttr::getTextures),
            ResourceLocation.CODEC.optionalFieldOf("animations").forGetter(VariantAttr::getAnimations),
            ResourceLocation.CODEC.optionalFieldOf("controllers").forGetter(VariantAttr::getControllers),
            SUB_PART_MAP_CODEC.fieldOf("sub_parts").forGetter(VariantAttr::getSubParts)
    ).apply(instance, VariantAttr::new));

    public VariantAttr(List<ResourceLocation> tags, ResourceLocation model, Map<String, ResourceLocation> textures,
                       Optional<ResourceLocation> animations, Optional<ResourceLocation> controllers,
                       Map<String, SubPartAttr> subParts) {
        this.tags = tags;
        this.model = model;
        this.textures = textures;
        this.animations = animations;
        this.controllers = controllers;
        this.subParts = subParts;
        // 选取根子部件（质量最大者），供实体布放与预览动画体共用，保证锚点一致
        this.rootSubPartName = selectRootSubPartName(subParts);
        OModel oModel = OModel.getOrEmpty(new ModelIndex("part", model));
        if (oModel.equals(OModel.Companion.getEMPTY())){
            throw new PackContentException(Component.translatableWithFallback(
                    "error.machine_max.part.model_not_found",
                    "Model file not found at: %1$s", model.toString()));
        }
        if (getTextures().isEmpty()) {
            throw new PackContentException(Component.translatableWithFallback(
                    "error.machine_max.part.missing_textures", "Part textures must not be empty"));
        }
        // RegularPart 级按名寻址（Molang local.*）要求名称在单变体内无歧义
        validateUniqueNames();
        // 为每个子部件计算"自动排除骨骼"（其所有后代子部件的 start_bone），
        // 使嵌套子部件的骨骼不会被多个子部件重复宣称。需在构建碰撞体积之前完成，
        // 因为 getCollisionShape 内部会将配置 end_bones 与自动排除骨骼合并后再过滤。
        computeAutoEndBones(oModel);
        // 构建并缓存部件碰撞体积
        for (SubPartAttr subPartAttr : subParts.values()) {
            subPartAttr.getCollisionShape(this);
        }
    }

    /**
     * 选取根子部件：质量最大的子部件；质量相同时取 {@code sub_parts} 声明顺序中的第一个。
     *
     * @param subParts 子部件属性表
     * @return 根子部件名称，表为空时返回 null
     */
    private static String selectRootSubPartName(Map<String, SubPartAttr> subParts) {
        String best = null;
        float maxMass = -Float.MAX_VALUE;
        for (Map.Entry<String, SubPartAttr> entry : subParts.entrySet()) {
            float mass = entry.getValue().getMass();
            if (mass > maxMass) {
                maxMass = mass;
                best = entry.getKey();
            }
        }
        return best;
    }

    /**
     * 校验单个变体内「连接点 / 子系统 / 交互区」名称唯一，且不使用信号保留地址。
     * <p>三者共用信号命名空间；RegularPart 级按名寻址（Molang {@code local.*}）必须无歧义。
     * 作用域是单变体的 {@code sub_parts} 集合（不是 part 类型全局，避免多变体复用同名被误判）。</p>
     */
    private void validateUniqueNames() {
        Map<String, String> ownerByName = new HashMap<>();// 名称 -> "子零件名/类别"
        for (Map.Entry<String, SubPartAttr> entry : subParts.entrySet()) {
            String subPartName = entry.getKey();
            SubPartAttr attr = entry.getValue();
            for (String name : attr.connectors.keySet())
                checkNameUnique(name, "connector", subPartName, ownerByName);
            for (String name : attr.subsystems.keySet())
                checkNameUnique(name, "subsystem", subPartName, ownerByName);
            for (String name : attr.interactBoxes.keySet())
                checkNameUnique(name, "interact_box", subPartName, ownerByName);
        }
    }

    private void checkNameUnique(String name, String kind, String subPartName, Map<String, String> ownerByName) {
        if (RESERVED_SIGNAL_NAMES.contains(name)) {
            throw new PackContentException(Component.translatableWithFallback(
                    "error.machine_max.part.reserved_name",
                    "Name %1$s (%2$s of %3$s) in part variant %4$s is a reserved signal address and cannot be used for connectors/subsystems/interact boxes",
                    name, kind, subPartName, model.toString()));
        }
        String owner = subPartName + "/" + kind;
        String previous = ownerByName.putIfAbsent(name, owner);
        if (previous != null) {
            throw new PackContentException(Component.translatableWithFallback(
                    "error.machine_max.part.duplicate_name",
                    "Duplicate name %1$s in part variant %4$s: %2$s conflicts with %3$s",
                    name, previous, owner, model.toString()));
        }
    }

    /**
     * 根据模型骨骼层级，为每个子部件计算其所有后代子部件的 start_bone，
     * 作为该子部件的"自动排除骨骼"写入运行时缓存。
     * 逻辑与插件侧的 auto_end_bones 预览一致，但在此处作为权威数据在运行时计算。
     * @param oModel 部件的模型
     */
    private void computeAutoEndBones(OModel oModel) {
        Map<String, OBone> allBones = oModel.getBones();
        for (SubPartAttr subPart : subParts.values()) {
            List<String> auto = new ArrayList<>();
            String startBone = subPart.getStartBone();
            for (SubPartAttr other : subParts.values()) {
                if (other == subPart) continue;
                String otherStart = other.getStartBone();
                if (otherStart == null || otherStart.isEmpty()) continue;
                // 当本子部件 start_bone 为空（占据整个模型）时，其余所有子部件都应被排除；
                // 否则仅排除那些 start_bone 是本子部件 start_bone 子代的后代子部件。
                boolean isDescendant;
                if (startBone.isEmpty()) {
                    isDescendant = true;
                } else {
                    OBone otherBone = allBones.get(otherStart);
                    isDescendant = otherBone != null && otherBone.isChildOf(startBone);
                }
                if (isDescendant) auto.add(otherStart);
            }
            subPart.setAutoEndBones(auto);
        }
    }

    /**
     * 装载期检查：本变体必须声明至少一个子部件，并在整批定义发布后核对资源绑定。
     *
     * <p>子部件的跨引用检查依赖模型与区间，不能在属性构造器里做——{@code SubPartAttr} 与其中的
     * 子系统/命中框/交互框都在变体解码过程中构造，此时同级 {@code model} 尚未解码、
     * {@code start_bone}/{@code end_bones} 区间也未计算。</p>
     *
     * @param partId 本部件定义的注册名，仅用于错误信息
     * @param variantName 本变体的名称，仅用于错误信息
     * @throws PackContentException 变体没有子部件，或任一子部件检查失败时抛出
     */
    public void validate(ResourceLocation partId, String variantName) {
        if (subParts.isEmpty()) {
            throw new PackContentException(
                    MMLanguageRegistry.ERROR_PART_EMPTY_SUB_PARTS.component(variantName, model.toString()));
        }
        validateResourceBinding(partId, variantName, "animations", animations);
        validateResourceBinding(partId, variantName, "controllers", controllers);
        for (Map.Entry<String, SubPartAttr> subPartEntry : subParts.entrySet()) {
            subPartEntry.getValue().validate(this, subPartEntry.getKey());
        }
    }

    /**
     * 核对显式声明的资源集合确实存在。
     *
     * <p>隐式同名绑定找不到集合是正常情况：多数部件没有动画。显式指定却找不到是配置错误——
     * 资源键写错时动画不会播放、控制器不会运行，两端都没有日志，所以在装载期就拒绝该定义，
     * 不静默回退，也不留给 tick 反复报错。</p>
     *
     * <p>该检查依赖动画集与控制器集已经读入发布表。两个模块都在 {@code parts} 之前读取、
     * 且全部模块的读取在任何 {@code onFinish} 之前完成，因此晚校验阶段两张表都已完整。</p>
     */
    private void validateResourceBinding(ResourceLocation partId, String variantName,
                                         String field, Optional<ResourceLocation> declared) {
        if (declared.isEmpty()) return;
        ResourceLocation id = declared.get();
        if (EMPTY_ANIM.equals(id)) return;
        AnimResourceKey key = new AnimResourceKey("part", id);
        boolean found = "animations".equals(field)
                ? OAnimationSet.getOrNull(key) != null
                : OAnimStateMachineSet.getOrNull(key) != null;
        if (found) return;
        throw new PackContentException(Component.translatableWithFallback(
                "error.machine_max.part.resource_set_not_found",
                "Part %1$s variant %2$s declares %3$s %4$s but no %3$s/part/%5$s/ set exists in the content packs",
                partId, variantName, field, id, id.getPath()));
    }

    /**
     * 获取可用纹理列表
     */
    public List<ResourceLocation> getTextureList() {
        return textures.values().stream().toList();
    }

    /**
     * 有效动画集位置：显式声明值，缺省与 {@link #model} 同名。
     */
    public ResourceLocation getAnimationSet() {
        return animations.orElse(model);
    }

    /**
     * 有效控制器集位置：显式声明值，缺省与 {@link #model} 同名。
     */
    public ResourceLocation getControllerSet() {
        return controllers.orElse(model);
    }

    /**
     * 本变体是否有动画集；显式写 {@link #EMPTY_ANIM} 时为 {@code false}。
     */
    public boolean hasAnimationSet() {
        return !EMPTY_ANIM.equals(getAnimationSet());
    }

    /**
     * 获取指定名称的纹理
     */
    public ResourceLocation getTexture(String name) {
        return textures.getOrDefault(name, EMPTY_TEXTURE);
    }

    @Nullable
    public Iterator<Pair<String, String>> getConnectorIterator() {
        Set<Pair<String, String>> connectors = new HashSet<>();
        for (Map.Entry<String, SubPartAttr> subParts : this.subParts.entrySet()) {//遍历零件
            String subPartName = subParts.getKey();
            SubPartAttr subPart = subParts.getValue();
            for (Map.Entry<String, ConnectorAttr> connector : subPart.connectors.entrySet()) {//遍历零件的接口
                if (!connector.getValue().isInternal())
                    connectors.add(Pair.of(subPartName, connector.getKey()));//外部接口加入可用接口集合
            }
        }
        if (!connectors.isEmpty()) return connectors.iterator();
        else return null;
    }

    /**
     * @return 部件所有外部连接点名称与对应的接口属性 The external connectors of the part and their corresponding locator attributes.
     */
    public Map<Pair<String, String>, ConnectorAttr> getPartOutwardConnectors() {
        Map<Pair<String, String>, ConnectorAttr> connectors = new HashMap<>(1);//获取部件所有外部连接点名称与类型
        for (Map.Entry<String, SubPartAttr> entry : this.subParts.entrySet()) {
            String subPartName = entry.getKey();
            SubPartAttr subPart = entry.getValue();
            for (Map.Entry<String, ConnectorAttr> entry1 : subPart.connectors.entrySet()) {
                if (!entry1.getValue().isInternal())//外部零件连接点
                    connectors.put(Pair.of(subPartName, entry1.getKey()), entry1.getValue());
            }
        }
        return connectors;
    }
}
