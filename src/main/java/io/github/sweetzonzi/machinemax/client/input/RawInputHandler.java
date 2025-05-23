package io.github.sweetzonzi.machinemax.client.input;

import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.common.registry.MMAttachments;
import io.github.sweetzonzi.machinemax.common.vehicle.Part;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machinemax.mixin_interface.IEntityMixin;
import io.github.sweetzonzi.machinemax.network.payload.MovementInputPayload;
import io.github.sweetzonzi.machinemax.network.payload.RegularInputPayload;
import io.github.sweetzonzi.machinemax.util.data.KeyInputMapping;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.UUID;

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

    static int trans_x_input = 0;
    static int trans_y_input = 0;
    static int trans_z_input = 0;
    static int rot_x_input = 0;
    static int rot_y_input = 0;
    static int rot_z_input = 0;

    public static final HashMap<KeyMapping, Integer> keyPressTicks = HashMap.newHashMap(15);//各个按键被按下的持续时间

    public static void init(FMLClientSetupEvent event) {
        client = Minecraft.getInstance();
    }

    /**
     * 在每个客户端tick事件后调用，处理按键逻辑。
     *
     * @param event 客户端tick事件对象
     */
    @SubscribeEvent
    public static void handleMoveInputs(ClientTickEvent.Post event) {
        if (client.player != null &&
                ((IEntityMixin) client.player).machine_Max$getRidingSubsystem() instanceof SeatSubsystem subSystem &&
                subSystem.owner instanceof Part part) {
            String subSystemName = subSystem.name;
            UUID vehicleUuid = part.vehicle.uuid;
            UUID partUuid = part.uuid;
            int trans_x_conflict = 0;
            int trans_y_conflict = 0;
            int trans_z_conflict = 0;
            int rot_x_conflict = 0;
            int rot_y_conflict = 0;
            int rot_z_conflict = 0;
            switch (part.vehicle.mode) {
                case GROUND:
                    //移动
                    if (KeyBinding.groundForwardKey.isDown() && KeyBinding.groundBackWardKey.isDown()) {
                        trans_z_conflict = 1;
                        trans_z_input = 0;
                    } else if (KeyBinding.groundForwardKey.isDown()) trans_z_input = 100;
                    else if (KeyBinding.groundBackWardKey.isDown()) trans_z_input = -100;
                    else trans_z_input = 0;
                    //转向
                    if (KeyBinding.groundLeftwardKey.isDown() && KeyBinding.groundRightwardKey.isDown()) {
                        rot_y_conflict = 1;
                        rot_y_input = 0;
                    } else if (KeyBinding.groundLeftwardKey.isDown()) rot_y_input = 100;
                    else if (KeyBinding.groundRightwardKey.isDown()) rot_y_input = -100;
                    else rot_y_input = 0;
                    break;
                case SHIP:
                    break;
                case PLANE:
                    //TODO:键盘输入的优先级应当高于视角朝向
                    break;
                case MECH:
                    break;
                default:
                    break;
            }
            moveInputCache = moveInputs;
            moveInputs = new byte[]{
                    (byte) (trans_x_input),
                    (byte) (trans_y_input),
                    (byte) (trans_z_input),
                    (byte) (rot_x_input),
                    (byte) (rot_y_input),
                    (byte) (rot_z_input)};
            moveInputConflicts = new byte[]{
                    (byte) (trans_x_conflict),
                    (byte) (trans_y_conflict),
                    (byte) (trans_z_conflict),
                    (byte) (rot_x_conflict),
                    (byte) (rot_y_conflict),
                    (byte) (rot_z_conflict)};
            if (vehicleUuid != null && partUuid != null && subSystemName != null && moveInputs != moveInputCache)
                PacketDistributor.sendToServer(new MovementInputPayload(
                        vehicleUuid, partUuid, subSystemName, moveInputs, moveInputConflicts));
        }
    }

    @SubscribeEvent
    public static void handleMouseInputs(ClientTickEvent.Post event) {
        if (client.player == null) return;
        if (KeyBinding.generalFreeCamKey.isDown()) {
            freeCam = true;
        } else {
            freeCam = false;
        }
    }

    @SubscribeEvent
    public static void handleNormalInputs(ClientTickEvent.Post event) {
        var client = Minecraft.getInstance();
        /*
          通用功能
         */
        //载具交互
        if (KeyBinding.generalInteractKey.isDown()) {
            if (keyPressTicks.getOrDefault(KeyBinding.generalInteractKey, 0) == 0) {//一般互动
                if (client.player != null)
                    client.player.getData(MMAttachments.getENTITY_EYESIGHT().get()).clientInteract();
            }
            if (keyPressTicks.getOrDefault(KeyBinding.generalInteractKey, 0) == 10) {//长按0.5秒，进入交互模式

            }
            //按键计时器
            keyPressTicks.put(KeyBinding.generalInteractKey, keyPressTicks.getOrDefault(KeyBinding.generalInteractKey, 0) + 1);

        } else if (keyPressTicks.getOrDefault(KeyBinding.generalInteractKey, 0) > 0) {//按键松开且按下持续至少1tick
            keyPressTicks.put(KeyBinding.generalInteractKey, 0);//重置计时器
            //TODO:退出交互模式
        }
        //离开载具
        if (KeyBinding.generalLeaveVehicleKey.isDown()) {
            if (keyPressTicks.getOrDefault(KeyBinding.generalLeaveVehicleKey, 0) == 10) {//长按0.5秒，离开载具
                PacketDistributor.sendToServer(new RegularInputPayload(KeyInputMapping.LEAVE_VEHICLE.getValue(), 10));
            }
            //按键计时
            keyPressTicks.put(KeyBinding.generalLeaveVehicleKey, keyPressTicks.getOrDefault(KeyBinding.generalLeaveVehicleKey, 0) + 1);
            //离开载具进度显示
            if (Minecraft.getInstance().player instanceof Player player && (player.getVehicle() != null || ((IEntityMixin) player).machine_Max$getRidingSubsystem() != null))
                player.displayClientMessage(
                        Component.translatable("message.machine_max.leaving_vehicle",
                                KeyBinding.generalLeaveVehicleKey.getTranslatedKeyMessage(),
                                String.format("%.2f", Math.clamp(0.05 * keyPressTicks.getOrDefault(KeyBinding.generalLeaveVehicleKey, 0), 0.0, 0.5))
                        ), true
                );
        } else if (keyPressTicks.getOrDefault(KeyBinding.generalLeaveVehicleKey, 0) > 0) {//按键松开且按下持续至少1tick
            keyPressTicks.put(KeyBinding.generalLeaveVehicleKey, 0);
            if (Minecraft.getInstance().player instanceof Player player && player.getVehicle() != null)
                player.displayClientMessage(Component.empty(), true);
        }
        /*
          地面载具
         */
        //离合
        if (KeyBinding.groundClutchKey.isDown()) {
            if (keyPressTicks.getOrDefault(KeyBinding.groundClutchKey, 0) == 0) {//按下时
                PacketDistributor.sendToServer(new RegularInputPayload(KeyInputMapping.CLUTCH.getValue(), 0));
            }
            keyPressTicks.put(KeyBinding.groundClutchKey, keyPressTicks.getOrDefault(KeyBinding.groundClutchKey, 0) + 1);
        } else if (keyPressTicks.getOrDefault(KeyBinding.groundClutchKey, 0) > 0) {//按键松开且按下持续至少1tick
            PacketDistributor.sendToServer(new RegularInputPayload(KeyInputMapping.CLUTCH.getValue(), keyPressTicks.get(KeyBinding.groundClutchKey)));
            keyPressTicks.put(KeyBinding.groundClutchKey, 0);
        }
        //升档
        if (KeyBinding.groundUpShiftKey.isDown()) {
            if (keyPressTicks.getOrDefault(KeyBinding.groundUpShiftKey, 0) == 0) {//按下时
                PacketDistributor.sendToServer(new RegularInputPayload(KeyInputMapping.UP_SHIFT.getValue(), 0));
            }
            keyPressTicks.put(KeyBinding.groundUpShiftKey, keyPressTicks.getOrDefault(KeyBinding.groundUpShiftKey, 0) + 1);
        } else if (keyPressTicks.getOrDefault(KeyBinding.groundUpShiftKey, 0) > 0) {//按键松开且按下持续至少1tick
            keyPressTicks.put(KeyBinding.groundUpShiftKey, 0);
        }
        //降档
        if (KeyBinding.groundDownShiftKey.isDown()) {
            if (keyPressTicks.getOrDefault(KeyBinding.groundDownShiftKey, 0) == 0) {//按下时
                PacketDistributor.sendToServer(new RegularInputPayload(KeyInputMapping.DOWN_SHIFT.getValue(), 0));
            }
            keyPressTicks.put(KeyBinding.groundDownShiftKey, keyPressTicks.getOrDefault(KeyBinding.groundDownShiftKey, 0) + 1);
        } else if (keyPressTicks.getOrDefault(KeyBinding.groundDownShiftKey, 0) > 0) {//按键松开且按下持续至少1tick
            keyPressTicks.put(KeyBinding.groundDownShiftKey, 0);
        }
        //按住手刹
        if (KeyBinding.groundHandBrakeKey.isDown()) {
            if (keyPressTicks.getOrDefault(KeyBinding.groundHandBrakeKey, 0) == 0) {//按下时
                PacketDistributor.sendToServer(new RegularInputPayload(KeyInputMapping.HAND_BRAKE.getValue(), 0));
            }
            keyPressTicks.put(KeyBinding.groundHandBrakeKey, keyPressTicks.getOrDefault(KeyBinding.groundHandBrakeKey, 0) + 1);
        } else if (keyPressTicks.getOrDefault(KeyBinding.groundHandBrakeKey, 0) > 0) {//按键松开且按下持续至少1tick
            PacketDistributor.sendToServer(new RegularInputPayload(KeyInputMapping.HAND_BRAKE.getValue(), keyPressTicks.get(KeyBinding.groundHandBrakeKey)));
            keyPressTicks.put(KeyBinding.groundHandBrakeKey, 0);
        }
        //切换手刹
        if (KeyBinding.groundToggleHandBrakeKey.isDown()) {
            if (keyPressTicks.getOrDefault(KeyBinding.groundToggleHandBrakeKey, 0) == 0) {//按下时
                PacketDistributor.sendToServer(new RegularInputPayload(KeyInputMapping.TOGGLE_HAND_BRAKE.getValue(), 0));
            }
            keyPressTicks.put(KeyBinding.groundToggleHandBrakeKey, keyPressTicks.getOrDefault(KeyBinding.groundToggleHandBrakeKey, 0) + 1);
        } else if (keyPressTicks.getOrDefault(KeyBinding.groundToggleHandBrakeKey, 0) > 0) {//按键松开且按下持续至少1tick
            keyPressTicks.put(KeyBinding.groundToggleHandBrakeKey, 0);
        }
        /*
          载具组装
         */
        //切换部件对接口
        if (KeyBinding.assemblyCycleConnectorKey.isDown()) {
            if (keyPressTicks.getOrDefault(KeyBinding.assemblyCycleConnectorKey, 0) == 0) {//按下时循环切换配件连接点键时发包服务器
                PacketDistributor.sendToServer(new RegularInputPayload(KeyInputMapping.CYCLE_PART_CONNECTORS.getValue(), 0));
            }
            keyPressTicks.put(KeyBinding.assemblyCycleConnectorKey, keyPressTicks.getOrDefault(KeyBinding.assemblyCycleConnectorKey, 0) + 1);
        } else if (keyPressTicks.getOrDefault(KeyBinding.assemblyCycleConnectorKey, 0) > 0) {//按键松开且按下持续至少1tick
            keyPressTicks.put(KeyBinding.assemblyCycleConnectorKey, 0);
        }
        //切换部件变体类型
        if (KeyBinding.assemblyCycleVariantKey.isDown()) {
            if (keyPressTicks.getOrDefault(KeyBinding.assemblyCycleVariantKey, 0) == 0) {//按下时循环切换配件连接点键时发包服务器
                PacketDistributor.sendToServer(new RegularInputPayload(KeyInputMapping.CYCLE_PART_VARIANTS.getValue(), 0));
            }
            keyPressTicks.put(KeyBinding.assemblyCycleVariantKey, keyPressTicks.getOrDefault(KeyBinding.assemblyCycleVariantKey, 0) + 1);
        } else if (keyPressTicks.getOrDefault(KeyBinding.assemblyCycleVariantKey, 0) > 0) {//按键松开且按下持续至少1tick
            keyPressTicks.put(KeyBinding.assemblyCycleVariantKey, 0);
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


