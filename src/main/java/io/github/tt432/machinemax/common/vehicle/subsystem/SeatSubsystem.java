package io.github.tt432.machinemax.common.vehicle.subsystem;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.client.input.KeyBinding;
import io.github.tt432.machinemax.client.input.RawInputHandler;
import io.github.tt432.machinemax.common.vehicle.ISubsystemHost;
import io.github.tt432.machinemax.common.vehicle.Part;
import io.github.tt432.machinemax.common.vehicle.connector.AbstractConnector;
import io.github.tt432.machinemax.common.vehicle.signal.*;
import io.github.tt432.machinemax.common.vehicle.attr.subsystem.SeatSubsystemAttr;
import io.github.tt432.machinemax.mixin_interface.IEntityMixin;
import io.github.tt432.machinemax.util.data.KeyInputMapping;
import lombok.Getter;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class SeatSubsystem extends AbstractSubsystem implements ISignalReceiver, ISignalSender {
    public final SeatSubsystemAttr attr;
    public boolean disableVanillaActions;
    public AbstractConnector seatLocator;
    public LivingEntity passenger;
    public boolean occupied;

    public SeatSubsystem(ISubsystemHost owner, String name, SeatSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        this.disableVanillaActions = !this.attr.allowUseItems;
    }

    @Override
    public void onAttach() {
        SeatSubsystemAttr attr = (SeatSubsystemAttr) subSystemAttr;
        if (owner.getPart() != null) {
            seatLocator = owner.getPart().allConnectors.get(attr.connector);
            if (seatLocator == null)
                MachineMax.LOGGER.error("在部件{}中找不到名为{}的对接口作为座椅子系统的乘坐点", owner.getPart().name, attr.connector);
        } else MachineMax.LOGGER.error("无法为{}的座椅子系统找到部件", owner);
    }

    @Override
    public void onTick() {
        if (passenger != null && this.owner.getPart() instanceof Part part) {
            if (passenger.isRemoved() || passenger.isDeadOrDying() || passenger.getVehicle() != part.entity) {
                removePassenger();
                return;
            }
            passenger.resetFallDistance();//防止摔死
        } else {
            resetSignalOutputs();
        }
    }

    public boolean setPassenger(LivingEntity passenger) {
        if (passenger == null) removePassenger();
        if (!occupied && owner.getPart() != null && owner.getPart().entity != null && ((IEntityMixin) passenger).getRidingSubsystem() == null) {
            passenger.startRiding(owner.getPart().entity, true);
            occupied = true;
            this.passenger = passenger;
            ((IEntityMixin) passenger).setRidingSubsystem(this);
            getPart().vehicle.activate();
            //TODO:换成在hud角落常驻显示好了
            if (passenger.level() instanceof ClientLevel && passenger instanceof Player player)
                player.displayClientMessage(
                        Component.translatable("message.machine_max.leaving_vehicle",
                                KeyBinding.generalInteractKey.getTranslatedKeyMessage(),
                                String.format("%.2f", Math.clamp(0.05 * RawInputHandler.keyPressTicks.getOrDefault(KeyBinding.generalInteractKey, 0), 0.0, 0.5))
                        ), true
                );
            return true;
        } else return false;
    }

    public void removePassenger() {
        if (passenger != null) {
            if (((IEntityMixin) passenger).getRidingSubsystem() == this)
                ((IEntityMixin) passenger).setRidingSubsystem(null);
            passenger = null;
        }
        occupied = false;
        resetSignalOutputs();
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        Map<String, List<String>> result = new HashMap<>(1);
        result.putAll(attr.moveSignalTargets);
        result.putAll(attr.regularSignalTargets);
        result.putAll(attr.viewSignalTargets);
        return result;
    }

    public void setMoveInputSignal(byte[] inputs, byte[] conflicts) {
        if (!attr.moveSignalTargets.isEmpty()) {
            for (String signalKey : attr.moveSignalTargets.keySet()) {
                sendSignalToAllTargets(signalKey, new MoveInputSignal(inputs, conflicts));
            }
            for (int i = 0; i < 6; i++) {
                if (inputs[i] != 0 && getPart() != null && getPart().vehicle != null) {
                    getPart().vehicle.activate();
                    break;
                }
            }
        }
    }

    public void setRegularInputSignal(KeyInputMapping inputType, int tickCount) {
        if (!attr.regularSignalTargets.isEmpty()) {
            for (String signalKey : attr.regularSignalTargets.keySet()) {
                sendSignalToAllTargets(signalKey, new RegularInputSignal(inputType, tickCount));
            }
        }
    }

    public void setViewInputSignal() {
        if (!attr.viewSignalTargets.isEmpty()) {
            for (String signalKey : attr.viewSignalTargets.keySet()) {
                sendSignalToAllTargets(signalKey, new EmptySignal());
            }
        }
    }
}
