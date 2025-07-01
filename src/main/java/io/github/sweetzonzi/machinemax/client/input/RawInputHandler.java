package io.github.sweetzonzi.machinemax.client.input;

import com.cinemamod.mcef.MCEF;
import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.web.widget.HudSmartWidget;
import io.github.sweetzonzi.machinemax.client.gui.WebAppHud;
import io.github.sweetzonzi.machinemax.client.screen.MMWebScreen;
import io.github.sweetzonzi.machinemax.common.registry.MMAttachments;
import io.github.sweetzonzi.machinemax.common.vehicle.Part;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machinemax.external.js.MMInitialJS;
import io.github.sweetzonzi.machinemax.external.js.hook.Hook;
import io.github.sweetzonzi.machinemax.external.js.hook.KeyHooks;
import io.github.sweetzonzi.machinemax.mixin_interface.IEntityMixin;
import io.github.sweetzonzi.machinemax.network.payload.MovementInputPayload;
import io.github.sweetzonzi.machinemax.network.payload.RegularInputPayload;
import io.github.sweetzonzi.machinemax.util.MMJoystickHandler;
import io.github.sweetzonzi.machinemax.util.data.KeyInputMapping;
import io.github.sweetzonzi.machinemax.web.MMWebApp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

import static io.github.sweetzonzi.machinemax.external.js.hook.KeyHooks.Combination.LEFT_CTRL;
import static io.github.sweetzonzi.machinemax.external.js.hook.KeyHooks.Combination.LEFT_SHIFT;

/**
 * 按键逻辑与发包
 *
 * @author 甜粽子
 */
