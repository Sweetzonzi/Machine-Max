package io.github.sweetzonzi.machinemax.common.vehicle;

import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.SparkMathKt;
import cn.solarmoon.spark_core.util.PPhase;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.MutableNetwork;
import com.google.common.graph.NetworkBuilder;
import com.jme3.bullet.joints.New6Dof;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.datafixers.util.Pair;
import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machinemax.common.vehicle.connector.AttachPointConnector;
import io.github.sweetzonzi.machinemax.common.vehicle.connector.SpecialConnector;
import io.github.sweetzonzi.machinemax.common.vehicle.data.ConnectionData;
import io.github.sweetzonzi.machinemax.common.vehicle.data.PartData;
import io.github.sweetzonzi.machinemax.common.vehicle.data.VehicleData;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machinemax.network.payload.assembly.ConnectorAttachPayload;
import io.github.sweetzonzi.machinemax.network.payload.assembly.ConnectorDetachPayload;
import io.github.sweetzonzi.machinemax.network.payload.assembly.PartRemovePayload;
import io.github.sweetzonzi.machinemax.network.payload.SubPartSyncPayload;
import io.github.sweetzonzi.machinemax.util.MMMath;
import io.github.sweetzonzi.machinemax.util.data.PosRotVelVel;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
public class VehicleCore {
    //存储所有部件与连接关系
    public final MutableNetwork<Part, Pair<AbstractConnector, AttachPointConnector>> partNet = NetworkBuilder.undirected().allowsParallelEdges(true).build();
    //存储所有部件
    public final ConcurrentMap<UUID, Part> partMap = new java.util.concurrent.ConcurrentHashMap<>();
    public String name = "Vehicle";//载具名称
    public final Level level;//载具所在世界
    @Setter
    public UUID uuid;//载具UUID
    @Setter
    private ChunkPos oldChunkPos = new ChunkPos(0, 0);
    public int tickCount = 0;
    public boolean inLevel = false;
    //属性
    @Setter
    public float hp = 20;//耐久度
    private Vec3 position = Vec3.ZERO;//位置
    private Vec3 velocity = Vec3.ZERO;//速度
    public float totalMass = 0;//总质量
    public int syncCountDown = 5;//同步倒计时
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

    public VehicleCore(Level level, Part rootPart) {
        this.level = level;
        this.uuid = rootPart.uuid;
        this.addPart(rootPart);
    }

    public VehicleCore(Level level, VehicleData savedData) {
        this.level = level;
        this.uuid = UUID.fromString(savedData.uuid);
        this.hp = savedData.hp;
        this.position = savedData.pos;
        this.name = savedData.name;
        try {
            //重建部件
            for (PartData partData : savedData.parts.values()) this.addPart(new Part(partData, level));
            //重建连接关系
            for (ConnectionData connectionData : savedData.connections) {
                Part partA = partMap.get(UUID.fromString(connectionData.PartUuidS));
                Part partB = partMap.get(UUID.fromString(connectionData.PartUuidA));
                if (partA != null && partB != null) {
                    this.attachConnector(
                            partMap.get(UUID.fromString(connectionData.PartUuidS)).subParts.get(connectionData.SubPartNameS).connectors.get(connectionData.SpecialConnectorName),
                            partMap.get(UUID.fromString(connectionData.PartUuidA)).subParts.get(connectionData.SubPartNameA).connectors.get(connectionData.AttachPointConnectorName),
                            null);
                } else throw new IllegalArgumentException("未在载具中找到连接数据所需的部件");
            }
        } catch (Exception e) {
            onRemoveFromLevel();//移除数据出错的载具
            throw e;
        }
    }

    /**
     * <p>载具因拓扑结构发生变化而分裂为多个部分时使用的构造方法</p>
     * <p>Method used to create a new vehicle when the topology of the vehicle changes and splits into multiple parts</p>
     *
     * @param uuid    新载具的UUID UUID of the new Vehicle
     * @param partNet 新载具的拓扑结构 Structure of the new Vehicle
     */
    public VehicleCore(Level level, UUID uuid, MutableNetwork<Part, Pair<AbstractConnector, AttachPointConnector>> partNet, VehicleCore oldVehicle) {
        this.level = level;
        this.uuid = uuid;
        this.name = oldVehicle.name;
        this.position = oldVehicle.position;
        //TODO:调整hp
        for (Part part : partNet.nodes()) {
            oldVehicle.subSystemController.removeSubsystems(part.subsystems.values(), true);
            oldVehicle.partMap.remove(part.uuid);
            oldVehicle.partNet.removeNode(part);
            this.partMap.put(part.uuid, part);
            this.partNet.addNode(part);
            part.vehicle = this;
            this.subSystemController.addSubsystems(part.subsystems.values(), false);
        }
        for (Pair<AbstractConnector, AttachPointConnector> edge : partNet.edges()) {
            EndpointPair<Part> connectedParts = partNet.incidentNodes(edge);
            this.partNet.addEdge(connectedParts, edge);
        }
        this.updateTotalMass();
        this.subSystemController.onVehicleStructureChanged();
    }

