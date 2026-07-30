package io.github.sweetzonzi.machine_max.network;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.network.handler.research.*;
import io.github.sweetzonzi.machine_max.network.payload.*;
import io.github.sweetzonzi.machine_max.network.payload.assembly.*;
import io.github.sweetzonzi.machine_max.network.payload.fabrication.FabricationCancelPayload;
import io.github.sweetzonzi.machine_max.network.payload.fabrication.FabricationCollectAllPayload;
import io.github.sweetzonzi.machine_max.network.payload.fabrication.FabricationCollectPayload;
import io.github.sweetzonzi.machine_max.network.payload.fabrication.FabricationStartPayload;
import io.github.sweetzonzi.machine_max.network.payload.physics_test.PhysicsTestRePlayPayload;
import io.github.sweetzonzi.machine_max.network.payload.projectile.ProjectilesHitPayload;
import io.github.sweetzonzi.machine_max.network.payload.projectile.ProjectilesSpawnPayload;
import io.github.sweetzonzi.machine_max.network.payload.research.*;
import io.github.sweetzonzi.machine_max.util.environment.EnvironmentSettings;
import io.github.sweetzonzi.machine_max.util.environment.EnvironmentWrapper;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.handling.MainThreadPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = MachineMax.MOD_ID)
public class MMPayloadRegistry {
    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar input = event.registrar("input:1.0.0");
        final PayloadRegistrar sync = event.registrar("sync:1.0.0");
        final PayloadRegistrar research = event.registrar("research:2.0.0");
        final PayloadRegistrar misc = event.registrar("misc:1.0.0");
        //注册网络包及其处理
        input.playToServer(//玩家配置
                ControlPreferencePayload.TYPE,
                ControlPreferencePayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(ControlPreferencePayload::handle)
        );
        input.playBidirectional(//移动输入
                MovementInputPayload.TYPE,
                MovementInputPayload.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        MovementInputPayload::clientHandler,
                        MovementInputPayload::serverHandler
                )
        );
        input.playBidirectional(//常规输入
                RegularInputPayload.TYPE,
                RegularInputPayload.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        RegularInputPayload::clientHandler,
                        RegularInputPayload::serverHandler
                )
        );
        input.playBidirectional(//子系统交互
                SubsystemInteractPayload.TYPE,
                SubsystemInteractPayload.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        SubsystemInteractPayload::clientHandler,
                        SubsystemInteractPayload::serverHandler
                )
        );
        input.playBidirectional(//JS自定义网络包
                ScriptablePayload.TYPE,
                ScriptablePayload.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        ScriptablePayload::clientHandler,
                        ScriptablePayload::serverHandler
                )
        );
        input.playBidirectional(//物理测试网络包
                PhysicsTestRePlayPayload.TYPE,
                PhysicsTestRePlayPayload.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        PhysicsTestRePlayPayload::clientHandler,
                        PhysicsTestRePlayPayload::serverHandler
                )
        );
        input.playToServer(//视角输入（瞄准点世界坐标）
                ViewInputPayload.TYPE,
                ViewInputPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(ViewInputPayload::serverHandler)
        );
        EnvironmentWrapper.run(EnvironmentSettings.PLAYER_LOOK_AT_PAYLOAD, () -> {
            input.playToServer(//同步玩家视角输入（瞄准点世界坐标）
                PlayerLookAtPayload.TYPE,
                PlayerLookAtPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(PlayerLookAtPayload::serverHandler)
            );
        });
        input.playToServer(//控制组按键绑定输入
                ControlBindingPayload.TYPE,
                ControlBindingPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(ControlBindingPayload::serverHandler)
        );
        sync.playToClient(//通知客户端创建载具
                VehicleCreatePayload.TYPE,
                VehicleCreatePayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(VehicleCreatePayload::handle)
        );
        sync.playToClient(//通知客户端移除载具
                VehicleRemovePayload.TYPE,
                VehicleRemovePayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(VehicleRemovePayload::handle)
        );
        sync.playToClient(//通知客户端创建部件与连接
                ConnectorAttachPayload.TYPE,
                ConnectorAttachPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(ConnectorAttachPayload::handle)
        );
        sync.playToClient(//通知客户端合并载具并建立连接
                VehicleMergePayload.TYPE,
                VehicleMergePayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(VehicleMergePayload::handle)
        );
        sync.playToClient(//通知客户端移除连接
                ConnectorDetachPayload.TYPE,
                ConnectorDetachPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(ConnectorDetachPayload::handle)
        );
        sync.playToClient(//通知客户端移除部件
                PartRemovePayload.TYPE,
                PartRemovePayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(PartRemovePayload::handle)
        );
        sync.playToClient(//通知客户端涂装变化
                PartPaintPayload.TYPE,
                PartPaintPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(PartPaintPayload::handle)
        );
        sync.playToClient(//通知客户端配方变化
                PartChangeRecipePayload.TYPE,
                PartChangeRecipePayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(PartChangeRecipePayload::handle)
        );
        sync.playToClient(//通知客户端部件组转进度改变
                PartAssemblyProgressSyncPayload.TYPE,
                PartAssemblyProgressSyncPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(PartAssemblyProgressSyncPayload::handle)
        );
        sync.playToClient(//同步客户端玩家部件组装缓存
                PlayerPartAssemblyCacheSyncPayload.TYPE,
                PlayerPartAssemblyCacheSyncPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(PlayerPartAssemblyCacheSyncPayload::handle)
        );
        sync.commonToServer(//客户端请求维度载具数据
                ClientRequestVehicleDataPayload.TYPE,
                ClientRequestVehicleDataPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(ClientRequestVehicleDataPayload::handle)
        );
        sync.playToClient(//向客户端发送维度载具数据
                LevelVehicleDataPayload.TYPE,
                LevelVehicleDataPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(LevelVehicleDataPayload::handle)
        );
        sync.playToClient(//同步载具耐久状态
                VehicleStatusSyncPayload.TYPE,
                VehicleStatusSyncPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(VehicleStatusSyncPayload::handle)
        );
        sync.playToClient(//运动体的同步数据
                SubPartSyncPayload.TYPE,
                SubPartSyncPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(SubPartSyncPayload::handler)
        );
        sync.playToClient(//运动体子系统的同步数据
                SubsystemSyncPayload.TYPE,
                SubsystemSyncPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(SubsystemSyncPayload::handler)
        );
        sync.playToClient(//运动体连接点的同步数据
                ConnectorSyncPayload.TYPE,
                ConnectorSyncPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(ConnectorSyncPayload::handler)
        );
        sync.playToClient(//投射物批量命中同步（服务端→客户端），替换旧单发包
                ProjectilesHitPayload.TYPE,
                ProjectilesHitPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(ProjectilesHitPayload::handle)
        );
        sync.playToClient(//投射物批量创建（服务端→客户端），替代单发包
                ProjectilesSpawnPayload.TYPE,
                ProjectilesSpawnPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(ProjectilesSpawnPayload::handle)
        );
        sync.playToClient(//向客户端发送载具数据，由客户端保存到本地文件
                VehicleDataSavedPayload.TYPE,
                VehicleDataSavedPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(VehicleDataSavedPayload::handler)
        );

        research.playToClient(//玩家蓝图的自由研发点同步
                FreeRpSyncPayload.TYPE,
                FreeRpSyncPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(FreeRpSyncHandler::handler)
        );
        research.playToServer(//玩家请求完成一个研发项目
                ResearchCompleteRequestPayload.TYPE,
                ResearchCompleteRequestPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(ResearchCompleteRequestHandler::handler)
        );
        research.playToServer(//玩家获取研发产物
                ResearchClaimPayload.TYPE,
                ResearchClaimPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(ResearchClaimHandler::handler)
        );
        research.playToServer(//玩家消耗研发点重新获取已研发蓝图
                ResearchReclaimPayload.TYPE,
                ResearchReclaimPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(ResearchReclaimHandler::handler)
        );
        research.playToClient(//通知客户端蓝图研发完成
                ResearchCompletePayload.TYPE,
                ResearchCompletePayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(ResearchCompleteHandler::handler)
        );
        research.playToClient(//同步玩家蓝图研发产物
                ResearchProductSyncPayload.TYPE,
                ResearchProductSyncPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(ResearchProductSyncHandler::handler)
        );
        research.playToClient(//同步玩家研发数据
                ResearchAttachmentSyncPayload.TYPE,
                ResearchAttachmentSyncPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(ResearchAttachmentSyncHandler::handler)
        );
        misc.playToServer(//通过GUI配置载具属性
                VehicleConfigPayload.TYPE,
                VehicleConfigPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(VehicleConfigPayload::handler)
        );
        misc.playToServer(//控制组编辑结果保存
                ControlGroupSetEditPayload.TYPE,
                ControlGroupSetEditPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(ControlGroupSetEditPayload::serverHandler)
        );
        misc.playToServer(//GUI控件操作（PULSE/TOGGLE/SLIDER）
                GuiActionPayload.TYPE,
                GuiActionPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(GuiActionPayload::serverHandler)
        );
        misc.playToServer(//制造机开始制造
                FabricationStartPayload.TYPE,
                FabricationStartPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(FabricationStartPayload::handler)
        );
        misc.playToServer(//制造机取消制造
                FabricationCancelPayload.TYPE,
                FabricationCancelPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(FabricationCancelPayload::handler)
        );
        misc.playToServer(//制造机收取产物
                FabricationCollectPayload.TYPE,
                FabricationCollectPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(FabricationCollectPayload::handler)
        );
        misc.playToServer(//制造机收取产物
                FabricationCollectAllPayload.TYPE,
                StreamCodec.unit(new FabricationCollectAllPayload()),
                new MainThreadPayloadHandler<>(FabricationCollectAllPayload::handler)
        );
    }
}
