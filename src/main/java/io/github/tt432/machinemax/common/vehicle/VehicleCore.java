package io.github.tt432.machinemax.common.vehicle;

import cn.solarmoon.spark_core.physics.SparkMathKt;
import com.google.common.graph.MutableNetwork;
import com.google.common.graph.NetworkBuilder;
import com.mojang.datafixers.util.Pair;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.vehicle.connector.AbstractConnector;
import io.github.tt432.machinemax.common.vehicle.connector.AttachPointConnector;
import io.github.tt432.machinemax.common.vehicle.data.ConnectionData;
import io.github.tt432.machinemax.common.vehicle.data.PartData;
import io.github.tt432.machinemax.common.vehicle.data.VehicleData;
import io.github.tt432.machinemax.network.payload.ConnectorAttachPayload;
import io.github.tt432.machinemax.network.payload.PartRemovePayload;
import io.github.tt432.machinemax.util.MMMath;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

@Getter
public class VehicleCore {
    //存储所有部件与连接关系
    public final MutableNetwork<Part, Pair<AbstractConnector, AttachPointConnector>> partNet = NetworkBuilder.undirected().allowsParallelEdges(true).build();
    //存储所有部件
    public final ConcurrentMap<UUID, Part> partMap = new java.util.concurrent.ConcurrentHashMap<>();
    //存储所有连接关系
    public final List<ConnectionData> connectionList = new java.util.ArrayList<>();
    public String name;//载具名称
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
    public ControlMode mode = ControlMode.GROUND;//控制模式
    public enum ControlMode {GROUND, PLANE, SHIP, MECH}

    public VehicleCore(Level level, Part rootPart) {
        this.level = level;
        this.uuid = UUID.randomUUID();
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
        for (ConnectionData connectionData : savedData.connections)
            this.attachConnector(
                    partMap.get(UUID.fromString(connectionData.PartUuidA)).subParts.get(connectionData.SubPartNameA).connectors.get(connectionData.connectorNameA),
                    partMap.get(UUID.fromString(connectionData.PartUuidB)).subParts.get(connectionData.SubPartNameB).connectors.get(connectionData.connectorNameB),
                    null);
    }

    /**
     * 主线程tick，默认tps=20
     */
    public void tick() {
        if (inLoadedChunk) {//TODO:如果在已加载区块内，或速度大于某个阈值
            //保持激活与控制量更新
        } else {
            //休眠
        }
        if (partNet.nodes().isEmpty()) VehicleManager.removeVehicle(this);
        //TODO:从物理线程更新位置
        tickCount++;
    }

    public void physicsTick() {

    }

    /**
     * 将部件添加到载具中
     * 已有载具应使用{@link VehicleCore#attachConnector}方法添加部件并连接部件接口
     *
     * @param part 新部件
     * @see VehicleCore#attachConnector
     */
    protected void addPart(Part part) {
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
            if (!inLevel && !level.isClientSide()) {//发包客户端移除部件
                PacketDistributor.sendToPlayersInDimension(
                        (ServerLevel) this.level,
                        new PartRemovePayload(this.uuid.toString(), partUuid)
                );
            }
            if (partNet.nodes().isEmpty()) VehicleManager.removeVehicle(this);//如果所有部件都被移除，则销毁载具
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
        if (newPart != null && (connector1.subPart.part == newPart || connector2.subPart.part == newPart))
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
        if (newPart != null) comboList = comboAttachConnector(newPart);//检查同部件内是否仍有可连接的接口，如有则连接
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
        partNet.nodes().forEach(node -> node.addToLevel());
        this.inLevel = true;
    }

    public void onRemoveFromLevel() {
        this.position = null;
        partNet.nodes().forEach(part -> {
            part.destroy();
            partNet.removeNode(part);
        });
    }

    /**
     * 将载具的所有部件存入一个Map中
     *
     * @return key:部件UUID，value:部件数据
     */
    public Map<String, PartData> getPartData() {
        Map<String, PartData> result = new java.util.HashMap<>();
        partNet.nodes().forEach(part -> result.put(part.getName(), new PartData(part)));
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