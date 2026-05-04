package io.github.sweetzonzi.machine_max.common.vehicle;

import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.event.PhysicsLevelInitEvent;
import cn.solarmoon.spark_core.event.PhysicsLevelTickEvent;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.util.PPhase;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.vehicle.data.VehicleData;
import io.github.sweetzonzi.machine_max.common.visual.VisualEffectHelper;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import io.github.sweetzonzi.machine_max.network.payload.assembly.ClientRequestVehicleDataPayload;
import io.github.sweetzonzi.machine_max.network.payload.assembly.LevelVehicleDataPayload;
import io.github.sweetzonzi.machine_max.network.payload.assembly.VehicleCreatePayload;
import io.github.sweetzonzi.machine_max.network.payload.assembly.VehicleRemovePayload;
import io.github.sweetzonzi.machine_max.util.ChunkHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = MachineMax.MOD_ID)
public class ObjectManager {
    public static final Map<Level, Map<UUID, VehicleCore>> levelVehicles = new ConcurrentHashMap<>();
    public static final Map<Level, Map<Integer, DestroyableObject>> levelDestroyableObjects = new ConcurrentHashMap<>();
    public static final Map<UUID, VehicleCore> serverAllVehicles = HashMap.newHashMap(64);
    public static final Map<UUID, VehicleCore> clientAllVehicles = HashMap.newHashMap(64);
    public static final Map<UUID, VehicleCore> serverVehiclesToAdd = new HashMap<>();
    public static final Map<UUID, VehicleCore> clientVehiclesToAdd = new HashMap<>();

    public static void addDestroyableObject(DestroyableObject object) {
        Level level = object.level;
        levelDestroyableObjects.computeIfAbsent(level, k -> new ConcurrentHashMap<>()).put(object.getId(), object);
    }

    public static void removeDestroyableObject(DestroyableObject object) {
        Level level = object.level;
        levelDestroyableObjects.get(level).remove(object.getId());
    }

    public static void removeDestroyableObject(Level level, int id) {
        if (levelDestroyableObjects.containsKey(level)) {
            levelDestroyableObjects.get(level).remove(id);
        } else {
            MachineMax.LOGGER.warn("尝试从维度{}中移除不存在的DestroyableObject: {}", level.dimension().location(), id);
        }
    }

    @Nullable
    public static DestroyableObject getDestroyableObject(Level level, int id) {
        if (levelDestroyableObjects.containsKey(level)) {
            return levelDestroyableObjects.get(level).get(id);
        } else return null;
    }

    public static void initVehicle(VehicleCore vehicle){
        if (vehicle.level.isClientSide()){
            clientVehiclesToAdd.put(vehicle.getUuid(), vehicle);
        } else serverVehiclesToAdd.put(vehicle.getUuid(), vehicle);
    }

    /**
     * 注册VehicleCore到载具管理器中
     * 并将载具添加到相应维度
     *
     * @param vehicle 载具核心
     */
    public static void addVehicle(VehicleCore vehicle) {
        levelVehicles.computeIfAbsent(vehicle.level, k -> new ConcurrentHashMap<>()).put(vehicle.getUuid(), vehicle);
        if (!vehicle.level.isClientSide()) {
            serverAllVehicles.put(vehicle.getUuid(), vehicle);
            serverVehiclesToAdd.remove(vehicle.getUuid());
            saveVehicles((ServerLevel) vehicle.level);//维度内载具发生变更，保存维度载具数据到Level的Attachment
            PacketDistributor.sendToPlayersInDimension(//发包给维度内玩家，在他们的客户端添加载具
                    (ServerLevel) vehicle.level,
                    new VehicleCreatePayload(vehicle.level.dimension(), new VehicleData(vehicle)));
        } else {
            clientVehiclesToAdd.remove(vehicle.getUuid());
            clientAllVehicles.put(vehicle.getUuid(), vehicle);
        }
        vehicle.onAddToLevel();
    }