    /**
     * 主线程tick，默认tps=20
     */
    public void tick() {
        //保持激活与控制量更新
        Vec3 newPos = new Vec3(0, 0, 0);
        Vec3 newVel = new Vec3(0, 0, 0);
        for (Part part : partMap.values()) {
            Vec3 partPos = SparkMathKt.toVec3(part.rootSubPart.body.getPhysicsLocation(null));
            Vec3 partVel = SparkMathKt.toVec3(part.rootSubPart.body.getLinearVelocity(null));
            newPos = newPos.add(partPos);//计算载具形心位置
            newVel = newVel.add(partVel);//计算载具形心速度
            if (inLoadedChunk && !isRemoved) part.onTick();
        }
        this.position = newPos.scale((double) 1 / partMap.values().size());//更新载具形心位置
        this.velocity = newVel.scale((double) 1 / partMap.values().size());//更新载具形心速度
        if (partMap.values().isEmpty() || this.position.y < -1024) {
            VehicleManager.removeVehicle(this);//移除掉出世界的载具
            return;
        }
        if (inLoadedChunk && !isRemoved) {//TODO:如果在已加载区块内，或速度大于某个阈值
            if (!loaded) {
                if (loadFromSavedData) {
                    if (tickCount > 100) {//等待五秒防止因地形未加载而跌入虚空
                        loaded = true;
                        setKinematic(false);
                    }
                } else {
                    loaded = true;
                }
            }
            if (!level.isClientSide && syncCountDown <= 0) {
                syncSubParts(null);//同步零件位置姿态速度
                syncCountDown = Math.max((int) (40 * Math.pow(2, -0.1 * velocity.length())), 2);//速度越大，同步冷却时间越短
            }
            subSystemController.tick();
        } else if (this.velocity.length() < 30) {
//            deactivate();//休眠
        }
        tickCount++;
        if (syncCountDown > 0) syncCountDown--;
    }

    public void prePhysicsTick() {
        subSystemController.prePhysicsTick();
        for (Part part : partMap.values()) {
            part.onPrePhysicsTick();
        }
    }

    public void postPhysicsTick() {
        subSystemController.postPhysicsTick();
        for (Part part : partMap.values()) {
            part.onPostPhysicsTick();
        }
    }

    public void syncSubParts(@Nullable HashMap<UUID, HashMap<String, Pair<PosRotVelVel, Boolean>>> subPartSyncData) {
        if (!level.isClientSide()) {
            HashMap<UUID, HashMap<String, Pair<PosRotVelVel, Boolean>>> subPartSyncDataToSend = new HashMap<>(1);
            for (Map.Entry<UUID, Part> entry : partMap.entrySet()) {
                HashMap<String, Pair<PosRotVelVel, Boolean>> subPartSyncDataMap = new HashMap<>(1);
                Part part = entry.getValue();
                for (Map.Entry<String, SubPart> subPartEntry : part.subParts.entrySet()) {
                    SubPart subPart = subPartEntry.getValue();
                    PhysicsRigidBody body = subPart.body;
                    boolean isSleep = !body.isActive();
                    PosRotVelVel data = new PosRotVelVel(
                            body.getPhysicsLocation(null),
                            SparkMathKt.toQuaternionf(body.getPhysicsRotation(null)),
                            body.getLinearVelocity(null),
                            body.getAngularVelocity(null));
                    subPartSyncDataMap.put(subPartEntry.getKey(), Pair.of(data, isSleep));
                }
                subPartSyncDataToSend.put(entry.getKey(), subPartSyncDataMap);
            }
            if (!subPartSyncDataToSend.isEmpty()) {
                PacketDistributor.sendToPlayersInDimension((ServerLevel) level, new SubPartSyncPayload(this.uuid, subPartSyncDataToSend));
            }
        } else if (subPartSyncData != null) {
            this.level.getPhysicsLevel().submitImmediateTask(PPhase.PRE, () -> {
                for (Map.Entry<UUID, HashMap<String, Pair<PosRotVelVel, Boolean>>> outerEntry : subPartSyncData.entrySet()) {
                    UUID partUUID = outerEntry.getKey();
                    Part part = this.partMap.get(partUUID);
                    HashMap<String, Pair<PosRotVelVel, Boolean>> innerMap = outerEntry.getValue();
                    if (part != null) {
                        for (Map.Entry<String, Pair<PosRotVelVel, Boolean>> innerEntry : innerMap.entrySet()) {
                            String subPartName = innerEntry.getKey();
                            PosRotVelVel data = innerEntry.getValue().getFirst();
                            boolean isSleep = innerEntry.getValue().getSecond();
                            SubPart subPart = part.subParts.get(subPartName);
                            if (subPart != null) {
                                PhysicsRigidBody body = subPart.body;
                                if (!body.isActive() && isSleep) continue;//如果零件已休眠，则跳过此零件的同步
                                body.setPhysicsLocation(data.position());
                                body.setPhysicsRotation(SparkMathKt.toBQuaternion(data.rotation()));
                                body.setLinearVelocity(data.linearVel());
                                body.setAngularVelocity(data.angularVel());
                                if (isSleep) body.forceDeactivate();
                                else body.activate();
                            } else
                                MachineMax.LOGGER.error("载具{}的部件{}中不存在零件{}，无法同步。", this.name, partUUID, subPartName);
                        }
                    } else MachineMax.LOGGER.error("载具{}中不存在部件{}，无法同步。", this.name, partUUID);
                }
                return null;
            });
        }
    }

