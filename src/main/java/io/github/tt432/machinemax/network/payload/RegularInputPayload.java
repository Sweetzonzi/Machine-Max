package io.github.tt432.machinemax.network.payload;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.item.prop.MMPartItem;
import io.github.tt432.machinemax.common.registry.MMAttachments;
import io.github.tt432.machinemax.common.registry.MMItems;
import io.github.tt432.machinemax.common.vehicle.connector.AbstractConnector;
import io.github.tt432.machinemax.util.data.KeyInputMapping;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record RegularInputPayload(int key, int tick_count) implements CustomPacketPayload {
    public static final Type<RegularInputPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "regular_input_payload"));
    public static final StreamCodec<ByteBuf, RegularInputPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            RegularInputPayload::key,//按下的按键
            ByteBufCodecs.VAR_INT,
            RegularInputPayload::tick_count,//0为按下，1为松开
            RegularInputPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

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
            case FREE_CAM://自由相机模式

                break;
            case INTERACT://与载具等交互
                if (player.getVehicle() == null && payload.tick_count() < 10) {
                    //未处于骑乘状态，则在按下按键时交互
                } else if (player.getVehicle() != null && payload.tick_count() >= 10) {
                    Vec3 dismountLocation = player.getVehicle().getDismountLocationForPassenger(player);
                    player.dismountTo(dismountLocation.x, dismountLocation.y, dismountLocation.z);
                }
                break;
            case CYCLE_PART_ATTACH_POINTS://切换零件连接点
                var eyesightBody = player.getData(MMAttachments.getENTITY_EYESIGHT());
                AbstractConnector targetPort = eyesightBody.getConnector();//获取视线看着的部件对接口
                if (targetPort != null && !targetPort.hasPart()) {
                    ItemStack heldItem = player.getMainHandItem();
                    if (!heldItem.is(MMItems.getPART_ITEM())) {//确保手持物品为部件物品
                        heldItem = player.getOffhandItem();
                        if (!heldItem.is(MMItems.getPART_ITEM())) break;
                    }
//                    if (MMPartItem.getPart(heldItem, player.level()) != null) {
//                        //切换零件连接点
//                        AbstractPortPort currentPort = MMPartItem.getNextPort(heldItem, player.level());
//                        int i = 0;
//                        //当当前接口不是连接点接口且目标接口也不为连接点接口时
//                        while (!(currentPort instanceof AttachPointPortPort) && !(targetPort instanceof AttachPointPortPort) && i < 100) {
//                            //循环获取下一个端口，直到找到连接点接口或到达迭代次数上限
//                            currentPort = MMPartItem.getNextPort(heldItem, player.level());
//                            i++;
//                        }
//                    }
                }
                break;
        }
    }
}
