package io.github.sweetzonzi.machine_max.common.vehicle;

import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.util.PPhase;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.MutableNetwork;
import com.google.common.graph.NetworkBuilder;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.datafixers.util.Pair;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AdvancedConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.ConnectorAlignmentHelper;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.SimpleConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.data.ConnectionData;
import io.github.sweetzonzi.machine_max.common.vehicle.data.PartData;
import io.github.sweetzonzi.machine_max.common.vehicle.data.VehicleData;
import io.github.sweetzonzi.machine_max.common.vehicle.event.connector.ConnectorAttachEvent;
import io.github.sweetzonzi.machine_max.common.vehicle.event.connector.ConnectorDetachEvent;
import io.github.sweetzonzi.machine_max.common.vehicle.interact.InteractBox;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.network.payload.assembly.ConnectorAttachPayload;
import io.github.sweetzonzi.machine_max.network.payload.assembly.ConnectorDetachPayload;
import io.github.sweetzonzi.machine_max.network.payload.assembly.PartRemovePayload;
import io.github.sweetzonzi.machine_max.network.payload.assembly.VehicleMergePayload;
import io.github.sweetzonzi.machine_max.network.payload.assembly.VehicleStatusSyncPayload;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SyncedDataHolder;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
public class VehicleCore implements SyncedDataHolder {
    private static final float COMBO_ATTACH_MAX_POS_ERROR = ConnectorAlignmentHelper.DEFAULT_MAX_POS_ERROR;
    private static final float COMBO_ATTACH_MAX_DIRECTION_ERROR = ConnectorAlignmentHelper.DEFAULT_MAX_DIRECTION_ERROR;//1°以内视为方向对齐

    //存储所有部件与连接关系
    public final MutableNetwork<Part, Pair<AbstractConnector, SimpleConnector>> partNet = NetworkBuilder.undirected().allowsParallelEdges(true).build();
    //存储所有部件
    public final ConcurrentMap<UUID, Part> partMap = new java.util.concurrent.ConcurrentHashMap<>();
    public String name = "Vehicle";//载具名称
    public final Level level;//载具所在世界
    @Setter
    public UUID uuid;//载具UUID
    @Setter
    private ChunkPos oldChunkPos = new ChunkPos(0, 0);
    public int tickCount = 0;
    public volatile boolean inLevel = false;
    //属性
    private static final EntityDataAccessor<Float> DATA_HP_ID = SynchedEntityData.defineId(VehicleCore.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_DESTROYED_ID = SynchedEntityData.defineId(VehicleCore.class, EntityDataSerializers.BOOLEAN);
    private final SynchedEntityData synchedData;
    private float maxHp = 20f;//耐久度上限（本地重算，不走网络同步）
    private boolean destroyedHandled = false;
    private Vec3 position = Vec3.ZERO;//位置
    private Vec3 velocity = Vec3.ZERO;//速度
    private Vec3 oldPosition = Vec3.ZERO;//上一帧位置
    private Vec3 oldVelocity = Vec3.ZERO;//上一帧速度
    public float totalMass = 0;//总质量
    public int statusSyncCountDown = 5;//状态同步倒计时
    @Setter
    public boolean inLoadedChunk = false;//是否睡眠
    public boolean loadFromSavedData = false;//是否已加载
    public boolean loaded = false;//是否已加载完毕
    public boolean isRemoved = false;//是否已被移除
    //控制
    public SubsystemController subSystemController = new SubsystemController(this);
    private final AtomicInteger skillCount = new AtomicInteger();
    public ControlMode mode = ControlMode.GROUND;//控制模式

    public enum ControlMode {GROUND, PLANE, SHIP, MECH}

    //渲染
    public float cameraDistance = 4f;//相机距离

    public VehicleCore(Level level, Part rootPart) {
        this.level = level;
        this.synchedData = this.createSynchedData();
        this.uuid = rootPart.uuid;
        ObjectManager.initVehicle(this);
        this.addPart(rootPart);
        subSystemController.initAllSubsystems();//子系统初始化
        recalculateMaxHp(HpRecalcMode.INIT_TO_MAX);
    }

    public VehicleCore(Level level, VehicleData savedData, boolean readAdditionalData) {
        this.level = level;
        this.synchedData = this.createSynchedData();
        this.uuid = UUID.fromString(savedData.uuid);
        this.synchedData.set(DATA_HP_ID, Math.max(savedData.hp, 0f));
        this.position = savedData.pos;
        this.oldPosition = savedData.pos;
        this.name = savedData.name;
        ObjectManager.initVehicle(this);
        try {
            //重建部件
            for (PartData partData : savedData.parts.values())
                this.addPart(new Part(partData, level, readAdditionalData));
            //重建连接关系
            for (ConnectionData connectionData : savedData.connections) {
                Part partA = partMap.get(UUID.fromString(connectionData.partUuidA));
                Part partB = partMap.get(UUID.fromString(connectionData.partUuidS));
                if (partA != null && partB != null) {
                    AbstractConnector advConnector = partA.subParts.get(connectionData.subPartNameA).connectors.get(connectionData.getAdvConnectorName());
                    AbstractConnector simpleConnector = partB.subParts.get(connectionData.subPartNameS).connectors.get(connectionData.getSimpleConnectorName());
                    if (advConnector != null && simpleConnector != null) {
                        advConnector.setActualTransform(connectionData.posRotA.toTransform());
                        simpleConnector.setActualTransform(connectionData.posRotS.toTransform());
                        this.attachConnector(advConnector, simpleConnector, null);
                    } else MachineMax.LOGGER.error("Connector {} not found in vehicle {}.", connectionData, this.name);
                } else throw new IllegalArgumentException("未在载具中找到连接数据所需的部件");
            }
            subSystemController.initAllSubsystems();//子系统初始化
            recalculateMaxHp(HpRecalcMode.CLAMP_ONLY);
            recalculateCameraDistance();
        } catch (Exception e) {
            onRemoveFromLevel(); // 移除数据出错的载具
            throw e;
        }
    }

    /**
     * <p>载具因拓扑结构发生变化而分裂为多个部分时使用的构造方法</p>
     * <p>Method used to create a new vehicle when the topology of the vehicle changes and splits into multiple parts</p>
     *
     * @param uuid       新载具的UUID UUID of the new Vehicle
     * @param partNet    新载具的拓扑结构 Structure of the new Vehicle
     * @param oldVehicle 被分裂的载具 The vehicle that was split
     */
    public VehicleCore(Level level, UUID uuid, MutableNetwork<Part, Pair<AbstractConnector, SimpleConnector>> partNet, VehicleCore oldVehicle) {
        this.level = level;
        this.synchedData = this.createSynchedData();
        this.uuid = uuid;
        this.name = oldVehicle.name;
        this.position = oldVehicle.position;
        this.synchedData.set(DATA_HP_ID, 0f);
        for (Part part : partNet.nodes()) {
            Set<AbstractSubsystem> subsystems = part.getAllSubsystems();
            oldVehicle.subSystemController.removeSubsystems(subsystems, true);
            oldVehicle.partMap.remove(part.uuid);
            oldVehicle.partNet.removeNode(part);
            this.partMap.put(part.uuid, part);
            this.partNet.addNode(part);
            part.vehicle = this;
            this.subSystemController.addSubsystems(subsystems);
        }
        for (Pair<AbstractConnector, SimpleConnector> edge : partNet.edges()) {
            EndpointPair<Part> connectedParts = partNet.incidentNodes(edge);
            this.partNet.addEdge(connectedParts, edge);
        }
        this.updateTotalMass();
        this.subSystemController.onVehicleStructureChanged();
        recalculateMaxHp(HpRecalcMode.CLAMP_ONLY);
        recalculateCameraDistance();
    }

    private enum HpRecalcMode {
        INIT_TO_MAX,
        GROW_WITH_MAX_DELTA,
        CLAMP_ONLY
    }

    private SynchedEntityData createSynchedData() {
        SynchedEntityData.Builder builder = new SynchedEntityData.Builder(this);
        builder.define(DATA_HP_ID, 20.0f);
        builder.define(DATA_DESTROYED_ID, false);
        return builder.build();
    }

    public SynchedEntityData getSyncedData() {
        return this.synchedData;
    }

    @Override
    public void onSyncedDataUpdated(@NotNull List<SynchedEntityData.DataValue<?>> updatedData) {
    }

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> key) {
        if (!level.isClientSide()) return;
        if (key.equals(DATA_DESTROYED_ID) && isDestroyed()) {
            markDestroyed();
        }
    }

