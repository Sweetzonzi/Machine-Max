package io.github.tt432.machinemax.common.vehicle;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.registry.MMAttachments;
import io.github.tt432.machinemax.common.registry.MMRegistries;
import io.github.tt432.machinemax.common.vehicle.data.VehicleData;
import io.github.tt432.machinemax.network.payload.ClientRequestVehicleDataPayload;
import io.github.tt432.machinemax.network.payload.LevelVehicleDataPayload;
import io.github.tt432.machinemax.network.payload.VehicleCreatePayload;
import io.github.tt432.machinemax.network.payload.VehicleRemovePayload;
import io.github.tt432.machinemax.util.ChunkHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = MachineMax.MOD_ID)
public class VehicleManager {
    //    private static final Map<Level, Map<ChunkPos, Set<VehicleCore>>> levelChunkMapping = new ConcurrentHashMap<>();
    public static final Map<Level, Set<VehicleCore>> levelVehicles = new ConcurrentHashMap<>();
    public static final Map<UUID, VehicleCore> allVehicles = HashMap.newHashMap(64);

    /**
     * 注册VehicleCore到载具管理器中
     * 并将载具添加到相应维度
     *
     * @param vehicle 载具核心
     */
    public static void addVehicle(VehicleCore vehicle) {
//        ChunkPos chunkPos = ChunkHelper.getChunkPos(partData.getPosition());
//        // 获取或创建以Level为键的Map项
//        levelChunkMapping.computeIfAbsent(level, k -> new ConcurrentHashMap<>());
//        // 获取或创建以ChunkPos为键的Set项，并将VehicleCore添加到其中
//        levelChunkMapping.get(level).computeIfAbsent(chunkPos, k -> ConcurrentHashMap.newKeySet()).add(partData);
        levelVehicles.computeIfAbsent(vehicle.level, k -> ConcurrentHashMap.newKeySet()).add(vehicle);
        allVehicles.put(vehicle.getUuid(), vehicle);
        vehicle.onAddToLevel();
        if (!vehicle.level.isClientSide()) {
            saveVehicles((ServerLevel) vehicle.level);//维度内载具发生变更，保存维度载具数据到Level的Attachment
            PacketDistributor.sendToPlayersInDimension(//发包给维度内玩家，在他们的客户端添加载具
                    (ServerLevel) vehicle.level,
                    new VehicleCreatePayload(vehicle.level.dimension(), new VehicleData(vehicle)));
        }
    }

    /**
     * 从载具管理器中移除VehicleCore
     * 并将载具从相应维度移除
     *
     * @param vehicle 载具核心
     */
    public static void removeVehicle(VehicleCore vehicle) {
        levelVehicles.get(vehicle.level).remove(vehicle);
        allVehicles.remove(vehicle.getUuid());
        vehicle.onRemoveFromLevel();
        if (!vehicle.level.isClientSide()) {
            saveVehicles((ServerLevel) vehicle.level);//维度内载具发生变更，保存维度载具数据到Level的Attachment
            PacketDistributor.sendToPlayersInDimension(//发包给维度内玩家，通知他们有载具消失
                    (ServerLevel) vehicle.level,
                    new VehicleRemovePayload(vehicle.level.dimension(), vehicle.uuid.toString()));
        }
    }

    @SubscribeEvent
    public static void onTick(LevelTickEvent.Post event) {
        levelVehicles.computeIfAbsent(event.getLevel(), k -> ConcurrentHashMap.newKeySet()).forEach(vehicleCore -> {
            vehicleCore.tick();
            updateVehicleChunk(vehicleCore);
        });
    }

    @SubscribeEvent
    public static void onPhysicsTick(LevelTickEvent.Post event) {
        //TODO:改成物理LevelTickEvent
        levelVehicles.computeIfAbsent(event.getLevel(), k -> ConcurrentHashMap.newKeySet()).forEach(VehicleCore::physicsTick);
    }

    private static void updateVehicleChunk(VehicleCore vehicle) {
        Level level = vehicle.getLevel();// 获取载具所在的世界
        ChunkPos chunkPos = ChunkHelper.getChunkPos(vehicle.getPosition());// 使用ChunkHelper计算载具当前位置的区块坐标
        if (!vehicle.getOldChunkPos().equals(chunkPos)) {// 检查载具是否移动到了新的区块
//            // 从旧区块中移除载具
//            levelChunkMapping.get(level).get(partData.getOldChunkPos()).remove(partData);
//            // 将车辆添加到新的区块中，如果新的区块不在表中则创建一个
//            levelChunkMapping.get(level).computeIfAbsent(chunkPos, k -> ConcurrentHashMap.newKeySet()).add(partData);
            vehicle.setOldChunkPos(chunkPos);// 更新旧区块坐标为当前区块坐标
        }
        vehicle.setInLoadedChunk(level.getChunkSource().hasChunk(chunkPos.x, chunkPos.z));
    }

    public static void saveVehicles(ServerLevel serverLevel) {
        Set<VehicleData> savedVehicles = new HashSet<>();
        for (VehicleCore vehicle : levelVehicles.get(serverLevel)) {
            savedVehicles.add(new VehicleData(vehicle));
        }
        serverLevel.setData(MMAttachments.getLEVEL_VEHICLES(), savedVehicles);
    }

    @SubscribeEvent
    public static void transmitVehicleData(PlayerEvent.PlayerLoggedInEvent event) {
        Level level = event.getEntity().level();
        PacketDistributor.sendToPlayer((ServerPlayer) event.getEntity(), new LevelVehicleDataPayload(level.dimension(), level.getData(MMAttachments.getLEVEL_VEHICLES())));
        MachineMax.LOGGER.info("玩家{}登录进入维度{}，发送维度内现有载具数据包", event.getEntity().getName().getString(), level.dimension().location());
    }

    /**
     * 服务端世界加载时，或客户端世界接收到载具数据包时
     * 加载载具数据，实体化为载具核心
     *
     * @param level 世界
     */
    public static void loadVehicles(Level level) {
        if (level.isClientSide()) {//客户端清空可能的已有载具数据，从服务器获取新维度的载具数据
            levelVehicles.clear();
            allVehicles.clear();
        }
        Set<VehicleData> savedVehicles = level.getData(MMAttachments.getLEVEL_VEHICLES());
        for (VehicleData savedVehicleData : savedVehicles) {
            VehicleCore vehicle = new VehicleCore(level, savedVehicleData);
            addVehicle(vehicle);
        }
        MachineMax.LOGGER.info("已从维度{}加载{}个载具", level.dimension().location(), savedVehicles.size());
    }

    @SubscribeEvent//加载服务端世界时加载载具核心数据
    public static void loadVehicleData(LevelEvent.Load event) {
        Level level = (Level) event.getLevel();
        ResourceKey<Level> dimension = level.dimension();
        if (!level.isClientSide()) loadVehicles(level);
        else if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new ClientRequestVehicleDataPayload(dimension));
            MachineMax.LOGGER.info("客户端进入维度{}，向服务器请求维度内现有载具数据", level.dimension().location());
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
