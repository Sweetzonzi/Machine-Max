package io.github.sweetzonzi.machine_max.client.render.gui.hud;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.item.prop.PdaData;
import io.github.sweetzonzi.machine_max.common.item.prop.PdaHelper;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.PartType;
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.recipe.PartFabricatingRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * 设计模式的 HUD 快捷栏叠层：管理 AUI Overlay Document 的创建、显隐与格位内容刷新。
 *
 * <p>原版快捷栏的取消由 {@code MMGuiManager} 负责，HUD 的逐帧绘制由 AUI 自身完成，都不在本类内。
 * 本类只做两件事：按"手持 PDA 的 designMode"同步显隐，以及把当前格位与格位绑定写成 DOM。</p>
 *
 * <p>文档一经创建即保留复用：退出设计模式只切隐藏类，不销毁文档，避免反复创建重建布局。
 * 退出世界时销毁，避免残留上一个服务器的资源。</p>
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = MachineMax.MOD_ID, value = Dist.CLIENT)
public final class PdaHotbarOverlay {
    private static final String AUI_DOC_PATH = "machine_max/pda/pda_hotbar.html";
    /** 文档根元素上的隐藏类：样式表据此让叠层整体不参与布局与绘制。 */
    private static final String HIDDEN_CLASS = "pda-hotbar-hidden";

    @Nullable
    private static Document overlayDocument;
    /** 上次写入的格位与选中态签名；未变化时不动 DOM。 */
    private static String shownSignature = "";

    private PdaHotbarOverlay() {
    }

    /** 按"手持 PDA 的 designMode"同步显隐；首次需要显示时创建文档。 */
    public static void refresh() {
        LocalPlayer player = Minecraft.getInstance().player;
        InteractionHand hand = player == null ? null : PdaHelper.heldPdaHand(player);
        ItemStack stack = hand == null ? ItemStack.EMPTY : player.getItemInHand(hand);
        boolean visible = hand != null && PdaHelper.getData(stack).designMode();
        if (!visible) {
            if (overlayDocument != null) setHidden(true);
            return;
        }
        if (overlayDocument == null) {
            overlayDocument = ApricityUI.createDocument(AUI_DOC_PATH);
            if (overlayDocument == null) return;
            shownSignature = "";
        }
        setHidden(false);
        updateContent(player, stack);
    }

    /** 移除文档。幂等。 */
    public static void dispose() {
        if (overlayDocument != null) {
            overlayDocument.remove();
            overlayDocument = null;
        }
        shownSignature = "";
    }

    /** 当前文档；尚未创建时为 null。 */
    @Nullable
    public static Document document() {
        return overlayDocument;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        refresh();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        dispose();
    }

    /** 给文档根元素切换隐藏类，显隐不依赖文档的创建与销毁。 */
    private static void setHidden(boolean hidden) {
        if (overlayDocument == null) return;
        Element root = overlayDocument.getDocumentElement();
        if (root == null) return;
        if (hidden) {
            root.getClassList().add(HIDDEN_CLASS);
        } else {
            root.getClassList().remove(HIDDEN_CLASS);
        }
    }

    /** 写入选中蓝图名、格位图标与状态类；签名未变时不动 DOM。 */
    private static void updateContent(LocalPlayer player, ItemStack stack) {
        if (overlayDocument == null) return;
        PdaData data = PdaHelper.getData(stack);
        String signature = signature(data);
        if (signature.equals(shownSignature)) return;
        shownSignature = signature;
        Level level = player.level();
        Element label = overlayDocument.getElementById("pda-hotbar-label");
        if (label != null) label.setTextContent(labelOf(level, data.shortcutAt(data.selected())));
        for (int i = 0; i < PdaData.SHORTCUT_COUNT; i++) {
            Element slot = overlayDocument.getElementById("pda-hotbar-slot-" + i);
            if (slot == null) continue;
            ResourceLocation recipeId = data.shortcutAt(i);
            setClass(slot, "selected", i == data.selected());
            setClass(slot, "empty", recipeId == null);
            setClass(slot, "blocked", recipeId != null && PdaHelper.partRecipeOf(level, recipeId) == null);
            Element icon = slot.querySelector(".hs-icon");
            if (icon != null) icon.setAttribute("src", iconOf(level, recipeId));
        }
    }

    /** 格位内容签名：只覆盖 HUD 实际显示的内容（当前格位与各格绑定）。 */
    private static String signature(PdaData data) {
        StringBuilder builder = new StringBuilder();
        builder.append(data.selected());
        for (int i = 0; i < PdaData.SHORTCUT_COUNT; i++) {
            builder.append('|').append(data.shortcutAt(i));
        }
        return builder.toString();
    }

    /** 选中蓝图名：零件配方取零件显示名，通用配方取产物名，查不到时给降级文案。 */
    private static String labelOf(Level level, @Nullable ResourceLocation recipeId) {
        if (recipeId == null) return Component.translatable("gui.machine_max.pda.hint.empty_slot").getString();
        RecipeHolder<FabricatingRecipe> holder = PdaHelper.recipeOf(level, recipeId);
        if (holder == null) return Component.translatable("gui.machine_max.pda.tag.unknown").getString();
        if (holder.value() instanceof PartFabricatingRecipe partRecipe) {
            ResourceLocation partTypeId = partRecipe.getPartType();
            if (partTypeId != null) return Component.translatable(partTypeId.toLanguageKey()).getString();
        }
        return holder.value().getResult().getHoverName().getString();
    }

    /** 条目图标：零件配方取零件默认图标，其余返回空串（不绘制纹理）。 */
    private static String iconOf(Level level, @Nullable ResourceLocation recipeId) {
        if (recipeId == null) return "";
        RecipeHolder<PartFabricatingRecipe> holder = PdaHelper.partRecipeOf(level, recipeId);
        if (holder == null) return "";
        ResourceLocation partTypeId = holder.value().getPartType();
        if (partTypeId == null) return "";
        PartType partType = PartType.get(level, partTypeId);
        return partType == null ? "" : partType.getDefaultIcon().toString();
    }

    private static void setClass(Element element, String className, boolean present) {
        if (present) {
            element.getClassList().add(className);
        } else {
            element.getClassList().remove(className);
        }
    }
}