    public float getHp() {
        return this.synchedData.get(DATA_HP_ID);
    }

    public void setHp(float hp) {
        this.synchedData.set(DATA_HP_ID, Math.clamp(hp, 0f, this.maxHp));
    }

    public boolean isDestroyed() {
        return this.synchedData.get(DATA_DESTROYED_ID);
    }

    private float calculateMaxHpFromParts() {
        float result = 0f;
        for (Part part : partMap.values()) {
            result += Math.max(0f, part.getVehicleDurabilityContribution());
        }
        return Math.max(0f, result);
    }

    private void recalculateMaxHp(HpRecalcMode mode) {
        float oldMaxHp = this.maxHp;
        float newMaxHp = calculateMaxHpFromParts();
        this.maxHp = newMaxHp;
        if (isDestroyed()) {
            setHp(0f);
            return;
        }
        switch (mode) {
            case INIT_TO_MAX -> setHp(newMaxHp);
            case GROW_WITH_MAX_DELTA -> setHp(getHp() + Math.max(0f, newMaxHp - oldMaxHp));
            case CLAMP_ONLY -> setHp(getHp());
        }
    }

    public void applyVehicleDamage(float damage) {
        if (level.isClientSide() || damage <= 0 || isRemoved || isDestroyed()) return;
        setHp(getHp() - damage);
    }

    public void refreshMaxHp() {
        recalculateMaxHp(HpRecalcMode.CLAMP_ONLY);
    }
    private void markDestroyed() {
        this.synchedData.set(DATA_DESTROYED_ID, true);
        setHp(0f);
        for (Part part : partMap.values()) {
            for (SubPart subPart : part.subParts.values()) {
                if (!subPart.isDestroyed()) {
                    subPart.setDestroyed();
                }
            }
        }
    }

    private void syncStatusToClient() {
        if (level.isClientSide()) return;
        List<SynchedEntityData.DataValue<?>> list = this.synchedData.packDirty();
        if (list != null) {
            PacketDistributor.sendToPlayersInDimension((ServerLevel) this.level, new VehicleStatusSyncPayload(this.uuid, list));
        }
    }

