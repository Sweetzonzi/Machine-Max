package io.github.sweetzonzi.machine_max.network.payload;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.attachment.VehicleAssemblyAttachment;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
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
import net.minecraft.world.level.Level;
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
        //将玩家输入转发给其他玩家，以在其他玩家客户端模拟自己的操作 TODO: 这可行吗？
        Player player = context.player();
        PacketDistributor.sendToPlayersInDimension((ServerLevel) player.level(), payload);
    }

    public static void handle(final RegularInputPayload payload, final IPayloadContext context) {
        Player player = context.player();
        Level level = player.level();
        VehicleAssemblyAttachment assemblyCache;
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
            case CYCLE_PART_ATTACH_ANGLE://切换部件安装角度
                if (!level.isClientSide()) {//仅在服务器端处理
                    assemblyCache = player.getData(MMAttachments.getVEHICLE_ASSEMBLY());
                    assemblyCache.cycleAttachAngle();
                }
                break;
            case CYCLE_PART_CONNECTORS://切换部件连接点
                if (!level.isClientSide()) {//仅在服务器端处理
                    assemblyCache = player.getData(MMAttachments.getVEHICLE_ASSEMBLY());
                    assemblyCache.cycleConnectors();
                }
                break;
            case CYCLE_PART_VARIANTS://切换部件变体
                if (!level.isClientSide()) {//仅在服务器端处理
                    assemblyCache = player.getData(MMAttachments.getVEHICLE_ASSEMBLY());
                    assemblyCache.cycleVariants();
                }
                break;
        }
    }

    private static void handleRegularInputForSeatSubsystem(Player player, KeyInputMapping key, int tickCount) {
        if (player.getVehicle() instanceof MMPartEntity) {
            AbstractControllableSubsystem subsystem = ((IEntityMixin) player).machine_Max$getControllingSubsystem();
            if (subsystem != null) {
                subsystem.setRegularInputSignal(key, tickCount);
            }
        }
    }

}
