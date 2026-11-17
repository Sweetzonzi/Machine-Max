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
import io.github.sweetzonzi.machine_max.common.attachment.CrewReloadAttachment;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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
     * 装填候选的人物 UUID（同步）。空值表示本座位此刻没有候选。<br>
     * 客户端不能读 {@link #passenger}（它只有服务端有值），因此候选身份必须随同步值下发；
     * 只有 {@link #occupied} 也无法推出"有一个倍率 1.0 的装填手"。
     */
    private static final EntityDataAccessor<Optional<UUID>> DATA_LOADER_ID =
            SynchedEntityData.defineId(SeatSubsystem.class, EntityDataSerializers.OPTIONAL_UUID);

    /** 候选是否仍可作为存活执行者：乘员未移除、未濒死。 */
    private static final EntityDataAccessor<Boolean> DATA_LOADER_USABLE_ID =
            SynchedEntityData.defineId(SeatSubsystem.class, EntityDataSerializers.BOOLEAN);

    /** 候选的有效时间倍率 {@code A_eff}：属性层与药水效果的合成，由人物现算后人下发。 */
    private static final EntityDataAccessor<Float> DATA_LOADER_MULTIPLIER_ID =
            SynchedEntityData.defineId(SeatSubsystem.class, EntityDataSerializers.FLOAT);
    /**
     * 该座位的上车点，表达为所在 SubPart 刚体的局部坐标；null 表示本座位尚无车外登入记录。
     * <p>只在「从车外登入」时采样覆盖，或在「同一装配体内换座」时由旧座位经世界坐标换算交接过来；
     * 下车、死亡与子系统移除都不清空，随座位实例存续。</p>
     */
    public Vector3f boardingLocalOffset;

    /**
     * 本座位实际接受的频道：型号的交互输入与安装实例的装填发现频道取并集。<br>
     * 在构造时算一次，广播与终端查询每 tick 都要读它，不在调用现场拼表。
     */
    private final List<String> acceptedChannels;

    public SeatSubsystem(ISubsystemHost owner, String name, SeatSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        this.acceptedChannels = mergeAcceptedChannels(
                attr.staticAttribute.getAcceptedChannels(), attr.getLoaderDiscoveryInputs());
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
            } else if (part.entity != null && passenger.getVehicle() != part.entity) {
                if (!passenger.level().isClientSide)
                    passenger.startRiding(part.entity, true);
                passenger.resetFallDistance();//防止摔死
            } else if (part.entity == null) {
                removePassenger();
            } else {
                passenger.resetFallDistance();//防止摔死
            }
        } else resetSignalOutputs();
        // 乘员计数是状态型输出：结构变化清空信号槽后由本 tick 补回，不依赖上下车边沿。
        for (String channel : attr.passengerNumSignalTargets.keySet()) {
            sendSignalToAllTargets(channel, occupied ? 1f : 0f);
        }
        // 装填候选是人员条件的权威采样：上下座与个人能力变化都不触发结构变化，只能逐轮采样
        publishLoaderCandidate();
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
        builder.define(DATA_LOADER_ID, Optional.empty());
        builder.define(DATA_LOADER_USABLE_ID, false);
        builder.define(DATA_LOADER_MULTIPLIER_ID, (float) CrewReloadAttachment.DEFAULT_MULTIPLIER);
    }

    /**
     * 装填候选快照：人物 UUID、是否可作为存活执行者、有效时间倍率。
     *
     * <p>服务端由乘员实体现算，客户端由同步值组装——两端的业务判据因此是同一套字段，
     * 客户端不需要 {@code passenger} 引用，也不会把"座位占了人"当成"有人能装填"。</p>
     *
     * @param personId   人物 UUID
     * @param usable     是否可作为存活执行者
     * @param multiplier 有效时间倍率 {@code A_eff}（属性层 × 药水层，见
     *                   {@link CrewReloadAttachment#effectiveMultiplier()}）
     */
    public record LoaderCandidate(UUID personId, boolean usable, double multiplier) {
    }

    /**
     * 本座位此刻的装填候选。
     *
     * <p>服务端读乘员实体；客户端读同步值（见 {@link #DATA_LOADER_ID}），因此候选身份在两端同源。
     * 座位自身的启用／摧毁状态由调用方另判——那是座位条件，不是人的条件。</p>
     *
     * @return 候选；没有乘员时返回 {@code null}
     */
    @Nullable
    public LoaderCandidate loaderCandidate() {
        if (getLevel().isClientSide()) {
            Optional<UUID> id = synchedData.get(DATA_LOADER_ID);
            if (id.isEmpty()) return null;
            return new LoaderCandidate(id.get(), synchedData.get(DATA_LOADER_USABLE_ID),
                    synchedData.get(DATA_LOADER_MULTIPLIER_ID));
        }
        LivingEntity person = passenger;
        if (person == null) return null;
        boolean usable = !person.isRemoved() && !person.isDeadOrDying();
        return new LoaderCandidate(person.getUUID(), usable,
                CrewReloadAttachment.of(person).effectiveMultiplier());
    }

    /**
     * 服务端：把本座位的装填候选采样进同步值，人物或倍率变化时才写。
     * <p>乘员上下座与人物能力变化都不触发结构变化，因此每轮业务 tick 采一次；值相等时
     * 不写同步字段，也就不会产生无谓的包。
     */
    private void publishLoaderCandidate() {
        if (getLevel().isClientSide()) return;
        LivingEntity person = passenger;
        Optional<UUID> id = person == null
                ? Optional.empty()
                : Optional.of(person.getUUID());
        boolean usable = person != null && !person.isRemoved() && !person.isDeadOrDying();
        float multiplier = person == null
                ? (float) CrewReloadAttachment.DEFAULT_MULTIPLIER
                : (float) CrewReloadAttachment.of(person).effectiveMultiplier();
        if (!Objects.equals(synchedData.get(DATA_LOADER_ID), id)) {
            synchedData.set(DATA_LOADER_ID, id);
        }
        if (synchedData.get(DATA_LOADER_USABLE_ID) != usable) {
            synchedData.set(DATA_LOADER_USABLE_ID, usable);
        }
        if (Float.compare(synchedData.get(DATA_LOADER_MULTIPLIER_ID), multiplier) != 0) {
            synchedData.set(DATA_LOADER_MULTIPLIER_ID, multiplier);
        }
    }

    /**
     * 全量补推之前把装填候选刷成当前值：新加入的客户端要能立刻按候选选人，而不是等到下一次采样。
     */
    @Override
    public void refreshSyncBaseline() {
        publishLoaderCandidate();
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

    /** 主线程：从本端当前乘员实体读取名称，实体引用暂缺时返回空文本。 */
    public String getOccupantName() {
        return occupied && passenger != null ? passenger.getName().getString() : "";
    }

    /**
     * 本座位此刻能否被换入：座位与宿主都还在、宿主已有代理实体、且未被占用。
     * <p>只读 {@link #occupied}（服务端维护并同步，两端语义一致）；{@link #passenger} 只有服务端有值，
     * 客户端读它会把所有座位当成空位。</p>
     */
    public boolean isAvailable() {
        return isActive() && !isDestroyed()
                && !getSubPart().isDestroyed()
                && getSubPart().getEntity() != null
                && !occupied;
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
        resetSignalOutputs();
        clearInputSignals();
    }

    @Override
    public List<String> getAcceptedChannels() {
        return acceptedChannels;
    }

    /**
     * 交互输入与装填发现频道取并集，重复的频道只保留首次出现的位置。
     *
     * @param interactInputs 型号声明的交互输入
     * @param operatorInputs 安装实例声明的装填发现频道
     * @return 不可变的频道表
     */
    private static List<String> mergeAcceptedChannels(List<String> interactInputs, List<String> operatorInputs) {
        if (operatorInputs.isEmpty()) return List.copyOf(interactInputs);
        List<String> merged = new ArrayList<>(interactInputs);
        for (String channel : operatorInputs) {
            if (!merged.contains(channel)) merged.add(channel);
        }
        return List.copyOf(merged);
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
