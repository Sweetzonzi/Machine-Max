package io.github.sweetzonzi.machine_max.client.render.gui.hud.molang;

import io.github.sweetzonzi.machine_max.client.MMClientConfig;
import io.github.sweetzonzi.machine_max.client.input.CameraController;
import io.github.sweetzonzi.machine_max.client.input.KeyBinding;
import io.github.sweetzonzi.machine_max.client.render.gui.seat.SeatMapLayout;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.seat.SeatOrder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.seat.SeatSwitchFeedback;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 同一台载具的 HUD 条目共享一份客户端 tick 读数。 */
public record HudVehicleSnapshot(@Nullable UUID assemblyId, List<Seat> seats, int current, int next,
                                 int pending, boolean successPulse, String switchKey, boolean mapVisible) {
    public static final HudVehicleSnapshot EMPTY = new HudVehicleSnapshot(
            null, List.of(), 0, 0, 0, false, "", false);

    public HudVehicleSnapshot {
        seats = List.copyOf(seats);
    }

    public record Seat(int subPartId, String key, String name, boolean hasPosition,
                       float right, float up, float forward,
                       boolean occupied, boolean available, boolean failed) {}

    private static final Seat MISSING_SEAT = new Seat(-1, "", "", false, 0, 0, 0, false, false, false);

    public Seat seat(double number) {
        if (number < 1 || number > seats.size() || number != Math.rint(number)) return MISSING_SEAT;
        return seats.get((int) number - 1);
    }

    public static HudVehicleSnapshot capture(VehicleCore vehicle, @Nullable SeatSubsystem observer) {
        SeatMapLayout.Snapshot layout = SeatMapLayout.snapshot(vehicle);
        int current = layout.numberOf(observer);
        SeatSubsystem next = current == 0 ? null : SeatOrder.next(layout.seats(), observer, true);
        int pending = current == 0 ? 0 : layout.numberOf(SeatSwitchFeedback.pendingTarget());
        boolean successPulse = current != 0 && SeatSwitchFeedback.pulseTicks() > 0;
        Minecraft minecraft = Minecraft.getInstance();
        boolean visible = current != 0 && layout.seats().size() > 1 && minecraft.screen == null
                && !minecraft.options.hideGui && !CameraController.isCameraMode() && MMClientConfig.isSeatMapVisible();
        var bounds = layout.bounds();
        var reference = layout.reference();
        Matrix4f worldToReference = reference == null ? null : reference.getWorldPositionMatrix(1f).invert();
        List<Seat> seats = new ArrayList<>(layout.seats().size());
        for (SeatSubsystem seat : layout.seats()) {
            int id = seat.getSubPart().getId();
            String key = seat.getName();
            Vector3f point = worldToReference == null ? null : SeatMapLayout.seatWorldPosition(seat, 1f);
            if (point != null) {
                worldToReference.transformPosition(point);
                if (bounds != null) point.sub(bounds.centerX(), bounds.centerY(), bounds.centerZ());
                if (!point.isFinite()) point = null;
            }
            seats.add(new Seat(id, key, Component.translatable(key).getString(), point != null,
                    point == null ? 0 : point.x, point == null ? 0 : point.y, point == null ? 0 : -point.z,
                    seat.isOccupied(), SeatOrder.available(seat),
                    current != 0 && SeatSwitchFeedback.failureAt(vehicle, id, key)));
        }
        return new HudVehicleSnapshot(vehicle.getAssemblyId(), seats, current, layout.numberOf(next), pending,
                successPulse, KeyBinding.generalCycleSeatKey.getTranslatedKeyMessage().getString(), visible);
    }
}
