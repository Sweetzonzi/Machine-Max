package io.github.tt432.machinemax.common.vehicle;

import cn.solarmoon.spark_core.physics.SparkMathKt;
import com.google.common.graph.MutableNetwork;
import com.google.common.graph.NetworkBuilder;
import com.jme3.bullet.joints.New6Dof;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.mojang.datafixers.util.Pair;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.entity.MMPartEntity;
import io.github.tt432.machinemax.common.vehicle.connector.AbstractConnector;
import io.github.tt432.machinemax.common.vehicle.connector.AttachPointConnector;
import io.github.tt432.machinemax.common.vehicle.data.ConnectionData;
import io.github.tt432.machinemax.common.vehicle.data.PartData;
import io.github.tt432.machinemax.common.vehicle.data.VehicleData;
import io.github.tt432.machinemax.network.payload.ConnectorAttachPayload;
import io.github.tt432.machinemax.network.payload.PartRemovePayload;
import io.github.tt432.machinemax.network.payload.SubPartSyncPayload;
import io.github.tt432.machinemax.util.MMMath;
import io.github.tt432.machinemax.util.data.PosRotVelVel;
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

@Getter
public class VehicleCore {
    //存储所有部件与连接关系
    public final MutableNetwork<Part, Pair<AbstractConnector, AttachPointConnector>> partNet = NetworkBuilder.undirected().allowsParallelEdges(true).build();
    //存储所有部件
    public final ConcurrentMap<UUID, Part> partMap = new java.util.concurrent.ConcurrentHashMap<>();
    //存储所有连接关系
    public final List<ConnectionData> connectionList = new java.util.ArrayList<>();
    public String name = "Vehicle";//载具名称
    public final Level level;//载具所在世界
    public final UUID uuid;//载具UUID
    @Setter
    private ChunkPos oldChunkPos = new ChunkPos(0, 0);
    public volatile int tickCount = 0;
    public boolean inLevel = false;
    //属性
    @Setter
    public float hp = 20;//耐久度
    @Setter
    public Vec3 position = Vec3.ZERO;//位置
    @Setter
    public boolean inLoadedChunk = false;//是否睡眠
    public boolean isRemoved = false;//是否已被移除
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
        //重建部件
        for (PartData partData : savedData.parts.values()) this.addPart(new Part(partData, level));
        //重建连接关系
        for (ConnectionData connectionData : savedData.connections) {
            try {
                Part partA = partMap.get(UUID.fromString(connectionData.PartUuidA));
                Part partB = partMap.get(UUID.fromString(connectionData.PartUuidB));
                if (partA != null && partB != null) {
                    this.attachConnector(
                            partMap.get(UUID.fromString(connectionData.PartUuidA)).subParts.get(connectionData.SubPartNameA).connectors.get(connectionData.connectorNameA),
                            partMap.get(UUID.fromString(connectionData.PartUuidB)).subParts.get(connectionData.SubPartNameB).connectors.get(connectionData.connectorNameB),
                            null);
                } else throw new IllegalArgumentException("未在载具中找到连接数据所需的部件");
            } catch (IllegalArgumentException e) {
                MachineMax.LOGGER.error("载具{}中存在无效的连接数据，已跳过无效数据。", this.name);
            }
        }
    }

    /**
     * 主线程tick，默认tps=20
     */
    public void tick() {
        if (inLoadedChunk && !isRemoved) {//TODO:如果在已加载区块内，或速度大于某个阈值
            //保持激活与控制量更新
            Vec3 newPos = new Vec3(0, 0, 0);
            for (Part part : partNet.nodes()) {
                Vec3 partPos = SparkMathKt.toVec3(part.rootSubPart.body.getPhysicsLocation(null));
                if (part.entity == null || part.entity.isRemoved()) {
                    if (!level.isClientSide()) refreshPartEntity(part);
                }
                if (part.entity != null) {//更新实体生命值
                    part.entity.setHealth(part.durability);
                }
                newPos = newPos.add(partPos);//计算载具形心位置
            }
            this.position = newPos.scale((double) 1 / partNet.nodes().size());//更新载具形心位置
            if (!level.isClientSide && tickCount % 10 == 0) syncSubParts(null);//同步零件位置姿态速度
        } else {
            //休眠
            for (Part part : partNet.nodes()) {
//                if (part.entity != null) {
//                    part.entity.part = null;
//                    part.entity.remove(Entity.RemovalReason.DISCARDED);
//                }
            }
        }
        if (partNet.nodes().isEmpty() || this.position.y < -1024) VehicleManager.removeVehicle(this);
        tickCount++;
    }

    public void physicsTick() {

    }

    public void refreshPartEntity(Part part) {
        part.entity = new MMPartEntity(level, part);
        part.entity.setHealth(part.durability);
        level.addFreshEntity(part.entity);
    }

    public void syncSubParts(@Nullable HashMap<UUID, HashMap<String, PosRotVelVel>> subPartSyncData) {
        if (!level.isClientSide()) {
            HashMap<UUID, HashMap<String, PosRotVelVel>> subPartSyncDataToSend = new HashMap<>(1);
            for (Map.Entry<UUID, Part> entry : partMap.entrySet()) {
                HashMap<String, PosRotVelVel> subPartSyncDataMap = new HashMap<>(1);
                Part part = entry.getValue();
                for (Map.Entry<String, SubPart> subPartEntry : part.subParts.entrySet()) {
                    SubPart subPart = subPartEntry.getValue();
                    PhysicsRigidBody body = subPart.body;
                    subPartSyncDataMap.put(subPartEntry.getKey(), new PosRotVelVel(
                            body.getPhysicsLocation(null),
                            SparkMathKt.toQuaternionf(body.getPhysicsRotation(null)),
                            body.getLinearVelocity(null),
                            body.getAngularVelocity(null)
                    ));
                }
                subPartSyncDataToSend.put(entry.getKey(), subPartSyncDataMap);
            }
            PacketDistributor.sendToPlayersInDimension((ServerLevel) level, new SubPartSyncPayload(this.uuid, subPartSyncDataToSend));
        } else if (subPartSyncData != null) {
            this.level.getPhysicsLevel().submitTask((a, b) -> {
                for (Map.Entry<UUID, HashMap<String, PosRotVelVel>> outerEntry : subPartSyncData.entrySet()) {
                    UUID partUUID = outerEntry.getKey();
                    Part part = this.partMap.get(partUUID);
                    HashMap<String, PosRotVelVel> innerMap = outerEntry.getValue();
                    if (part != null) {
                        for (Map.Entry<String, PosRotVelVel> innerEntry : innerMap.entrySet()) {
                            String subPartName = innerEntry.getKey();
                            PosRotVelVel data = innerEntry.getValue();
                            SubPart subPart = part.subParts.get(subPartName);
                            if (subPart != null) {
                                PhysicsRigidBody body = subPart.body;
                                body.setPhysicsLocation(data.position());
                                body.setPhysicsRotation(SparkMathKt.toBQuaternion(data.rotation()));
                                body.setLinearVelocity(data.linearVel());
                                body.setAngularVelocity(data.angularVel());
                            } else
                                MachineMax.LOGGER.error("载具{}的部件{}中不存在零件{}，无法同步。", this, partUUID, subPartName);
                        }
                    } else MachineMax.LOGGER.error("载具{}中不存在部件{}，无法同步。", this, partUUID);
                }
                return null;
            });
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
        partMap.put(part.uuid, part);
        partNet.addNode(part);
    }

    public void removePart(Part part) {
        if (partMap.containsValue(part)) {
            String partUuid = part.getUuid().toString();
            part.destroy();
            partNet.removeNode(part);
            partMap.remove(part.uuid, part);
            //TODO:连通性检查
            if (inLevel && !level.isClientSide()) {//发包客户端移除部件
                PacketDistributor.sendToPlayersInDimension(
                        (ServerLevel) this.level,
                        new PartRemovePayload(this.uuid.toString(), partUuid)
                );
            }
            if (partMap.values().isEmpty()) VehicleManager.removeVehicle(this);//如果所有部件都被移除，则销毁载具
            else for(Part p : partMap.values()) p.subParts.values().forEach(subPart -> subPart.body.activate());//重新激活，进行部件移除后的物理计算
        } else MachineMax.LOGGER.error("在载具{}中找不到部件{}，无法移除 ", this.name, part.name);
    }

    public void removePart(UUID uuid) {
        if (partMap.containsKey(uuid)) {
            Part part = partMap.get(uuid);
            this.removePart(part);
        } else MachineMax.LOGGER.error("在载具{}中找不到部件{}，无法移除 ", this.name, uuid.toString());
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
        this.partNet.addEdge(//添加连接关系
                specialConnector.subPart.part,
                attachPoint.subPart.part,
                Pair.of(specialConnector, attachPoint)
        );
        specialConnector.attach(attachPoint);//连接部件
        if (newPart != null) {
            if (!level.isClientSide()) comboList = comboAttachConnector(newPart);//检查同部件内是否仍有可连接的接口，如有则连接
            newPart.addToLevel();//将新部件加入到世界
        }
        if (isInLevel()) specialConnector.addToLevel();//将关节约束加入到世界
        if (inLevel && !level.isClientSide()) {
            comboList.addFirst(new ConnectionData(specialConnector, attachPoint));//打包新增连接关系
            //发包客户端创建连接关系
            PacketDistributor.sendToPlayersInDimension((ServerLevel) this.level, new ConnectorAttachPayload(
                    this.uuid,
                    comboList,
                    newPart != null,
                    newPart == null ? null : new PartData(newPart)
            ));
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
                                float posError = MMMath.RelPointWorldPos(attachPoint.subPartTransform.getTranslation(), attachPoint.subPart.body).subtract(
                                        MMMath.RelPointWorldPos(specialConnector.subPartTransform.getTranslation(), specialConnector.subPart.body)
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

    public void detachConnector(AbstractConnector connector1, AbstractConnector connector2) {
        if (connector2 instanceof AttachPointConnector) connector1.detach(false);
        else if (connector1 instanceof AttachPointConnector) connector2.detach(false);
        else throw new IllegalArgumentException("要拆解的对接口之一必须是AttachPointConnector类型");
        //TODO:连通性检查，如果有部件分离，则创建新的VehicleCore
        //TODO:为分离的部件指定新的VehicleCore
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
        level.getPhysicsLevel().submitTask((a, b) -> {
            joints.forEach(joint -> level.getPhysicsLevel().getWorld().addJoint(joint));
            return null;
        });
        this.inLevel = true;
    }

    public void onRemoveFromLevel() {
        this.position = null;
        for (Part part : partMap.values()) {
            partNet.removeNode(part);
            part.destroy();
        }
        partMap.clear();
    }

    /**
     * 将载具的所有部件存入一个Map中
     *
     * @return key:部件UUID，value:部件数据
     */
    public Map<String, PartData> getPartData() {
        Map<String, PartData> result = new java.util.HashMap<>();
        partNet.nodes().forEach(part -> result.put(part.uuid.toString(), new PartData(part)));
        return result;
    }

    /**
     * 将载具的所有连接关系存入一个List中
     *
     * @return 连接关系列表
     */
    public List<ConnectionData> getConnectionData() {
        List<ConnectionData> result = new java.util.ArrayList<>();
        partNet.edges().forEach(connectorPair -> result.add(new ConnectionData(connectorPair)));
        return result;
    }
}