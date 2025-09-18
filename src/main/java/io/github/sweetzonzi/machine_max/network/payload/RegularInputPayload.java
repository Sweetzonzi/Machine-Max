package io.github.sweetzonzi.machine_max.network.payload;

import cn.solarmoon.spark_core.animation.model.origin.OLocator;
import cn.solarmoon.spark_core.animation.model.origin.OModel;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.attachment.LivingEntityEyesightAttachment;
import io.github.sweetzonzi.machine_max.common.component.PartAssemblyCacheComponent;
import io.github.sweetzonzi.machine_max.common.component.PartAssemblyInfoComponent;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.item.prop.PartItem;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import io.github.sweetzonzi.machine_max.common.registry.MMItems;
import io.github.sweetzonzi.machine_max.common.vehicle.PartType;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.ConnectorAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AttachPointConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractControllableSubsystem;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import io.github.sweetzonzi.machine_max.util.data.KeyInputMapping;
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
import org.joml.Quaternionf;
import org.joml.Vector3f;

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
            /*
             *  通用功能
             */
            case FREE_CAM://自由相机模式

                break;
            case INTERACT:

                break;
            case LEAVE_VEHICLE://与载具等交互
                if ((player.getVehicle() != null || ((IEntityMixin) player).machine_Max$getControllingSubsystem() != null) && payload.tick_count() >= 10) {
                    //处于骑乘状态，且长按互动键1秒，则尝试脱离载具
                    if (((IEntityMixin) player).machine_Max$getControllingSubsystem() instanceof SeatSubsystem seatSubSystem) {
                        seatSubSystem.removePassenger();
                        player.stopRiding();//保险措施，确保停止骑乘
                    } else player.stopRiding();//一般载具实体的处理方式
                }
                break;
            /*
             * 地面载具
             */
            case CLUTCH, UP_SHIFT, DOWN_SHIFT, HAND_BRAKE, TOGGLE_HAND_BRAKE:
                handleRegularInputForSeatSubsystem(player, KeyInputMapping.fromValue(payload.key()), payload.tick_count());
                break;
            /*
             *  载具组装
             */
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
                        PartType partType = PartItem.getPartType(heldItem, level);
                        PartAssemblyInfoComponent info = PartItem.getPartAssemblyInfo(heldItem, level);
                        PartAssemblyCacheComponent iterators = PartItem.getPartAssemblyCache(heldItem, level);
                        Map<String, ConnectorAttr> partConnectors = partType.getPartOutwardConnectors();
                        int i = partConnectors.size();//设置最大迭代次数
                        var connectors = partType.getPartOutwardConnectors();
                        String variant = info.variant();
                        while (i > 0) {
                            //循环获取下一个端口，直到找到合适的接口或到达迭代次数上限
                            String connectorName = iterators.getNextConnector();//获取下一个部件接口
                            if (partConnectors.get(connectorName).type().equals("AttachPoint") || targetConnector instanceof AttachPointConnector) {
                                // TODO: 检查部件Tag是否与目标接口接受的类型匹配
                                OModel model = OModel.getOrEmpty(partType.variants.get(variant));
                                var locators = model.getLocators();
                                var connectorAttr = connectors.get(connectorName);
                                OLocator partConnectorLocator = locators.get(connectorAttr.locatorName());
                                Vector3f offset = partConnectorLocator.getOffset().toVector3f();
                                Vector3f rotation = partConnectorLocator.getRotation().toVector3f();
                                Quaternionf quaternion = new Quaternionf().rotationZYX(rotation.x, rotation.y, rotation.z);
                                info = new PartAssemblyInfoComponent(variant, connectorName, connectorAttr.type(), offset, quaternion);
                                break;
                            }
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
                    PartType partType = PartItem.getPartType(heldItem, level);
                    PartAssemblyInfoComponent info = PartItem.getPartAssemblyInfo(heldItem, level);
                    PartAssemblyCacheComponent iterators = PartItem.getPartAssemblyCache(heldItem, level);
                    var connectors = partType.getPartOutwardConnectors();
                    int i = partType.variants.size();//设置最大迭代次数
                    while (i >= 0) {
                        //循环获取下一个部件变体，直到找到合适的部件变体或到达迭代次数上限
                        String variant = iterators.getNextVariant();//获取下一个部件变体
                        if (targetConnector == null || targetConnector.acceptableVariants.contains(variant)) {
                            OModel model = OModel.getOrEmpty(partType.variants.get(variant));
                            var locators = model.getLocators();
                            OLocator partConnectorLocator = locators.get(connectors.get(info.connector()).locatorName());
                            Vector3f offset = partConnectorLocator.getOffset().toVector3f();
                            Vector3f rotation = partConnectorLocator.getRotation().toVector3f();
                            Quaternionf quaternion = new Quaternionf().rotationZYX(rotation.x, rotation.y, rotation.z);
                            info = new PartAssemblyInfoComponent(variant, info.connector(), info.connectorType(), offset, quaternion);
                            break;
                        }
                        i--;
                    }
                    heldItem.set(MMDataComponents.getPART_ASSEMBLY_INFO(), info);//更新物品保存的部件连接信息
                }
                break;
        }
    }

    private static void handleRegularInputForSeatSubsystem(Player player, KeyInputMapping key, int tickCount) {
        if (player.getVehicle() instanceof MMPartEntity) {
            AbstractControllableSubsystem subsystem = ((IEntityMixin)player).machine_Max$getControllingSubsystem();
            if (subsystem != null) {
                subsystem.setRegularInputSignal(key, tickCount);
            }
        }
    }

}
