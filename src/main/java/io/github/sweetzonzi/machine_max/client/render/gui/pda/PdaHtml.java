package io.github.sweetzonzi.machine_max.client.render.gui.pda;

import io.github.sweetzonzi.machine_max.common.item.prop.PdaData;
import io.github.sweetzonzi.machine_max.common.item.prop.PdaHelper;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.PartType;
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.recipe.PartFabricatingRecipe;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 蓝图终端界面的 HTML 片段渲染工具：已收纳条目行、背包蓝图行与快捷栏格位。
 *
 * <p>与项目其他 AUI 页面一致：Java 侧生成片段字符串，再由 {@code Element.setInnerHTML} 写入容器；
 * 行内可点击元素统一带 {@code data-act} 属性，取值域见接口设计文档 7.1。</p>
 */
public final class PdaHtml {
    /** 无限次数的展示符号。 */
    public static final String INFINITE_SYMBOL = "\u221E";
    /** 行内动作：选中条目进入待绑定态，行内需带 {@code data-target}。 */
    public static final String ACT_SELECT_ENTRY = "SELECT_ENTRY";
    /** 行内动作：存入该行蓝图，行内需带 {@code data-slot}。 */
    public static final String ACT_DEPOSIT = "DEPOSIT";
    /** 行内动作：清除该条目的全部格位引用。 */
    public static final String ACT_UNBIND = "UNBIND";

    private PdaHtml() {
    }

    /** 条目在界面上的展示信息：标签、名称与图标。 */
    public record EntryView(String tagClass, String tagLabel, String name, String icon) {
    }

    /**
     * 单个快捷栏格位：序号、图标与选中态；未绑定时只留序号。
     *
     * @param index    格位序号
     * @param bound    该格绑定的配方 id；{@code null} 表示未绑定
     * @param selected 是否为当前格位
     */
    public static String shortcutSlot(int index, @Nullable ResourceLocation bound, boolean selected, Level level) {
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"shortcut").append(selected ? " selected" : "")
                .append("\" id=\"pda-shortcut-").append(index)
                .append("\" data-index=\"").append(index).append("\">")
                .append("<span class=\"key\">").append(index + 1).append("</span>");
        if (bound != null) html.append(texture("icon", iconOf(level, bound)));
        return html.append("</div>").toString();
    }

    /**
     * 已收纳条目行；被任一格位引用时附带解绑按钮。
     *
     * @param recipeId 条目的配方 id，即 {@code data-target} 的取值
     * @param uses     残留次数；{@code null} 不会出现（条目必有次数）
     * @param bound    是否被任一格位引用
     */
    public static String entryRow(ResourceLocation recipeId, @Nullable Integer uses, EntryView view, boolean bound) {
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"row\" data-act=\"").append(ACT_SELECT_ENTRY)
                .append("\" data-target=\"").append(escape(recipeId.toString())).append("\">")
                .append(tag(view))
                .append(texture("row-icon", view.icon()))
                .append("<span class=\"row-name\">").append(escape(view.name())).append("</span>")
                .append("<span class=\"row-uses")
                .append(uses != null && PdaData.isInfinite(uses) ? " infinite" : "").append("\">")
                .append(usesText(uses)).append("</span>");
        if (bound) {
            html.append("<span class=\"row-act\" data-act=\"").append(ACT_UNBIND).append("\">")
                    .append(escape(translatable("gui.machine_max.pda.btn.unbind"))).append("</span>");
        }
        return html.append("</div>").toString();
    }

    /**
     * 背包蓝图行：右侧按条目当前状态给出「存入 / 已收纳 / 合并」三种按钮形态。
     *
     * @param slot 该蓝图所在的主背包槽位，写入 {@code data-slot}
     */
    public static String inventoryRow(ResourceLocation recipeId, int slot, EntryView view, @Nullable Integer uses) {
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"row\" data-target=\"").append(escape(recipeId.toString())).append("\">")
                .append(tag(view))
                .append(texture("row-icon", view.icon()))
                .append("<span class=\"row-name\">").append(escape(view.name())).append("</span>");
        if (uses == null) {
            html.append("<span class=\"row-act\" data-act=\"").append(ACT_DEPOSIT)
                    .append("\" data-slot=\"").append(slot).append("\">")
                    .append(escape(translatable("gui.machine_max.pda.btn.deposit"))).append("</span>");
        } else if (PdaData.isInfinite(uses)) {
            html.append("<span class=\"row-act disabled\">")
                    .append(escape(translatable("gui.machine_max.pda.btn.stored"))).append("</span>");
        } else {
            // 蓝图物品当前不携带次数，有限次条目一律按升级为无限处理
            html.append("<span class=\"row-act\">")
                    .append(escape(translatable("gui.machine_max.pda.btn.merge", INFINITE_SYMBOL)))
                    .append("</span>");
        }
        return html.append("</div>").toString();
    }

    /** 条目类别标签（零件 / 通用 / 未知蓝图）。 */
    public static String tag(EntryView view) {
        return "<span class=\"tag " + view.tagClass() + "\">" + escape(view.tagLabel()) + "</span>";
    }

    /** 条目在界面上的展示信息；反查不到配方时降级为「未知蓝图」。 */
    public static EntryView viewOf(Level level, ResourceLocation recipeId) {
        RecipeHolder<FabricatingRecipe> holder = PdaHelper.recipeOf(level, recipeId);
        if (holder == null) {
            return new EntryView("tag-unknown", translatable("gui.machine_max.pda.tag.unknown"),
                    recipeId.toString(), "");
        }
        if (holder.value() instanceof PartFabricatingRecipe partRecipe) {
            ResourceLocation partTypeId = partRecipe.getPartType();
            String name = partTypeId != null
                    ? Component.translatable(partTypeId.toLanguageKey()).getString()
                    : holder.value().getResult().getHoverName().getString();
            return new EntryView("tag-part", translatable("gui.machine_max.pda.tag.part"),
                    name, iconOf(level, recipeId));
        }
        return new EntryView("tag-general", translatable("gui.machine_max.pda.tag.general"),
                holder.value().getResult().getHoverName().getString(), "");
    }

    /** 条目图标：零件配方取零件默认图标，其余返回空串（不绘制纹理）。 */
    public static String iconOf(Level level, ResourceLocation recipeId) {
        RecipeHolder<PartFabricatingRecipe> holder = PdaHelper.partRecipeOf(level, recipeId);
        if (holder == null) return "";
        ResourceLocation partTypeId = holder.value().getPartType();
        if (partTypeId == null) return "";
        PartType partType = PartType.get(level, partTypeId);
        return partType == null ? "" : partType.getDefaultIcon().toString();
    }

    /** 残留次数展示：无限次用符号，有限次用数字，无次数用空串。 */
    public static String usesText(@Nullable Integer uses) {
        if (uses == null) return "";
        return PdaData.isInfinite(uses) ? INFINITE_SYMBOL : Integer.toString(uses);
    }

    /** 生成 AUI 的纹理元素；{@code src} 为空时不生成节点。 */
    public static String texture(String className, String src) {
        if (src == null || src.isEmpty()) return "";
        return "<texture class=\"" + className + "\" src=\"" + escape(src) + "\"></texture>";
    }

    /** 取语言键的当前语言文本并做参数替换。 */
    public static String translatable(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    /** 转义写入 innerHTML 的文本，避免名称中的特殊字符破坏结构。 */
    public static String escape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
