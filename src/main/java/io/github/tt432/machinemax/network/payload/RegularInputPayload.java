package io.github.tt432.machinemax.network.payload;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.attachment.LivingEntityEyesightAttachment;
import io.github.tt432.machinemax.common.component.PartAssemblyCacheComponent;
import io.github.tt432.machinemax.common.component.PartAssemblyInfoComponent;
import io.github.tt432.machinemax.common.entity.MMPartEntity;
import io.github.tt432.machinemax.common.item.prop.MMPartItem;
import io.github.tt432.machinemax.common.registry.MMAttachments;
import io.github.tt432.machinemax.common.registry.MMDataComponents;
import io.github.tt432.machinemax.common.registry.MMItems;
import io.github.tt432.machinemax.common.vehicle.PartType;
import io.github.tt432.machinemax.common.vehicle.connector.AbstractConnector;
import io.github.tt432.machinemax.common.vehicle.connector.AttachPointConnector;
import io.github.tt432.machinemax.common.vehicle.subsystem.AbstractSubsystem;
import io.github.tt432.machinemax.common.vehicle.subsystem.SeatSubsystem;
import io.github.tt432.machinemax.mixin_interface.IEntityMixin;
import io.github.tt432.machinemax.util.data.KeyInputMapping;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

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
        Level level = player.level();
        ItemStack heldItem;
        LivingEntityEyesightAttachment eyesightBody;
        AbstractConnector targetConnector;
        switch (KeyInputMapping.fromValue(payload.key())) {
            case FREE_CAM://自由相机模式

                break;
            case INTERACT://与载具等交互
                if (payload.tick_count() == 0) {
                    //未乘坐于载具部件，则在按下按键时交互
                    var a = player.getVehicle();
                    var b = ((IEntityMixin) player).getRidingSubsystem();
                } else if (player.getVehicle() != null && payload.tick_count() >= 10) {
                    //处于骑乘状态，且长按互动键1秒，则尝试脱离载具
                    if (player.getVehicle() instanceof MMPartEntity vehicle) {
                        for (AbstractSubsystem subSystem : vehicle.part.subsystems.values()) {
                            if (subSystem instanceof SeatSubsystem seatSubSystem) {
                                seatSubSystem.removePassenger();
                                break;
                            }
                        }
                        player.stopRiding();//保险措施，确保停止骑乘
                    } else player.stopRiding();//一般载具实体的处理方式
                }
                break;
            case CYCLE_PART_CONNECTORS://切换部件连接点
                if (!level.isClientSide()) {//仅在服务器端处理
                    heldItem = player.getMainHandItem();
                    if (!heldItem.is(MMItems.getPART_ITEM())) {//确保手持物品为部件物品
                        heldItem = player.getOffhandItem();
                        if (!heldItem.is(MMItems.getPART_ITEM())) break;
                    }
                    eyesightBody = player.getData(MMAttachments.getENTITY_EYESIGHT());
                    targetConnector = eyesightBody.getConnector();//获取视线看着的部件对接口
                    if (targetConnector != null && !targetConnector.hasPart()) {
                        PartType partType = MMPartItem.getPartType(heldItem, level);
                        PartAssemblyInfoComponent info = MMPartItem.getPartAssemblyInfo(heldItem, level);
                        PartAssemblyCacheComponent iterators = MMPartItem.getPartAssemblyCache(heldItem, level);
                        Map<String, String> partConnectors = partType.getPartOutwardConnectors();
                        int i = partConnectors.size();//设置最大迭代次数
                        String connector = iterators.getNextConnector();//获取下一个部件接口
                        info = new PartAssemblyInfoComponent(info.variant(), connector, partConnectors.get(connector));
                        while (i > 0) {
                            //循环获取下一个端口，直到找到合适的接口或到达迭代次数上限
                            if (partConnectors.get(info.connector()).equals("AttachPoint") || targetConnector instanceof AttachPointConnector) {
                                break;
                            } //TODO: 检查部件Tag是否与目标接口接受的类型匹配
                            connector = iterators.getNextConnector();//若接口类型不匹配，则尝试下一个接口
                            info = new PartAssemblyInfoComponent(info.variant(), connector, partConnectors.get(connector));
                            i--;
                        }
                        heldItem.set(MMDataComponents.getPART_ASSEMBLY_INFO(), info);//更新物品保存的部件连接信息
                    }
                }
                break;
            case CYCLE_PART_VARIANTS://切换部件变体
                if (!level.isClientSide()) {//仅在服务器端处理
                    heldItem = player.getMainHandItem();
                    if (!heldItem.is(MMItems.getPART_ITEM())) {//确保手持物品为部件物品
                        heldItem = player.getOffhandItem();
                        if (!heldItem.is(MMItems.getPART_ITEM())) break;
                    }
                    eyesightBody = player.getData(MMAttachments.getENTITY_EYESIGHT());
                    targetConnector = eyesightBody.getConnector();//获取视线看着的部件对接口
                    if (targetConnector != null && !targetConnector.hasPart()) {
                        PartType partType = MMPartItem.getPartType(heldItem, level);
                        PartAssemblyInfoComponent info = MMPartItem.getPartAssemblyInfo(heldItem, level);
                        PartAssemblyCacheComponent iterators = MMPartItem.getPartAssemblyCache(heldItem, level);
                        int i = partType.variants.size();//设置最大迭代次数
                        String variant = iterators.getNextVariant();//获取下一个部件变体
                        info = new PartAssemblyInfoComponent(variant, info.connector(), info.connectorType());
                        while (i > 0) {
                            //循环获取下一个部件变体，直到找到合适的部件变体或到达迭代次数上限
                            if (targetConnector.acceptableVariants.contains(info.variant())) break;
                            variant = iterators.getNextVariant();//若部件变体不在目标接口可接受的变体列表中，则尝试下一个变体
                            info = new PartAssemblyInfoComponent(variant, info.connector(), info.connectorType());
                            i--;
                        }
                        heldItem.set(MMDataComponents.getPART_ASSEMBLY_INFO(), info);//更新物品保存的部件连接信息
                    }
                }
                break;
        }
    }


}
