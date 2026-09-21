package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.CollisionManager;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalResult;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.control.ControlGroupSet;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.AbstractSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.SeatSubsystemAttr;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import io.github.sweetzonzi.machine_max.util.MMMath;
import io.github.sweetzonzi.machine_max.util.mechanic.MassUtil;
import lombok.Getter;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class SeatSubsystem extends AbstractControllableSubsystem {
    public final SeatSubsystemAttr attr;
    public boolean disableVanillaActions;
    public LivingEntity passenger;
    public boolean occupied;
    /**
     * 该座位的上车点，表达为所在 SubPart 刚体的局部坐标；null 表示本座位尚无车外登入记录。
     * <p>只在「从车外登入」时采样覆盖，或在「同一装配体内换座」时由旧座位经世界坐标换算交接过来；
     * 下车、死亡与子系统移除都不清空，随座位实例存续。</p>
     */
    public Vector3f boardingLocalOffset;
    public SeatSubsystem(ISubsystemHost owner, String name, SeatSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        // 从动态属性加载摄像机发现目标
        this.cameraDiscoveryTargets = attr.getCameraDiscoveryTargets();
        //从动态属性加载控制组预设
        if (!attr.controlGroupPreset.equals(AbstractSubsystemAttr.NO_CONTROL_GROUP_PRESET)) {
            ControlGroupSet preset = MMDynamicRes.CONTROL_GROUP_PRESETS.get(attr.controlGroupPreset);
            if (preset != null) {
                setControlGroupSet(preset);
            } else {
                MachineMax.LOGGER.warn("控制组预设 {} 未找到，使用空控制组", attr.controlGroupPreset);
            }
        }
        this.disableVanillaActions = !this.attr.staticAttribute.allowUseItems;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        removePassenger();
    }

    @Override
    public void onTick() {
        super.onTick();
        if (passenger != null && this.getOwner().getSubPart() instanceof SubPart part) {
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
        } else resetSignalOutputs();
    }

    @Override
    public SignalResult onInteract(LivingEntity entity) {
        SignalResult result = super.onInteract(entity);
        if (!occupied && isActive()) {
            setPassenger(entity);
            return SignalResult.CONSUME;
        }
        return occupied ? SignalResult.FAIL : result;
    }

    public void setPassenger(LivingEntity passenger) {
        if (owner.getSubPart() != null && owner.getSubPart().entity != null) {
            updateBoardingOffset(passenger);
            if (((IEntityMixin) passenger).machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat) {
                if (seat == this) return;
                else seat.removePassenger();
                if (seat.getSubPart().getEntity() == owner.getSubPart().getEntity() && getOwner().getLevel().isClientSide)
                    passenger.startRiding(owner.getSubPart().entity, true);
            }
            if (!getOwner().getLevel().isClientSide) {
                passenger.startRiding(owner.getSubPart().entity, true);
            }
            occupied = true;
            for (String channel : attr.passengerNumSignalTargets.keySet()) {
                sendSignalToAllTargets(channel, 1f);
            }
            this.passenger = passenger;
            ((IEntityMixin) passenger).machine_Max$setControllingSubsystem(this);
            // 上报乘客体重作为额外质量
            setExtraMass((float) MassUtil.getEntityMass(passenger));
            getOwner().getSubPart().getPart().assembly.activatePhysics();
            if (getOwner().getSubPart().getPart().assembly instanceof VehicleCore vc) {
                vc.recalculateCameraDistance();
            }
        }
    }

    /**
     * 更新本座位的上车点缓存。
     * <p>只有两种情况会写入：乘客此前不在任何载具上（从车外登入）时采样其当前位置；
     * 乘客此前在<strong>同一装配体</strong>的另一个座位（车内换座）时，把旧座位的缓存
     * 经世界坐标换算交接过来 —— 局部坐标锚在不同刚体上，直接复制没有几何意义。
     * 跨载具换乘与其他宿主未就绪的情形都不写入，缓存保持原值。</p>
     *
     * <p>采样条件必须限定为「车外登入」：若任何登入都采样，车内换座会把乘客的坐姿位置
     * 写成新车座的上车点。</p>
     *
     * @param passenger 本次登入的乘客
     */
    private void updateBoardingOffset(LivingEntity passenger) {
        SubPart host = owner.getSubPart();
        if (host == null || host.body == null) return;
        if (passenger.getVehicle() == null) {
            // 车外登入：乘客当前位置就是上车点，直接覆盖旧值
            this.boardingLocalOffset = MMMath.worldPointLocalPos(
                    PhysicsHelperKt.toBVector3f(passenger.position()), host.body);
            return;
        }
        // 已在载具上：只有同一装配体内的换座需要交接缓存
        if (((IEntityMixin) passenger).machine_Max$getControllingSubsystem() instanceof SeatSubsystem oldSeat
                && oldSeat != this
                && oldSeat.getBoardingLocalOffset() != null
                && oldSeat.getSubPart() != null
                && oldSeat.getSubPart().body != null
                && oldSeat.getSubPart().getPart().assembly == host.getPart().assembly) {
            Vector3f worldPoint = MMMath.relPointWorldPos(oldSeat.getBoardingLocalOffset(), oldSeat.getSubPart().body);
            this.boardingLocalOffset = MMMath.worldPointLocalPos(worldPoint, host.body);
        }
    }

    public void removePassenger() {
        if (passenger != null) {
            if (((IEntityMixin) passenger).machine_Max$getControllingSubsystem() == this) {
                ((IEntityMixin) passenger).machine_Max$setControllingSubsystem(null);
                passenger.stopRiding();
                // 玩家运动受客户端控制，需要在客户端修改deltamovement
                if ((passenger.level().isClientSide() && passenger instanceof Player) || (!(passenger instanceof Player) && !passenger.level().isClientSide()))
                    CollisionManager.addImpulse(passenger, SparkMathKt.toVec3(getSubPart().getLinearVelocity().add(0,1,0).mult(0.05f)));
            }
            passenger = null;
        }
        occupied = false;
        // 乘客离去，清除额外质量
        setExtraMass(0f);
        for (String channel : attr.passengerNumSignalTargets.keySet()) {
            sendSignalToAllTargets(channel, 0f);
        }
        resetSignalOutputs();
        clearInputSignals();
    }

    @Override
    public List<String> getAcceptedChannels() {
        return attr.staticAttribute.getAcceptedChannels();
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        Map<String, List<String>> result = setUpTargets(new HashMap<>(1));
        result.putAll(attr.passengerNumSignalTargets);
        return result;
    }

    public Transform getSeatPointWorldTransform() {
        String locatorName = attr.locator;
        return getOwner().getSubPart().getLocatorWorldTransform(locatorName);
    }

    public Transform getSeatPointLocalTransform() {
        String locatorName = attr.locator;
        return getOwner().getSubPart().getLocatorLocalTransform(locatorName);
    }
}
