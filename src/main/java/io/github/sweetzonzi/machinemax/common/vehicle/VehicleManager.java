package io.github.sweetzonzi.machinemax.common.vehicle;

import cn.solarmoon.spark_core.event.PhysicsLevelTickEvent;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.util.PPhase;
import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.common.registry.MMAttachments;
import io.github.sweetzonzi.machinemax.common.registry.MMVisualEffects;
import io.github.sweetzonzi.machinemax.common.vehicle.data.VehicleData;
import io.github.sweetzonzi.machinemax.external.js.hook.Hook;
import io.github.sweetzonzi.machinemax.network.payload.assembly.ClientRequestVehicleDataPayload;
import io.github.sweetzonzi.machinemax.network.payload.assembly.LevelVehicleDataPayload;
import io.github.sweetzonzi.machinemax.network.payload.assembly.VehicleCreatePayload;
import io.github.sweetzonzi.machinemax.network.payload.assembly.VehicleRemovePayload;
import io.github.sweetzonzi.machinemax.util.ChunkHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
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
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static io.github.sweetzonzi.machinemax.external.js.MMInitialJS.JS_RUNNER;
import static io.github.sweetzonzi.machinemax.external.js.MMInitialJS.JS_SCOPE;

@EventBusSubscriber(modid = MachineMax.MOD_ID)
public class VehicleManager {
    public static final Map<Level, Set<VehicleCore>> levelVehicles = new ConcurrentHashMap<>();
    public static final Map<UUID, VehicleCore> serverAllVehicles = HashMap.newHashMap(64);
    public static final Map<UUID, VehicleCore> clientAllVehicles = HashMap.newHashMap(64);

    /**
     * 注册VehicleCore到载具管理器中
     * 并将载具添加到相应维度
     *
     * @param vehicle 载具核心
     */
    public static void addVehicle(VehicleCore vehicle) {
        levelVehicles.computeIfAbsent(vehicle.level, k -> ConcurrentHashMap.newKeySet()).add(vehicle);
        if (!vehicle.level.isClientSide()) {
            serverAllVehicles.put(vehicle.getUuid(), vehicle);
            saveVehicles((ServerLevel) vehicle.level);//维度内载具发生变更，保存维度载具数据到Level的Attachment
            PacketDistributor.sendToPlayersInDimension(//发包给维度内玩家，在他们的客户端添加载具
                    (ServerLevel) vehicle.level,
                    new VehicleCreatePayload(vehicle.level.dimension(), new VehicleData(vehicle)));
        } else clientAllVehicles.put(vehicle.getUuid(), vehicle);
        vehicle.onAddToLevel();
    }

    /**
     * 从载具管理器中移除VehicleCore
     * 并将载具从相应维度移除
     *
     * @param vehicle 载具核心
     */
    public static void removeVehicle(VehicleCore vehicle) {
        vehicle.isRemoved = true;
        levelVehicles.get(vehicle.level).remove(vehicle);
        if (!vehicle.level.isClientSide()) {
            serverAllVehicles.remove(vehicle.getUuid());
            saveVehicles((ServerLevel) vehicle.level);//维度内载具发生变更，保存维度载具数据到Level的Attachment
            PacketDistributor.sendToPlayersInDimension(//发包给维度内玩家，通知他们有载具消失
                    (ServerLevel) vehicle.level,
                    new VehicleRemovePayload(vehicle.level.dimension(), vehicle.uuid.toString()));
        } else clientAllVehicles.remove(vehicle.getUuid());
        vehicle.onRemoveFromLevel();
    }

    public static int removeAllVehiclesInLevel(Level level) {
        // 获取该Level中的所有物体
        Set<VehicleCore> vehicles = levelVehicles.getOrDefault(level, Set.of());
        // 遍历并移除所有物体
        int i = 0;
        for (VehicleCore vehicle : new HashSet<>(vehicles)) {
            removeVehicle(vehicle);
            i++;
        }
        return i;
    }

