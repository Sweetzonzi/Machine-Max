package io.github.sweetzonzi.machine_max.network.payload;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractControllableSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 视角输入数据包（客户端→服务端），携带玩家瞄准点的世界坐标 + 无稳轴的鼠标增量偏移 + 稳定标志。<p>
 * 服务端通过 SubPart 的全局 ID 直接定位到 AbstractControllableSubsystem，统一经控制组 viewTargets 发送。
 */
public record ViewInputPayload(
        int subPartId,
        String subSystemName,
        double aimPointX,
        double aimPointY,
        double aimPointZ,
        float localPitchOffsetDeg,
        float localYawOffsetDeg,
        boolean pitchStabilized,
        boolean yawStabilized
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
            float pitchOff = buf.readFloat();
            float yawOff = buf.readFloat();
            boolean pStab = buf.readBoolean();
            boolean yStab = buf.readBoolean();
            return new ViewInputPayload(subPartId, subSystemName, x, y, z, pitchOff, yawOff, pStab, yStab);
        }

        @Override
        public void encode(FriendlyByteBuf buffer, @NotNull ViewInputPayload payload) {
            buffer.writeInt(payload.subPartId());
            buffer.writeUtf(payload.subSystemName());
            buffer.writeDouble(payload.aimPointX());
            buffer.writeDouble(payload.aimPointY());
            buffer.writeDouble(payload.aimPointZ());
            buffer.writeFloat(payload.localPitchOffsetDeg());
            buffer.writeFloat(payload.localYawOffsetDeg());
            buffer.writeBoolean(payload.pitchStabilized());
            buffer.writeBoolean(payload.yawStabilized());
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
                    controllable.setViewInputSignal(aimPoint,
                            payload.localPitchOffsetDeg(), payload.localYawOffsetDeg(),
                            payload.pitchStabilized(), payload.yawStabilized());
                } else {
                    MachineMax.LOGGER.warn("收到视角输入数据包，但子系统 {} 不存在于 SubPart(id={}) 或不可控",
                            payload.subSystemName(), payload.subPartId());
                }
            } else {
                MachineMax.LOGGER.warn("收到视角输入数据包，但维度 {} 中不存在 SubPart(id={})",
                        context.player().level().dimension().location(), payload.subPartId());
            }
        });
    }
}
