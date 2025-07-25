package io.github.sweetzonzi.machinemax.common.vehicle.subsystem;

import com.jme3.math.Transform;
import io.github.sweetzonzi.machinemax.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machinemax.common.vehicle.Part;
import io.github.sweetzonzi.machinemax.common.vehicle.SignalTargetsHolder;
import io.github.sweetzonzi.machinemax.common.vehicle.attr.subsystem.SeatSubsystemAttr;
import io.github.sweetzonzi.machinemax.common.vehicle.signal.ISignalSender;
import io.github.sweetzonzi.machinemax.common.vehicle.signal.InteractSignal;
import io.github.sweetzonzi.machinemax.mixin_interface.IEntityMixin;
import lombok.Getter;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class SeatSubsystem extends AbstractSubsystem implements IControllableSubsystem  {
    public final SeatSubsystemAttr attr;
    public boolean disableVanillaActions;
    public LivingEntity passenger;
    public boolean occupied;
    private final SignalTargetsHolder signalTargetsHolder = new SignalTargetsHolder(this);

    public SeatSubsystem(ISubsystemHost owner, String name, SeatSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        signalTargetsHolder.setUp(attr.moveSignalTargets, attr.viewSignalTargets, attr.regularSignalTargets);
        this.disableVanillaActions = !this.attr.allowUseItems;
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

            //TODO:考虑删除，下面的状态展示已经挪到RawInput的全新键位事件系统中
//            if (passenger.level().isClientSide && passenger instanceof Player player)
//                player.displayClientMessage(
//                        Component.translatable("message.machine_max.leaving_vehicle",
//                                KeyBinding.generalLeaveVehicleKey.getTranslatedKeyMessage(),
//                                String.format("%.2f", Math.clamp(0.05 * RawInputHandler.keyPressTicks.getOrDefault(KeyBinding.generalInteractKey, 0), 0.0, 0.5))
//                        ), true
//                );
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
    public SignalTargetsHolder getHolder() {
        return signalTargetsHolder;
    }
    @Override
    public Map<String, List<String>> getTargetNames() {
        return signalTargetsHolder.setUpTargets(new HashMap<>(1));
    }

    public Transform getSeatPointWorldTransform() {
        String locatorName = attr.locator;
        return getPart().getLocatorWorldTransform(locatorName);
    }

    public Transform getSeatPointLocalTransform() {
        String locatorName = attr.locator;
        return getPart().getLocatorLocalTransform(locatorName);
    }
}
