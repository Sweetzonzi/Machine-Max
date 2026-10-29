package io.github.sweetzonzi.machine_max.mixin;

import io.github.sweetzonzi.machine_max.client.render.gui.creative.CreativePartBrowser;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.HolderLookup;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.Set;

/**
 * 把零部件创造页的筛选控件接进原版创造界面。
 *
 * <p>本 Mixin 只做四件事：挂载控件、把输入先交给 {@link CreativePartBrowser}、读写原版私有状态
 * （主搜索框、滚动位置、标签提示集合、当前页）、以及在装载未就绪时跳过原版的页刷新。过滤本身全部
 * 在 {@code CreativePartBrowser} 与 {@code SeriesPickerWidget} 里。</p>
 *
 * <p>焦点约定：主搜索框由本 Mixin 直接管理焦点标记，不写 {@code Screen#setFocused}，避免两套焦点
 * 记录互相清除；原版 {@code selectTab} 对目标页强制的 {@code setCanLoseFocus(false)} 在 TAIL 处改回。</p>
 *
 * <p>线程：主线程（20tps）。</p>
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin {

    @Shadow
    private static CreativeModeTab selectedTab;

    @Shadow
    private EditBox searchBox;

    @Shadow
    private float scrollOffs;

    @Shadow
    @Final
    private Set<TagKey<Item>> visibleTags;

    @Shadow
    private boolean ignoreTextInput;

    @Shadow
    private void refreshSearchResults() {
        throw new AssertionError("Mixin shadow");
    }

    @Unique
    private CreativePartBrowser machineMax$browser;

    @Unique
    private CreativeModeInventoryScreen machineMax$screen() {
        return (CreativeModeInventoryScreen) (Object) this;
    }

    /**
     * 把控件按 {@code Screen#addRenderableWidget} 的语义加入三个列表。
     *
     * <p>该方法声明在 {@code Screen}，既不能 {@code @Shadow}（Mixin 不解析超类成员），
     * 也不能整体放宽访问（会与 {@code CreateWorldScreen} 的同名覆写冲突），因此逐项加入：
     * {@code renderables} 本就 public，{@code children} 与 {@code narratables} 由访问转换器放宽。</p>
     */
    @Unique
    private void machineMax$mount(AbstractWidget widget) {
        CreativeModeInventoryScreen screen = this.machineMax$screen();
        screen.renderables.add(widget);
        screen.children.add(widget);
        screen.narratables.add(widget);
    }

    // ==================== 初始化与切页 ====================

    /** 重建控件前解除上轮实例引用，并保存主搜索串。 */
    @Inject(method = "init()V", at = @At("HEAD"))
    private void machineMax$initHead(CallbackInfo ci) {
        if (this.machineMax$browser == null) return;
        this.machineMax$browser.captureQuery();
        this.machineMax$browser = null;
    }

    /** 挂载筛选控件并恢复条件。 */
    @Inject(method = "init()V", at = @At("TAIL"))
    private void machineMax$initTail(CallbackInfo ci) {
        if (this.searchBox == null) return;
        CreativeModeInventoryScreen screen = this.machineMax$screen();
        this.machineMax$browser = new CreativePartBrowser(
                screen, this.visibleTags, this.searchBox, () -> this.refreshSearchResults());
        for (AbstractWidget widget : this.machineMax$browser.widgets()) {
            this.machineMax$mount(widget);
        }
        this.machineMax$browser.onTabChanged(
                CreativePartBrowser.isFilterTab(selectedTab), selectedTab.getDisplayItems());
    }

    /** 离开目标页前保存主搜索串。 */
    @Inject(method = "selectTab", at = @At("HEAD"))
    private void machineMax$selectTabHead(CreativeModeTab tab, CallbackInfo ci) {
        if (this.machineMax$browser == null) return;
        if (CreativePartBrowser.isFilterTab(selectedTab)) this.machineMax$browser.captureQuery();
    }

    /** 切页后同步控件显隐、关闭弹层、恢复主搜索框设置与焦点。 */
    @Inject(method = "selectTab", at = @At("TAIL"))
    private void machineMax$selectTabTail(CreativeModeTab tab, CallbackInfo ci) {
        if (this.machineMax$browser == null || this.searchBox == null) return;
        this.machineMax$browser.onTabChanged(
                CreativePartBrowser.isFilterTab(selectedTab), selectedTab.getDisplayItems());
    }

    // ==================== 结果写回 ====================

    /**
     * 目标页的搜索结果全部改由 {@code CreativePartBrowser} 计算。
     *
     * <p>首次 init 中 Browser 尚未建立，此时让原版执行；init 的 TAIL 负责最终展示。</p>
     */
    @Inject(method = "refreshSearchResults", at = @At("HEAD"), cancellable = true)
    private void machineMax$refreshSearchResults(CallbackInfo ci) {
        if (this.machineMax$browser == null) return;
        if (!CreativePartBrowser.isFilterTab(selectedTab)) return;
        this.machineMax$browser.refresh();
        this.scrollOffs = 0.0F;
        this.machineMax$screen().getMenu().scrollTo(0.0F);
        ci.cancel();
    }

    // ==================== 输入 ====================

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void machineMax$mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (this.machineMax$browser != null && this.machineMax$browser.mouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void machineMax$mouseReleased(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (this.machineMax$browser != null && this.machineMax$browser.mouseReleased(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void machineMax$mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (this.machineMax$browser != null && this.machineMax$browser.mouseScrolled(mouseX, mouseY, scrollY)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void machineMax$mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (this.machineMax$browser != null && this.machineMax$browser.mouseDragged(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    /**
     * 键盘入口：弹层打开时独占输入；主搜索有焦点时编辑键先给搜索框，数字键因此不再落进原版快捷栏分支。
     */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void machineMax$keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (this.machineMax$browser == null || !this.machineMax$browser.isActive()) return;
        this.ignoreTextInput = false;
        if (this.machineMax$browser.keyPressed(keyCode, scanCode, modifiers)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void machineMax$charTyped(char codePoint, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (this.machineMax$browser == null) return;
        if (this.machineMax$browser.charTyped(codePoint, modifiers)) {
            cir.setReturnValue(true);
            return;
        }
        if (this.machineMax$browser.shouldBlockUntargetedCharTyped()) cir.setReturnValue(false);
    }

    // ==================== 目录、滚动与绘制 ====================

    /**
     * 装载未就绪时跳过原版页刷新，保留最近完整目录与已选页。
     *
     * <p>原版会在这条路径里对空目录调用 {@code selectTab(默认页)}；跳过它以后，就绪后的下一次
     * {@code containerTick} 自然重入并恢复正常检查。</p>
     */
    @Inject(method = "tryRefreshInvalidatedTabs", at = @At("HEAD"), cancellable = true)
    private void machineMax$skipWhileLoading(FeatureFlagSet enabledFeatures, boolean hasPermissions,
                                             HolderLookup.Provider provider, CallbackInfo ci) {
        if (CreativePartBrowser.isFilterTab(selectedTab) && !PartDefinitions.isReady(true)) ci.cancel();
    }

    /** 目录轮次变化时重建一次可见页列表；其余每 tick 只做引用比较。 */
    @Inject(method = "containerTick", at = @At("TAIL"))
    private void machineMax$containerTick(CallbackInfo ci) {
        if (this.machineMax$browser == null) return;
        if (this.machineMax$browser.builtGeneration() != CreativePartBrowser.contentsGeneration()) {
            CreativeModeInventoryScreen screen = this.machineMax$screen();
            screen.init(Minecraft.getInstance(), screen.width, screen.height);
            return;
        }
        if (!this.machineMax$browser.isActive()) return;
        this.machineMax$browser.containerTick(selectedTab.getDisplayItems());
    }

    /** 重建后行数为零会让滚动的除法得到非有限值，进入前先归零。 */
    @Inject(method = "resize", at = @At("HEAD"))
    private void machineMax$resizeHead(Minecraft minecraft, int width, int height, CallbackInfo ci) {
        if (!Float.isFinite(this.scrollOffs)) this.scrollOffs = 0.0F;
    }

    /** 保证滚动位置始终有限并与槽位一致。 */
    @Inject(method = "resize", at = @At("TAIL"))
    private void machineMax$resizeTail(Minecraft minecraft, int width, int height, CallbackInfo ci) {
        this.machineMax$sanitizeScroll();
    }

    /**
     * 目录刷新重建内容后同样要保证滚动位置有限。
     *
     * <p>该路径在候选数为 37—45 时也会走到 {@code ItemPickerMenu#getScrollForRowIndex}，行数为零，
     * 除法得到 NaN 或无穷；这里统一钳制并回写一次，不修改 {@code ItemPickerMenu} 本身。</p>
     */
    @Inject(method = "refreshCurrentTabContents", at = @At("TAIL"))
    private void machineMax$sanitizeScrollAfterRefresh(Collection<ItemStack> items, CallbackInfo ci) {
        this.machineMax$sanitizeScroll();
    }

    @Unique
    private void machineMax$sanitizeScroll() {
        if (!Float.isFinite(this.scrollOffs)) this.scrollOffs = 0.0F;
        this.scrollOffs = Mth.clamp(this.scrollOffs, 0.0F, 1.0F);
        this.machineMax$screen().getMenu().scrollTo(this.scrollOffs);
    }

    /**
     * 绘制弹层与零结果提示，并在弹层覆盖鼠标时抑制背后的槽位提示。
     *
     * <p>原版 {@code render} 的最后一句就是槽位提示的绘制，因此在这里取消可以把提示与弹层一起处理；
     * 弹层自身在取消之前绘制，不会被一起跳过。</p>
     */
    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen;renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;II)V",
                    shift = At.Shift.BEFORE
            ),
            cancellable = true
    )
    private void machineMax$renderOverlay(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (this.machineMax$browser == null) return;
        this.machineMax$browser.renderOverlay(guiGraphics, mouseX, mouseY);
        if (this.machineMax$browser.isPopupCovering(mouseX, mouseY)) ci.cancel();
    }
}