    /**
     * 将因组装拓扑结构改变而分裂出的载具加入载具管理器中
     *
     * @param vehicle 载具核心
     */
    public static void addSpiltVehicle(VehicleCore vehicle) {
        levelVehicles.computeIfAbsent(vehicle.level, k -> new ConcurrentHashMap<>()).put(vehicle.getUuid(), vehicle);
        if (!vehicle.level.isClientSide()) {
            serverAllVehicles.put(vehicle.getUuid(), vehicle);
            serverVehiclesToAdd.remove(vehicle.getUuid());
            saveVehicles((ServerLevel) vehicle.level);//维度内载具发生变更，保存维度载具数据到Level的Attachment
        } else {
            clientAllVehicles.put(vehicle.getUuid(), vehicle);
            clientVehiclesToAdd.remove(vehicle.getUuid());
        }
        vehicle.inLevel = true;
    }

    /**
     * 从载具管理器中移除VehicleCore
     * 并将载具从相应维度移除
     *
     * @param vehicle 载具核心
     */
    public static void removeVehicle(VehicleCore vehicle) {
        vehicle.isRemoved = true;
        levelVehicles.get(vehicle.level).remove(vehicle.uuid);
        if (!vehicle.level.isClientSide()) {
            serverAllVehicles.remove(vehicle.getUuid());
            saveVehicles((ServerLevel) vehicle.level);//维度内载具发生变更，保存维度载具数据到Level的Attachment
            PacketDistributor.sendToPlayersInDimension(//发包给维度内玩家，通知他们有载具消失
                    (ServerLevel) vehicle.level,
                    new VehicleRemovePayload(vehicle.level.dimension(), vehicle.uuid));
        } else clientAllVehicles.remove(vehicle.getUuid());
        vehicle.onRemoveFromLevel();
    }

    /**
     * 用于载具合并后的被吸收载具移除：
     * 仅从管理器注销，不销毁部件，不广播VehicleRemovePayload
     *
     * @param vehicle 被吸收载具
     * @return 是否成功移除
     */
    public static boolean removeMergedVehicle(VehicleCore vehicle) {
        if (vehicle == null) return false;
        if (!vehicle.partMap.isEmpty() || !vehicle.partNet.nodes().isEmpty()) {
            MachineMax.LOGGER.error("载具{}仍包含部件，拒绝按合并流程移除", vehicle.getUuid());
            return false;
        }

        Map<UUID, VehicleCore> vehiclesInLevel = levelVehicles.get(vehicle.level);
        if (vehiclesInLevel != null) vehiclesInLevel.remove(vehicle.uuid);

        if (!vehicle.level.isClientSide()) {
            serverAllVehicles.remove(vehicle.getUuid());
            serverVehiclesToAdd.remove(vehicle.getUuid());
            saveVehicles((ServerLevel) vehicle.level);
        } else {
            clientAllVehicles.remove(vehicle.getUuid());
            clientVehiclesToAdd.remove(vehicle.getUuid());
        }

        vehicle.isRemoved = true;
        vehicle.inLevel = false;
        vehicle.subSystemController.allSubsystems.clear();
        vehicle.subSystemController.channels.clear();
        vehicle.subSystemController.signalStorage.clear();
        vehicle.subSystemController.resources.clear();
        return true;
    }

    public static int removeAllVehiclesInLevel(Level level) {
        // 获取该Level中的所有物体
        var vehicles = levelVehicles.getOrDefault(level, Map.of()).values();
        // 遍历并移除所有物体
        int i = 0;
        for (VehicleCore vehicle : new HashSet<>(vehicles)) {
            removeVehicle(vehicle);
            i++;
        }
        return i;
    }

