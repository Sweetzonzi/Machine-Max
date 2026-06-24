package io.github.sweetzonzi.machine_max.client.render.gui.panel;

import io.github.sweetzonzi.machine_max.common.mech.control.*;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractControllableSubsystem;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import io.github.sweetzonzi.machine_max.network.payload.ControlGroupSetEditPayload;
import io.github.sweetzonzi.machine_max.network.payload.GuiActionPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * 控制数据获取适配器。<br>
 * 封装从客户端 Player 到 ControlGroupSet 的读写链路。<br>
 * 优先从当前控制的子系统获取真实数据，玩家未乘坐时回退到 Mock 数据用于 UI 调试。
 */
@OnlyIn(Dist.CLIENT)
public class ControlDataAccessor {

    /** 用于 GUI 测试的预置控制组集合（仅在无真实数据时使用） */
    private static ControlGroupSet FOR_GUI_TEST;

    /** 最近一次加载的 ControlGroupSet 快照，用于编辑器未保存时的本地回显 */
    private static ControlGroupSet cachedSnapshot;

    /**
     * 获取当前控制组集合。<br>
     * <ol>
     *   <li>如果玩家正在控制某子系统（在座椅中），返回该子系统的真实 {@link ControlGroupSet}</li>
     *   <li>否则返回 Mock 数据用于调试</li>
     * </ol>
     */
    @Nullable
    public static ControlGroupSet getCurrentControlSet(Minecraft mc) {
        // 1. 尝试获取真实数据
        if (mc.player instanceof IEntityMixin mixin
                && mixin.machine_Max$getControllingSubsystem() instanceof AbstractControllableSubsystem sub) {
            ControlGroupSet real = sub.getControlGroupSet();
            if (real != null && real != ControlGroupSet.EMPTY) {
                cachedSnapshot = real;
                return real;
            }
        }
        // 2. 无真实数据时用 Mock 数据
        if (FOR_GUI_TEST == null) {
            buildMockData();
        }
        return FOR_GUI_TEST;
    }

    /**
     * 判断玩家当前是否在控制座位上（是否有真实数据来源）。
     */
    public static boolean hasRealControlSet(Minecraft mc) {
        return mc.player instanceof IEntityMixin mixin
                && mixin.machine_Max$getControllingSubsystem() instanceof AbstractControllableSubsystem;
    }

    /**
     * 构建用于 GUI 调试的 Mock 数据。<br>
     * 包含 baseGroup + 3 个子控制组 + 6 个 GUI 交互元素。
     */
    private static void buildMockData() {
        var baseGroup = new ControlGroup("BASE", ControlMode.GROUND,
                Map.of(
                        "forward", List.of("main_engine"),
                        "steering", List.of("steering_gear")
                ),
                Map.of(
                        "camera", List.of("main_camera")
                ),
                Map.of(
                        "handbrake", List.of("brake_system")
                ),
                Map.of(),
                Map.of(),
                List.of(
                        new ControlBinding("key.w", BindingAction.HOLD, "forward", List.of("main_engine")),
                        new ControlBinding("key.s", BindingAction.HOLD, "brake", List.of("main_engine")),
                        new ControlBinding("key.a", BindingAction.HOLD, "steering", List.of("steering_gear")),
                        new ControlBinding("key.d", BindingAction.HOLD, "steering", List.of("steering_gear")),
                        new ControlBinding("key.space", BindingAction.HOLD, "handbrake", List.of("brake_system"))
                )
        );

        var combat = new ControlGroup("COMBAT", ControlMode.INHERIT,
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                List.of(
                        new ControlBinding("mouse.left", BindingAction.PRESS, "fire_primary", List.of("turret")),
                        new ControlBinding("mouse.right", BindingAction.TOGGLE, "aim_mode", List.of("turret")),
                        new ControlBinding("key.r", BindingAction.PRESS, "reload", List.of("turret")),
                        new ControlBinding("key.f", BindingAction.PRESS, "cycle_weapon", List.of("turret"))
                )
        );

        var mining = new ControlGroup("MINING", ControlMode.INHERIT,
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                List.of(
                        new ControlBinding("mouse.left", BindingAction.HOLD, "drill_activate", List.of("mining_head")),
                        new ControlBinding("key.q", BindingAction.TOGGLE, "drill_mode", List.of("mining_head"))
                )
        );

        var utility = new ControlGroup("UTILITY", ControlMode.INHERIT,
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                List.of(
                        new ControlBinding("key.l", BindingAction.TOGGLE, "lights", List.of("lighting")),
                        new ControlBinding("key.h", BindingAction.PRESS, "horn", List.of("vehicle"))
                )
        );

        FOR_GUI_TEST = new ControlGroupSet(baseGroup,
                List.of(combat, mining, utility),
                List.of(
                        new GuiToggleAction("武器保险", "weapon_safety", List.of("turret")),
                        new GuiToggleAction("巡航模式", "cruise_control", List.of("main_engine")),
                        new GuiSliderAction("瞄准灵敏度", "aim_sensitivity", List.of("turret"),
                                0f, 100f, 1f, 65f),
                        new GuiSliderAction("引擎功率", "engine_power", List.of("main_engine"),
                                0f, 100f, 5f, 80f),
                        new GuiPulseAction("武器切换", "weapon_cycle", List.of("turret")),
                        new GuiPulseAction("紧急制动", "emergency_brake", List.of("brake_system"))
                ),
                0
        );
    }

