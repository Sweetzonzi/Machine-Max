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
import io.github.sweetzonzi.machine_max.common.visual.HudLayout;
import io.github.sweetzonzi.machine_max.common.visual.HudView;
import io.github.sweetzonzi.machine_max.common.visual.IHudHost;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import io.github.sweetzonzi.machine_max.util.MMMath;
import io.github.sweetzonzi.machine_max.util.mechanic.MassUtil;
import lombok.Getter;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class SeatSubsystem extends AbstractControllableSubsystem implements IHudHost {
    public final SeatSubsystemAttr attr;
    public boolean disableVanillaActions;
    public LivingEntity passenger;
    /**
     * 座位是否已被占用。服务端是权威端，客户端靠同步值跟随；座位图与换座目标筛选都读它，
     * 不读 {@link #passenger}（后者只有服务端有值）。
     */
    public boolean occupied;

    /** 占用状态的同步值：变化极少，每 tick 的脏数据推送即可覆盖 */
    private static final EntityDataAccessor<Boolean> DATA_OCCUPIED_ID =
            SynchedEntityData.defineId(SeatSubsystem.class, EntityDataSerializers.BOOLEAN);
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
        this.disableVanillaActions = !this.attr.isAllowUseItems();
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

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_OCCUPIED_ID, false);
    }

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (accessor == DATA_OCCUPIED_ID) this.occupied = synchedData.get(DATA_OCCUPIED_ID);
    }

    /** 占用状态的唯一写入口：服务端同时更新同步值，客户端只跟随本地字段 */
    private void setOccupied(boolean value) {
        this.occupied = value;
        if (!getLevel().isClientSide()) synchedData.set(DATA_OCCUPIED_ID, value);
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
            setOccupied(true);
            for (String channel : attr.passengerNumSignalTargets.keySet()) {
                sendSignalToAllTargets(channel, 1f);
            }
            clearInputSignals();
            this.passenger = passenger;
            ((IEntityMixin) passenger).machine_Max$setControllingSubsystem(this);
            // 上报乘客体重作为额外质量
            setExtraMass((float) MassUtil.getEntityMass(passenger));
            getOwner().getSubPart().getPart().assembly.activatePhysics();
            if (getOwner().getSubPart().getPart().assembly instanceof VehicleCore vc) {
                vc.recalculateCameraDistance();
            }
            // 落座后把活动控制组复位到该座位预设的默认值，避免继承上一个乘客留下的子组
            resetControlGroupToPreset();
        }
    }

    /**
     * 车内换座的旧座位侧：与 {@link #removePassenger} 清同样的字段，但不 {@code stopRiding}、
     * 不加冲量——骑乘关系由换座路径自己处理（同子零件时根本不变）。
     */
    public void detachForSwitch(LivingEntity passenger) {
        if (((IEntityMixin) passenger).machine_Max$getControllingSubsystem() == this) {
            ((IEntityMixin) passenger).machine_Max$setControllingSubsystem(null);
        }
        clearSeatState();
    }

    /**
     * 车内换座的新座位侧：与 {@link #setPassenger} 写同样的字段，但不重复移除旧座位、
     * 也不调用 {@code startRiding}——同子零件路径不需要挂载，跨子零件路径由调用方先挂载成功。
     */
    public void attachForSwitch(LivingEntity passenger) {
        setOccupied(true);
        for (String channel : attr.passengerNumSignalTargets.keySet()) {
            sendSignalToAllTargets(channel, 1f);
        }
        clearInputSignals();
        this.passenger = passenger;
        ((IEntityMixin) passenger).machine_Max$setControllingSubsystem(this);
        setExtraMass((float) MassUtil.getEntityMass(passenger));
        getOwner().getSubPart().getPart().assembly.activatePhysics();
        if (getOwner().getSubPart().getPart().assembly instanceof VehicleCore vc) {
            vc.recalculateCameraDistance();
        }
        resetControlGroupToPreset();
    }

    /** 客户端的旧座位清理：只改本地显示字段，骑乘关系由原版包负责 */
    public void clientClearSeat() {
        this.passenger = null;
        setOccupied(false);
        setExtraMass(0f);
    }

    /**
     * 客户端的新座位接管。{@code controlGroup} 直接照抄结果包里的值，
     * 不重查预设——两端各自查表得到的相等依赖两个注册表都加载成功且内容包一致。
     */
    public void clientAttachSeat(LivingEntity passenger, int controlGroup) {
        setOccupied(true);
        this.passenger = passenger;
        ((IEntityMixin) passenger).machine_Max$setControllingSubsystem(this);
        setExtraMass((float) MassUtil.getEntityMass(passenger));
        applyControlGroupIndex(controlGroup);
    }

    /** 把活动子组写成给定索引；越界也照写，语义与服务端一致（{@code getActiveGroup()} 返回 null 退化为 base） */
    public void applyControlGroupIndex(int index) {
        ControlGroupSet current = getControlGroupSet();
        if (current == null || current == ControlGroupSet.EMPTY) return;
        if (current.getActiveIndex() == index) return;
        setControlGroupSet(new ControlGroupSet(current.baseGroup, current.groups, current.getGuiActions(), index));
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
    void updateBoardingOffset(LivingEntity passenger) {
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
        }
        clearSeatState();
    }

    /** 座位侧的状态清理：乘客、占用、额外质量、乘客计数信号与输入信号 */
    private void clearSeatState() {
        passenger = null;
        setOccupied(false);
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

    /**
     * 座椅只有第一/第三人称布局；炮镜视角的基础元素由相机/瞄具的
     * {@code scope_hud_layout} 提供，武器分划由武器控制器提供，座椅在该视角返回 {@code null}。
     * <p>布局键取 {@link SeatSubsystemAttr#getFirstHudLayout()} / {@link SeatSubsystemAttr#getThirdHudLayout()}：
     * 零件实例可以按安装位覆盖型号定义里的默认布局。</p>
     */
    @Override
    @Nullable
    public HudLayout hudLayoutFor(HudView view) {
        return IHudHost.resolve(switch (view) {
            case FIRST -> attr.getFirstHudLayout();
            case THIRD -> attr.getThirdHudLayout();
            case SCOPE -> HudLayout.EMPTY;
        });
    }
}
