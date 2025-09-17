package io.github.sweetzonzi.machine_max.common.vehicle.subsystem;

import com.jme3.math.Transform;
import io.github.sweetzonzi.machine_max.client.input.KeyBinding;
import io.github.sweetzonzi.machine_max.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.vehicle.SignalTargetsHolder;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.SeatSubsystemAttr;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import lombok.Getter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class SeatSubsystem extends AbstractSubsystem implements IControllableSubsystem {
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
        if (!occupied) {//如果此座椅已有乘客，则忽略信号
            setPassenger(entity);
        }
    }

    public void setPassenger(LivingEntity passenger) {
        if (owner.getPart() != null && owner.getPart().entity != null && ((IEntityMixin) passenger).machine_Max$getControllingSubsystem() != this) {
            if (!getPart().level.isClientSide) {
                if (((IEntityMixin) passenger).machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat) {
                    seat.removePassenger();
                }
                passenger.startRiding(owner.getPart().entity, true);
            }
            occupied = true;
            this.passenger = passenger;
            ((IEntityMixin) passenger).machine_Max$setControllingSubsystem(this);
            getPart().vehicle.activate();
            //TODO:换成在hud角落常驻显示好了
            if (passenger.level().isClientSide && passenger instanceof Player player)
                player.displayClientMessage(
                        Component.translatable("message.machine_max.leaving_vehicle",
                                KeyBinding.generalLeaveVehicleKey.getTranslatedKeyMessage(),
                                0.0
                        ), true
                );
        }
    }

    public void removePassenger() {
        if (passenger != null) {
            if (((IEntityMixin) passenger).machine_Max$getControllingSubsystem() == this)
                ((IEntityMixin) passenger).machine_Max$setControllingSubsystem(null);
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
    public AbstractSubsystem getControllableSubsystem() {
        return this;
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
