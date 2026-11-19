package io.github.sweetzonzi.machine_max.common.gametest;

import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.util.PPhase;
import com.jme3.bullet.RotationOrder;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.joints.New6Dof;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Matrix3f;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.control.ControlGroup;
import io.github.sweetzonzi.machine_max.common.mech.control.ControlGroupSet;
import io.github.sweetzonzi.machine_max.common.mech.control.aim.AimGeometrySnapshot;
import io.github.sweetzonzi.machine_max.common.mech.control.aim.AimTargetState;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.signal.EmptySignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.ViewInputSignal;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractControllableSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractViewpointSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SightSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.TurretDriverSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.WeaponControllerSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.SeatSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.WeaponControllerSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPartType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.event.VehicleStructureChangeEvent;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 武器控制器侧的视点发现、座椅候选合并与机构配对的服务端验收。
 *
 * <p>夹具是 M1A2 SEP 炮塔 + 遥控武器站：炮塔里四台视点（炮手镜、并列机枪镜、车尾机枪镜、周视镜）
 * 与各火控同在一个 Part，武器站的 RWS 镜在另一个 Part，靠一对挂点连接。这些视点由各自的火控声明，
 * 座位只保留周视镜这一条静态声明，本类因此同时是“火控声明视点”这条内容形态的回归。</p>
 */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ViewpointDiscoveryGameTest {
    private static final String BATCH = "mmViewpointDiscovery";
    private static final String TURRET = "m1a2_sep:m1a2_turret";
    private static final String CROWS = "m1a2_sep:m153_crows";

    private static final String GUNNER_OPTICS = "subsystem.machine_max.gunner_optics";
    private static final String CITV_OPTICS = "subsystem.m1a2_sep.citv_optics";
    private static final String PORT_MG_SIGHT = "subsystem.machine_max.loader_mg_sight";
    private static final String AFT_MG_SIGHT = "subsystem.machine_max.aa_sight";
    private static final String RWS_SIGHT = "subsystem.m1a2_sep.rws_sight";
    private static final String MAIN_CONTROLLER = "subsystem.machine_max.main_controller";
    private static final String RWS_CONTROLLER = "subsystem.m1a2_sep.rws_controller";
    private static final String CITV_CONTROLLER = "subsystem.m1a2_sep.citv_controller";
    private static final String PORT_MG_CONTROLLER = "subsystem.m1a2_sep.loader_mg_controller";
    private static final String AA_CONTROLLER = "subsystem.machine_max.aa_controller";
    private static final String COMMANDER_SEAT = "subsystem.machine_max.commander_seat";
    private static final String LOADER_SEAT = "subsystem.machine_max.loader_seat";
    private static final String AA_SEAT = "subsystem.machine_max.aa_seat";

    private ViewpointDiscoveryGameTest() {
    }

    /** 炮塔 + 遥控武器站的装配体及其按名索引。 */
    private record Fixture(RegularPart turret, RegularPart rws, VehicleCore vehicle,
                           Map<String, AbstractSubsystem> byName) {
    }

    private static Fixture fixture(GameTestHelper helper) {
        RegularPartType type = PartDefinitions.regular(false).get(ResourceLocation.parse(TURRET));
        helper.assertTrue(type != null, "M1A2 SEP 炮塔的部件定义必须已加载");
        RegularPart turret = PartFactory.createRegular(type, "desert", helper.getLevel());
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), turret);
        RegularPart rws = attachRws(helper, vehicle, turret);
        // 夹具不进世界，attachConnector 因此不会发结构变化；手工补一次，让座位与火控重新发现。
        vehicle.onStructureChanged(VehicleStructureChangeEvent.Reason.ATTACH);
        Map<String, AbstractSubsystem> byName = new LinkedHashMap<>();
        turret.subParts.values().forEach(subPart ->
                subPart.ownSubsystems().forEach(subsystem -> byName.put(subsystem.getName(), subsystem)));
        rws.subParts.values().forEach(subPart ->
                subPart.ownSubsystems().forEach(subsystem -> byName.put(subsystem.getName(), subsystem)));
        return new Fixture(turret, rws, vehicle, byName);
    }

    /** 与 M1A2MountWiringGameTest#attachRws 相同的接法：炮塔侧 Simple、武器站侧 Advanced。 */
    private static RegularPart attachRws(GameTestHelper helper, VehicleCore vehicle, RegularPart turret) {
        RegularPartType type = PartDefinitions.regular(false).get(ResourceLocation.parse(CROWS));
        helper.assertTrue(type != null, "M1A2 SEP 遥控武器站的部件定义必须已加载");
        RegularPart rws = PartFactory.createRegular(type, "desert", helper.getLevel());
        AbstractConnector advanced = rws.subParts.get("sub_part.m1a2_sep.rws_azimuth")
                .connectors.get("connector.m1a2_sep.rws_turret");
        AbstractConnector simple = turret.subParts
                .get("sub_part.machine_max.main").connectors.get("connector.m1a2_sep.turret_rws");
        vehicle.attachConnector(advanced, simple, rws);
        return rws;
    }

    private static void dispose(Fixture fixture) {
        fixture.vehicle().onRemoveFromLevel();
    }

    private static AbstractSubsystem require(GameTestHelper helper, Fixture fixture, String name) {
        AbstractSubsystem subsystem = fixture.byName().get(name);
        helper.assertTrue(subsystem != null, "子系统必须存在：" + name);
        return subsystem;
    }

    private static WeaponControllerSubsystem controller(GameTestHelper helper, Fixture fixture, String name) {
        AbstractSubsystem subsystem = require(helper, fixture, name);
        helper.assertTrue(subsystem instanceof WeaponControllerSubsystem,
                name + " 必须是武器控制器，实际 " + subsystem.getClass().getSimpleName());
        return (WeaponControllerSubsystem) subsystem;
    }

    private static SeatSubsystem seat(GameTestHelper helper, Fixture fixture, String name) {
        AbstractSubsystem subsystem = require(helper, fixture, name);
        helper.assertTrue(subsystem instanceof SeatSubsystem,
                name + " 必须是座位，实际 " + subsystem.getClass().getSimpleName());
        return (SeatSubsystem) subsystem;
    }

    private static AbstractViewpointSubsystem viewpoint(GameTestHelper helper, Fixture fixture, String name) {
        AbstractSubsystem subsystem = require(helper, fixture, name);
        helper.assertTrue(subsystem instanceof AbstractViewpointSubsystem,
                name + " 必须是视点，实际 " + subsystem.getClass().getSimpleName());
        return (AbstractViewpointSubsystem) subsystem;
    }

    /** 以火控为蓝本造一台只声明发现表的探针，只读发现不参与任何命令输出。 */
    private static WeaponControllerSubsystem probe(Fixture fixture, String name,
                                                   Map<String, List<String>> controlOutputs,
                                                   Map<String, List<String>> cameraDiscovery) {
        WeaponControllerSubsystem blueprint = (WeaponControllerSubsystem) fixture.byName().get(MAIN_CONTROLLER);
        WeaponControllerSubsystemAttr attr = new WeaponControllerSubsystemAttr(
                blueprint.attr.getModelName(), controlOutputs, cameraDiscovery,
                blueprint.attr.getFirstHudLayout(), blueprint.attr.getThirdHudLayout(),
                blueprint.attr.getScopeHudLayout());
        return new WeaponControllerSubsystem(blueprint.getOwner(), name, attr);
    }

    private static List<String> candidateNames(AbstractControllableSubsystem seat) {
        return seat.getViewCandidates().stream()
                .map(candidate -> candidate.viewpoint().getName()).toList();
    }

    private static void switchGroup(SeatSubsystem seat, int index) {
        ControlGroupSet groups = seat.getControlGroupSet();
        seat.setControlGroupSet(new ControlGroupSet(groups.baseGroup, groups.groups,
                groups.getGuiActions(), index));
    }

    private static CompletableFuture<Void> physics(WeaponControllerSubsystem controller, Runnable work) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        controller.getPhysicsLevel().submitImmediateTask(PPhase.PRE, () -> {
            try {
                work.run();
                result.complete(null);
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
            return null;
        });
        return result;
    }

    /**
     * T01：武器控制器经 {@code global} 发现视点，只收同装配体的视点，且解析不写视点的输入槽。
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void weaponControllerFindsViewpointThroughGlobal(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        try {
            WeaponControllerSubsystem probe = probe(fixture, "probe_global_viewpoint",
                    Map.of(), Map.of("sight_rws", List.of("global")));
            AbstractViewpointSubsystem rwsSight = viewpoint(helper, fixture, RWS_SIGHT);
            helper.assertTrue(rwsSight.getAcceptedChannels().contains("sight_rws"),
                    "RWS 镜必须声明接受 sight_rws，否则广播线路按接收侧筛选会漏掉它");
            int channelsBefore = rwsSight.getSignalInputChannels().size();

            probe.setTargetFromNames();
            probe.onAttach();

            helper.assertTrue(probe.getDiscoveredViewpoints().size() == 1
                            && probe.getDiscoveredViewpoints().getFirst() == rwsSight,
                    "global 发现必须恰好命中装配体里的 RWS 镜，实际 " + probe.getDiscoveredViewpoints().size());
            helper.assertTrue(rwsSight.getSignalInputChannels().size() == channelsBefore,
                    "发现只读：解析前后视点的输入槽数量必须不变");
            helper.succeed();
        } finally {
            dispose(fixture);
        }
    }

    /**
     * T02：具名目标发现同 Part 跨子零件视点；目标名解不出时建表期记错误，且不进发现结果。
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void weaponControllerFindsNamedViewpointAcrossSubParts(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        try {
            WeaponControllerSubsystem probe = probe(fixture, "probe_named_viewpoint",
                    Map.of(), Map.of("sight_port_mg", List.of(PORT_MG_SIGHT)));
            AbstractViewpointSubsystem portMgSight = viewpoint(helper, fixture, PORT_MG_SIGHT);
            helper.assertTrue(portMgSight.getSubPart().getName().contains("loader_mg_elevation"),
                    "夹具前提：并列机枪镜必须挂在另一个子零件上");

            probe.setTargetFromNames();
            probe.onAttach();
            helper.assertTrue(probe.getDiscoveredViewpoints().size() == 1
                            && probe.getDiscoveredViewpoints().getFirst() == portMgSight,
                    "具名目标必须跨子零件命中同一实例");

            WeaponControllerSubsystem missing = probe(fixture, "probe_missing_viewpoint",
                    Map.of(), Map.of("sight_port_mg", List.of("subsystem.machine_max.no_such_sight")));
            missing.setTargetFromNames();
            missing.onAttach();
            helper.assertTrue(missing.getDiscoveredViewpoints().isEmpty(),
                    "解不出的目标名不得进入发现结果（建表期只记错误日志）");
            helper.succeed();
        } finally {
            dispose(fixture);
        }
    }

    /**
     * T03：座位静态列表与火控发现同一视点时只出现一次，配对指向该火控。
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void staticAndDiscoveredViewpointDeduplicateWithControllerPairing(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        try {
            SeatSubsystem commander = seat(helper, fixture, COMMANDER_SEAT);
            AbstractViewpointSubsystem citv = viewpoint(helper, fixture, CITV_OPTICS);
            helper.assertTrue(candidateNames(commander).contains(citv.getName()),
                    "夹具前提：车长席静态声明了周视镜");
            helper.assertTrue(commander.getViewCandidateController(citv) == null,
                    "周视镜没有被任何火控声明，静态条目因此没有配对控制器");

            // 再造一台也发现同一视点的活动火控：候选仍只有一条，配对随之指向那台控制器。
            WeaponControllerSubsystem first = probe(fixture, "probe_citv_first",
                    Map.of(), Map.of("commander_citv", List.of(CITV_OPTICS)));
            first.setTargetFromNames();
            first.onAttach();
            commander.getWeaponControllersByGroup().put("base", List.of(first));
            List<String> names = candidateNames(commander);
            helper.assertTrue(names.stream().filter(citv.getName()::equals).count() == 1,
                    "候选必须按实例去重，实际 " + names);
            helper.assertTrue(commander.getViewCandidateController(citv) == first,
                    "静态条目被活动火控发现后，配对必须指向该控制器");
            helper.succeed();
        } finally {
            dispose(fixture);
        }
    }

    /**
     * T04 / T14：控制器失活、摧毁或宿主子零件被移除时其视点离开候选；静态声明的同一视点仍在。
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void inactiveOrDestroyedControllerDropsItsViewpoint(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        try {
            SeatSubsystem commander = seat(helper, fixture, COMMANDER_SEAT);
            WeaponControllerSubsystem rwsController = controller(helper, fixture, RWS_CONTROLLER);
            AbstractViewpointSubsystem rwsSight = viewpoint(helper, fixture, RWS_SIGHT);
            AbstractViewpointSubsystem citv = viewpoint(helper, fixture, CITV_OPTICS);
            helper.assertTrue(candidateNames(commander).contains(rwsSight.getName()),
                    "基础组里遥控武器站的火控是活动的，它的视点必须在候选里");

            rwsController.setActive(false);
            helper.assertTrue(!commander.hasViewCandidate(rwsSight), "火控失活后其视点必须离开候选");
            helper.assertTrue(commander.hasViewCandidate(citv), "静态声明的周视镜不受火控状态影响");

            rwsController.setActive(true);
            rwsController.onDestroyed();
            helper.assertTrue(!commander.hasViewCandidate(rwsSight), "火控被摧毁后其视点必须离开候选");

            WeaponControllerSubsystem portController = controller(helper, fixture, PORT_MG_CONTROLLER);
            AbstractViewpointSubsystem portMgSight = viewpoint(helper, fixture, PORT_MG_SIGHT);
            SeatSubsystem loader = seat(helper, fixture, LOADER_SEAT);
            helper.assertTrue(loader.hasViewCandidate(portMgSight), "夹具前提：装填手席能发现并列机枪镜");
            portController.getSubPart().isRemoved = true;
            try {
                helper.assertTrue(!loader.hasViewCandidate(portMgSight),
                        "宿主子零件被移除后，其视点必须同时从候选里消失");
            } finally {
                portController.getSubPart().isRemoved = false;
            }
            helper.succeed();
        } finally {
            dispose(fixture);
        }
    }

    /**
     * T05：切控制组使活动集合变化，候选即时跟随；视点离开所属控制器的活动集合后通不过校验。
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void candidateSetFollowsControlGroup(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        try {
            SeatSubsystem commander = seat(helper, fixture, COMMANDER_SEAT);
            AbstractViewpointSubsystem gunnerOptics = viewpoint(helper, fixture, GUNNER_OPTICS);
            helper.assertTrue(!commander.hasViewCandidate(gunnerOptics),
                    "基础组只带动武器站与周视镜火控，看不到主炮的炮手镜");

            switchGroup(commander, 1);
            helper.assertTrue(commander.hasViewCandidate(gunnerOptics),
                    "切到超控组后主炮火控进入活动集合，炮手镜必须出现");
            helper.assertTrue(commander.getViewCandidateController(gunnerOptics)
                            == controller(helper, fixture, MAIN_CONTROLLER),
                    "炮手镜必须配对到主炮火控");

            switchGroup(commander, -1);
            helper.assertTrue(!commander.hasViewCandidate(gunnerOptics),
                    "切回基础组后炮手镜必须即时从候选里消失");
            helper.succeed();
        } finally {
            dispose(fixture);
        }
    }

    /**
     * T06：副武器线上的控制器同样贡献视点，不依赖主武器频道。
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void secondaryWeaponControllerContributesViewpoints(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        try {
            SeatSubsystem commander = seat(helper, fixture, COMMANDER_SEAT);
            WeaponControllerSubsystem rwsController = controller(helper, fixture, RWS_CONTROLLER);
            AbstractViewpointSubsystem rwsSight = viewpoint(helper, fixture, RWS_SIGHT);
            helper.assertTrue(commander.getActiveWeaponControllers().contains(rwsController),
                    "遥控武器站的火控必须出现在活动集合里");
            helper.assertTrue(commander.getActiveMainWeaponControllers().isEmpty(),
                    "基础组没有主武器频道，按主武器筛会一台都取不到——候选不能用这个口径");
            helper.assertTrue(commander.getViewCandidateController(rwsSight) == rwsController,
                    "没有主武器频道时，副武器火控的视点仍必须配对成功");
            helper.succeed();
        } finally {
            dispose(fixture);
        }
    }

    /**
     * T07：座椅 tick 的视点来源校验读同一份候选——火控发现时保留输入，移出活动集合后清档。
     *
     * <p>夹具用最小的可控子系统而不是真座位：空座位按设计在 tick 里 {@code resetSignalOutputs()}，
     * 会把输入一起清掉，那属于“没人坐”的既有行为，不是这条判据要测的分支。探针继承同一个
     * {@code AbstractControllableSubsystem#onTick}，因此校验分支是生产代码本身。</p>
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void seatTickKeepsOrClearsWeaponDiscoveredViewSource(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        try {
            AbstractViewpointSubsystem optics = viewpoint(helper, fixture, GUNNER_OPTICS);
            WeaponControllerSubsystem main = controller(helper, fixture, MAIN_CONTROLLER);
            SeatSubsystemAttr seatAttr = SeatSubsystemAttr.CODEC.codec().parse(JsonOps.INSTANCE,
                    JsonParser.parseString("{\"definition\":\"machine_max:default_3rd_seat\","
                            + "\"locator\":\"probe\"}")).getOrThrow();
            var host = fixture.turret().subParts.get("sub_part.machine_max.main");
            ViewSourceProbe probe = new ViewSourceProbe(host, "probe_view_source", seatAttr, Map.of());
            ControlGroup base = new ControlGroup("base", null, Map.of(),
                    Map.of("crew_gunner_aim", List.of("global")), Map.of(), Map.of(), Map.of(), List.of());
            probe.setControlGroupSet(new ControlGroupSet(base, List.of(), List.of(), -1));
            probe.setTargetFromNames();
            probe.onAttach();
            helper.assertTrue(probe.getActiveWeaponControllers().contains(main),
                    "探针必须经 crew_gunner_aim 发现主炮火控");
            helper.assertTrue(probe.hasViewCandidate(optics),
                    "火控发现炮手镜后，来源校验必须放行");

            Vec3 point = new Vec3(30, 20, -40);
            helper.assertTrue(probe.setViewInputSignal(
                            new ViewInputSignal(point, Vec2.ZERO, AimTargetState.Mode.WORLD),
                            optics.getSubPart().getId(), optics.getName()),
                    "有效子系统必须接受视点输入");
            helper.assertTrue(main.getSignalValueFrom("crew_gunner_aim", probe) instanceof ViewInputSignal,
                    "视点输入必须到达主炮火控");
            probe.onTick();
            helper.assertTrue(main.getSignalValueFrom("crew_gunner_aim", probe) instanceof ViewInputSignal,
                    "来源是火控发现的视点时，tick 校验不得清掉输入");

            probe.getWeaponControllersByGroup().put("base", List.of());
            probe.onTick();
            helper.assertTrue(main.getSignalValueFrom("crew_gunner_aim", probe) instanceof EmptySignal,
                    "火控移出活动集合后，同一来源必须触发 clearInputSignals");
            helper.succeed();
        } finally {
            dispose(fixture);
        }
    }

    /**
     * T08：服务端校验读同一个按实例的判据——候选内的视点通过，候选外的被拒绝。
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void viewInputMembershipFollowsCandidates(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        try {
            SeatSubsystem loader = seat(helper, fixture, LOADER_SEAT);
            AbstractViewpointSubsystem portMgSight = viewpoint(helper, fixture, PORT_MG_SIGHT);
            helper.assertTrue(loader.hasViewCandidate(portMgSight), "候选内的视点必须通过校验");

            AbstractViewpointSubsystem citv = viewpoint(helper, fixture, CITV_OPTICS);
            helper.assertTrue(!loader.hasViewCandidate(citv),
                    "装填手席的候选不含周视镜，服务端必须拒绝它");

            portMgSight.setActive(false);
            try {
                helper.assertTrue(!loader.hasViewCandidate(portMgSight),
                        "失活的视点必须同时从候选与服务端校验里消失");
            } finally {
                portMgSight.setActive(true);
            }
            helper.succeed();
        } finally {
            dispose(fixture);
        }
    }

    /**
     * T09：固定炮镜的机构来源。判据 A 查候选配对（不被“活动集合第一台”带偏、主 WC 为 null），
     * 判据 B 走真实采样链看实际角是否跟随该火控的机枪高低机，而不是主炮。
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 200)
    public static void fixedSightSamplesThePairedControllerMechanism(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        WeaponControllerSubsystem portMgController = controller(helper, fixture, PORT_MG_CONTROLLER);
        WeaponControllerSubsystem main = controller(helper, fixture, MAIN_CONTROLLER);
        SeatSubsystem loader = seat(helper, fixture, LOADER_SEAT);
        AbstractViewpointSubsystem portMgSight = viewpoint(helper, fixture, PORT_MG_SIGHT);
        helper.assertTrue(portMgSight instanceof SightSubsystem, "夹具前提：并列机枪镜是固定炮镜");
        SightSubsystem sight = (SightSubsystem) portMgSight;

        // 判据 A：配对身份。活动集合前面排一台不发现任何视点的探针，抓“取第一台”这类错误。
        WeaponControllerSubsystem decoy = probe(fixture, "probe_decoy_controller",
                portMgController.attr.getControlOutputTargets(), Map.of());
        loader.getWeaponControllersByGroup().put("base", List.of(decoy, portMgController));
        helper.assertTrue(loader.getActiveMainWeaponController() == null,
                "装填手席没有主武器频道，主 WC 必须为 null");
        helper.assertTrue(loader.getViewCandidateController(sight) == portMgController,
                "该镜必须配对到并列机枪火控，而不是活动集合里排在前面但没发现视点的那台");

        CompletableFuture<Void> result = physics(portMgController, () -> {
            TurretDriverSubsystem portPitch = driver(fixture, "subsystem.m1a2_sep.loader_mg_elevation_driver");
            TurretDriverSubsystem mainPitch = driver(fixture, "subsystem.machine_max.elevation_driver");
            helper.assertTrue(portPitch != null && mainPitch != null,
                    "夹具必须同时提供机枪高低机与主炮高低机");
            ensureJoint(portPitch);
            ensureJoint(mainPitch);
            var pitchBody = portPitch.getSubPart().body;
            var original = pitchBody.getPhysicsRotation(null);
            try {
                // 绕三个轴一起转，保证至少有一个分量落在关节的俯仰轴上；测试不跑求解器。
                pitchBody.setPhysicsRotation(new Quaternion().fromAngles(.2f, .3f, .4f).mult(original));
                for (var subPart : fixture.turret().subParts.values()) {
                    PhysicsBodyExtensionKt.stateOf(subPart.body).update();
                }
                sight.setAimController(portMgController);
                sight.onPostPhysicsTick();
                AimGeometrySnapshot geometry = sight.getAimGeometry();
                helper.assertTrue(geometry != null, "配对成功后必须发布机构几何");
                float portAngle = ((New6Dof) portPitch.connector.joint).getAngles(null).x;
                float mainAngle = ((New6Dof) mainPitch.connector.joint).getAngles(null).x;
                helper.assertTrue(Math.abs(geometry.actualAngles().x - portAngle) < 1e-5,
                        "实际角必须来自机枪高低机，实际 " + geometry.actualAngles().x + " vs " + portAngle);
                helper.assertTrue(Math.abs(portAngle - mainAngle) > 1e-3,
                        "两套机构的角度必须不同，否则这条断言区分不了配对结果：" + portAngle + " / " + mainAngle);
            } finally {
                pitchBody.setPhysicsRotation(original);
                PhysicsBodyExtensionKt.stateOf(pitchBody).update();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(result.isDone(), "等待物理线程的机构采样验证完成");
            try {
                result.join();
            } finally {
                dispose(fixture);
            }
        });
    }

    /**
     * T12：迁移后的内容形态——车长席静态保留周视镜，其余视角来自各自的火控。
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void m1a2ViewpointsComeFromFireControls(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        try {
            SeatSubsystem commander = seat(helper, fixture, COMMANDER_SEAT);
            AbstractViewpointSubsystem citv = viewpoint(helper, fixture, CITV_OPTICS);
            AbstractViewpointSubsystem rwsSight = viewpoint(helper, fixture, RWS_SIGHT);
            AbstractViewpointSubsystem gunnerOptics = viewpoint(helper, fixture, GUNNER_OPTICS);
            WeaponControllerSubsystem citvController = controller(helper, fixture, CITV_CONTROLLER);
            WeaponControllerSubsystem rwsController = controller(helper, fixture, RWS_CONTROLLER);

            List<String> base = candidateNames(commander);
            helper.assertTrue(base.contains(citv.getName()), "车长基础组必须看得到周视镜：" + base);
            helper.assertTrue(base.contains(rwsSight.getName()), "车长基础组必须看得到 RWS 镜：" + base);
            helper.assertTrue(!base.contains(gunnerOptics.getName()), "基础组不得看得到炮手镜：" + base);

            // 周视镜由座位静态声明，摧毁它的火控不影响这个视角。
            citvController.onDestroyed();
            helper.assertTrue(commander.hasViewCandidate(citv),
                    "周视镜是席位自己的观察设备，不得随 citv_controller 的存亡消失");
            // RWS 镜随遥控武器站的火控一起失效。
            rwsController.onDestroyed();
            helper.assertTrue(!commander.hasViewCandidate(rwsSight),
                    "RWS 镜由 rws_controller 声明，火控被摧毁后必须离开候选");

            SeatSubsystem loader = seat(helper, fixture, LOADER_SEAT);
            SeatSubsystem aa = seat(helper, fixture, AA_SEAT);
            AbstractViewpointSubsystem portMgSight = viewpoint(helper, fixture, PORT_MG_SIGHT);
            AbstractViewpointSubsystem aftMgSight = viewpoint(helper, fixture, AFT_MG_SIGHT);
            helper.assertTrue(loader.hasViewCandidate(portMgSight),
                    "装填手席的并列机枪镜必须由 loader_mg_controller 发现");
            helper.assertTrue(aa.hasViewCandidate(aftMgSight),
                    "高射席的车尾机枪镜必须由 aa_controller 发现");
            helper.assertTrue(loader.getViewCandidateController(portMgSight)
                            == controller(helper, fixture, PORT_MG_CONTROLLER),
                    "并列机枪镜必须配对到 loader_mg_controller");
            helper.assertTrue(aa.getViewCandidateController(aftMgSight)
                            == controller(helper, fixture, AA_CONTROLLER),
                    "车尾机枪镜必须配对到 aa_controller");
            helper.succeed();
        } finally {
            dispose(fixture);
        }
    }

    /**
     * T13：连接点名跨 Part 发现。探针火控在炮塔，发现目标写炮塔侧的简单连接点；
     * 对侧端口的配置表由夹具写入并经装配体级重建解析进 targets。
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void connectorNamedDiscoveryCrossesParts(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        try {
            AbstractViewpointSubsystem rwsSight = viewpoint(helper, fixture, RWS_SIGHT);
            WeaponControllerSubsystem probe = probe(fixture, "probe_connector_viewpoint",
                    Map.of(), Map.of("sight_rws", List.of("connector.m1a2_sep.turret_rws")));
            probe.setTargetFromNames();
            fixture.vehicle().subSystemController.addSubsystems(List.of(probe));
            int channelsBefore = rwsSight.getSignalInputChannels().size();

            // 对侧（武器站一侧）端口的配置表：重建时由 setTargetFromNames 解析进 targets。
            AbstractConnector farConnector = fixture.rws().subParts.get("sub_part.m1a2_sep.rws_azimuth")
                    .connectors.get("connector.m1a2_sep.rws_turret");
            farConnector.signalPort.targetNames.put("sight_rws", List.of(RWS_SIGHT));
            fixture.vehicle().onStructureChanged(VehicleStructureChangeEvent.Reason.ATTACH);
            helper.assertTrue(probe.getDiscoveredViewpoints().size() == 1
                            && probe.getDiscoveredViewpoints().getFirst() == rwsSight,
                    "连接点名必须沿挂点找到对侧零件里的视点，实际 " + probe.getDiscoveredViewpoints().size());
            helper.assertTrue(rwsSight.getSignalInputChannels().size() == channelsBefore,
                    "发现只读：连接点线路也不得写视点的输入槽");

            farConnector.signalPort.targetNames.remove("sight_rws");
            fixture.vehicle().onStructureChanged(VehicleStructureChangeEvent.Reason.ATTACH);
            helper.assertTrue(probe.getDiscoveredViewpoints().isEmpty(),
                    "清空对侧配置表并重建后，发现结果必须为空");
            helper.assertTrue(rwsSight.getSignalInputChannels().size() == channelsBefore,
                    "清空后同样不得留下输入槽");
            helper.succeed();
        } finally {
            dispose(fixture);
        }
    }

    /** 按名取炮塔驱动器。 */
    private static TurretDriverSubsystem driver(Fixture fixture, String name) {
        return (TurretDriverSubsystem) fixture.byName().get(name);
    }

    /** 外部车体未入场时补一颗未入世界的关节，只提供 native 参考，不运行求解器。 */
    private static void ensureJoint(TurretDriverSubsystem driver) {
        if (driver.connector.joint != null) {
            return;
        }
        PhysicsRigidBody support = new PhysicsRigidBody(new BoxCollisionShape(.1f), 0);
        support.setPhysicsTransform(driver.getSubPart().body.getTransform(null));
        driver.connector.joint = new New6Dof(driver.getSubPart().body, support, Vector3f.ZERO, Vector3f.ZERO,
                Matrix3f.IDENTITY, Matrix3f.IDENTITY, RotationOrder.YXZ);
    }

    /**
     * 只保留 tick 中视点来源校验的可控子系统：发现表、首跳表与座位同形，但不带座位在空座时
     * 按设计清空输出信号的那一段。
     */
    private static final class ViewSourceProbe extends AbstractControllableSubsystem {
        ViewSourceProbe(ISubsystemHost owner, String name, SeatSubsystemAttr attr,
                        Map<String, List<String>> discoveryTargets) {
            super(owner, name, attr);
            this.cameraDiscoveryTargets = discoveryTargets;
        }

        @Override
        public Map<String, List<String>> getTargetNames() {
            return setUpTargets(new LinkedHashMap<>());
        }
    }
}
