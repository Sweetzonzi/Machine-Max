package io.github.tt432.machinemax.common.vehicle;

import com.google.common.graph.MutableNetwork;
import com.google.common.graph.NetworkBuilder;
import com.mojang.datafixers.util.Pair;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.vehicle.connector.AbstractConnector;
import io.github.tt432.machinemax.common.vehicle.connector.AttachPointConnector;
import io.github.tt432.machinemax.common.vehicle.data.ConnectionData;
import io.github.tt432.machinemax.common.vehicle.data.PartData;
import io.github.tt432.machinemax.common.vehicle.data.VehicleData;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

@Getter
public class VehicleCore {
    //存储所有部件的连接关系
    public final MutableNetwork<Part, Pair<AbstractConnector, AttachPointConnector>> partNet = NetworkBuilder.undirected().allowsParallelEdges(true).build();
    //存储所有部件
    public final ConcurrentMap<UUID, Part> partMap = new java.util.concurrent.ConcurrentHashMap<>();
    public String name;
    public final Level level;
    public final UUID uuid;
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

    public VehicleCore(Level level) {
        this.level = level;
        this.uuid = UUID.randomUUID();
    }

    public VehicleCore(Level level, VehicleData savedData) {
        this.level = level;
        this.uuid = UUID.fromString(savedData.uuid);
        this.hp = savedData.hp;
        this.position = savedData.pos;
        //重建部件
        for (PartData partData : savedData.parts.values()) partNet.addNode(new Part(partData, level));
        //TODO:重建连接关系
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

    public VehicleCore addPart(Part part) {
        part.vehicle = this;
        partMap.put(part.uuid, part);
        partNet.addNode(part);
        return this;
    }

    public VehicleCore removePart(Part part) {
        if (partMap.containsValue(part)) {
            part.destroy();
            partNet.removeNode(part);
            partMap.remove(part.uuid, part);
            //TODO:连通性检查
            if (partNet.nodes().isEmpty()) VehicleManager.removeVehicle(this);//如果所有部件都被移除，则销毁载具
        } else MachineMax.LOGGER.error("在载具{}中找不到部件{}，无法移除 ", this.name, part.name);
        return this;
    }

    public VehicleCore removePart(UUID uuid) {
        if (partMap.containsKey(uuid)) {
            Part part = partMap.get(uuid);
            this.removePart(part);
        } else MachineMax.LOGGER.error("在载具{}中找不到部件{}，无法移除 ", this.name, uuid.toString());
        return this;
    }

    public void attachConnector(AbstractConnector connector, AbstractConnector connector2) {
        //TODO:添加连接关系
        if (inLevel) {
            //TODO:发包客户端创建部件与连接关系
        }
    }

    public void detachConnector(AbstractConnector connector, AbstractConnector connector2) {
        //TODO:移除连接关系
        //TODO:连通性检查，如果有部件分离，则创建新的VehicleCore
        //TODO:发包客户端删除部件与连接关系，为分离的部件指定新的VehicleCore
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