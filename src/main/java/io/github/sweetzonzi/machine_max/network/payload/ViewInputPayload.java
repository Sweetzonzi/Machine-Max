package io.github.sweetzonzi.machine_max.network.payload;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractControllableSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.CameraSubsystem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 视角输入数据包（客户端→服务端），携带玩家瞄准点的世界坐标。
 * 服务端通过 SubPart 的全局 ID 直接查找目标子系统，无需逐级解析载具和部件。
 */
public record ViewInputPayload(
        int subPartId,
        String subSystemName,
        double aimPointX,
        double aimPointY,
        double aimPointZ
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ViewInputPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "view_input_payload"));
    public static final StreamCodec<FriendlyByteBuf, ViewInputPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull ViewInputPayload decode(FriendlyByteBuf buf) {
            int subPartId = buf.readInt();
            String subSystemName = buf.readUtf();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            return new ViewInputPayload(subPartId, subSystemName, x, y, z);
        }

        @Override
        public void encode(FriendlyByteBuf buffer, @NotNull ViewInputPayload payload) {
            buffer.writeInt(payload.subPartId());
            buffer.writeUtf(payload.subSystemName());
            buffer.writeDouble(payload.aimPointX());
            buffer.writeDouble(payload.aimPointY());
            buffer.writeDouble(payload.aimPointZ());
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void serverHandler(final ViewInputPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            DestroyableObject object = ObjectManager.getDestroyableObject(context.player().level(), payload.subPartId());
            if (object instanceof SubPart subPart) {
                AbstractSubsystem subsystem = subPart.subsystems.get(payload.subSystemName());
                if (subsystem instanceof AbstractControllableSubsystem controllable && controllable.isActive()) {
                    Vec3 aimPoint = new Vec3(payload.aimPointX(), payload.aimPointY(), payload.aimPointZ());
                    controllable.setViewInputSignal(aimPoint);
                } else if (subsystem instanceof CameraSubsystem cam && cam.isActive()) {
                    Vec3 aimPoint = new Vec3(payload.aimPointX(), payload.aimPointY(), payload.aimPointZ());
                    cam.receiveClientAimInput(aimPoint);
                } else {
                    MachineMax.LOGGER.warn("收到视角输入数据包，但子系统 {} 不存在于 SubPart(id={})", payload.subSystemName(), payload.subPartId());
                }
            } else {
                MachineMax.LOGGER.warn("收到视角输入数据包，但维度 {} 中不存在 SubPart(id={})", context.player().level().dimension().location(), payload.subPartId());
            }
        });
    }
}
