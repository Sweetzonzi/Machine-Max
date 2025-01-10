package io.github.tt432.machinemax.network.handler;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.network.payload.RegularInputPayload;
import io.github.tt432.machinemax.util.data.KeyInputMapping;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 除移动和视角外的其他输入在此统一处理
 */
public class InputPayloadHandler {
    //将其他玩家的输入同步至本机，以在客户端模拟其他玩家的操作
    public static void clientHandler(final RegularInputPayload payload, final IPayloadContext context) {
        handle(payload, context);
    }

    public static void serverHandler(final RegularInputPayload payload, final IPayloadContext context) {
        handle(payload, context);
        //将玩家输入转发给其他玩家，以在其他玩家客户端模拟自己的操作
        Player player = context.player();
        PacketDistributor.sendToPlayersInDimension((ServerLevel) player.level(), payload);
    }

    public static void handle(final RegularInputPayload payload, final IPayloadContext context) {
        Player player = context.player();
        switch (KeyInputMapping.fromValue(payload.key())) {
            case FREE_CAM:
                //自由相机模式
                break;
            case INTERACT:
                //与载具等交互
                if (player.getVehicle() == null && payload.tick_count() < 10) {
                    //未处于骑乘状态，则在按下按键时交互
                } else if (player.getVehicle() !=null && payload.tick_count() >= 10){
                Vec3 dismountLocation = player.getVehicle().getDismountLocationForPassenger(player);
                player.dismountTo(dismountLocation.x, dismountLocation.y, dismountLocation.z);
            }
        }
    }
}