@EventBusSubscriber(modid = MachineMax.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
@OnlyIn(Dist.CLIENT)
public class RawInputHandler {

    private static Minecraft client;
    static byte[] moveInputCache = new byte[6];//x,y,z方向的平移和绕x,y,z轴的旋转输入
    static byte[] moveInputs = new byte[6];//x,y,z方向的平移和绕x,y,z轴的旋转输入
    static byte[] moveInputConflicts = new byte[6];//相应轴向上的输入冲突
    public static boolean freeCam = false;//自由视角是否激活
    static HudSmartWidget sw_x_line = new HudSmartWidget("x_line");
//    static int trans_x_input = 0;
//    static int trans_y_input = 0;
//    static int trans_z_input = 0;
//    static int rot_x_input = 0;
//    static int rot_y_input = 0;
//    static int rot_z_input = 0;


    /**
     * 在每个客户端tick事件后调用，处理按键逻辑。
     *
     * @param event 客户端tick事件对象
     */
    @SubscribeEvent
    public static void handleMoveInputs(ClientTickEvent.Post event) {
        if (client == null) client = Minecraft.getInstance();
        if (client.player != null &&
                ((IEntityMixin) client.player).machine_Max$getRidingSubsystem() instanceof SeatSubsystem seat &&
                seat.owner instanceof Part part) {
            String subSystemName = seat.name;
            UUID vehicleUuid = part.vehicle.uuid;
            UUID partUuid = part.uuid;

            int trans_x_input = 0;
            int trans_y_input = 0;
            int trans_z_input = 0;
            int rot_x_input = 0;
            int rot_y_input = 0;
            int rot_z_input = 0;

            MMJoystickHandler.refreshState();

            switch (part.vehicle.mode) {
                case GROUND -> {
                    if (new KeyHooks.EVENT(KeyBinding.groundForwardKey).isHover()) trans_z_input += 100;
                    if (new KeyHooks.EVENT(KeyBinding.groundBackWardKey).isHover()) trans_z_input -= 100;
                    trans_z_input += Math.round((MMJoystickHandler.getAxisState(0, GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER) + 1) / 2 * 100);
                    trans_z_input -= Math.round((MMJoystickHandler.getAxisState(0, GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER) + 1) / 2 * 100);
                    if (new KeyHooks.EVENT(KeyBinding.groundLeftwardKey).isHover()) rot_y_input += 100;
                    if (new KeyHooks.EVENT(KeyBinding.groundRightwardKey).isHover()) rot_y_input -= 100;
                    rot_y_input -= Math.round(MMJoystickHandler.getAxisState(0, GLFW.GLFW_GAMEPAD_AXIS_LEFT_X) * 100);
                }
                case SHIP -> {}
                case PLANE -> {
                    //TODO:键盘输入的优先级应当高于视角朝向
                }
                case MECH -> {}
                default -> {}
            }
            moveInputCache = moveInputs;
            moveInputs = new byte[]{
                    (byte) (Math.clamp(trans_x_input, -100, 100)),
                    (byte) (Math.clamp(trans_y_input, -100, 100)),
                    (byte) (Math.clamp(trans_z_input, -100, 100)),
                    (byte) (Math.clamp(rot_x_input, -100, 100)),
                    (byte) (Math.clamp(rot_y_input, -100, 100)),
                    (byte) (Math.clamp(rot_z_input, -100, 100))};
            moveInputConflicts = new byte[]{ //TODO:待删除 conflicts
                    (byte) 0,
                    (byte) 0,
                    (byte) 0,
                    (byte) 0,
                    (byte) 0,
                    (byte) 0};
            if (vehicleUuid != null && partUuid != null && subSystemName != null && moveInputs != moveInputCache)
                PacketDistributor.sendToServer(new MovementInputPayload(
                        vehicleUuid, partUuid, subSystemName, moveInputs, moveInputConflicts));
        }
    }

    @SubscribeEvent
    public static void handleMouseInputs(ClientTickEvent.Post event) {
        if (client == null) client = Minecraft.getInstance();
        if (client.player == null) return;
        Vec3 pos = client.player.position();
        if (KeyBinding.generalFreeCamKey.isDown()) {
            freeCam = true;
        } else {
            freeCam = false;
        }
    }


    @SubscribeEvent
    public static void handleNormalInputs(ClientTickEvent.Post event) {
        if (client == null) client = Minecraft.getInstance();

        // TODO: 策略组的事件体测试区

        new KeyHooks.EVENT("z")
                .with(LEFT_CTRL)
                .OnKeyDown(()->{
                    System.out.println("闪烁");
                    sw_x_line.setStatus(HudSmartWidget.HudStatus.blink);
                    sw_x_line.update();
                })
                .flush() //与上方的绑定断开
                .with(LEFT_SHIFT)
                .OnKeyDown(()->{
                    System.out.println("常亮");
                    sw_x_line.setStatus(HudSmartWidget.HudStatus.on);
                    sw_x_line.update();
                })
                .flush() //与上方的绑定断开
                .with(LEFT_CTRL)
                .with(LEFT_SHIFT)
                .OnKeyDown(()->{
                    System.out.println("关闭");
                    sw_x_line.setStatus(HudSmartWidget.HudStatus.off);
                    sw_x_line.update();
                })
        ;


        /*
          js功能
         */

        new KeyHooks.EVENT("backslash").with(LEFT_CTRL).OnKeyDown(() -> { //反斜杠热更新
            MMInitialJS.clear();
            MMInitialJS.hotReload();
            MMInitialJS.register();
        });

        //浏览器页面
        if (MMWebApp.browser == null) {
            boolean transparent = true;
            MCEF.getSettings().setUseCache(false);//关闭cache
            MMWebApp.browser = MCEF.createBrowser(MMWebApp.URL+"#/hud", transparent);
        }

        if (client.player != null ) {
            new KeyHooks.EVENT("o")
                    .OnKeyDown(() -> {
                        Minecraft.getInstance().setScreen(new MMWebScreen());
                    });
            new KeyHooks.EVENT("h").OnKeyDown(() -> WebAppHud.hidden = !WebAppHud.hidden);
        /*
          通用功能
         */

            //载具交互
            new KeyHooks.EVENT(KeyBinding.generalInteractKey)
                    .OnKeyDown(() -> {
                        client.player.getData(MMAttachments.getENTITY_EYESIGHT().get()).clientInteract();
                    });

            //离开载具
            new KeyHooks.EVENT(KeyBinding.generalLeaveVehicleKey)
                    .OnKeyHover((tick -> {
                        if (tick <= 10.0) {
                            PacketDistributor.sendToServer(new RegularInputPayload(KeyInputMapping.LEAVE_VEHICLE.getValue(), (int) tick));
                            if (client.player.getVehicle() != null || ((IEntityMixin) client.player).machine_Max$getRidingSubsystem() != null) client.player.displayClientMessage(
                                    Component.translatable("message.machine_max.leaving_vehicle",
                                            KeyBinding.generalLeaveVehicleKey.getTranslatedKeyMessage(),
                                            String.format("%.2f", Math.clamp(0.05 * tick, 0.0, 0.5))
                                    ), true
                            );

                        }
                    }))
                    .OnKeyUp((() -> {
                        if (Minecraft.getInstance().player instanceof Player player && player.getVehicle() != null)
                            player.displayClientMessage(Component.empty(), true);
                    }));



        /*
          地面载具
         */
            //离合
            new KeyHooks.EVENT(KeyBinding.groundClutchKey)
                    .OnKeyDown(() -> {
                        PacketDistributor.sendToServer(new RegularInputPayload(KeyInputMapping.CLUTCH.getValue(), 0));
                    })
                    .OnKeyUp(() -> {
                        PacketDistributor.sendToServer(new RegularInputPayload(KeyInputMapping.CLUTCH.getValue(), 1));
                    });

            //升档
            new KeyHooks.EVENT(KeyBinding.groundUpShiftKey)
                    .OnKeyDown(() -> {
                        PacketDistributor.sendToServer(new RegularInputPayload(KeyInputMapping.UP_SHIFT.getValue(), 0));
                    });

            //降档
            new KeyHooks.EVENT(KeyBinding.groundDownShiftKey)
                    .OnKeyDown(() -> {
                        PacketDistributor.sendToServer(new RegularInputPayload(KeyInputMapping.DOWN_SHIFT.getValue(), 0));
                    });

            //按住手刹
            new KeyHooks.EVENT(KeyBinding.groundHandBrakeKey)
                    .addChild(new KeyHooks.EVENT( // 模仿尘埃拉力：手柄B键也会触发
                            new KeyHooks.GamePadSetting(0, KeyHooks.GamePadSetting.GType.Button, GLFW.GLFW_GAMEPAD_BUTTON_B)))
                    .OnKeyDown(() -> {
                        PacketDistributor.sendToServer(new RegularInputPayload(KeyInputMapping.HAND_BRAKE.getValue(), 0));
                    })
                    .OnKeyUp(() -> {
                        PacketDistributor.sendToServer(new RegularInputPayload(KeyInputMapping.HAND_BRAKE.getValue(), 1));
                    });

            //切换手刹
            new KeyHooks.EVENT(KeyBinding.groundToggleHandBrakeKey)
                    .OnKeyDown(() -> {
                        PacketDistributor.sendToServer(new RegularInputPayload(KeyInputMapping.TOGGLE_HAND_BRAKE.getValue(), 0));
                    });

        /*
          载具组装
         */
            //切换部件对接口
            new KeyHooks.EVENT(KeyBinding.assemblyCycleConnectorKey)
                    .OnKeyDown(() -> {
                        PacketDistributor.sendToServer(new RegularInputPayload(KeyInputMapping.CYCLE_PART_CONNECTORS.getValue(), 0));
                    });

            //切换部件变体类型
            new KeyHooks.EVENT(KeyBinding.assemblyCycleVariantKey)
                    .OnKeyDown(() -> {
                        PacketDistributor.sendToServer(new RegularInputPayload(KeyInputMapping.CYCLE_PART_VARIANTS.getValue(), 0));
                    });
        }


    }

    @SubscribeEvent
    public static void handVanillaInputs(InputEvent.InteractionKeyMappingTriggered event) {
        // 乘坐载具时屏蔽部分原版按键功能 Disable some vanilla key function when on a vehicle
        LocalPlayer player = Minecraft.getInstance().player;
        if (player instanceof IEntityMixin passenger &&
                passenger.machine_Max$getRidingSubsystem() instanceof SeatSubsystem seat &&
                seat.disableVanillaActions) {
            if (event.getKeyMapping() == Minecraft.getInstance().options.keyAttack ||
                    event.getKeyMapping() == Minecraft.getInstance().options.keyUse ||
                    event.getKeyMapping() == Minecraft.getInstance().options.keyPickItem) {
                event.setSwingHand(false);
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void handleVanillaInputs(InputEvent.Key event) {
        // 乘坐载具时屏蔽部分原版按键功能 Disable some vanilla key function when on a vehicle
        LocalPlayer player = Minecraft.getInstance().player;
        if (player instanceof IEntityMixin passenger &&
                passenger.machine_Max$getRidingSubsystem() instanceof SeatSubsystem seat) {
            //很奇怪，必须套一层if判断，屏蔽效果才能生效 Wired, must have a if to work
            if (Minecraft.getInstance().options.keyUp.consumeClick()) {
                Minecraft.getInstance().options.keyUp.setDown(false);
            } else if (Minecraft.getInstance().options.keyDown.consumeClick()) {
                Minecraft.getInstance().options.keyDown.setDown(false);
            } else if (Minecraft.getInstance().options.keyLeft.consumeClick()) {
                Minecraft.getInstance().options.keyLeft.setDown(false);
            } else if (Minecraft.getInstance().options.keyRight.consumeClick()) {
                Minecraft.getInstance().options.keyRight.setDown(false);
            } else if (Minecraft.getInstance().options.keyShift.consumeClick()) {
                Minecraft.getInstance().options.keyShift.setDown(false);
            } else if (Minecraft.getInstance().options.keyInventory.isDown() && seat.disableVanillaActions) {
                Minecraft.getInstance().options.keyInventory.consumeClick();
                Minecraft.getInstance().options.keyInventory.setDown(false);
            } else if (Minecraft.getInstance().options.keyDrop.isDown() && seat.disableVanillaActions) {
                Minecraft.getInstance().options.keyDrop.consumeClick();
                Minecraft.getInstance().options.keyDrop.setDown(false);
            } else if (Minecraft.getInstance().options.keySwapOffhand.isDown() && seat.disableVanillaActions) {
                Minecraft.getInstance().options.keySwapOffhand.consumeClick();
                Minecraft.getInstance().options.keySwapOffhand.setDown(false);
            }
        }
    }
}


