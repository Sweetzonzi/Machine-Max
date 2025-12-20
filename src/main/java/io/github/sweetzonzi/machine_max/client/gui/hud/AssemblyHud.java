package io.github.sweetzonzi.machine_max.client.gui.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sweetzonzi.machine_max.common.attachment.LivingEntityEyesightAttachment;
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.registry.MMItems;
import io.github.sweetzonzi.machine_max.common.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.recipe.IngredientCountPair;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector2i;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class AssemblyHud implements LayeredDraw.Layer {
    private static final int HUD_WIDTH = 120;
    private static final int HUD_HEIGHT = 200;
    private static final int PROGRESS_BAR_WIDTH = 10;
    private static final int PROGRESS_BAR_HEIGHT = 150;
    private static final int MATERIAL_ITEM_SIZE = 16;
    private static final int MATERIAL_ITEM_SPACING = 20;
    private static final int LINE_COLOR = 0xFFFFFFFF; // 白色
    private static final int BACKGROUND_COLOR = 0x80000000; // 半透明黑色背景
    private static final int PROGRESS_BAR_BG_COLOR = 0x80808080; // 半透明灰色
    private static final int PROGRESS_BAR_FILL_COLOR = 0xFF00FF00; // 绿色填充
    private static final int TEXT_COLOR = 0xFFFFFFFF; // 白色文字

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        if (player.getMainHandItem().getItem() == MMItems.getWELDING_TORCH_ITEM().get()) {
            LivingEntityEyesightAttachment eyesight = player.getData(MMAttachments.getENTITY_EYESIGHT());
            SubPart subPart = eyesight.getSubPart();
            if (subPart != null) {
                Part part = subPart.part;
                renderPartAssemblingProgress(part, guiGraphics);
            }
        }
    }

    /**
     * 渲染零部件的组装进度和材料需求情况
     */
    public void renderPartAssemblingProgress(Part part, GuiGraphics guiGraphics) {
        if (part.getRecipe() instanceof FabricatingRecipe recipe) {
            // 获取屏幕尺寸
            int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
            int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();

            // 计算HUD位置（屏幕右侧）
            int hudX = screenWidth - HUD_WIDTH - 10;
            int hudY = (screenHeight - HUD_HEIGHT) / 2;

            // 绘制半透明背景
            guiGraphics.fill(hudX, hudY, hudX + HUD_WIDTH, hudY + HUD_HEIGHT, BACKGROUND_COLOR);

            // 绘制进度条背景
            int progressBarX = hudX + 10;
            int progressBarY = hudY + 20;
            guiGraphics.fill(progressBarX, progressBarY,
                    progressBarX + PROGRESS_BAR_WIDTH,
                    progressBarY + PROGRESS_BAR_HEIGHT,
                    PROGRESS_BAR_BG_COLOR);

            // 绘制进度条填充
            float progress = part.getAssemblingProgress();
            int fillHeight = (int)(PROGRESS_BAR_HEIGHT * progress);
            guiGraphics.fill(progressBarX, progressBarY + PROGRESS_BAR_HEIGHT - fillHeight,
                    progressBarX + PROGRESS_BAR_WIDTH,
                    progressBarY + PROGRESS_BAR_HEIGHT,
                    PROGRESS_BAR_FILL_COLOR);

            // 绘制进度百分比文本
            String progressText = String.format("%.0f%%", progress * 100);
            guiGraphics.drawString(Minecraft.getInstance().font, progressText,
                    progressBarX + PROGRESS_BAR_WIDTH - 10,
                    progressBarY + PROGRESS_BAR_HEIGHT / 2 - 4,
                    TEXT_COLOR);

            // 绘制材料需求
            List<IngredientCountPair> ingredientPairs = recipe.getIngredientPairs();

            // 计算材料进度
            int totalMaterialSteps = recipe.getIngredientList().size();
            int currentMaterialProgress = part.getMaterialProgress();

            // 绘制每个材料需求
            int materialY = progressBarY;
            int materialX = progressBarX + PROGRESS_BAR_WIDTH + 30;

            // 获取玩家背包（用于检查已有材料数量）
            LocalPlayer player = Minecraft.getInstance().player;
            boolean isCreative = player != null && player.isCreative();

            for (int i = 0; i < ingredientPairs.size(); i++) {
                IngredientCountPair pair = ingredientPairs.get(i);
                Ingredient ingredient = pair.ingredient();
                int requiredCount = pair.count();

                // 计算该材料对应的进度位置
                // 首先计算该材料之前的进度步骤数
                int previousSteps = 0;
                for (int j = 0; j < i; j++) {
                    previousSteps += ingredientPairs.get(j).count();
                }

                // 计算该材料结束的进度位置
                float endProgress = (float) (previousSteps + requiredCount) / totalMaterialSteps;

                // 在进度条上的Y坐标
                int endY = progressBarY + PROGRESS_BAR_HEIGHT - (int)(PROGRESS_BAR_HEIGHT * endProgress);

                // 当前材料进度
                int currentStepInThisMaterial = Math.max(0, Math.min(requiredCount,
                        currentMaterialProgress - previousSteps));

                // 绘制连接到进度条的线
                int lineX1 = progressBarX + PROGRESS_BAR_WIDTH;
                int lineY1 = endY + MATERIAL_ITEM_SIZE / 2;
                int lineX2 = materialX - 5;

                // 绘制虚线
                guiGraphics.hLine(lineX1, lineX2, lineY1, LINE_COLOR);

                // 绘制材料图标
                ItemStack[] matchingStacks = ingredient.getItems();
                if (matchingStacks.length > 0) {
                    ItemStack displayStack = matchingStacks[0].copy();
                    displayStack.setCount(requiredCount);

                    // 渲染物品图标
                    guiGraphics.renderItem(displayStack, materialX, endY);

                    // 渲染物品数量（在物品右下角）
                    guiGraphics.renderItemDecorations(Minecraft.getInstance().font,
                            displayStack, materialX, endY);

                    // 计算玩家已有数量
                    int playerHasCount = 0;
                    if (isCreative) {
                        playerHasCount = Integer.MAX_VALUE; // 创造模式显示∞
                    } else if (player != null) {
                        for (ItemStack stack : player.getInventory().items) {
                            if (!stack.isEmpty() && ingredient.test(stack)) {
                                playerHasCount += stack.getCount();
                            }
                        }
                    }

                    // 绘制数量文本
                    String countText;
                    if (isCreative) {
                        countText = requiredCount + "/∞";
                    } else {
                        countText = requiredCount + "/" + playerHasCount;
                    }

                    // 根据是否足够改变颜色
                    int countColor = (playerHasCount >= requiredCount) ? 0xFF00FF00 : 0xFFFF0000;

                    guiGraphics.drawString(Minecraft.getInstance().font, countText,
                            materialX + MATERIAL_ITEM_SIZE + 5,
                            materialY + 4,
                            countColor);

                    // 绘制材料进度（已完成/总共）
                    String materialProgressText = currentStepInThisMaterial + "/" + requiredCount;
                    int progressColor = (currentStepInThisMaterial == requiredCount) ? 0xFF00FF00 : 0xFFFFFF00;

                    guiGraphics.drawString(Minecraft.getInstance().font, materialProgressText,
                            materialX + MATERIAL_ITEM_SIZE + 5,
                            materialY + 14,
                            progressColor);
                }

                materialY += MATERIAL_ITEM_SPACING;
            }

            // 绘制部件名称
            String partName = Component.translatable(part.type.getRegistryKey().toLanguageKey()).getString();
            guiGraphics.drawCenteredString(Minecraft.getInstance().font, partName,
                    hudX + HUD_WIDTH / 2,
                    hudY + 5,
                    TEXT_COLOR);
        }
    }

    /**
     * 绘制虚线
     */
    private void drawDottedLine(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color) {
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();

        // 计算线段长度和方向
        float dx = x2 - x1;
        float dy = y2 - y1;
        float distance = (float)Math.sqrt(dx * dx + dy * dy);

        if (distance > 0) {
            // 绘制虚线（每段4像素，间隔2像素）
            float step = 6; // 虚线段长度+间隔
            int segments = (int)(distance / step);

            for (int i = 0; i < segments; i++) {
                float startRatio = (i * step) / distance;
                float endRatio = ((i * step) + 4) / distance; // 4像素实线

                if (endRatio > 1) endRatio = 1;

                int segX1 = (int)(x1 + dx * startRatio);
                int segY1 = (int)(y1 + dy * startRatio);
                int segX2 = (int)(x1 + dx * endRatio);
                int segY2 = (int)(y1 + dy * endRatio);

                guiGraphics.hLine(segX1, segX2, segY1, color);
            }
        }

        poseStack.popPose();
    }
}