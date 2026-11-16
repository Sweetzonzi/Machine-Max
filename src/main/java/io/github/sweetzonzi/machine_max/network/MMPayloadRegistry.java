package io.github.sweetzonzi.machine_max.network;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.network.handler.pda.*;
import io.github.sweetzonzi.machine_max.network.handler.research.*;
import io.github.sweetzonzi.machine_max.network.payload.*;
import io.github.sweetzonzi.machine_max.network.payload.assembly.*;
import io.github.sweetzonzi.machine_max.network.payload.pda.*;
import io.github.sweetzonzi.machine_max.network.payload.explosion.ExplosionDetonatePayload;
import io.github.sweetzonzi.machine_max.network.payload.fabrication.FabricationCancelPayload;
import io.github.sweetzonzi.machine_max.network.payload.fabrication.FabricationCollectAllPayload;
import io.github.sweetzonzi.machine_max.network.payload.fabrication.FabricationCollectPayload;
import io.github.sweetzonzi.machine_max.network.payload.fabrication.FabricationStartPayload;
import io.github.sweetzonzi.machine_max.network.payload.grab.*;
import io.github.sweetzonzi.machine_max.network.payload.library.*;
import io.github.sweetzonzi.machine_max.network.payload.physics_test.PhysicsTestRePlayPayload;
import io.github.sweetzonzi.machine_max.network.payload.projectile.ProjectilesGuidedStatePayload;
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
        // 3.0.0 / 9.0.0 / 2.0.0 对应子系统、连接点与交互区的线上寻址改为声明序序号：
        // 载荷里的「子零件 ID + writeUtf(名字)」换成「varint 子零件 ID + varint 序号」，接收方按同一份内容定义
        // 重建的同一个子零件用 SubPart#subsystemAt / #connectorAt / #interactBoxAt 反查。序号越界即整包丢弃，
        // 不回退按名字再查；名字仍是存档键与翻译键。
        final PayloadRegistrar input = event.registrar("input:3.0.0");
        // TrackLinkData#STREAM_CODEC 依次编码 clockwise 绕向、track_type 型号 id 与 link_count 节数，
        // VehicleData 的同步和蓝图载荷使用同一格式。
        // sync 与 misc 的版本共同约束两端线格式，防止参数错位。
        // PartAssemblyProgressSyncPayload 通过单个 SubPart ID 寻址普通部件，编码长度为 13 字节。
        // sync 与 misc 的版本随 RegularPartData.STREAM_CODEC 的字段扩展一起升级（新增 destroyed 与 destroy_time）。
        // 2.8.0 对应再生装弹机改为 ModularSubsystem：继承链多注册一个模块访问器，子系统自身的访问器编号整体后移，
        // 两端必须同版本才可解码同一份 SubsystemSyncPayload。
        // 2.9.0 / 1.4.0 对应 TrackLinkData 增加 link_count：节数改由服务端持久化并随载具 / 蓝图数据下发。
        // 2.9.1 新增 SubPartHitEffectPayload：命中音效与命中火花改由客户端按碰撞箱身份重建。
        // 2.9.2 为发动机的 SubsystemSyncPayload 增加 FMOD 使用的油门开度。
        // 3.0.0 对应弹药交付改为一份 AmmoBelt：发射器增加已装 Belt 与膛内弹两个同步项，再生装弹机去掉弹链指针，
        // 武器控制器选中的弹链改为交付规格——子系统访问器编号与载荷类型都变了，两端必须同版本。
        // 4.0.0 对应装填计时搬到发射器：发射器再增加作业目标、已推进 tick 与本阶段时长三个同步项，
        // 同一子系统类的访问器编号整体后移，两端必须同版本才可解码同一份 SubsystemSyncPayload。
        // 5.0.0 对应作业身份与分配下行：发射器增加作业编号、实际来源、冻结规格与预留份额，独占机构增加
        // 槽位拥有者，再生架增加已预留总量；SupplierRef 的编码由「Part uuid + SubPart 名 + 子系统名」
        // 收成「宿主 SubPart ID + 子系统名」。
        // 6.0.0 对应结束结果：发射器再增加最近一次结束的作业编号与已交付结论两个同步项（访问器编号后移）。
        // 7.0.0 对应装填作业的人工段：发射器增加当前阶段一个同步项（访问器编号后移），阶段切换与换人
        // 重定时都会改写本阶段时长与进度基线，客户端按阶段分别推进。
        // 8.0.0 对应装填作业改为整包同步：发射器的八个作业访问器收成一个可选快照，
        // 结果两项仍在外面，访问器编号整体后移。
        // 10.0.0 / 3.0.0 对应 DTO 的 uuid 线上编码统一改为双 long（16 字节）：
        // VehicleData.uuid、RegularPartData.uuid、ConnectionData.partUuidA/S 由 writeUtf 改为 writeUUID；
        // ConnectorAttachPayload 的 connections/partData 由 JSON Codec 改为二进制 STREAM_CODEC，与 Detach 对齐。
        // 仅网络线格式变更，JSON 落盘/蓝图 CODEC 仍为字符串 UUID，存档兼容不受影响。
        // 11.0.0 对应弹药三角色拆分：发射器的供给者身份收成一对路由引用（ROUTE_TRANSPORTER / ROUTE_PROVIDER，
        // 类型 AmmoRef = 宿主 SubPart ID + 声明序序号），装填作业快照里的两个引用改为运输者与提供者；
        // SupplierRef 被 AmmoRef 取代，序列化器 id 由 supplier_ref 改为 ammo_ref，作业身份改按子系统序号编码。
        // 12.0.0 对应子系统同步改为一轮一批：SubsystemSyncPayload 由"单个子系统一个包"变成
        // "同一控制器一轮一份批次"，条目内是 (子零件 ID, 声明序序号, 变化值)。
        // 15.0.0 两处：发射器下发权威的下一个作业编号（NEXT_JOB_ID），客户端据它把本地计数推到下界之后；
        // 批次头收成装配体身份 + 条目列表——同步值是值表，客户端按 (子零件, 序号, 数据项) 合并，
        // 代次、批次序号、采样步与 full 标记连同随队列作废的机制一并去掉。
        // 16.0.0 对应装填作业的同步拆成两支：冻结分配（ReloadJobSpec，受理/收尾/全量补推时下发）与
        // 动态样本（ReloadJobProgress，换段与换人重定时下发），序列化器由 optional_reload_job_sync
        // 换成 optional_reload_job_spec 与 optional_reload_job_progress，访问器编号随之改变。
        // 17.0.0 对应结束结果由一对标量改成一串有序结果（ReloadJobResults）：同一轮可以结束不止一条作业，
        // 标量只留得下最后一次；序列化器 reload_job_results。
        // 18.0.0 对应删掉两个没有消费方的同步字段：装弹机的槽位拥有者与弹药架的已预留总量。两者都只由服务端
        // 写入、客户端从未读取，客户端的对应表由本端作业收发维护；序列化器 reload_job_key 随之移除，
        // 访问器编号整体前移。
        // 19.0.0 对应作业身份改为结构三元组、作业编号字段删除：发射器不再下发 NEXT_JOB_ID，两端各自按
        // (宿主 SubPart ID, 发射器序号, 运输者, 提供者) 算出同一个键；作业的两支样本与结束结果里的编号换成
        // 本管交付轮次（ReloadJobSpec.round / ReloadJobProgress.round / ReloadJobResults.Entry.round），
        // 线宽不变，类内访问器少一个。
        final PayloadRegistrar sync = event.registrar("sync:19.0.0");
        final PayloadRegistrar research = event.registrar("research:2.0.0");
        final PayloadRegistrar misc = event.registrar("misc:3.0.0");
        final PayloadRegistrar pda = event.registrar("pda:1.0.0");
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
        input.playToServer(//换座请求（携带目标座位，服务端只校验不排序）
                SeatSwitchPayload.TYPE,
                SeatSwitchPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(SeatSwitchPayload::serverHandler)
        );
        input.playToClient(//换座结果（纯状态包，成功时广播维度）
                SeatSwitchResultPayload.TYPE,
                SeatSwitchResultPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(SeatSwitchResultPayload::clientHandler)
        );
        input.playToServer(//开始抓取（只携带意图，服务端重算目标与参数）
                GrabStartPayload.TYPE,
                StreamCodec.unit(new GrabStartPayload()),
                new MainThreadPayloadHandler<>(GrabStartPayload::handler)
        );
        input.playToServer(//放下
                GrabReleasePayload.TYPE,
                StreamCodec.unit(new GrabReleasePayload()),
                new MainThreadPayloadHandler<>(GrabReleasePayload::handler)
        );
        input.playToServer(//丢出
                GrabThrowPayload.TYPE,
                StreamCodec.unit(new GrabThrowPayload()),
                new MainThreadPayloadHandler<>(GrabThrowPayload::handler)
        );
        input.playToServer(//手动零件组装放置请求
                PartAssemblyRequestPayload.TYPE,
                PartAssemblyRequestPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(PartAssemblyRequestPayload::serverHandler)
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
        sync.playToClient(//通知客户端部件组装进度改变
                PartAssemblyProgressSyncPayload.TYPE,
                PartAssemblyProgressSyncPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(PartAssemblyProgressSyncPayload::handle)
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
        sync.playToClient(//同步履带列表 / 耐久 / 断带状态
                TrackLinkSyncPayload.TYPE,
                TrackLinkSyncPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(TrackLinkSyncPayload::handle)
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
        sync.playToClient(//制导弹位姿快照（服务端→客户端），客户端不施加制导
                ProjectilesGuidedStatePayload.TYPE,
                ProjectilesGuidedStatePayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(ProjectilesGuidedStatePayload::handle)
        );
        sync.playToClient(//向客户端发送载具数据，由客户端保存到本地文件
                VehicleDataSavedPayload.TYPE,
                VehicleDataSavedPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(VehicleDataSavedPayload::handler)
        );
        sync.playToClient(//起爆事件（起爆点 + 种子 + 参数集），客户端据此复现表现
                ExplosionDetonatePayload.TYPE,
                ExplosionDetonatePayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(ExplosionDetonatePayload::handle)
        );
        sync.playToClient(//零件命中表现（子部件 id + 碰撞箱名 + 局部命中点 + 喷射轴），音效与火花由客户端解析
                SubPartHitEffectPayload.TYPE,
                SubPartHitEffectPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(SubPartHitEffectPayload::handle)
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
        research.playToServer(//玩家消耗空白蓝图抄录已研发条目的制造蓝图
                ResearchTranscribePayload.TYPE,
                ResearchTranscribePayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(ResearchTranscribeHandler::handler)
        );
        research.playToClient(//通知客户端蓝图研发完成
                ResearchCompletePayload.TYPE,
                ResearchCompletePayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(ResearchCompleteHandler::handler)
        );
        research.playToClient(//通知客户端打开研究台界面
                ResearchScreenOpenPayload.TYPE,
                ResearchScreenOpenPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(ResearchScreenOpenHandler::handler)
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
        misc.playToServer(//取出内容包蓝图（只发 blueprintId，服务端自行解析模板）
                BlueprintExtractPackPayload.TYPE,
                BlueprintExtractPackPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(BlueprintExtractPackPayload::serverHandler)
        );
        misc.playToServer(//取出玩家库蓝图（完整 VehicleData + 独立 meta）
                BlueprintExtractLocalPayload.TYPE,
                BlueprintExtractLocalPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(BlueprintExtractLocalPayload::serverHandler)
        );
        misc.playToServer(//将手中蓝图存入库（服务端读组件后回传 VehicleDataSavedPayload）
                BlueprintStoreRequestPayload.TYPE,
                BlueprintStoreRequestPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(BlueprintStoreRequestPayload::serverHandler)
        );
        misc.playToClient(//蓝图取出结果回执
                BlueprintExtractResultPayload.TYPE,
                BlueprintExtractResultPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(BlueprintExtractResultPayload::clientHandler)
        );

        pda.playToServer(//把背包中的蓝图存入 PDA
                PdaDepositPayload.TYPE,
                PdaDepositPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(PdaDepositHandler::handler)
        );
        pda.playToServer(//绑定或解绑设计模式快捷栏格位
                PdaBindShortcutPayload.TYPE,
                PdaBindShortcutPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(PdaBindShortcutHandler::handler)
        );
        pda.playToServer(//写入当前格位序号
                PdaSelectShortcutPayload.TYPE,
                PdaSelectShortcutPayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(PdaSelectShortcutHandler::handler)
        );
        pda.playToServer(//写入设计模式开关
                PdaSetDesignModePayload.TYPE,
                PdaSetDesignModePayload.STREAM_CODEC,
                new MainThreadPayloadHandler<>(PdaSetDesignModeHandler::handler)
        );
    }
}
