package io.github.sweetzonzi.machine_max.client.render.gui.renderable;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.awt.*;

public class ItemModelWidget extends AbstractWidget {
    private final Minecraft minecraft;
    @Getter
    private ItemStack itemStack = ItemStack.EMPTY;
    @Setter
    private float rotationSpeed = 0.3f; // 旋转速度
    @Getter
    private float currentRotation = 0.0f;
    @Setter
    @Getter
    private float scale = 30.0f; // 默认缩放值
    @Setter
    private boolean autoRotate = true;
    @Setter
    private Component emptyText = Component.translatable("gui.machine_max.fabricator.no_item");

    // 鼠标交互相关变量
    private boolean isDragging = false;
    private float rotationX = 0.0f; // X轴旋转角度（上下视角）
    private float rotationY = 0.0f;   // Y轴旋转角度（左右视角）
    private int offsetX = 0; // 渲染平移的X坐标
    private int offsetY = 0; // 渲染平移的Y坐标

    // 缩放限制
    private static final float MIN_SCALE = 10.0f;
    private static final float MAX_SCALE = 200.0f;

    // 旋转灵敏度
    private static final float ROTATION_SENSITIVITY = 0.5f;

    public ItemModelWidget(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
        this.minecraft = Minecraft.getInstance();
    }

    public ItemModelWidget(int x, int y, int width, int height, ItemStack itemStack) {
        this(x, y, width, height);
        this.itemStack = itemStack;
    }

    public void setItemStack(ItemStack itemStack) {
        if (ItemStack.isSameItemSameComponents(this.itemStack, itemStack)) {
            return;
        }
        this.itemStack = itemStack;
        // 重置旋转到默认视角
        resetView();
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 启用裁剪
        graphics.enableScissor(getX(), getY(), getX() + width, getY() + height);

        try {
            // 渲染背景
            graphics.fill(getX(), getY(), getX() + width, getY() + height, new Color(16, 16, 16, 128).getRGB());

            // 绘制边框
            graphics.renderOutline(getX(), getY(), width, height, 0xFF555555);
            if (itemStack.isEmpty()) {
                // 显示占位文本
                graphics.drawString(minecraft.font, emptyText,
                        getX() + width / 2 - minecraft.font.width(emptyText) / 2,
                        getY() + height / 2 - 4, 0xAAAAAA, false);
                return;
            }

            // 自动旋转（仅在未拖动时）
            if (autoRotate && !isDragging) {
                currentRotation += rotationSpeed;
                if (currentRotation >= 360.0f) {
                    currentRotation -= 360.0f;
                }
            }

            // 渲染3D物品模型
            renderItemModel(graphics, itemStack, getX() + width / 2 + offsetX, getY() + height / 2 + offsetY, scale, currentRotation, rotationY, rotationX, 0);

            // 如果正在拖动，显示提示边框
            if (isDragging) {
                graphics.renderOutline(getX(), getY(), width, height, 0x80FFFFFF);
            }

        } finally {
            // 禁用裁剪
            graphics.disableScissor();
        }
    }

    private void renderItemModel(GuiGraphics graphics, ItemStack stack, int x, int y, float scale,
                                 float rotation,
                                 float extraYaw, float extraPitch, float extraRoll) {
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();

        // 设置渲染位置和缩放
        poseStack.translate(x, y, 100.0F); // Z坐标确保在GUI上层
        poseStack.scale(scale, -scale, scale);

        poseStack.mulPose(Axis.YP.rotationDegrees(rotation));   // Y轴旋转

        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotationDegrees(extraRoll));   // Z轴旋转
        poseStack.mulPose(Axis.YP.rotationDegrees(extraYaw));   // Y轴旋转
        poseStack.mulPose(Axis.XP.rotationDegrees(extraPitch)); // X轴旋转

        // 设置渲染状态
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 渲染物品
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        minecraft.getItemRenderer().renderStatic(
                stack,
                ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,//显示最精致的模型
                0xF000F0,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                bufferSource,
                minecraft.level,
                0
        );

        bufferSource.endBatch();

        poseStack.popPose();
        poseStack.popPose();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        // narration implementation
    }

    // 鼠标按下事件 - 开始拖动
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.active && this.visible && this.isMouseOver(mouseX, mouseY)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                this.isDragging = true;
                return true;
            }
        }
        return false;
    }

    // 鼠标释放事件 - 停止拖动
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            this.isDragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // 鼠标拖动事件 - 旋转视角
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isDragging && this.active && this.visible) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) { // 左键
                // 更新旋转角度
                this.rotationY += (float) dragX * ROTATION_SENSITIVITY;
                this.rotationX += (float) dragY * ROTATION_SENSITIVITY;
                // 限制X轴旋转角度（避免过度翻转）
                this.rotationX = Math.max(-90.0f, Math.min(90.0f, this.rotationX));
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) { // 右键
                this.offsetX += (int) (1 * dragX);
                this.offsetY += (int) (1 * dragY);
            }
            return true;
        }
        return false;
    }

    // 鼠标滚轮事件 - 缩放
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.active && this.visible && this.isMouseOver(mouseX, mouseY)) {
            float zoomFactor = 1.1f; // 缩放因子
            if (scrollY > 0) {
                // 滚轮向上 - 放大
                this.scale = Math.min(MAX_SCALE, this.scale * zoomFactor);
            } else if (scrollY < 0) {
                // 滚轮向下 - 缩小
                this.scale = Math.max(MIN_SCALE, this.scale / zoomFactor);
            }
            return true;
        }
        return false;
    }

    // 用于手动控制旋转的方法
    public void rotate(float yaw, float pitch, float roll) {
        this.currentRotation = yaw;
        this.rotationX = pitch;
        this.rotationY = roll;
    }

    // 重置视角到默认状态
    public void resetView() {
        this.currentRotation = 0.0f;
        this.rotationX = 0.0f;
        this.rotationY = 0.0f;
        this.offsetX = 0;
        this.offsetY = 0;
        this.scale = 30.0f;
    }

    // 获取当前视角信息（用于调试或保存状态）
    public String getViewInfo() {
        return String.format("Scale: %.1f, Rotation: (X:%.1f, Y:%.1f, Z:%.1f)",
                scale, rotationX, rotationY, currentRotation);
    }
}
