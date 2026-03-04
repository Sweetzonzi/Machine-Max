package io.github.sweetzonzi.machine_max.network;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.network.handler.research.*;
import io.github.sweetzonzi.machine_max.network.payload.*;
import io.github.sweetzonzi.machine_max.network.payload.assembly.*;
import io.github.sweetzonzi.machine_max.network.payload.fabrication.FabricationCancelPayload;
import io.github.sweetzonzi.machine_max.network.payload.fabrication.FabricationCollectAllPayload;
import io.github.sweetzonzi.machine_max.network.payload.fabrication.FabricationCollectPayload;
import io.github.sweetzonzi.machine_max.network.payload.fabrication.FabricationStartPayload;
import io.github.sweetzonzi.machine_max.network.payload.research.*;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.handling.MainThreadPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = MachineMax.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class MMPayloadRegistry {
    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar input = event.registrar("input:1.0.0");
        final PayloadRegistrar sync = event.registrar("sync:1.0.0");
        final PayloadRegistrar research = event.registrar("research:1.0.0");
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

        research.playToClient(//玩家蓝图的自由研发点同步
                FreeRpSyncPayload.TYPE,
                FreeRpSyncPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(FreeRpSyncHandler::handler)
        );
        research.playToServer(//玩家应用自由研发点
                ResearchApplyFreeRpPayload.TYPE,
                ResearchApplyFreeRpPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(ResearchApplyFreeRpHandler::handler)
        );
        research.playToClient(//玩家推进蓝图研发进度
                ResearchPushPayload.TYPE,
                ResearchPushPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(ResearchPushHandler::handler)
        );
        research.playBidirectional(//玩家改变蓝图研发目标
                ResearchSetPayload.TYPE,
                ResearchSetPayload.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        ResearchSetClientHandler::clientHandler,
                        ResearchSetServerHandler::serverHandler
                )
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
        research.playBidirectional(//玩家取消蓝图研发
                ResearchCancelPayload.TYPE,
                ResearchCancelPayload.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        ResearchCancelHandler::clientHandler,
                        ResearchCancelHandler::serverHandler
                )
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