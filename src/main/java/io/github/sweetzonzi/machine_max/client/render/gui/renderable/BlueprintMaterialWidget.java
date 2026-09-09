package io.github.sweetzonzi.machine_max.client.render.gui.renderable;

import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.PartData;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.VehicleData;
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 蓝图库标签页的材料清单控件。
 *
 * <p>按 {@link VehicleData#getParts()} 逐件解析<b>有效配方 id</b>（与研发门禁共用
 * {@link PartData#resolveRecipeId(net.minecraft.world.level.Level)}）并汇总：</p>
 * <ul>
 *   <li>可手工组装的零件 → 展开 {@code getManualAssembleIngredientList()}，显示原材料并汇总数量；</li>
 *   <li>不可手工组装的零件 → 显示该配方产物（成品零件）并汇总数量。</li>
 * </ul>
 *
 * <p>每个槽位按「已有 / 所需」着色，悬停显示明细。</p>
 */
public class BlueprintMaterialWidget extends AbstractWidget {
    private final Minecraft minecraft = Minecraft.getInstance();

    private final List<MaterialEntry> entries = new ArrayList<>();

    private static final int SLOT_SIZE = 20;
    private static final int SLOT_PADDING = 2;
    private static final int BG_COLOR = new Color(16, 16, 16, 128).getRGB();
    private static final int BORDER_COLOR = 0xFF555555;
    private static final int TEXT_MUTED = new Color(140, 140, 140).getRGB();
    private static final int COLOR_ENOUGH = 0x3344AA44;
    private static final int COLOR_MISSING = 0x33AA4444;

    public BlueprintMaterialWidget(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
    }

    /**
     * 设置要展示的载具数据并重建材料清单。
     *
     * @param data 载具数据；为空时清空
     */
    public void setVehicleData(@Nullable VehicleData data) {
        entries.clear();
        if (data == null) return;
        Player player = minecraft.player;
        if (player == null) return;
        boolean creative = player.isCreative();

        // 先按物品合并所需数量，再统一统计背包持有量，避免同物品重复累加
        Map<Integer, Accumulator> accumulators = new LinkedHashMap<>();
        for (PartData part : data.getParts().values()) {
            ResourceLocation recipeId = part.resolveRecipeId(player.level());
            RecipeHolder<?> holder = player.level().getRecipeManager().byKey(recipeId).orElse(null);
            if (holder == null || !(holder.value() instanceof FabricatingRecipe recipe)) continue;

            if (recipe.isManualAssemblablePart()) {
                // 可手工组装：展开原材料
                for (Ingredient ingredient : recipe.getManualAssembleIngredientList()) {
                    ItemStack[] items = ingredient.getItems();
                    if (items.length == 0) continue;
                    ItemStack display = items[0].copyWithCount(1);
                    accumulators.computeIfAbsent(ItemStack.hashItemAndComponents(display),
                            k -> new Accumulator(display)).required++;
                }
            } else {
                // 不可手工组装：显示成品零件
                ItemStack result = recipe.getResultItem(player.level().registryAccess()).copyWithCount(1);
                if (result.isEmpty()) continue;
                accumulators.computeIfAbsent(ItemStack.hashItemAndComponents(result),
                        k -> new Accumulator(result)).required++;
            }
        }

        for (Accumulator accumulator : accumulators.values()) {
            int have = countInInventory(player, accumulator.display);
            entries.add(new MaterialEntry(accumulator.display, accumulator.required, have,
                    creative || have >= accumulator.required));
        }
    }

    /** 统计背包中与展示物品同类同组件的数量 */
    private static int countInInventory(Player player, ItemStack display) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, display)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(getX(), getY(), getX() + width, getY() + height, BG_COLOR);
        graphics.renderOutline(getX(), getY(), width, height, BORDER_COLOR);

        if (entries.isEmpty()) {
            String text = Component.translatable("gui.machine_max.blueprint_library.no_materials").getString();
            int tw = minecraft.font.width(text);
            graphics.drawString(minecraft.font, text,
                    getX() + (width - tw) / 2, getY() + height / 2 - 4, TEXT_MUTED, false);
            return;
        }

        int columns = Math.max(1, (width - SLOT_PADDING * 2) / (SLOT_SIZE + SLOT_PADDING));
        int startX = getX() + 3;
        int startY = getY() + 3;

        for (int i = 0; i < entries.size(); i++) {
            int row = i / columns;
            int col = i % columns;
            int x = startX + col * (SLOT_SIZE + SLOT_PADDING);
            int y = startY + row * (SLOT_SIZE + SLOT_PADDING);

            MaterialEntry entry = entries.get(i);
            ItemStack display = entry.display.copyWithCount(entry.required);

            if (x <= mouseX && mouseX < x + SLOT_SIZE && y <= mouseY && mouseY < y + SLOT_SIZE) {
                graphics.renderTooltip(minecraft.font, List.of(
                        display.getHoverName(),
                        Component.translatable("gui.machine_max.blueprint_library.material_count",
                                entry.playerCount, entry.required)
                ), java.util.Optional.empty(), mouseX, mouseY);
            }

            graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, entry.hasEnough ? COLOR_ENOUGH : COLOR_MISSING);
            graphics.renderItem(display, x + 2, y + 2);
            graphics.renderItemDecorations(minecraft.font, display, x + 2, y + 2);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }

    /** 合并累加器：同一展示物品的所需数量累加 */
    private static final class Accumulator {
        private final ItemStack display;
        private int required;

        private Accumulator(ItemStack display) {
            this.display = display;
        }
    }

    /**
     * 材料条目
     *
     * @param display     展示物品（数量为 1）
     * @param required    所需数量
     * @param playerCount 玩家持有数量
     * @param hasEnough   是否足够
     */
    private record MaterialEntry(ItemStack display, int required, int playerCount, boolean hasEnough) {
    }
}
