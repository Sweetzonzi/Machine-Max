package io.github.tt432.machinemax.client.input;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.network.payload.RegularInputPayload;
import io.github.tt432.machinemax.util.data.KeyInputMapping;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;

/**
 * 按键逻辑与发包
 *
 * @author 甜粽子
 */
@EventBusSubscriber(modid = MachineMax.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
@OnlyIn(Dist.CLIENT)
public class RawInputHandler {

    static byte[] moveInputs = new byte[6];//x,y,z方向的平移和绕x,y,z轴的旋转输入
    static byte[] moveInputConflicts = new byte[6];//相应轴向上的输入冲突
    static int id = 0;

    static int trans_x_input = 0;
    static int trans_y_input = 0;
    static int trans_z_input = 0;
    static int rot_x_input = 0;
    static int rot_y_input = 0;
    static int rot_z_input = 0;

    private static final HashMap<KeyMapping,Integer> keyPressTicks = HashMap.newHashMap(15);//各个按键被按下的持续时间
    /**
     * 在每个客户端tick事件后调用，处理按键逻辑。
     *
     * @param event 客户端tick事件对象
     */
    @SubscribeEvent
    public static void handleMoveInputs(ClientTickEvent.Post event) {
        var client = Minecraft.getInstance();

//        if (client.player != null && client.player.getVEHICLE_BUS() instanceof OldPartEntity e) {
//            id = e.getId();
//            int trans_x_conflict = 0;
//            int trans_y_conflict = 0;
//            int trans_z_conflict = 0;
//            int rot_x_conflict = 0;
//            int rot_y_conflict = 0;
//            int rot_z_conflict = 0;
//            switch (e.getMode()) {
//                case GROUND:
//                    //移动
//                    if (KeyBinding.groundForwardKey.isDown() && KeyBinding.groundBackWardKey.isDown()) {
//                        trans_z_conflict = 1;
//                        trans_z_input = 0;
//                    }
//                    else if (KeyBinding.groundForwardKey.isDown()) trans_z_input = 100;
//                    else if (KeyBinding.groundBackWardKey.isDown()) trans_z_input = -100;
//                    else trans_z_input = 0;
//                    //转向
//                    if (KeyBinding.groundLeftwardKey.isDown() && KeyBinding.groundRightwardKey.isDown()) {
//                        rot_y_conflict = 1;
//                        rot_y_input = 0;
//                    }
//                    else if (KeyBinding.groundLeftwardKey.isDown()) rot_y_input = 100;
//                    else if (KeyBinding.groundRightwardKey.isDown()) rot_y_input = -100;
//                    else rot_y_input = 0;
//                    break;
//                case SHIP:
//                    break;
//                case PLANE:
//                    //TODO:键盘输入的优先级应当高于视角朝向
//                    break;
//                case MECH:
//                    break;
//                default:
//                    break;
//            }
//
//            moveInputs = new byte[]{
//                    (byte) (trans_x_input),
//                    (byte) (trans_y_input),
//                    (byte) (trans_z_input),
//                    (byte) (rot_x_input),
//                    (byte) (rot_y_input),
//                    (byte) (rot_z_input)};
//            moveInputConflicts = new byte[]{
//                    (byte) (trans_x_conflict),
//                    (byte) (trans_y_conflict),
//                    (byte) (trans_z_conflict),
//                    (byte) (rot_x_conflict),
//                    (byte) (rot_y_conflict),
//                    (byte) (rot_z_conflict)};
//            PacketDistributor.sendToServer(new MovementInputPayload(id, moveInputs, moveInputConflicts));
//        }
    }

    @SubscribeEvent
    public static void handleAimInputs(ClientTickEvent.Post event) {
        if (KeyBinding.generalFreeCamKey.isDown()) {
            //TODO:自由视角键未被按下时，根据相机镜头角度发包视线输入
        }
    }

    @SubscribeEvent
    public static void handleNormalInputs(ClientTickEvent.Post event){
        //载具交互
        if (KeyBinding.generalInteractKey.isDown()){
            if(keyPressTicks.getOrDefault(KeyBinding.generalInteractKey,0) == 0){
                PacketDistributor.sendToServer(new RegularInputPayload(KeyInputMapping.INTERACT.getValue(), 0));
            }
            keyPressTicks.put(KeyBinding.generalInteractKey, keyPressTicks.getOrDefault(KeyBinding.generalInteractKey,0) + 1);
        } else if (keyPressTicks.getOrDefault(KeyBinding.generalInteractKey,0) > 0) {//按键松开且按下持续至少1tick
            PacketDistributor.sendToServer(new RegularInputPayload(KeyInputMapping.INTERACT.getValue(),keyPressTicks.get(KeyBinding.generalInteractKey)));
            keyPressTicks.put(KeyBinding.generalInteractKey, 0);
        }
        //切换部件对接口
        if(KeyBinding.assemblyCycleConnectorKey.isDown()){
            if(keyPressTicks.getOrDefault(KeyBinding.assemblyCycleConnectorKey,0) == 0){//按下时循环切换配件连接点键时发包服务器
                PacketDistributor.sendToServer(new RegularInputPayload(KeyInputMapping.CYCLE_PART_CONNECTORS.getValue(), 0));
            }
            keyPressTicks.put(KeyBinding.assemblyCycleConnectorKey, keyPressTicks.getOrDefault(KeyBinding.assemblyCycleConnectorKey,0) + 1);
        } else if (keyPressTicks.getOrDefault(KeyBinding.assemblyCycleConnectorKey,0) > 0) {//按键松开且按下持续至少1tick
            keyPressTicks.put(KeyBinding.assemblyCycleConnectorKey, 0);
        }
        //切换部件变体类型
        if(KeyBinding.assemblyCycleVariantKey.isDown()){
            if(keyPressTicks.getOrDefault(KeyBinding.assemblyCycleVariantKey,0) == 0){//按下时循环切换配件连接点键时发包服务器
                PacketDistributor.sendToServer(new RegularInputPayload(KeyInputMapping.CYCLE_PART_VARIANTS.getValue(), 0));
            }
            keyPressTicks.put(KeyBinding.assemblyCycleVariantKey, keyPressTicks.getOrDefault(KeyBinding.assemblyCycleVariantKey,0) + 1);
        } else if (keyPressTicks.getOrDefault(KeyBinding.assemblyCycleVariantKey,0) > 0) {//按键松开且按下持续至少1tick
            keyPressTicks.put(KeyBinding.assemblyCycleVariantKey, 0);
        }
    }
}


