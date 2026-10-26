package io.github.sweetzonzi.machine_max.common.item.prop;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.ItemAnimatable;
import cn.solarmoon.spark_core.animation.model.ModelIndex;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.item.ICustomModelItem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.fitting.FittingType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.fitting.FittingVisual;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import io.github.sweetzonzi.machine_max.util.MMMath;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * 载具配件物品：一件物品表示一个配件，具体型号由 {@code machine_max:fitting_type} 组件给出。
 *
 * <p>本类只负责通用的表现：名称取型号 id 的翻译键、物品栏优先画型号自带的 2D 图标、
 * 没有图标时改画单节 3D 模型。各类配件自己的交互与玩法由子类承担
 * （履带是 {@link TrackItem}），基类不预设任何履带字段。</p>
 *
 * <p>型号是内容包数据，物品只保存型号 id：同一个物品注册项下的不同型号靠
 * {@code machine_max:fitting_type} 组件区分，与零件物品用 {@code machine_max:part_type}
 * 区分部件类型同构。</p>
 */
public class FittingItem extends Item implements ICustomModelItem {
    /** 2D 图标使用的通用平面模型，与零件物品共用同一张 128×128 图标底板。 */
    private static final ModelIndex ICON_MODEL = new ModelIndex("item",
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "item_icon_2d_128x"));

    /** 单节模型在 GUI 里的放大倍数：节模型只有 0.4 m 宽，需要放大才看得清。 */
    private static final float MODEL_SCALE_GUI = 2.0f;
    /** 单节模型在手持 / 掉落物里的放大倍数。 */
    private static final float MODEL_SCALE_HAND = 1.0f;

    public FittingItem() {
        super(new Properties().stacksTo(1));
    }

    /**
     * 物品携带的型号 id；组件缺失时返回 {@link FittingType#DEFAULT_ID}。
     *
     * @param stack 配件物品
     * @return 型号 id，永不为 {@code null}
     */
    public static ResourceLocation idOf(ItemStack stack) {
        ResourceLocation id = stack.get(MMDataComponents.getFITTING_TYPE());
        return id == null ? FittingType.DEFAULT_ID : id;
    }

    /**
     * 解析物品携带的配件型号。
     *
     * <p>组件缺失时回落 {@link FittingType#DEFAULT_ID}；型号不存在时返回 {@code null}。
     * 调用方按侧别取注册表（客户端 / 服务端各一份），因此必须传入 {@code level}。
     * 本方法会被渲染路径按帧调用，因此不打日志；数据问题的告警在交互入口
     * （如 {@link TrackItem#resolveType}）给出。</p>
     *
     * @param level 用于选择客户端 / 服务端型号注册表
     * @param stack 配件物品
     * @return 型号；缺失或不存在时返回 {@code null}
     */
    @Nullable
    public static FittingType typeOf(Level level, ItemStack stack) {
        return MMDynamicRes.getFittingType(level, idOf(stack));
    }

    /**
     * 物品携带型号的外观；型号缺失时返回 {@code null}，渲染退回普通物品模型。
     */
    @Nullable
    public static FittingVisual visualOf(Level level, ItemStack stack) {
        FittingType type = typeOf(level, stack);
        return type == null ? null : type.visual();
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(FittingType.nameKey(idOf(stack)));
    }

    @Override
    public IAnimatable<?> createItemAnimatable(ItemStack stack, Level level, ItemDisplayContext context) {
        ItemAnimatable animatable = new ItemAnimatable(stack, level);
        FittingVisual visual = visualOf(level, stack);
        if (visual == null) return animatable;

        if (context == ItemDisplayContext.GUI && visual.hasIcon()) {
            // GUI 且有图标：画 128×128 平面图标，2D 图标不受模型轴向影响
            animatable.getModelController().setModel(ICON_MODEL);
            animatable.getModelController().setTextureLocation(visual.icon());
        } else {
            // 没有图标，或不在 GUI：画型号自己的单节模型
            animatable.getModelController().setModel(new ModelIndex("fitting", visual.model()));
            animatable.getModelController().setTextureLocation(visual.texture());
        }
        cacheAnimatable(stack, context, animatable);
        return animatable;
    }

    @Override
    public boolean use2dModel(ItemStack stack, Level level, ItemDisplayContext displayContext) {
        if (displayContext != ItemDisplayContext.GUI) return false;
        FittingVisual visual = visualOf(level, stack);
        return visual != null && visual.hasIcon();
    }

    @Override
    public Vector3f getRenderScale(ItemStack stack, Level level, ItemDisplayContext displayContext) {
        if (use2dModel(stack, level, displayContext)) return MMMath.ONE;
        float scale = displayContext == ItemDisplayContext.GUI ? MODEL_SCALE_GUI : MODEL_SCALE_HAND;
        return new Vector3f(scale);
    }
}
