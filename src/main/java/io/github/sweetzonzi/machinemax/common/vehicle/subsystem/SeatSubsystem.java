package io.github.sweetzonzi.machinemax.common.vehicle.subsystem;

import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.client.input.KeyBinding;
import io.github.sweetzonzi.machinemax.client.input.RawInputHandler;
import io.github.sweetzonzi.machinemax.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machinemax.common.vehicle.Part;
import io.github.sweetzonzi.machinemax.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machinemax.common.vehicle.signal.*;
import io.github.sweetzonzi.machinemax.common.vehicle.attr.subsystem.SeatSubsystemAttr;
import io.github.sweetzonzi.machinemax.mixin_interface.IEntityMixin;
import io.github.sweetzonzi.machinemax.util.data.KeyInputMapping;
import lombok.Getter;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class SeatSubsystem extends AbstractSubsystem {
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
    public void onDetach() {
        super.onDetach();
        removePassenger();
    }

    @Override
    public void onTick() {
        super.onTick();
        if (passenger != null && this.owner.getPart() instanceof Part part) {
            if (passenger.isRemoved() || passenger.isDeadOrDying()) {
                removePassenger();
                return;
            }
            if (part.entity != null && passenger.getVehicle() != part.entity) {
                if (!passenger.level().isClientSide)
                    passenger.startRiding(part.entity, true);
                passenger.resetFallDistance();//防止摔死
            } else if (part.entity == null) {
                removePassenger();
            } else {
                passenger.resetFallDistance();//防止摔死
            }
        } else {
            resetSignalOutputs();
        }
    }

    @Override
    public void onInteract(LivingEntity entity) {
        super.onInteract(entity);
        setPassenger(entity);
    }

    @Override
    public void onSignalUpdated(String channelName, ISignalSender sender) {
        super.onSignalUpdated(channelName, sender);
        if (!occupied) {//如果此座椅已有乘客，则忽略信号
            Object signal = getSignalValueFrom(channelName, sender);
            if (signal instanceof InteractSignal interactSignal) {
                LivingEntity entity = interactSignal.getEntity();
                onInteract(entity);
            }
        }
    }

    public void setPassenger(LivingEntity passenger) {
        if (owner.getPart() != null && owner.getPart().entity != null && ((IEntityMixin) passenger).machine_Max$getRidingSubsystem() == null) {
            if (!getPart().level.isClientSide)
                passenger.startRiding(owner.getPart().entity, true);
            occupied = true;
            this.passenger = passenger;
            ((IEntityMixin) passenger).machine_Max$setRidingSubsystem(this);
            getPart().vehicle.activate();
            //TODO:换成在hud角落常驻显示好了
            if (passenger.level().isClientSide && passenger instanceof Player player)
                player.displayClientMessage(
                        Component.translatable("message.machine_max.leaving_vehicle",
                                KeyBinding.generalLeaveVehicleKey.getTranslatedKeyMessage(),
                                String.format("%.2f", Math.clamp(0.05 * RawInputHandler.keyPressTicks.getOrDefault(KeyBinding.generalInteractKey, 0), 0.0, 0.5))
                        ), true
                );
        }
    }

    public void removePassenger() {
        if (passenger != null) {
            if (((IEntityMixin) passenger).machine_Max$getRidingSubsystem() == this)
                ((IEntityMixin) passenger).machine_Max$setRidingSubsystem(null);
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
        //TODO:将0~100的byte缩放到0~1的float
        if (!attr.moveSignalTargets.isEmpty() && active) {
            for (String signalKey : attr.moveSignalTargets.keySet()) {
                sendSignalToAllTargets(signalKey, new MoveInputSignal(inputs, conflicts));
            }
            for (int i = 0; i < 6; i++) {
                if (inputs[i] != 0 && getPart() != null && getPart().vehicle != null) {
                    break;
                }
            }
            getPart().vehicle.activate();
        }
    }

    public void setRegularInputSignal(KeyInputMapping inputType, int tickCount) {
        if (!attr.regularSignalTargets.isEmpty() && active) {
            for (String signalKey : attr.regularSignalTargets.keySet()) {
                sendSignalToAllTargets(signalKey, new RegularInputSignal(inputType, tickCount));
            }
            getPart().vehicle.activate();
        }
    }

    public void setViewInputSignal() {
        if (!attr.viewSignalTargets.isEmpty() && active) {
            for (String signalKey : attr.viewSignalTargets.keySet()) {
                sendSignalToAllTargets(signalKey, new EmptySignal());
            }
            getPart().vehicle.activate();
        }
    }
}
