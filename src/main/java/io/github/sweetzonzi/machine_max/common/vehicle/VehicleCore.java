package io.github.sweetzonzi.machine_max.common.vehicle;

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
import io.github.sweetzonzi.machine_max.common.vehicle.connector.SimpleConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AdvancedConnector;
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
import io.github.sweetzonzi.machine_max.util.MMMath;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
@EventBusSubscriber(modid = MachineMax.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class VehicleCore {
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
    private boolean structureRemoved = false;
    //属性
    @Setter
    public float hp = 20;//耐久度
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
        this.uuid = rootPart.uuid;
        ObjectManager.initVehicle(this);
        this.addPart(rootPart);
        subSystemController.initAllSubsystems();//子系统初始化
    }

    public VehicleCore(Level level, VehicleData savedData, boolean readAdditionalData) {
        this.level = level;
        this.uuid = UUID.fromString(savedData.uuid);
        this.hp = savedData.hp;
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
                    } else throw new IllegalArgumentException("未在载具中找到连接数据所需的连接点");
                } else throw new IllegalArgumentException("未在载具中找到连接数据所需的部件");
            }
            subSystemController.initAllSubsystems();//子系统初始化
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
        this.uuid = uuid;
        this.name = oldVehicle.name;
        this.position = oldVehicle.position;
        //TODO:调整hp
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
        recalculateCameraDistance();
    }

    /**
     * 主线程tick，默认tps=20
     */
    public void preTick() {
        if (tickCount == 100)
            recalculateCameraDistance();
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
        if (inLoadedChunk && !isRemoved) {//TODO:如果在已加载区块内，或速度大于某个阈值
            if (!loaded) {
                if (loadFromSavedData) {
                    if (tickCount > 100 && !level.isClientSide()) {//等待五秒防止因地形未加载而跌入虚空
                        loaded = true;
                        setKinematic(false);
                    }
                } else {
                    loaded = true;
                }
            }
            subSystemController.tick();
        } else if (this.velocity.length() < 30) {
//            deactivate();//休眠
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
        level.getPhysicsLevel().submitImmediateTask(PPhase.PRE, () -> {
            for (Part part : partMap.values()) part.subParts.values().forEach(subPart -> subPart.body.activate());
            return null;
        });
    }

    public void setGravity(Vector3f gravity) {
        level.getPhysicsLevel().submitImmediateTask(PPhase.PRE, () -> {
            for (Part part : partMap.values())
                part.subParts.values().forEach(subPart -> subPart.body.setGravity(gravity));
            return null;
        });
    }

    public void setKinematic(boolean kinematic) {
        level.getPhysicsLevel().submitImmediateTask(PPhase.PRE, () -> {
            for (Part part : partMap.values())
                part.subParts.values().forEach(subPart -> subPart.body.setKinematic(kinematic));
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
                                new PartRemovePayload(this.uuid, partUuid, serverHandleSpilt(spiltPartNets))
                        );
                    }
                } else {
                    clientHandleSpilt(spiltPartNets, spiltVehicles);
                }
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

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onConnectorAttach(ConnectorAttachEvent.Post event) {
//        MachineMax.LOGGER.debug("收到连接器{}与{}的连接事件", event.getAdvancedConnector().name, event.getSimpleConnector().name);
        AbstractConnector advancedConnector = event.getAdvancedConnector();
        SimpleConnector simpleConnector = event.getSimpleConnector();
        VehicleCore vehicle1 = advancedConnector.getSubPart().getPart().vehicle;
        VehicleCore vehicle2 = simpleConnector.getSubPart().getPart().vehicle;
        if (vehicle1 != vehicle2 && vehicle1 != null && vehicle2 != null) {
            throw new UnsupportedOperationException("暂不支持连接不同载具之间的连接点"); //TODO:支持不同载具之间的连接点链接
        }
        VehicleCore vehicle = vehicle1 != null ? vehicle1 : vehicle2;
        if (vehicle != null) {
            Part part1 = advancedConnector.getSubPart().getPart();
            Part part2 = simpleConnector.getSubPart().getPart();
            vehicle.partMap.put(part1.uuid, part1);
            vehicle.partMap.put(part2.uuid, part2);
//            vehicle.partNet.addEdge(
//                    advancedConnector.getSubPart().getPart(),
//                    simpleConnector.getSubPart().getPart(),
//                    Pair.of(advancedConnector, simpleConnector)
//            );
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onConnectorDetach(ConnectorDetachEvent.Post event) {
//        MachineMax.LOGGER.debug("收到连接器{}与{}的断开事件", event.getAdvancedConnector().name, event.getSimpleConnector().name);
        VehicleCore vehicle1 = event.getAdvancedConnector().getSubPart().part.vehicle;
        VehicleCore vehicle2 = event.getSimpleConnector().getSubPart().part.vehicle;
        if (vehicle1 != vehicle2 && vehicle1 != null && vehicle2 != null) { // 若是不同载具之间的接口断开
            vehicle1.structureRemoved = true;
            vehicle2.structureRemoved = true;
        }
        VehicleCore vehicle = vehicle1 != null ? vehicle1 : vehicle2;
        if (vehicle != null) {
            vehicle.structureRemoved = true;
        }
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
        if (newPart != null && !partMap.containsKey(newPart.uuid) && (connector1.subPart.part == newPart || connector2.subPart.part == newPart))
            this.addPart(newPart);
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
        List<ConnectionData> comboList = new java.util.ArrayList<>(1);
        boolean attached = advancedConnector.attach(simpleConnector);//连接部件
        if (attached) {
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
                                //检查连接是否合理(连接点位置姿态差异)
                                //TODO:同样检查法线是否对齐
                                float posError = MMMath.relPointWorldPos(simpleConnector.offsetFromMassCenter.getTranslation(), simpleConnector.subPart.body).subtract(
                                        MMMath.relPointWorldPos(advancedConnector.offsetFromMassCenter.getTranslation(), advancedConnector.subPart.body)
                                ).length();//计算连接点位置差异
                                float rotError = SparkMathKt.toQuaternionf(
                                        simpleConnector.subPart.body.getPhysicsRotation(null).mult(simpleConnector.offsetFromMassCenter.getRotation()).mult(
                                                advancedConnector.subPart.body.getPhysicsRotation(null).mult(advancedConnector.offsetFromMassCenter.getRotation()).inverse()
                                        )
                                ).angle();//计算连接点姿态差异
                                if (posError < 0.1f && rotError < 1f) {//若位置姿态差异小于阈值，则尝试连接
                                    this.partNet.addEdge(//添加连接关系
                                            advancedConnector.subPart.part,
                                            simpleConnector.subPart.part,
                                            Pair.of(advancedConnector, simpleConnector)
                                    );
                                    advancedConnector.attach(simpleConnector);//连接部件
                                    result.add(new ConnectionData(advancedConnector, simpleConnector));//打包新增连接关系
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
        for (Pair<AbstractConnector, SimpleConnector> connection : connections) {
            connection.getFirst().detach(false);
            this.activate();
            //TODO: 检查单部件多连接时（kluo车门）找不到连接的问题
            boolean removed = partNet.removeEdge(connection);
            if (!removed && connection.getSecond() instanceof SimpleConnector simpleConnector)
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
                        uuid, connectionsToRemove, serverHandleSpilt(spiltPartNets)));
            }
        } else {//客户端行为，仅应被载具断开连接的网络包调用
            clientHandleSpilt(spiltPartNets, spiltVehicles);
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
    private Map<UUID, UUID> serverHandleSpilt(Set<MutableNetwork<Part, Pair<AbstractConnector, SimpleConnector>>> spiltPartNets) {
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
                //TODO: 调整HP
                spiltVehiclesToSend.put(referencePartUuid, newVehicleUuid);
            }

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

    private void clientHandleSpilt(Set<MutableNetwork<Part, Pair<AbstractConnector, SimpleConnector>>> spiltPartNets, Map<UUID, UUID> spiltVehicles) {
        for (Map.Entry<UUID, UUID> entry : spiltVehicles.entrySet()) {
            Part part = partMap.get(entry.getKey());
            UUID spiltVehicleUUID = entry.getValue();
            for (var vehicle : spiltPartNets) {
                if (vehicle.nodes().contains(part)) {
                    //为分离的部件指定新的VehicleCore
                    VehicleCore spiltVehicle = new VehicleCore(level, spiltVehicleUUID, vehicle, this);
                    ObjectManager.addSpiltVehicle(spiltVehicle);
                    break;//处理下一个被分离的部件
                }
            }
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
        else level.getPhysicsLevel().submitImmediateTask(PPhase.PRE, () -> {
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
                float distance = center.distance(subPart.body.getPhysicsLocation(null));
                float radius = subPart.collisionShape.maxRadius() + distance;
                if (radius > maxDistance) maxDistance = radius;
            }
        }
        this.cameraDistance = maxDistance;
    }
}