    /**
     * 获取当前控制子系统的 subPartId，-1 表示不存在。
     */
    public static int getCurrentSubPartId(Minecraft mc) {
        if (mc.player instanceof IEntityMixin mixin
                && mixin.machine_Max$getControllingSubsystem() instanceof AbstractControllableSubsystem sub) {
            return sub.getOwner().getSubPart().getId();
        }
        return -1;
    }

    /**
     * 获取当前子系统名称。
     */
    @Nullable
    public static String getCurrentSubSystemName(Minecraft mc) {
        if (mc.player instanceof IEntityMixin mixin
                && mixin.machine_Max$getControllingSubsystem() instanceof AbstractControllableSubsystem sub) {
            return sub.name;
        }
        return null;
    }

    /**
     * 保存 ControlGroupSet 到服务端，同时更新本地子系统副本。<br>
     * 发包后直接修改本地子系统的 {@code controlGroupSet}，避免等服务端回传导致的延迟。
     * 如果玩家未在座椅中（无真实子系统），则仅更新本地 Mock 缓存。
     */
    public static void saveControlSet(Minecraft mc, ControlGroupSet modified) {
        // 1. 立即更新本地子系统副本（发包前改，确保即时生效）
        if (mc.player instanceof IEntityMixin mixin
                && mixin.machine_Max$getControllingSubsystem() instanceof AbstractControllableSubsystem sub) {
            sub.setControlGroupSet(modified);
        }
        // 2. 发送网络包到服务端
        int subPartId = getCurrentSubPartId(mc);
        String subSystemName = getCurrentSubSystemName(mc);
        if (subPartId >= 0 && subSystemName != null) {
            PacketDistributor.sendToServer(new ControlGroupSetEditPayload(subPartId, subSystemName, modified));
            cachedSnapshot = modified;
        } else {
            // 无座椅时：更新本地 Mock 数据
            FOR_GUI_TEST = modified;
        }
    }

    /**
     * 发送 GUI 控件操作到服务端。<br>
     * 如果玩家未在座椅中则不发送（静默丢弃）。
     */
    public static void sendGuiAction(Minecraft mc, int actionIndex, GuiActionType type, float value) {
        int subPartId = getCurrentSubPartId(mc);
        String subSystemName = getCurrentSubSystemName(mc);
        if (subPartId < 0 || subSystemName == null) return;
        PacketDistributor.sendToServer(new GuiActionPayload(subPartId, subSystemName, actionIndex, type, value));
    }
}
