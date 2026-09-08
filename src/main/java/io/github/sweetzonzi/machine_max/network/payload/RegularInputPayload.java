package io.github.sweetzonzi.machine_max.network.payload;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleAssemblyServerHelper;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SubsystemController;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractControllableSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.LightingSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import io.github.sweetzonzi.machine_max.util.data.KeyInputMapping;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;


@RequiredArgsConstructor
@Accessors(chain=true)
@Data
public class RegularInputPayload implements CustomPacketPayload {
    Integer executingPlayerId = null;
    final int key;
    final int tick_count;

    public static final Type<RegularInputPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "regular_input_payload"));
    public static final StreamCodec<FriendlyByteBuf, RegularInputPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull RegularInputPayload decode(FriendlyByteBuf buffer) {
            return new RegularInputPayload(buffer.readInt(), buffer.readInt())
                    .setExecutingPlayerId(buffer.readBoolean() ? null : buffer.readInt());
        }

        @Override
        public void encode(FriendlyByteBuf buffer, @NotNull RegularInputPayload value) {
            buffer.writeInt(value.getKey());
            buffer.writeInt(value.getTick_count());
            boolean playerIdIsNull = value.getExecutingPlayerId() == null;
            buffer.writeBoolean(playerIdIsNull);
            if (!playerIdIsNull) buffer.writeInt(value.getExecutingPlayerId());
        }
    };

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
        PacketDistributor.sendToPlayersInDimension((ServerLevel) player.level(), payload.setExecutingPlayerId(player.getId()));
    }

    public static void handle(final RegularInputPayload payload, final IPayloadContext context) {
        //todo 重复的代码 后期建议在需要这种按角色分界的情形直接继承特定FL功能即可（或者其他更好的封装设计）
        Level level = context.player().level();
        String envStr = level.isClientSide() ? "客户端" : "服务端";
        final Player executingPlayer;
        if (payload.getExecutingPlayerId() == null) {
            //为空，说明是服务端在调用接收
            executingPlayer = context.player();
        } else if (level.getEntity(payload.getExecutingPlayerId()) instanceof Player ep) {
            executingPlayer = ep;
        } else {
            executingPlayer = null;
        }
        if (executingPlayer == null) {
            MachineMax.LOGGER.warn("实体id 为 {} 的触发者在该{}未被发现, handle发送终止", payload.getExecutingPlayerId(), envStr);
            return;
        }


        switch (KeyInputMapping.fromValue(payload.getKey())) {
            /*
             *  通用功能
             */
            case FREE_CAM://自由相机模式

                break;
            case INTERACT:

                break;
            case LEAVE_VEHICLE://与载具等交互
                if ((executingPlayer.getVehicle() != null || ((IEntityMixin) executingPlayer).machine_Max$getControllingSubsystem() != null) && payload.getTick_count() >= 10) {
                    //处于骑乘状态，且长按互动键1秒，则尝试脱离载具
                    if (((IEntityMixin) executingPlayer).machine_Max$getControllingSubsystem() instanceof SeatSubsystem seatSubSystem) {
                        seatSubSystem.removePassenger();
                    } else executingPlayer.stopRiding();//一般载具实体的处理方式
                }
                break;
            case TOGGLE_LIGHT://灯光开关
                if (!level.isClientSide()) {
                    handleToggleLight(executingPlayer, payload.getTick_count() == 1);
                }
                break;
            /*
             * 地面载具
             */
            case CLUTCH, UP_SHIFT, DOWN_SHIFT, HAND_BRAKE, TOGGLE_HAND_BRAKE:
                handleRegularInputForSeatSubsystem(executingPlayer, KeyInputMapping.fromValue(payload.getKey()), payload.getTick_count());
                break;
            /*
             *  武器控制 — 路由到控制组的 mainWeaponTargets / secondaryWeaponTargets
             */
            case MAIN_FIRE, NEXT_AMMO_TYPE, PREV_AMMO_TYPE:
                handleWeaponInputForSeatSubsystem(executingPlayer, KeyInputMapping.fromValue(payload.getKey()), payload.getTick_count(), true);
                break;
            case SECONDARY_FIRE:
                handleWeaponInputForSeatSubsystem(executingPlayer, KeyInputMapping.fromValue(payload.getKey()), payload.getTick_count(), false);
                break;
            /*
             *  载具组装
             */
            case CYCLE_PART_RECIPES://切换部件配方
                if (!level.isClientSide()) {//仅在服务器端处理（改的是共享世界状态并广播）
                    VehicleAssemblyServerHelper.cycleRecipe(executingPlayer);
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

    /** 将武器控制按键路由到控制组的主武器或副武器目标频道 */
    private static void handleWeaponInputForSeatSubsystem(Player player, KeyInputMapping key, int tickCount, boolean isMain) {
        AbstractControllableSubsystem subsystem = ((IEntityMixin) player).machine_Max$getControllingSubsystem();
        if (subsystem != null) {
            if (isMain) {
                subsystem.setMainWeaponInputSignal(key, tickCount);
            } else {
                subsystem.setSecondaryWeaponInputSignal(key, tickCount);
            }
        }
    }

    private static void handleToggleLight(Player player, boolean on) {
        if (player.getVehicle() instanceof MMPartEntity) {
            AbstractControllableSubsystem subsystem = ((IEntityMixin) player).machine_Max$getControllingSubsystem();
            if (subsystem != null) {
                ISubsystemHost host = subsystem.getOwner();
                SubsystemController controller = host.getSubsystemController();
                for (AbstractSubsystem sub : controller.getAllSubsystems()) {
                    if (sub instanceof LightingSubsystem light) {
                        light.setActive(on);
                    }
                }
            }
        }
    }

}