    @SubscribeEvent
    public static void onTick(LevelTickEvent.Post event) {
        levelVehicles.computeIfAbsent(event.getLevel(), k -> ConcurrentHashMap.newKeySet()).forEach(vehicleCore -> {
            vehicleCore.tick();
            updateVehicleChunk(vehicleCore);
        });
//        Hook.LISTENING_EVENT.get(Hook.Thread.tick).forEach((eventToJS -> eventToJS.call(JS_RUNNER, JS_SCOPE)));
    }

    @SubscribeEvent
    public static void onPrePhysicsTick(PhysicsLevelTickEvent.Pre event) {
        levelVehicles.computeIfAbsent(event.getLevel().getMcLevel(), k -> ConcurrentHashMap.newKeySet()).forEach(VehicleCore::prePhysicsTick);
//        Hook.LISTENING_EVENT.get(Hook.Thread.pre).forEach((eventToJS -> eventToJS.call(JS_RUNNER, JS_SCOPE)));
    }

    @SubscribeEvent
    public static void onPostPhysicsTick(PhysicsLevelTickEvent.Post event) {
        levelVehicles.computeIfAbsent(event.getLevel().getMcLevel(), k -> ConcurrentHashMap.newKeySet()).forEach(VehicleCore::postPhysicsTick);
//        Hook.LISTENING_EVENT.get(Hook.Thread.post).forEach((eventToJS -> eventToJS.call(JS_RUNNER, JS_SCOPE)));
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
        for (VehicleCore vehicle : levelVehicles.getOrDefault(serverLevel, Set.of())) {
            savedVehicles.add(new VehicleData(vehicle));
        }
        serverLevel.setData(MMAttachments.getLEVEL_VEHICLES(), savedVehicles);
    }

    @SubscribeEvent
    public static void transmitVehicleData(PlayerEvent.PlayerLoggedInEvent event) {
        Level level = event.getEntity().level();
        Set<VehicleData> dataToSend = level.getData(MMAttachments.getLEVEL_VEHICLES());
        int packetNum = (dataToSend.size() + 4) / 5;//计算分包数量，5个载具为一包
        Iterator<VehicleData> iterator = dataToSend.iterator();
        for (int i = 0; i < packetNum; i++) {
            Set<VehicleData> packetData = new HashSet<>();
            for (int j = 0; j < 5 && iterator.hasNext(); j++) {
                packetData.add(iterator.next());
            }
            PacketDistributor.sendToPlayer((ServerPlayer) event.getEntity(), new LevelVehicleDataPayload(level.dimension(), packetData, packetNum));
        }
        MachineMax.LOGGER.info("玩家{}登录进入维度{}，发送维度内现有载具数据包", event.getEntity().getName().getString(), level.dimension().location());
    }

    /**
     * 服务端世界加载时，或客户端世界接收到载具数据包时
     * 加载载具数据，实体化为载具核心
     *
     * @param level 世界
     */
    public static void loadVehicles(Level level) {
        levelVehicles.computeIfAbsent(level, k -> ConcurrentHashMap.newKeySet()).clear();
        if (level.isClientSide()) {//客户端清空可能的已有载具数据，从服务器获取新维度的载具数据
            MachineMax.LOGGER.info("客户端进入维度{}，清理维度载具数据", level.dimension().location());
            clientAllVehicles.clear();
        }
        Set<VehicleData> savedVehicles = level.getData(MMAttachments.getLEVEL_VEHICLES());
        if (!savedVehicles.isEmpty()) {
            MachineMax.LOGGER.info("正在从维度{}加载{}个载具...", level.dimension().location(), savedVehicles.size());
            int i = 0;
            try{
                for (VehicleData savedVehicleData : savedVehicles) {
                    try {
                        VehicleCore vehicle = new VehicleCore(level, savedVehicleData);
                        vehicle.loadFromSavedData = true;
                        vehicle.setKinematic(true);
                        addVehicle(vehicle);
                        i++;
                    } catch (Exception e) {
                        savedVehicles.remove(savedVehicleData);
                        MachineMax.LOGGER.error("载具加载失败，出错载具数据：{}", savedVehicleData, e);
                    }
                }
            } catch (Exception e){
                level.setData(MMAttachments.getLEVEL_VEHICLES().get(), savedVehicles);
                MachineMax.LOGGER.error("有载具未能成功加载，已清除出错载具");
            }
            MachineMax.LOGGER.info("已成功从维度{}加载{}个载具", level.dimension().location(), i);
        }
    }