    private void distributeSplitHp(float sourceHp, List<VehicleCore> splitVehicles) {
        List<VehicleCore> allVehicles = new ArrayList<>(1 + splitVehicles.size());
        allVehicles.add(this);
        allVehicles.addAll(splitVehicles);

        for (VehicleCore vehicle : allVehicles) {
            vehicle.recalculateMaxHp(HpRecalcMode.CLAMP_ONLY);
        }

        float totalMaxHp = 0f;
        for (VehicleCore vehicle : allVehicles) {
            totalMaxHp += vehicle.getMaxHp();
        }
        float availableHp = Math.max(sourceHp, 0f);
        if (totalMaxHp <= 1e-6f) {
            for (VehicleCore vehicle : allVehicles) vehicle.setHp(0f);
            return;
        }

        float assignedHp = 0f;
        for (int i = 0; i < allVehicles.size(); i++) {
            VehicleCore vehicle = allVehicles.get(i);
            float shareHp = (i == allVehicles.size() - 1)
                    ? (availableHp - assignedHp)
                    : (availableHp * vehicle.getMaxHp() / totalMaxHp);
            vehicle.setHp(shareHp);
            assignedHp += vehicle.getHp();
        }
    }

    /**
     * 主线程tick，默认tps=20
     */
    public void preTick() {
//        if (tickCount == 100)
//            recalculateCameraDistance();
        //保持激活与控制量更新
        Vec3 newPos = new Vec3(0, 0, 0);
        Vec3 newVel = new Vec3(0, 0, 0);
        int count = 0;
        for (Part part : partMap.values()) {
            if (part.isDestroyed()) {
                removePart(part);
                continue;
            }
            Vec3 partPos = SparkMathKt.toVec3(PhysicsBodyExtensionKt.stateOf(part.rootSubPart.body).getTransform().getTranslation());
            Vec3 partVel = SparkMathKt.toVec3(part.rootSubPart.body.getLinearVelocity(null));
            newPos = newPos.add(partPos);//计算载具形心位置
            newVel = newVel.add(partVel);//计算载具形心速度
            count++;
            if (inLoadedChunk && !isRemoved) part.onTick();
        }
        if (count > 0) {
            this.oldPosition = this.position;
            this.oldVelocity = this.velocity;
            this.position = newPos.scale((double) 1 / count);//更新载具形心位置
            this.velocity = newVel.scale((double) 1 / count);//更新载具形心速度
        }
        if (partMap.values().isEmpty() || this.position.y < -1024) {
            ObjectManager.removeVehicle(this);//移除掉出世界的载具
            return;
        }
        if (inLoadedChunk && !isRemoved) {
            if (!loaded) {
                loaded = true;
            }
            subSystemController.tick();
        } else if (this.velocity.length() < 30) {
//            deactivate();//休眠
        }
        if (!isDestroyed() && getHp() <= 0f) {
            markDestroyed();
        }
        if (!level.isClientSide()) {
            syncStatusToClient();
        }
        tickCount++;
    }

    public void prePhysicsTick() {
        subSystemController.prePhysicsTick();
        for (Part part : partMap.values()) {
            part.onPrePhysicsTick();
        }
    }

    public void postPhysicsTick() {
        if (!level.isClientSide && statusSyncCountDown > 0) statusSyncCountDown--;
        subSystemController.postPhysicsTick();
        for (Part part : partMap.values()) {
            part.onPostPhysicsTick();
        }
    }

    /**
     * 激活载具所有零件的运动体
     */
    public void activate() {
        SparkLevel.getPhysicsLevel(level).submitImmediateTask(PPhase.PRE, () -> {
            for (Part part : partMap.values()) part.subParts.values().forEach(subPart -> subPart.body.activate());
            return null;
        });
    }

    public void updateTotalMass() {
        this.totalMass = 0;
        for (Part part : partMap.values()) {
            this.totalMass += part.totalMass;
        }
    }

    /**
     * 将部件添加到载具中
     * 已有载具应使用{@link VehicleCore#attachConnector}方法添加部件并连接部件接口
     *
     * @param part 新部件
     * @see VehicleCore#attachConnector
     */
    public void addPart(Part part) {
        part.vehicle = this;
        for (AbstractSubsystem subSystem : part.getAllSubsystems()) {//连接部件内子系统的信号传输关系
            subSystem.setTargetFromNames();
        }
        for (AbstractConnector connector : part.allConnectors.values()) {//连接部件内信号端口的传输关系
            if (connector.signalPort != null) connector.signalPort.setTargetFromNames();
        }
        for (SubPart subPart : part.subParts.values()) {//连接部件内交互判定区的信号传输关系
            if (subPart.interactBoxes != null) {
                for (InteractBox interactBox : subPart.interactBoxes.values()) {
                    interactBox.setTargetFromNames();
                    if (isInLevel()) interactBox.onVehicleStructureChanged();
                }
            }
        }
        this.updateTotalMass();
        partMap.put(part.uuid, part);
        partNet.addNode(part);
        subSystemController.addSubsystems(part.getAllSubsystems());
    }

    public void removePart(Part part) {
        removePart(part, Map.of());
    }

