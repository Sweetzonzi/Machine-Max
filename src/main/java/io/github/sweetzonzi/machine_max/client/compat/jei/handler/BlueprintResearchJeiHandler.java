package io.github.sweetzonzi.machine_max.client.compat.jei.handler;

import io.github.sweetzonzi.machine_max.client.render.gui.renderable.TabbedMaterialWidget;
import io.github.sweetzonzi.machine_max.client.render.gui.screen.BlueprintResearchScreen;
import lombok.Setter;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IClickableIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JEI GUI 容器处理器，使 BlueprintResearchScreen 中自定义渲染的物品支持 JEI 查询
 */
@Setter
public class BlueprintResearchJeiHandler implements IGuiContainerHandler<BlueprintResearchScreen> {

    /**
     * 由 MMJeiPlugin 在运行时注入 IIngredientManager
     */
    private IIngredientManager ingredientManager;

    @Override
    public @NotNull Optional<IClickableIngredient<?>> getClickableIngredientUnderMouse(
            BlueprintResearchScreen screen, double mouseX, double mouseY) {
        if (ingredientManager == null) return Optional.empty();

        /* 检查材料 Widget 区域 */
        TabbedMaterialWidget materialWidget = screen.getMaterialWidget();
        if (materialWidget != null && materialWidget.isMouseOver(mouseX, mouseY)) {
            ItemStack stack = materialWidget.getIngredientAt(mouseX, mouseY);
            if (!stack.isEmpty()) {
                Rect2i area = getSlotArea(materialWidget, mouseX, mouseY);
                return ingredientManager
                        .createClickableIngredient(VanillaTypes.ITEM_STACK, stack, area, false)
                        .map(clickable -> (IClickableIngredient<?>) clickable);
            }
        }

        /* 检查制造产物区域 */
        ItemStack product = screen.getProductIngredientAt(mouseX, mouseY);
        if (!product.isEmpty()) {
            int infoX = screen.getGuiLeft() + 175;
            int infoY = screen.getGuiTop() + 8;
            return ingredientManager
                    .createClickableIngredient(VanillaTypes.ITEM_STACK, product, new Rect2i(infoX, infoY, 18, 18), false)
                    .map(clickable -> (IClickableIngredient<?>) clickable);
        }

        return Optional.empty();
    }

    @Override
    public @NotNull List<Rect2i> getGuiExtraAreas(BlueprintResearchScreen screen) {
        List<Rect2i> areas = new ArrayList<>();
        TabbedMaterialWidget materialWidget = screen.getMaterialWidget();
        if (materialWidget != null) {
            areas.add(new Rect2i(
                    materialWidget.getX(), materialWidget.getY(),
                    materialWidget.getWidth(), materialWidget.getHeight()
            ));
        }
        return areas;
    }

    /**
     * 从材料网格中计算鼠标所在格子的区域
     */
    private static Rect2i getSlotArea(TabbedMaterialWidget widget, double mouseX, double mouseY) {
        int columns = Math.max(1, (widget.getWidth() - 2) / 22);
        int startX = widget.getX() + 3;
        int startY = widget.getY() + 16;
        for (int i = 0; i < 64; i++) {
            int row = i / columns;
            int col = i % columns;
            int x = startX + col * 22;
            int y = startY + row * 22;
            if (mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20) {
                return new Rect2i(x, y, 20, 20);
            }
        }
        return new Rect2i(startX, startY, 20, 20);
    }
}