    /**
     * 激活载具所有零件的运动体
     */
    public void activate() {
        syncCountDown = 0;
        level.getPhysicsLevel().submitImmediateTask(PPhase.PRE, () -> {
            for (Part part : partMap.values()) part.subParts.values().forEach(subPart -> subPart.body.activate());
            return null;
        });
    }

    /**
     * 令载具所有零件的运动体休眠
     */
    public void deactivate() {
        level.getPhysicsLevel().submitImmediateTask(PPhase.POST, () -> {
            for (Part part : partMap.values())
                part.subParts.values().forEach(subPart -> subPart.body.forceDeactivate());
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
        for (AbstractSubsystem subSystem : part.subsystems.values()) {//连接部件内子系统的信号传输关系
            subSystem.setTargetFromNames();
        }
        for (AbstractConnector connector : part.allConnectors.values()) {//连接部件内信号端口的传输关系
            if (connector.signalPort != null) connector.signalPort.setTargetFromNames();
        }
        for (SubPart subPart : part.subParts.values()) {//连接部件内交互判定区的信号传输关系
            if (subPart.interactBoxes != null)
                for (InteractBox interactBox : subPart.interactBoxes.values()) {
                    interactBox.setTargetFromNames();
                }
        }
        this.updateTotalMass();
        partMap.put(part.uuid, part);
        partNet.addNode(part);
        subSystemController.addSubsystems(part.subsystems.values(), true);
    }

    public void removePart(Part part) {
        removePart(part, Map.of());
    }

    public void removePart(Part part, Map<UUID, UUID> spiltVehicles) {
        if (partMap.containsValue(part)) {
            UUID partUuid = part.getUuid();
            subSystemController.removeSubsystems(part.subsystems.values(), true);
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
            if (partMap.values().isEmpty()) VehicleManager.removeVehicle(this);//如果所有部件都被移除，则销毁载具
            else {
                this.activate();//重新激活，进行部件移除后的物理计算
                this.subSystemController.onVehicleStructureChanged();//通知子系统载具结构更新
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
        if (newPart != null && !partMap.containsKey(newPart.uuid) && (connector1.subPart.part == newPart || connector2.subPart.part == newPart))
            this.addPart(newPart);
        if (connector1.subPart.part == connector2.subPart.part)
            throw new IllegalArgumentException("不能连接同一个部件内的接口");
        AbstractConnector specialConnector;
        AttachPointConnector attachPoint;
        if (connector2 instanceof AttachPointConnector) {
            attachPoint = (AttachPointConnector) connector2;
            specialConnector = connector1;
        } else if (connector1 instanceof AttachPointConnector) {
            attachPoint = (AttachPointConnector) connector1;
            specialConnector = connector2;
        } else throw new IllegalArgumentException("对接口之一必须是AttachPointConnector类型");
        List<ConnectionData> comboList = new java.util.ArrayList<>(1);
        boolean attached = specialConnector.attach(attachPoint);//连接部件
        if (attached) {
            this.partNet.addEdge(//添加连接关系
                    specialConnector.subPart.part,
                    attachPoint.subPart.part,
                    Pair.of(specialConnector, attachPoint)
            );
            if (newPart != null) {
                if (!level.isClientSide()) comboList = comboAttachConnector(newPart);//检查同部件内是否仍有可连接的接口，如有则连接
                newPart.addToLevel();//将新部件加入到世界
            }
            if (isInLevel()) specialConnector.addToLevel();//将关节约束加入到世界
            this.subSystemController.onVehicleStructureChanged();//通知子系统载具结构更新
            this.activate();
            if (inLevel && !level.isClientSide()) {
                comboList.addFirst(new ConnectionData(specialConnector, attachPoint));//特殊对接口在前面，以保证对接口属性得到正确应用
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
                                AbstractConnector specialConnector;
                                AttachPointConnector attachPoint;
                                if (connector2 instanceof AttachPointConnector) {
                                    attachPoint = (AttachPointConnector) connector2;
                                    specialConnector = connector1;
                                } else if (connector1 instanceof AttachPointConnector) {
                                    attachPoint = (AttachPointConnector) connector1;
                                    specialConnector = connector2;
                                } else continue;//二者中存在AttachPointConnector时才可尝试连接
                                //检查连接是否合理(连接点位置姿态差异)
                                float posError = MMMath.relPointWorldPos(attachPoint.subPartTransform.getTranslation(), attachPoint.subPart.body).subtract(
                                        MMMath.relPointWorldPos(specialConnector.subPartTransform.getTranslation(), specialConnector.subPart.body)
                                ).length();//计算连接点位置差异
                                float rotError = SparkMathKt.toQuaternionf(
                                        attachPoint.subPart.body.getPhysicsRotation(null).mult(attachPoint.subPartTransform.getRotation()).mult(
                                                specialConnector.subPart.body.getPhysicsRotation(null).mult(specialConnector.subPartTransform.getRotation()).inverse()
                                        )
                                ).angle();//计算连接点姿态差异
                                if (posError < 0.1f && rotError < 1f) {//若位置姿态差异小于阈值，则尝试连接
                                    this.partNet.addEdge(//添加连接关系
                                            specialConnector.subPart.part,
                                            attachPoint.subPart.part,
                                            Pair.of(specialConnector, attachPoint)
                                    );
                                    specialConnector.attach(attachPoint);//连接部件
                                    result.add(new ConnectionData(specialConnector, attachPoint));//打包新增连接关系
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
        List<Pair<AbstractConnector, AttachPointConnector>> connections = new ArrayList<>();
        for (AbstractConnector connector : connectors) {
            if (connector.subPart.part.vehicle == this) {
                if (!connector.internal) {//若是与外部部件连接的接口，则需要移除载具核心中记录的连接关系
                    if (connector.hasPart()) {
                        if (connector instanceof SpecialConnector)
                            connections.add(Pair.of(connector, (AttachPointConnector) connector.attachedConnector));
                        else connections.add(Pair.of(connector.attachedConnector, (AttachPointConnector) connector));
                    } else
                        MachineMax.LOGGER.warn("载具{}的接口{}未连接到任何部件，无法断开连接", this.name, connector.name);
                } else MachineMax.LOGGER.warn("载具{}的接口{}为内部接口，无法断开连接", this.name, connector.name);
            } else MachineMax.LOGGER.error("接口{}不属于载具{}，无法断开连接", connector.name, this.name);
        }
        detachConnections(connections);
    }

    private void detachConnections(List<Pair<AbstractConnector, AttachPointConnector>> connections) {
        detachConnections(connections, Map.of());
    }

    /**
     * <p>断开载具中指定的连接关系并检查连通性，若分裂为多个部分则相应创建新载具</p>
     * <p>Detach the specified connection relationship in the vehicle and check connectivity. If the connectivity is split into multiple parts, new vehicles are created accordingly.</p>
     *
     * @param connections
     * @param spiltVehicles
     */
    public void detachConnections(List<Pair<AbstractConnector, AttachPointConnector>> connections, Map<UUID, UUID> spiltVehicles) {
        List<ConnectionData> connectionsToRemove = new ArrayList<>();
        for (Pair<AbstractConnector, AttachPointConnector> connection : connections) {
            connection.getFirst().detach(false);
            boolean removed = partNet.removeEdge(connection);
            if (!removed && connection.getSecond() instanceof AttachPointConnector attachPoint)
                removed = partNet.removeEdge(Pair.of(connection.getSecond(), attachPoint));
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
    }

    private Map<UUID, UUID> serverHandleSpilt(Set<MutableNetwork<Part, Pair<AbstractConnector, AttachPointConnector>>> spiltPartNets) {
        Map<UUID, UUID> spiltVehiclesToSend = new HashMap<>();
        Iterator<MutableNetwork<Part, Pair<AbstractConnector, AttachPointConnector>>> iterator = spiltPartNets.iterator();
        while (iterator.hasNext()) {
            MutableNetwork<Part, Pair<AbstractConnector, AttachPointConnector>> network = iterator.next();
            UUID uuid = UUID.randomUUID();
            //最后一个网络视作此载具本身，不参与分裂
            if (iterator.hasNext()) {
                VehicleCore newVehicle = new VehicleCore(level, uuid, network, this);
                VehicleManager.addSpiltVehicle(newVehicle);
                spiltVehiclesToSend.put(network.nodes().iterator().next().uuid, uuid);
            }
        }
        //TODO:调整hp
        return spiltVehiclesToSend;
    }

    private void clientHandleSpilt(Set<MutableNetwork<Part, Pair<AbstractConnector, AttachPointConnector>>> spiltPartNets, Map<UUID, UUID> spiltVehicles) {
        for (Map.Entry<UUID, UUID> entry : spiltVehicles.entrySet()) {
            Part part = partMap.get(entry.getKey());
            UUID spiltVehicleUUID = entry.getValue();
            for (var vehicle : spiltPartNets) {
                if (vehicle.nodes().contains(part)) {
                    //为分离的部件指定新的VehicleCore
                    VehicleCore spiltVehicle = new VehicleCore(level, spiltVehicleUUID, vehicle, this);
                    VehicleManager.addSpiltVehicle(spiltVehicle);
                    break;//处理下一个被分离的部件
                }
            }
        }
    }

    /**
     * <p>获取载具部件连接关系图的所有联通子图的集合，用于连通性检查</p>
     * <p>Get all the connected sub-graphs of the vehicle part connection graph, used for connectivity check.</p>
     *
     * @return 连通子图集合 Subgraph set
     */
    public Set<MutableNetwork<Part, Pair<AbstractConnector, AttachPointConnector>>> partNetSpiltCheck() {
        if (partNet.nodes().isEmpty()) return Set.of();
        Set<MutableNetwork<Part, Pair<AbstractConnector, AttachPointConnector>>> splitPartNets = new HashSet<>();
        Set<Part> visitedParts = new HashSet<>();
        for (Part part : partMap.values()) {
            if (!visitedParts.contains(part)) {
                Set<Part> spiltParts = partNet.adjacentNodes(part);//寻找连通子图所有的节点
                HashSet<Part> parts = new HashSet<>(spiltParts);
                parts.add(part);
                MutableNetwork<Part, Pair<AbstractConnector, AttachPointConnector>> splitPartNet = NetworkBuilder.undirected().allowsParallelEdges(true).build();
                for (Part splitPart : parts) {
                    splitPartNet.addNode(splitPart); // 构建网络节点
                    for (Pair<AbstractConnector, AttachPointConnector> edge : partNet.incidentEdges(splitPart)) {
                        EndpointPair<Part> incidentNodes = partNet.incidentNodes(edge);
                        Part nodeU = incidentNodes.nodeU();
                        Part nodeV = incidentNodes.nodeV();
                        splitPartNet.addEdge(nodeU, nodeV, edge); // 构建网络边
                    }
                }
                splitPartNets.add(splitPartNet);
                visitedParts.addAll(parts);
            }
        }
        return splitPartNets;
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
        Set<New6Dof> joints = new HashSet<>();
        Set<Part> parts = new HashSet<>(partMap.values());
        partMap.values().forEach(node -> {
            for (SubPart subPart : node.subParts.values()) {
                for (AbstractConnector connector : subPart.connectors.values()) {
                    if (connector.hasPart()) joints.add(connector.joint);
                }
            }
        });
        parts.forEach(Part::addToLevel);
        level.getPhysicsLevel().submitImmediateTask(PPhase.PRE, () -> {
            joints.forEach(joint -> level.getPhysicsLevel().getWorld().addJoint(joint));
            return null;
        });
        this.inLevel = true;
    }

    public void onRemoveFromLevel() {
        this.position = null;
        subSystemController.destroy();
        for (Part part : partMap.values()) {
            partNet.removeNode(part);
            part.destroy();
        }
        partMap.clear();
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
}