    public void removePart(Part part, Map<UUID, UUID> spiltVehicles) {
        if (partMap.containsValue(part)) {
            UUID partUuid = part.getUuid();
            float hpBeforeSplit = getHp();
            subSystemController.removeSubsystems(part.getAllSubsystems(), false);
            partNet.removeNode(part);
            partMap.remove(part.uuid, part);
            part.destroy();
            var spiltPartNets = partNetSpiltCheck();
            if (inLevel) {//发包客户端移除部件
                if (!level.isClientSide()) {
                    if (spiltPartNets.size() <= 1) {
                        PacketDistributor.sendToPlayersInDimension(
                                (ServerLevel) this.level,
                                new PartRemovePayload(this.uuid, partUuid, Map.of())
                        );
                    } else {
                        PacketDistributor.sendToPlayersInDimension(
                                (ServerLevel) this.level,
                                new PartRemovePayload(this.uuid, partUuid, serverHandleSpilt(spiltPartNets, hpBeforeSplit))
                        );
                    }
                } else {
                    clientHandleSpilt(spiltPartNets, spiltVehicles, hpBeforeSplit);
                }
            }
            if (spiltPartNets.size() <= 1) {
                recalculateMaxHp(HpRecalcMode.CLAMP_ONLY);
            }
            if (partMap.values().isEmpty() && !level.isClientSide())
                ObjectManager.removeVehicle(this);//如果所有部件都被移除，则销毁载具
            else {
                this.activate();//重新激活，进行部件移除后的物理计算
                this.subSystemController.onVehicleStructureChanged();//通知子系统载具结构更新
                recalculateCameraDistance();
                for (SubPart subPart : part.subParts.values()) {
                    if (subPart.interactBoxes != null)
                        for (InteractBox interactBox : subPart.interactBoxes.values()) {
                            interactBox.onVehicleStructureChanged();
                        }
                }
            }
            this.updateTotalMass();
        } else MachineMax.LOGGER.error("在载具{}中找不到部件{}，无法移除 ", this.uuid, part.name);
    }

    public void removePart(UUID uuid, Map<UUID, UUID> spiltVehicles) {
        if (partMap.containsKey(uuid)) {
            Part part = partMap.get(uuid);
            this.removePart(part, spiltVehicles);
        } else MachineMax.LOGGER.error("在载具{}中找不到部件{}，无法移除 ", this.uuid, uuid.toString());
    }

    /**
     * 连接两个接口
     * 若是新安装的部件，则尝试连接部件接口与载具其他已有接口
     *
     * @param connector1 接口1
     * @param connector2 接口2
     * @param newPart    新安装的部件，可为null
     */
    public void attachConnector(AbstractConnector connector1, AbstractConnector connector2, @Nullable Part newPart) {
        VehicleCore connectorVehicle1 = connector1.subPart.part.vehicle;
        VehicleCore connectorVehicle2 = connector2.subPart.part.vehicle;
        if (connectorVehicle1 == null || connectorVehicle2 == null) {
            MachineMax.LOGGER.error("连接失败：连接点所属部件未绑定载具");
            return;
        }
        if (connectorVehicle1 != this && connectorVehicle2 != this) {
            MachineMax.LOGGER.error("连接失败：连接点{}与{}都不属于载具{}", connector1.name, connector2.name, this.uuid);
            return;
        }
        if (connector1.subPart.part == connector2.subPart.part)
            throw new UnsupportedOperationException("不能连接同一个部件内的接口");

        AbstractConnector advancedConnector;
        SimpleConnector simpleConnector;
        if (connector2 instanceof SimpleConnector) {
            simpleConnector = (SimpleConnector) connector2;
            advancedConnector = connector1;
        } else if (connector1 instanceof SimpleConnector) {
            simpleConnector = (SimpleConnector) connector1;
            advancedConnector = connector2;
        } else throw new UnsupportedOperationException("连接点之一必须是SimpleConnector类型");

        VehicleCore advancedVehicle = advancedConnector.subPart.part.vehicle;
        VehicleCore simpleVehicle = simpleConnector.subPart.part.vehicle;
        if (advancedVehicle == null || simpleVehicle == null) {
            MachineMax.LOGGER.error("连接失败：连接点所属部件未绑定载具");
            return;
        }
        if (advancedVehicle.level != simpleVehicle.level || this.level != advancedVehicle.level) {
            MachineMax.LOGGER.error("连接失败：跨维度载具不可连接");
            return;
        }

        if (advancedVehicle == simpleVehicle) {
            attachConnectorInSameVehicle(advancedConnector, simpleConnector, newPart);
            return;
        }

        if (newPart != null) {
            MachineMax.LOGGER.error("连接失败：跨载具合并不支持newPart参数");
            return;
        }

        VehicleCore donorVehicle = advancedVehicle == this ? simpleVehicle : advancedVehicle;
        attachConnectorAcrossVehicles(advancedConnector, simpleConnector, donorVehicle);
    }

    private void attachConnectorInSameVehicle(AbstractConnector advancedConnector, SimpleConnector simpleConnector, @Nullable Part newPart) {
        if (newPart != null && !partMap.containsKey(newPart.uuid) && (advancedConnector.subPart.part == newPart || simpleConnector.subPart.part == newPart)) {
            this.addPart(newPart);
            recalculateMaxHp(HpRecalcMode.GROW_WITH_MAX_DELTA);
        }
        List<ConnectionData> comboList = new java.util.ArrayList<>(1);
        boolean attached = advancedConnector.attach(simpleConnector);//连接部件
        if (!attached) return;

        this.partNet.addEdge(//添加连接关系
                advancedConnector.subPart.part,
                simpleConnector.subPart.part,
                Pair.of(advancedConnector, simpleConnector)
        );
        if (newPart != null) {
            if (!level.isClientSide()) comboList = comboAttachConnector(newPart);//检查同部件内是否仍有可连接的接口，如有则连接
            newPart.addToLevel();//将新部件加入到世界
        }
        if (isInLevel()) {
            advancedConnector.addToLevel();//将关节约束加入到世界
            this.subSystemController.onVehicleStructureChanged();//通知子系统载具结构更新
            recalculateCameraDistance();
            this.activate();
            if (!level.isClientSide()) {
                comboList.addFirst(new ConnectionData(advancedConnector, simpleConnector));//特殊连接点在前面，以保证连接点属性得到正确应用
                //发包客户端创建连接关系
                PacketDistributor.sendToPlayersInDimension((ServerLevel) this.level, new ConnectorAttachPayload(
                        this.uuid,
                        comboList,
                        newPart != null,
                        newPart == null ? null : new PartData(newPart)
                ));
            }
        }
    }

