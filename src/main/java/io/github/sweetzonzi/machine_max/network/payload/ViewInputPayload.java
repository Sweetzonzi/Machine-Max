package io.github.sweetzonzi.machine_max.network.payload;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.control.aim.AimTargetState;
import io.github.sweetzonzi.machine_max.common.mech.signal.ViewInputSignal;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.*;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 客户端向服务端提交的完整瞄准目标，经操作者当前控制组投递。<br>
 * 鼠标增量已在客户端按采样顺序累计；此载荷中的相对角是绝对目标，接收方直接覆盖。
 * 保持模式由服务端设备定义确定，客户端仅声明来源及目标值。
 *
 * @param subPartId 操作者座椅所属的子部件 ID
 * @param subSystemName 操作者座椅的子系统名
 * @param viewPartId 实体视点所属的子部件 ID；-1 表示座椅虚拟视点
 * @param viewName 实体视点的子系统名；虚拟视点固定为 seat
 * @param controlGroup 发送时的活动控制组索引，用于拒绝切组后的过期输入
 * @param aimPoint 双精度世界目标点，单位为米
 * @param relativeAim 无稳轴的绝对相对角，弧度，x=俯仰、y=偏航
 */
public record ViewInputPayload(int subPartId, String subSystemName,
                               int viewPartId, String viewName, int controlGroup,
                               Vec3 aimPoint, Vec2 relativeAim) implements CustomPacketPayload {
    public static final Type<ViewInputPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "view_input_payload"));

    /** 字段顺序与 record 一致；世界点保留 double 精度，相对角以 float 弧度传输。 */
    public static final StreamCodec<FriendlyByteBuf, ViewInputPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull ViewInputPayload decode(FriendlyByteBuf buf) {
            int part = buf.readInt();
            String name = buf.readUtf();
            int viewPart = buf.readInt();
            String view = buf.readUtf();
            int group = buf.readInt();
            Vec3 point = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            Vec2 relative = new Vec2(buf.readFloat(), buf.readFloat());
            return new ViewInputPayload(part, name, viewPart, view, group, point, relative);
        }

        @Override
        public void encode(FriendlyByteBuf buf, @NotNull ViewInputPayload payload) {
            buf.writeInt(payload.subPartId);
            buf.writeUtf(payload.subSystemName);
            buf.writeInt(payload.viewPartId);
            buf.writeUtf(payload.viewName);
            buf.writeInt(payload.controlGroup);
            buf.writeDouble(payload.aimPoint.x);
            buf.writeDouble(payload.aimPoint.y);
            buf.writeDouble(payload.aimPoint.z);
            buf.writeFloat(payload.relativeAim.x);
            buf.writeFloat(payload.relativeAim.y);
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 非有限坐标与角度不能进入求角或关节伺服链。 */
    public boolean isFinite() {
        return Double.isFinite(aimPoint.x) && Double.isFinite(aimPoint.y)
                && Double.isFinite(aimPoint.z)
                && Float.isFinite(relativeAim.x) && Float.isFinite(relativeAim.y);
    }

    /** 服务端主线程校验当前控制关系，再向现有信号频道覆盖完整目标。 */
    public static void serverHandler(ViewInputPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!payload.isFinite()) {
                return;
            }
            if (!(ObjectManager.getDestroyableObject(context.player().level(), payload.subPartId) instanceof SubPart part)) {
                return;
            }
            // 来源座椅、当前乘客、实际控制对象与活动组必须同时匹配。
            if (!(part.subsystems.get(payload.subSystemName) instanceof SeatSubsystem seat)
                    || seat.passenger != context.player()
                    || ((IEntityMixin) context.player()).machine_Max$getControllingSubsystem() != seat
                    || payload.controlGroup != seat.getControlGroupSet().getActiveIndex()) {
                return;
            }

            AimTargetState.Mode mode = AimTargetState.Mode.WORLD;
            if (payload.viewPartId >= 0) {
                // 发现关系只用于确认设备来源；武器的接收范围由座椅控制组决定。
                AbstractViewpointSubsystem view = seat.getDiscoveredCameras().stream()
                        .filter(v -> v.getSubPart().getId() == payload.viewPartId
                                && v.getName().equals(payload.viewName)
                                && v.isActive() && !v.isDestroyed() && !v.getSubPart().isRemoved
                                && v.getSubPart().getPart().assembly == part.getPart().assembly)
                        .findFirst().orElse(null);
                if (view == null) {
                    return;
                }
                if (view instanceof SightSubsystem sight) {
                    mode = AimTargetState.Mode.of(sight.sightAttr.staticAttribute.horizontalStabilized,
                            sight.sightAttr.staticAttribute.verticalStabilized);
                }
            } else if (payload.viewPartId != -1 || !payload.viewName.equals("seat")) {
                return;
            }
            seat.setViewInputSignal(new ViewInputSignal(payload.aimPoint, payload.relativeAim, mode),
                    payload.viewPartId, payload.viewName);
        });
    }
}