    @SubscribeEvent
    public static void onServerStart(ServerAboutToStartEvent event) {
        serverAllVehicles.clear();
        levelVehicles.clear();
    }

    @SubscribeEvent//加载服务端世界时加载载具核心数据
    public static void loadVehicleData(LevelEvent.Load event) {
        Level level = (Level) event.getLevel();
        PhysicsLevel physicsLevel = level.getPhysicsLevel();
        level.getPhysicsLevel().submitImmediateTask(PPhase.PRE, () -> {
            physicsLevel.getWorld().useDeterministicDispatch(true);//启用确定计算顺序以保证客户端服务端一致性
            physicsLevel.getWorld().useScr(true);//补偿弹性系数以改善小物体的碰撞精度
            physicsLevel.getWorld().getSolverInfo().setGlobalCfm(1e-5f);
            physicsLevel.getWorld().getSolverInfo().setNumIterations(25);
//            Plane plane = new Plane(Vector3f.UNIT_Y, -60);
//            PlaneCollisionShape shape = new PlaneCollisionShape(plane);
//            PhysicsRigidBody body = new PhysicsRigidBody("ground", null, shape, PhysicsBody.massForStatic);
//            physicsLevel.getWorld().add(body);
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
            MMVisualEffects.getPART_ASSEMBLY().attachPoints.clear();
            MMVisualEffects.getPART_ASSEMBLY().partToAssembly = null;
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

    /**
     * collideWithGroups bitmask that represents "no groups"
     */
    final public static int COLLISION_GROUP_NONE = 0x0;
    /**
     * 代表一般部件的碰撞组 #1
     * Represent the collision group for general parts #1
     */
    final public static int COLLISION_GROUP_PART = 0x0001;
    /**
     * 代表地形方块的碰撞组 #2
     * Represent the collision group for terrain blocks #2
     */
    final public static int COLLISION_GROUP_BLOCK = 0x0002;
    /**
     * 代表技能等范围效果判定区的碰撞组 #3
     * Represent the collision group for skill range effect detection etc. #3
     */
    final public static int COLLISION_GROUP_EFFECT = 0x0004;
    /**
     * 代表受物理引擎控制的视觉效果的碰撞组 #4
     * Represent the collision group for visual effects controlled by the physical engine #4
     */
    final public static int COLLISION_GROUP_VISUAL = 0x0008;
    /**
     * 代表除了射线检测等操作外，不应与任何物体发生碰撞的碰撞组 #5
     * Represent the collision group for any object that should not collide with anything except ray detection etc. #5
     */
    final public static int COLLISION_GROUP_NO_COLLISION = 0x0010;
    /**
     * 未使用的碰撞组 #6
     * Unused collision group #6
     */
    final public static int COLLISION_GROUP_06 = 0x0020;
    /**
     * 未使用的碰撞组 #7
     */
    final public static int COLLISION_GROUP_07 = 0x0040;
    /**
     * 未使用的碰撞组 #8
     */
    final public static int COLLISION_GROUP_08 = 0x0080;
    /**
     * 未使用的碰撞组 #9
     */
    final public static int COLLISION_GROUP_09 = 0x0100;
    /**
     * 未使用的碰撞组 #10
     */
    final public static int COLLISION_GROUP_10 = 0x0200;
    /**
     * 未使用的碰撞组 #11
     */
    final public static int COLLISION_GROUP_11 = 0x0400;
    /**
     * 未使用的碰撞组 #12
     */
    final public static int COLLISION_GROUP_12 = 0x0800;
    /**
     * 未使用的碰撞组 #13
     */
    final public static int COLLISION_GROUP_13 = 0x1000;
    /**
     * 未使用的碰撞组 #14
     */
    final public static int COLLISION_GROUP_14 = 0x2000;
    /**
     * 未使用的碰撞组 #15
     */
    final public static int COLLISION_GROUP_15 = 0x4000;
    /**
     * 未使用的碰撞组 #16
     */
    final public static int COLLISION_GROUP_16 = 0x8000;
}