    private void attachConnectorAcrossVehicles(AbstractConnector advancedConnector, SimpleConnector simpleConnector, VehicleCore donorVehicle) {
        boolean attached = advancedConnector.attach(simpleConnector);
        if (!attached) return;

        float mergedHp = this.getHp() + donorVehicle.getHp();
        this.absorbVehicle(donorVehicle);
        this.partNet.addEdge(
                advancedConnector.subPart.part,
                simpleConnector.subPart.part,
                Pair.of(advancedConnector, simpleConnector)
        );
        this.recalculateMaxHp(HpRecalcMode.CLAMP_ONLY);
        this.setHp(mergedHp);

        if (isInLevel()) advancedConnector.addToLevel();
        this.subSystemController.onVehicleStructureChanged();
        recalculateCameraDistance();
        this.activate();
        this.updateTotalMass();

        List<ConnectionData> newConnections = List.of(new ConnectionData(advancedConnector, simpleConnector));
        if (!level.isClientSide()) {
            if (!ObjectManager.removeMergedVehicle(donorVehicle)) {
                MachineMax.LOGGER.error("载具{}合并后无法移除被吸收载具{}", this.uuid, donorVehicle.uuid);
                return;
            }
            PacketDistributor.sendToPlayersInDimension(
                    (ServerLevel) this.level,
                    new VehicleMergePayload(this.uuid, donorVehicle.uuid, newConnections)
            );
        }
    }

    private void absorbVehicle(VehicleCore donorVehicle) {
        if (donorVehicle == this) return;
        if (donorVehicle.level != this.level)
            throw new IllegalArgumentException("不能合并不同维度的载具");

        List<Map.Entry<EndpointPair<Part>, Pair<AbstractConnector, SimpleConnector>>> donorEdges = new ArrayList<>();
        for (Pair<AbstractConnector, SimpleConnector> edge : donorVehicle.partNet.edges()) {
            donorEdges.add(Map.entry(donorVehicle.partNet.incidentNodes(edge), edge));
        }

        for (Part part : new ArrayList<>(donorVehicle.partMap.values())) {
            Set<AbstractSubsystem> subsystems = part.getAllSubsystems();
            donorVehicle.subSystemController.removeSubsystems(subsystems, true);
            donorVehicle.partMap.remove(part.uuid);
            donorVehicle.partNet.removeNode(part);
            this.partMap.put(part.uuid, part);
            this.partNet.addNode(part);
            part.vehicle = this;
            this.subSystemController.addSubsystems(subsystems);
        }

        for (Map.Entry<EndpointPair<Part>, Pair<AbstractConnector, SimpleConnector>> edgeEntry : donorEdges) {
            EndpointPair<Part> connectedParts = edgeEntry.getKey();
            this.partNet.addEdge(connectedParts.nodeU(), connectedParts.nodeV(), edgeEntry.getValue());
        }
        donorVehicle.updateTotalMass();
    }

    public void clientHandleMerge(VehicleCore donorVehicle, List<ConnectionData> newConnections) {
        if (!level.isClientSide()) return;
        if (donorVehicle == this) {
            MachineMax.LOGGER.warn("收到无效合并包：保留载具与被移除载具相同 {}", this.uuid);
            return;
        }
        float mergedHp = this.getHp() + donorVehicle.getHp();
        this.absorbVehicle(donorVehicle);
        this.recalculateMaxHp(HpRecalcMode.CLAMP_ONLY);
        this.setHp(mergedHp);
        this.updateTotalMass();
        for (ConnectionData connection : newConnections) {
            Pair<AbstractConnector, SimpleConnector> connectorPair = this.connectionDataToConnectorPair(connection);
            if (connectorPair == null) continue;
            this.attachConnector(connectorPair.getFirst(), connectorPair.getSecond(), null);
        }
        if (newConnections.isEmpty()) {
            this.subSystemController.onVehicleStructureChanged();
            recalculateCameraDistance();
            this.activate();
        }
        if (!ObjectManager.removeMergedVehicle(donorVehicle)) {
            MachineMax.LOGGER.error("客户端处理载具合并时无法移除被吸收载具{}", donorVehicle.uuid);
        }
    }

    @Nullable
    private Pair<AbstractConnector, SimpleConnector> connectionDataToConnectorPair(ConnectionData connection) {
        try {
            Part partA = this.partMap.get(UUID.fromString(connection.partUuidA));
            Part partB = this.partMap.get(UUID.fromString(connection.partUuidS));
            if (partA == null || partB == null) {
                MachineMax.LOGGER.error("载具{}中未找到连接关系对应部件: {}", this.uuid, connection);
                return null;
            }
            AbstractConnector connectorA = partA.subParts.get(connection.subPartNameA).connectors.get(connection.getAdvConnectorName());
            AbstractConnector connectorB = partB.subParts.get(connection.subPartNameS).connectors.get(connection.getSimpleConnectorName());
            if (!(connectorB instanceof SimpleConnector simpleConnector) || connectorA == null) {
                MachineMax.LOGGER.error("载具{}中未找到连接关系对应连接点: {}", this.uuid, connection);
                return null;
            }
            return Pair.of(connectorA, simpleConnector);
        } catch (Exception e) {
            MachineMax.LOGGER.error("解析连接关系失败: {}", connection, e);
            return null;
        }
    }

