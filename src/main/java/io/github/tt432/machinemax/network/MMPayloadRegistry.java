package io.github.tt432.machinemax.network;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.network.payload.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.handling.MainThreadPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = MachineMax.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class MMPayloadRegistry {
    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event){
        final PayloadRegistrar input = event.registrar("input:1.0.0");
        final PayloadRegistrar sync = event.registrar("sync:1.0.0");
        final PayloadRegistrar misc = event.registrar("misc:1.0.0");
        //注册网络包及其处理
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
        sync.commonToServer(//客户端请求维度载具数据
                ClientRequestVehicleDataPayload.TYPE,
                ClientRequestVehicleDataPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(ClientRequestVehicleDataPayload::handle)
        );
        sync.commonToClient(//向客户端发送维度载具数据
                LevelVehicleDataPayload.TYPE,
                LevelVehicleDataPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(LevelVehicleDataPayload::handle)
        );
        sync.playToClient(//运动体的位姿和速度
                PhysSyncPayload.TYPE,
                PhysSyncPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(PhysSyncPayload::handler)
        );
    }
}
