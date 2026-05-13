package io.github.sweetzonzi.machine_max.client.render.gui.hud;

import io.github.sweetzonzi.machine_max.common.attachment.LivingEntityEyesightAttachment;
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.recipe.IngredientCountPair;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class AssemblyHud implements LayeredDraw.Layer {

    /* ================== 布局参数 ================== */

    private static final int HUD_WIDTH = 150;
    private static final int HEADER_HEIGHT = 76;
    private static final int LINE_HEIGHT = 18;
    private static final int PADDING = 8;

    /* ================== 颜色定义（半透明扁平化） ================== */

    private static final int HUD_BG = 0xCC111111;
    private static final int HUD_SIDE = 0xFF66CCFF;
    private static final int BAR_BG = 0xAA2A2A2A;

    private static final int ROW_BG_DARK = 0x66222222;
    private static final int ROW_BG_LIGHT = 0xAA3A3A3A;
    private static final int ROW_BG_ACTIVE = 0xAA355C99;
    private static final int ROW_BG_LACK = 0xAA7A2222;

    private static final int TEXT_MAIN = 0xFFFFFFFF;
    private static final int TEXT_SUB = 0xFFBBBBBB;
    private static final int TEXT_DIM = 0xFF888888;

    /* ================== 动画状态 ================== */

    /** 总进度条动画进度（客户端 View 状态） */
    private float animatedProgress = 0f;

    /* ================== 材料状态模型 ================== */

    private record MaterialStatus(
            IngredientCountPair pair,
            int consumed,
            int inventory,
            boolean consuming
    ) {
        int required() {
            return pair.count();
        }

        boolean completed() {
            return consumed >= required();
        }

        boolean lacking() {
            return inventory < (required() - consumed);
        }

        float progress() {
            return required() == 0 ? 1f : consumed / (float) required();
        }
    }

    /* ================== HUD 入口 ================== */

    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        if (Minecraft.getInstance().options.hideGui) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

//        if (player.getMainHandItem().getItem() != MMItems.getWELDING_TORCH_ITEM().get()) return;

        LivingEntityEyesightAttachment eyesight = player.getData(MMAttachments.getENTITY_EYESIGHT());
        SubPart subPart = eyesight.getSubPart();
        if (subPart == null) return;

        renderPartHud(subPart.part, g, player, delta.getRealtimeDeltaTicks());
    }

    /* ================== 主 HUD 渲染 ================== */

    /**
     * 渲染零部件装配 HUD（高度随材料数量自适应）
     */
    private void renderPartHud(
            Part part,
            GuiGraphics g,
            LocalPlayer player,
            float deltaTicks
    ) {
        if (!(part.getRecipe() instanceof FabricatingRecipe recipe)) return;

        List<MaterialStatus> materials = buildMaterialStatus(recipe, part, player);

        int hudHeight = HEADER_HEIGHT + materials.size() * LINE_HEIGHT + PADDING;

        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();

        int x =  48;
        int y = (screenH - hudHeight) / 2;

        g.fill(x, y, x + HUD_WIDTH, y + hudHeight, HUD_BG);
        g.fill(x, y, x + 3,y + hudHeight, HUD_SIDE);

        var font = Minecraft.getInstance().font;

        /* ---------- 标题 ---------- */

        String partName = Component
                .translatable(part.type.getRegistryKey().toLanguageKey())
                .getString();
        g.pose().pushPose();
        g.pose().translate(x + PADDING, y + 6, 0);
        g.pose().scale(1.3f, 1.3f, 1.3f);
        g.drawString(font, partName, 0, 0, TEXT_MAIN, false);
        g.pose().popPose();
        g.drawString(font, "Status: Functional", x + PADDING, y + 20, TEXT_SUB, false);

        /* ---------- 总进度条 ---------- */

        drawAnimatedProgressBar(
                g,
                x + PADDING,
                y + 34,
                HUD_WIDTH - PADDING * 2,
                part.getAssemblingProgress(),
                deltaTicks
        );

        /* ---------- 材料列表 ---------- */

        int listY = y + HEADER_HEIGHT - 6;
        g.drawString(font, "Components:", x + PADDING, listY, TEXT_MAIN, false);
        listY += 10;

        for (MaterialStatus m : materials) {
            drawMaterialRow(g, font, x, listY, m);
            listY += LINE_HEIGHT;
        }
    }

    /* ================== 总进度条 ================== */

    /**
     * 绘制带动画与渐变的总体装配进度条，并居中显示百分比
     */
    private void drawAnimatedProgressBar(
            GuiGraphics g,
            int x,
            int y,
            int width,
            float targetProgress,
            float deltaTicks
    ) {
        // 平滑动画
        animatedProgress += (targetProgress - animatedProgress)
                * Math.min(1f, deltaTicks * 0.3f);

        int height = 10;

        g.fill(x, y, x + width, y + height, BAR_BG);

        int filled = (int) (width * animatedProgress);

        int fillColor = lerpColor(0xFF4A90E2, 0xFF6AFF6A, animatedProgress);
        g.fill(x, y, x + filled, y + height, fillColor);

        String percent = Math.round(animatedProgress * 100) + "%";
        int textW = Minecraft.getInstance().font.width(percent);

        g.drawString(
                Minecraft.getInstance().font,
                percent,
                x + width / 2 - textW / 2,
                y + height / 2 - 4,
                TEXT_MAIN,
                false
        );
    }

    /* ================== 材料行 ================== */

    /**
     * 绘制单个材料条目：
     * - 行底色反映状态
     * - 上层填充条反映材料消耗进度
     * - 填充条带轻微颜色渐变
     */
    private void drawMaterialRow(
            GuiGraphics g,
            net.minecraft.client.gui.Font font,
            int hudX,
            int y,
            MaterialStatus m
    ) {
        int rowX = hudX + PADDING;
        int rowW = HUD_WIDTH - PADDING * 2;
        int rowH = LINE_HEIGHT - 2;

        int baseColor;
        if (m.lacking()) {
            baseColor = ROW_BG_LACK;
        } else if (m.consuming) {
            baseColor = ROW_BG_ACTIVE;
        } else if (m.completed()) {
            baseColor = ROW_BG_LIGHT;
        } else {
            baseColor = ROW_BG_DARK;
        }

        /* 底层背景 */
        g.fill(rowX, y, rowX + rowW, y + rowH, baseColor);

        /* 消耗进度填充 */
        int fillW = (int) (rowW * m.progress());
        if (fillW > 0) {
            int fillColor = lerpColor(
                    brighten(baseColor, 1.3f),
                    brighten(baseColor, 0.7f),
                    m.progress()
            );
            g.fill(rowX, y, rowX + fillW, y + rowH, fillColor);
        }

        ItemStack icon = m.pair.ingredient().getItems()[0];
        g.renderItem(icon, rowX + 2, y + 1);

        int textColor = m.completed() ? TEXT_MAIN : TEXT_DIM;

        g.drawString(font,
                icon.getHoverName().getString(),
                rowX + 22,
                y + 5,
                textColor,
                false);

        g.drawString(font,
                String.valueOf(m.required()),
                rowX + rowW - 46,
                y + 5,
                TEXT_SUB,
                false);

        if (m.completed()) {
            g.drawString(font, "✔", rowX + rowW - 30, y + 5, TEXT_MAIN, false);
        }

        String inv = m.inventory == Integer.MAX_VALUE ? "∞" : String.valueOf(m.inventory);
        g.drawString(font, inv, rowX + rowW - 16, y + 5, TEXT_SUB, false);
    }

    /* ================== 材料状态构建 ================== */

    /**
     * 根据配方、装配进度和库存构建材料状态列表
     */
    private List<MaterialStatus> buildMaterialStatus(
            FabricatingRecipe recipe,
            Part part,
            LocalPlayer player
    ) {
        List<MaterialStatus> list = new ArrayList<>();

        int consumedTotal = part.getMaterialProgress();

        for (IngredientCountPair pair : recipe.getManualAssembleIngredientPairs()) {
            int required = pair.count();
            int consumed = Math.min(required, consumedTotal);

            boolean consuming = consumedTotal > 0 && consumedTotal < required;
            consumedTotal -= consumed;

            int inventory = 0;
            if (player.isCreative()) {
                inventory = Integer.MAX_VALUE;
            } else {
                for (ItemStack stack : player.getInventory().items) {
                    if (!stack.isEmpty() && pair.ingredient().test(stack)) {
                        inventory += stack.getCount();
                    }
                }
            }

            list.add(new MaterialStatus(pair, consumed, inventory, consuming));
        }
        return list;
    }

    /* ================== 工具方法 ================== */

    /** 颜色线性插值 */
    private int lerpColor(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 |
                ((int) (ar + (br - ar) * t) << 16) |
                ((int) (ag + (bg - ag) * t) << 8) |
                (int) (ab + (bb - ab) * t);
    }

    /** 简单提亮颜色 */
    private int brighten(int color, float factor) {
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) * factor));
        int b = Math.min(255, (int) ((color & 0xFF) * factor));
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}