    /**
     * 检查同部件内是否仍有可连接的接口，如有则连接
     *
     * @param newPart 新安装的部件
     * @return 新增的连接关系列表
     */
    private List<ConnectionData> comboAttachConnector(Part newPart) {
        List<ConnectionData> result = new java.util.ArrayList<>();
        for (SubPart newSubPart : newPart.subParts.values()) {//遍历新安装部件的零件
            for (AbstractConnector connector1 : newSubPart.connectors.values()) {//遍历新安装部件内的接口
                if (!connector1.internal && !connector1.hasPart()) {//若无连接零件且不为内部零件，则尝试连接载具已有其他接口
                    for (Part part : partNet.nodes()) {//遍历载具内所有部件
                        if (part == newPart) continue;//跳过新安装的部件
                        for (SubPart subPart : part.subParts.values()) {//遍历所有部件部件内的所有零件
                            for (AbstractConnector connector2 : subPart.connectors.values()) {
                                if (connector2.internal || connector2.hasPart()) continue;//跳过内部接口和已连接接口
                                AbstractConnector advancedConnector;
                                SimpleConnector simpleConnector;
                                if (connector2 instanceof SimpleConnector) {
                                    simpleConnector = (SimpleConnector) connector2;
                                    advancedConnector = connector1;
                                } else if (connector1 instanceof SimpleConnector) {
                                    simpleConnector = (SimpleConnector) connector1;
                                    advancedConnector = connector2;
                                } else continue;//二者中存在AttachPointConnector时才可尝试连接
                                if (ConnectorAlignmentHelper.isAlignedForAttach(
                                        advancedConnector,
                                        simpleConnector,
                                        COMBO_ATTACH_MAX_POS_ERROR,
                                        COMBO_ATTACH_MAX_DIRECTION_ERROR
                                )) {//若位置与方向误差均小于阈值，则尝试连接
                                    advancedConnector.alignActualTransformForJoint(simpleConnector);//连接前校正 actualTransform，避免关节初始应力异常
                                    boolean attached = advancedConnector.attach(simpleConnector);//先连接，成功后再写入拓扑，避免产生伪连接
                                    if (attached) {
                                        this.partNet.addEdge(//添加连接关系
                                                advancedConnector.subPart.part,
                                                simpleConnector.subPart.part,
                                                Pair.of(advancedConnector, simpleConnector)
                                        );
                                        result.add(new ConnectionData(advancedConnector, simpleConnector));//打包新增连接关系
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    public void detachConnector(AbstractConnector connector) {
        detachConnectors(List.of(connector));
    }

    public void detachConnectors(List<AbstractConnector> connectors) {
        List<Pair<AbstractConnector, SimpleConnector>> connections = new ArrayList<>();
        for (AbstractConnector connector : connectors) {
            if (connector.subPart.part.vehicle == this) {
                if (!connector.internal) {//若是与外部部件连接的接口，则需要移除载具核心中记录的连接关系
                    if (connector.hasPart()) {
                        if (connector instanceof AdvancedConnector)
                            connections.add(Pair.of(connector, (SimpleConnector) connector.attachedConnector));
                        else connections.add(Pair.of(connector.attachedConnector, (SimpleConnector) connector));
                    } else
                        MachineMax.LOGGER.warn("载具{}的接口{}未连接到任何部件，无法断开连接", this.name, connector.name);
                } else MachineMax.LOGGER.warn("载具{}的接口{}为内部接口，无法断开连接", this.name, connector.name);
            } else MachineMax.LOGGER.error("接口{}不属于载具{}，无法断开连接", connector.name, this.name);
        }
        detachConnections(connections);
    }

    private void detachConnections(List<Pair<AbstractConnector, SimpleConnector>> connections) {
        detachConnections(connections, Map.of());
    }

    /**
     * <p>断开载具中指定的连接关系并检查连通性，若分裂为多个部分则相应创建新载具</p>
     * <p>Detach the specified connection relationship in the vehicle and check connectivity. If the connectivity is split into multiple parts, new vehicles are created accordingly.</p>
     *
     * @param connections
     * @param spiltVehicles
     */
    public void detachConnections(List<Pair<AbstractConnector, SimpleConnector>> connections, Map<UUID, UUID> spiltVehicles) {
        List<ConnectionData> connectionsToRemove = new ArrayList<>();
        float hpBeforeSplit = getHp();
        for (Pair<AbstractConnector, SimpleConnector> connection : connections) {
            connection.getFirst().detach(false);
            this.activate();
            boolean removed = partNet.removeEdge(connection);
            if (!removed && connection.getFirst() instanceof SimpleConnector simpleConnector)
                removed = partNet.removeEdge(Pair.of(connection.getSecond(), simpleConnector));
            if (removed && !level.isClientSide()) connectionsToRemove.add(new ConnectionData(connection));
            if (!removed) MachineMax.LOGGER.error("载具{}中未找到连接关系{}，无法移除", this.name, connection);
        }
        var spiltPartNets = partNetSpiltCheck();
        if (!level.isClientSide()) {//若是服务端，则向客户端发包通知拆解接口
            if (spiltPartNets.size() <= 1) {//未分裂为多个载具
                PacketDistributor.sendToPlayersInDimension((ServerLevel) level, new ConnectorDetachPayload(
                        uuid, connectionsToRemove, Map.of()));
            } else {//分裂为至少两个部分
                //发包通知客户端拆除接口，并将分裂出的新载具UUID一同传输
                PacketDistributor.sendToPlayersInDimension((ServerLevel) level, new ConnectorDetachPayload(
                        uuid, connectionsToRemove, serverHandleSpilt(spiltPartNets, hpBeforeSplit)));
            }
        } else {//客户端行为，仅应被载具断开连接的网络包调用
            clientHandleSpilt(spiltPartNets, spiltVehicles, hpBeforeSplit);
        }
        if (spiltPartNets.size() <= 1) {
            recalculateMaxHp(HpRecalcMode.CLAMP_ONLY);
        }
        this.updateTotalMass();
        this.subSystemController.onVehicleStructureChanged();//通知子系统载具结构更新
        recalculateCameraDistance();
    }

    /**
     * 服务端处理载具分裂：保留总质量最大的部分，其余部分分裂为新载具
     *
     * @param spiltPartNets 连通性检查得出的所有子网络集合
     * @return 发送给客户端的同步 Map (子网络中某个部件的 UUID -> 新载具 UUID)
     */
    private Map<UUID, UUID> serverHandleSpilt(Set<MutableNetwork<Part, Pair<AbstractConnector, SimpleConnector>>> spiltPartNets, float sourceHp) {
        synchronized (partNet) { // 对图对象加锁，防止计算期间结构被修改
            if (spiltPartNets.size() <= 1) return Map.of();

            // 1. 将子网络转为列表并按总质量降序排序
            List<MutableNetwork<Part, Pair<AbstractConnector, SimpleConnector>>> sortedNets = new ArrayList<>(spiltPartNets);
            sortedNets.sort((net1, net2) -> {
                float mass1 = calculateNetworkMass(net1);
                float mass2 = calculateNetworkMass(net2);
                return Float.compare(mass2, mass1); // 降序排序
            });

            Map<UUID, UUID> spiltVehiclesToSend = new HashMap<>();
            List<VehicleCore> createdVehicles = new ArrayList<>();

            // 2. 索引为 0 的网络（质量最大者）保留当前载具身份，不进行处理。
            // 3. 从索引 1 开始，将较小的部分剥离并创建新载具。
            for (int i = 1; i < sortedNets.size(); i++) {
                MutableNetwork<Part, Pair<AbstractConnector, SimpleConnector>> network = sortedNets.get(i);
                UUID newVehicleUuid = UUID.randomUUID();

                // 获取该网络中任意一个部件的 UUID，用于客户端识别是哪一部分分裂了
                UUID referencePartUuid = network.nodes().iterator().next().uuid;

                // 调用分裂构造函数：
                // 该构造函数内部会从当前载具 (this) 中移除对应的 Part 和子系统
                VehicleCore newVehicle = new VehicleCore(level, newVehicleUuid, network, this);
                ObjectManager.addSpiltVehicle(newVehicle);
                createdVehicles.add(newVehicle);
                spiltVehiclesToSend.put(referencePartUuid, newVehicleUuid);
            }

            distributeSplitHp(sourceHp, createdVehicles);
            // 更新当前载具（即保留下来的最大部分）的总质量
            this.updateTotalMass();
            return spiltVehiclesToSend;
        }
    }

    /**
     * 辅助方法：计算一个子网络中所有部件的总质量
     */
    private float calculateNetworkMass(MutableNetwork<Part, Pair<AbstractConnector, SimpleConnector>> network) {
        float total = 0;
        for (Part part : network.nodes()) {
            total += part.totalMass;
        }
        return total;
    }

    private void clientHandleSpilt(Set<MutableNetwork<Part, Pair<AbstractConnector, SimpleConnector>>> spiltPartNets, Map<UUID, UUID> spiltVehicles, float sourceHp) {
        List<VehicleCore> createdVehicles = new ArrayList<>();
        for (Map.Entry<UUID, UUID> entry : spiltVehicles.entrySet()) {
            Part part = partMap.get(entry.getKey());
            if (part == null) continue;
            UUID spiltVehicleUUID = entry.getValue();
            for (var vehicle : spiltPartNets) {
                if (vehicle.nodes().contains(part)) {
                    //为分离的部件指定新的VehicleCore
                    VehicleCore spiltVehicle = new VehicleCore(level, spiltVehicleUUID, vehicle, this);
                    ObjectManager.addSpiltVehicle(spiltVehicle);
                    createdVehicles.add(spiltVehicle);
                    break;//处理下一个被分离的部件
                }
            }
        }
        if (!createdVehicles.isEmpty()) {
            distributeSplitHp(sourceHp, createdVehicles);
        } else {
            recalculateMaxHp(HpRecalcMode.CLAMP_ONLY);
        }
    }

    /**
     * <p>获取载具部件连接关系图的所有联通子图的集合，用于连通性检查</p>
     * <p>GetVehicleVariable all the connected sub-graphs of the vehicle part connection graph, used for connectivity check.</p>
     *
     * @return 连通子图集合 Subgraph set
     */
    public Set<MutableNetwork<Part, Pair<AbstractConnector, SimpleConnector>>> partNetSpiltCheck() {
        synchronized (partNet) { // 对图对象加锁，防止计算期间结构被修改
            if (partNet.nodes().isEmpty()) return Set.of();

            Set<MutableNetwork<Part, Pair<AbstractConnector, SimpleConnector>>> splitPartNets = new HashSet<>();
            Set<Part> unvisited = new HashSet<>(partNet.nodes()); // 使用 partNet 的节点集

            while (!unvisited.isEmpty()) {
                // 1. 开启一个新的连通子图搜索
                Part startPart = unvisited.iterator().next();
                Set<Part> componentNodes = new HashSet<>();
                Queue<Part> queue = new LinkedList<>();

                queue.add(startPart);
                componentNodes.add(startPart);
                unvisited.remove(startPart);

                // 2. BFS 搜索所有连通的节点
                while (!queue.isEmpty()) {
                    Part current = queue.poll();
                    for (Part neighbor : partNet.adjacentNodes(current)) {
                        if (unvisited.contains(neighbor)) {
                            unvisited.remove(neighbor);
                            componentNodes.add(neighbor);
                            queue.add(neighbor);
                        }
                    }
                }

                // 3. 为这个连通分量构建一个新的 Network 实例
                MutableNetwork<Part, Pair<AbstractConnector, SimpleConnector>> splitPartNet =
                        NetworkBuilder.undirected().allowsParallelEdges(true).build();

                for (Part node : componentNodes) {
                    splitPartNet.addNode(node);
                    // 将该节点的所有边加入新网络
                    for (Pair<AbstractConnector, SimpleConnector> edge : partNet.incidentEdges(node)) {
                        EndpointPair<Part> incidentNodes = partNet.incidentNodes(edge);
                        splitPartNet.addEdge(incidentNodes.nodeU(), incidentNodes.nodeV(), edge);
                    }
                }
                splitPartNets.add(splitPartNet);
            }
            return splitPartNets;
        }
    }

    public void setPos(Vec3 pos) {
        Vector3f delta = PhysicsHelperKt.toBVector3f(pos.subtract(this.position));
        if (!inLevel) moveRelatively(delta);
        else SparkLevel.getPhysicsLevel(level).submitImmediateTask(PPhase.PRE, () -> {
            moveRelatively(delta);
            return null;
        });
        this.position = pos;
    }

    private void moveRelatively(Vector3f delta) {
        Transform transform = new Transform();
        for (Part part : partMap.values()) {
            part.rootSubPart.body.getTransform(transform);
            transform.setTranslation(transform.getTranslation().add(delta));
            part.setTransform(transform);
        }
    }

    public void onAddToLevel() {
        // TODO: 使用多线程版本的物理库时，关节的存在会导致崩溃，是因为关节加入世界时刚体尚未加入吗？
        partMap.values().forEach(Part::addToLevel);
        this.inLevel = true;
    }

    public void onRemoveFromLevel() {
        subSystemController.destroy();
        for (Part part : partMap.values()) {
            partNet.removeNode(part);
            part.destroy();
        }
        partMap.clear();
        ObjectManager.clientVehiclesToAdd.remove(this.uuid);
        ObjectManager.serverVehiclesToAdd.remove(this.uuid);
    }

    /**
     * 将载具的所有部件存入一个Map中
     *
     * @return resourceType:部件UUID，value:部件数据
     */
    public Map<String, PartData> getPartData() {
        Map<String, PartData> result = new HashMap<>();
        partNet.nodes().forEach(part -> result.put(part.uuid.toString(), new PartData(part)));
        return result;
    }

    /**
     * 将载具的所有连接关系存入一个List中
     *
     * @return 连接关系列表
     */
    public List<ConnectionData> getConnectionData() {
        List<ConnectionData> result = new ArrayList<>();
        partNet.edges().forEach(connectorPair -> result.add(new ConnectionData(connectorPair)));
        return result;
    }

    public AABB getAABB() {
        float xMin = Float.MAX_VALUE;
        float yMin = Float.MAX_VALUE;
        float zMin = Float.MAX_VALUE;
        float xMax = -Float.MAX_VALUE;
        float yMax = -Float.MAX_VALUE;
        float zMax = -Float.MAX_VALUE;
        Vector3f min = new Vector3f();
        Vector3f max = new Vector3f();
        for (Part part : this.partMap.values()) {
            for (SubPart subPart : part.subParts.values()) {
                PhysicsBodyExtensionKt.stateOf(subPart.body).getCachedBoundingBox().getMin(min);
                PhysicsBodyExtensionKt.stateOf(subPart.body).getCachedBoundingBox().getMax(max);
                if (min.x < xMin) xMin = min.x;
                if (min.y < yMin) yMin = min.y;
                if (min.z < zMin) zMin = min.z;
                if (max.x > xMax) xMax = max.x;
                if (max.y > yMax) yMax = max.y;
                if (max.z > zMax) zMax = max.z;
            }
        }
        return new AABB(xMin, yMin, zMin, xMax, yMax, zMax);
    }

    public void recalculateCameraDistance() {
        Vector3f center = PhysicsHelperKt.toBVector3f(this.position);
        float maxDistance = 4f;
        for (Part part : this.partMap.values()) {
            for (SubPart subPart : part.subParts.values()) {
                float distance = center.distance(subPart.getPosition());
                float radius = subPart.collisionShape.maxRadius() + distance;
                if (radius > maxDistance) maxDistance = radius;
            }
        }
        this.cameraDistance = maxDistance;
    }
}