    @Nullable
    public static VehicleCore getVehicle(Level level, UUID uuid) {
        var vehicles = levelVehicles.getOrDefault(level, Map.of());
        VehicleCore vehicle = vehicles.get(uuid);
        if (vehicle == null) {
            if (level.isClientSide()) vehicle = clientVehiclesToAdd.get(uuid);
            else vehicle = serverVehiclesToAdd.get(uuid);
        }
        return vehicle;
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onPreTick(LevelTickEvent.Pre event) {
        levelVehicles.computeIfAbsent(event.getLevel(), k -> new ConcurrentHashMap<>()).values().forEach(vehicleCore -> {
            updateVehicleChunk(vehicleCore); // 先更新区块加载状态，确保 preTick 中 inLoadedChunk 已是最新值
            vehicleCore.preTick();
        });
        levelDestroyableObjects.computeIfAbsent(event.getLevel(), k -> new ConcurrentHashMap<>()).values().forEach(DestroyableObject::preTick);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onPostTick(LevelTickEvent.Post event) {
        levelDestroyableObjects.computeIfAbsent(event.getLevel(), k -> new ConcurrentHashMap<>()).values().forEach(DestroyableObject::postTick);
    }

    @SubscribeEvent
    public static void onPrePhysicsTick(PhysicsLevelTickEvent.Pre event) {
        levelVehicles.computeIfAbsent(event.getLevel().getMcLevel(), k -> new ConcurrentHashMap<>()).values().forEach(VehicleCore::prePhysicsTick);
        levelDestroyableObjects.computeIfAbsent(event.getLevel().getMcLevel(), k -> new ConcurrentHashMap<>()).values().forEach(DestroyableObject::prePhysicsTick);
    }

    @SubscribeEvent
    public static void onPostPhysicsTick(PhysicsLevelTickEvent.Post event) {
        levelVehicles.computeIfAbsent(event.getLevel().getMcLevel(), k -> new ConcurrentHashMap<>()).values().forEach(VehicleCore::postPhysicsTick);
        levelDestroyableObjects.computeIfAbsent(event.getLevel().getMcLevel(), k -> new ConcurrentHashMap<>()).values().forEach(DestroyableObject::postPhysicsTick);
    }

    private static void updateVehicleChunk(VehicleCore vehicle) {
        if (vehicle.isRemoved) return;
        Level level = vehicle.getLevel();// 获取载具所在的世界
        ChunkPos chunkPos = ChunkHelper.getChunkPos(vehicle.getPosition());// 使用ChunkHelper计算载具当前位置的区块坐标
        if (!vehicle.getOldChunkPos().equals(chunkPos)) {// 检查载具是否移动到了新的区块
            vehicle.setOldChunkPos(chunkPos);// 更新旧区块坐标为当前区块坐标
        }
        vehicle.setInLoadedChunk(level.getChunkSource().hasChunk(chunkPos.x, chunkPos.z));
    }

    public static void saveVehicles(ServerLevel serverLevel) {
        Set<VehicleData> savedVehicles = new HashSet<>();
        for (VehicleCore vehicle : levelVehicles.getOrDefault(serverLevel, Map.of()).values()) {
            savedVehicles.add(new VehicleData(vehicle));
        }
        serverLevel.setData(MMAttachments.getLEVEL_VEHICLES(), savedVehicles);
    }

    @SubscribeEvent
    public static void transmitVehicleData(PlayerEvent.PlayerLoggedInEvent event) {
        Level level = event.getEntity().level();
        Set<VehicleData> dataToSend = level.getData(MMAttachments.getLEVEL_VEHICLES());
        int packetNum = dataToSend.size();//计算分包数量，每个载具单独一包
        Iterator<VehicleData> iterator = dataToSend.iterator();
        for (int i = 0; i < packetNum; i++) {
            PacketDistributor.sendToPlayer((ServerPlayer) event.getEntity(), new LevelVehicleDataPayload(level.dimension(), iterator.next(), packetNum));
        }
        MachineMax.LOGGER.info("玩家{}登录进入维度{}，发送维度内现有载具数据包", event.getEntity().getName().getString(), level.dimension().location());
    }

    /**
     * 服务端物理世界初始化完成时，或客户端世界接收到载具数据包时
     * 加载载具数据，实体化为载具核心
     *
     * @param level 世界
     */
    public static void loadVehicles(Level level) {
        levelVehicles.computeIfAbsent(level, k -> new ConcurrentHashMap<>()).clear();
        if (level.isClientSide()) {//客户端清空可能的已有载具数据，从服务器获取新维度的载具数据
            MachineMax.LOGGER.info("客户端进入维度{}，清理维度载具数据", level.dimension().location());
            clientAllVehicles.clear();
        }
        Set<VehicleData> savedVehicles = level.getData(MMAttachments.getLEVEL_VEHICLES());
        if (!savedVehicles.isEmpty()) {
            MachineMax.LOGGER.info("正在从维度{}加载{}个载具...", level.dimension().location(), savedVehicles.size());
            int i = 0;
            try {
                for (VehicleData savedVehicleData : savedVehicles) {
                    try {
                        VehicleCore vehicle = new VehicleCore(level, savedVehicleData, true);
                        vehicle.loadFromSavedData = true;
                        addVehicle(vehicle);
                        i++;
                    } catch (Exception e) {
                        MachineMax.LOGGER.error("载具加载失败，出错载具数据：{}", savedVehicleData, e);
                    }
                }
            } catch (Exception e) {
                MachineMax.LOGGER.error("有载具未能成功加载，已跳过出错载具");
            }
            MachineMax.LOGGER.info("已成功从维度{}加载{}个载具", level.dimension().location(), i);
        }
    }

    @SubscribeEvent
    public static void displayCustomPackError(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof Player player) {
            MMDynamicRes.sendErrorToPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onServerStart(ServerAboutToStartEvent event) {
        MMDynamicRes.sendErrorToConsole(event.getServer());
        serverAllVehicles.clear();
        levelVehicles.clear();
    }

    @SubscribeEvent//加载服务端世界时加载载具核心数据
    public static void loadVehicleData(PhysicsLevelInitEvent event) {
        Level level = event.getLevel().getMcLevel();
        PhysicsLevel physicsLevel = event.getLevel();
        SparkLevel.getPhysicsLevel(level).submitImmediateTask(PPhase.PRE, () -> {
            physicsLevel.getWorld().getSolverInfo().setGlobalCfm(1e-5f);
            physicsLevel.getWorld().getSolverInfo().setNumIterations(50);
//            Plane plane = new Plane(Vector3f.UNIT_Y, -59.5f);//测试平面
//            PlaneCollisionShape shape = new PlaneCollisionShape(plane);
//            PhysicsRigidBody body = new PhysicsRigidBody(shape, 0);
//            physicsLevel.getWorld().add(body);
//            CompoundCollisionShape shape = new CompoundCollisionShape();
//            for(int i=0;i<16;i++){
//                for(int j=0;j<1;j++){
//                    for (int k = 0; k < 16; k++) {
//                        shape.addChildShape(new BoxCollisionShape(new Vector3f(0.5f, 0.5f, 0.5f)), new Vector3f(i, j, k));
//                    }
//                }
//            }
//            shape.addChildShape(new BoxCollisionShape(new Vector3f(8f,8f,8f)), new Vector3f());
//            for (int i = -2; i < 3; i++){
//                for (int j = 0; j < 24; j++){
//                    PhysicsRigidBody body = new PhysicsRigidBody(shape, 0);
//                    body.setPhysicsLocation(new Vector3f(i*16, -50, j*16));
//                    physicsLevel.getWorld().add(body);
//                }
//            }
            return null;
        });
        ResourceKey<Level> dimension = level.dimension();
        if (!level.isClientSide()) loadVehicles(level);
        else if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new ClientRequestVehicleDataPayload(dimension));
            MachineMax.LOGGER.info("客户端切换至维度{}，向服务器请求维度内现有载具数据", level.dimension().location());
        }
    }

    @SubscribeEvent//卸载服务端世界时清除相关数据
    public static void unloadVehicleData(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            VisualEffectHelper.attachPoints.clear();
            VisualEffectHelper.boundingBox = null;
            VisualEffectHelper.partToPlace = null;
        }
    }

    @SubscribeEvent//卸载服务端世界时保存载具核心数据
    public static void saveVehicleData(LevelEvent.Save event) {
        Level level = (Level) event.getLevel();
        if (!level.isClientSide()) saveVehicles((ServerLevel) level);
    }

    @SubscribeEvent
    public static void activateVehicle(ChunkEvent.Load event) {
        if (!event.getLevel().isClientSide()) {

        }
    }

    @SubscribeEvent//卸载时保存载具核心数据
    public static void deactivateVehicle(ChunkEvent.Unload event) {
        if (!event.getLevel().isClientSide()) {

        }
    }

    @SubscribeEvent//服务器发送载具核心数据给客户端
    public static void sendVehicleActivateMessage(ChunkWatchEvent.Sent event) {

    }
}
