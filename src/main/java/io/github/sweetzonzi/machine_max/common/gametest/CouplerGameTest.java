package io.github.sweetzonzi.machine_max.common.gametest;

import cn.solarmoon.spark_core.util.PPhase;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.CouplerSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPartType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AdvancedConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.SimpleConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.event.connector.ConnectorDetachEvent;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.InteractBox;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.function.Consumer;

/**
 * 锁扣子系统的连接结果、交互解扣与对账收尾。
 *
 * <p>夹具用桑塔纳车体与左前门：两件之间有一条门轴边，另有一条可有可无的门边锁扣边。
 * 车体上的 {@code machine_max:coupler} 以门边连接点为主动端；门上的连接点是装配期自动配对
 * 会跳过的普通简单端——锁扣必须自己发现它。</p>
 */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CouplerGameTest {

    private static final String BATCH = "mmCoupler";
    private static final String HULL = "machine_max:santana_hull";
    private static final String DOOR = "machine_max:santana_door";
    private static final String HINGE_SIMPLE = "connector.machine_max.axis";
    private static final String HINGE_ADVANCED = "connector.machine_max.left_front_door_axis";
    private static final String LOCK = "connector.machine_max.left_front_door_lock";
    private static final String COUPLER = "subsystem.machine_max.left_front_door_coupler";
    private static final String INTERACT_BOX = "interact.machine_max.left_front_door";

    private CouplerGameTest() {
    }

    /**
     * 预事件否决解扣时，双端引用、关节与图边都必须保持原样。
     *
     * <p>旧入口先移图边再问 Connector，否决时留下「断开了但图边已删除」的分叉；本用例钉住
     * 「只有 Connector 判定成立才改图」。</p>
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void vetoedDetachKeepsJointReferencesAndGraph(GameTestHelper helper) {
        Fixture fixture = Fixture.create(helper);
        VehicleCore vehicle = fixture.vehicle();
        AdvancedConnector lock = fixture.activeLock();
        SimpleConnector passive = fixture.passiveLock();
        try {
            helper.assertTrue(vehicle.attachConnector(lock, passive, null), "锁扣边应能建立");
            int edgesBefore = vehicle.getPartNet().edgesConnecting(fixture.hull(), fixture.door()).size();
            helper.assertTrue(edgesBefore == 2, "门轴与锁扣应各占一条平行边，实际 " + edgesBefore);
            helper.assertTrue(lock.getJoint() != null && lock.getJoint() == passive.getJoint(),
                    "接合后两端应共享同一个关节");

            Consumer<ConnectorDetachEvent.Pre> veto = event -> event.setCanceled(true);
            NeoForge.EVENT_BUS.addListener(veto);
            boolean accepted;
            try {
                accepted = lock.tryDetach();
            } finally {
                NeoForge.EVENT_BUS.unregister(veto);
            }

            helper.assertFalse(accepted, "被否决的解扣不得报告成功");
            helper.assertTrue(lock.hasPart() && passive.hasPart(), "被否决后双端引用必须保留");
            helper.assertTrue(lock.getJoint() != null && lock.getJoint() == passive.getJoint(),
                    "被否决后共享关节必须保留");
            int edgesAfter = vehicle.getPartNet().edgesConnecting(fixture.hull(), fixture.door()).size();
            helper.assertTrue(edgesAfter == edgesBefore, "被否决后图边数必须不变，实际 " + edgesAfter);
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
        }
    }

    /**
     * 陈旧图边：Connector 已被旁路解开、图里却还留着锁扣边时，巡检删边并清除标记。
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void staleGraphEdgeIsDropped(GameTestHelper helper) {
        Fixture fixture = Fixture.create(helper);
        VehicleCore vehicle = fixture.vehicle();
        AdvancedConnector lock = fixture.activeLock();
        SimpleConnector passive = fixture.passiveLock();
        try {
            helper.assertTrue(vehicle.attachConnector(lock, passive, null), "锁扣边应能建立");
            fixture.coupler().onTick();
            helper.assertTrue(fixture.coupler().isConnected(), "前置条件：标记应已补正");
            helper.assertTrue(vehicle.getPartNet()
                            .edgesConnecting(fixture.hull(), fixture.door()).size() == 2,
                    "前置条件：应有门轴与锁扣两条边");

            // 绕过 VehicleCore 直接解扣：引用与关节清了，图边故意留下。
            helper.assertTrue(lock.detach(false), "直接解扣应成立");
            helper.assertTrue(fixture.coupler().isConnected(), "标记尚未对账，暂时仍为 true");

            fixture.coupler().onTick();
            helper.assertFalse(fixture.coupler().isConnected(), "失去配对引用后标记应清除");
            helper.assertTrue(vehicle.getPartNet()
                            .edgesConnecting(fixture.hull(), fixture.door()).size() == 1,
                    "陈旧锁扣边应被巡检删除，只留下门轴边");
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
        }
    }

    /**
     * 双端引用互指但拓扑里缺边：巡检补入边并补正 {@code connected}，不重复接合、不重建关节。
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void missingGraphEdgeIsRepaired(GameTestHelper helper) {
        Fixture fixture = Fixture.create(helper);
        VehicleCore vehicle = fixture.vehicle();
        AdvancedConnector lock = fixture.activeLock();
        SimpleConnector passive = fixture.passiveLock();
        try {
            helper.assertTrue(vehicle.attachConnector(lock, passive, null), "锁扣边应能建立");
            helper.assertTrue(vehicle.getPartNet().removeEdge(
                            com.mojang.datafixers.util.Pair.of((AbstractConnector) lock, passive)),
                    "前置条件：锁扣边应能从图里移除");
            helper.assertTrue(vehicle.getPartNet()
                            .edgesConnecting(fixture.hull(), fixture.door()).size() == 1,
                    "移除后只应剩门轴边");

            fixture.coupler().onTick();
            helper.assertTrue(fixture.coupler().isConnected(), "双端引用互指时 connected 应被补正");
            helper.assertTrue(lock.hasPart() && passive.hasPart(), "补边不得重建或改动 Connector 配对");
            helper.assertTrue(vehicle.getPartNet()
                            .edgesConnecting(fixture.hull(), fixture.door()).size() == 2,
                    "巡检应补回锁扣边");
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
        }
    }

    /**
     * {@code connected} 必须进入子系统同步脏值：服务端对账写 true 后，本轮批次里应出现一个
     * 布尔真值字段。客户端声音基线依赖同一条同步通道。
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void connectedReachesSyncDirtyValues(GameTestHelper helper) {
        Fixture fixture = Fixture.create(helper);
        VehicleCore vehicle = fixture.vehicle();
        try {
            helper.assertTrue(vehicle.attachConnector(fixture.activeLock(), fixture.passiveLock(), null),
                    "锁扣边应能建立");
            fixture.coupler().packDirtyValues(); // 排空构造期的默认字段
            fixture.coupler().onTick();          // 对账把 connected 写成 true
            helper.assertTrue(fixture.coupler().isConnected(), "双端互指应补正标记");
            boolean synced = fixture.coupler().packDirtyValues().stream()
                    .anyMatch(value -> value.value() instanceof Boolean flag && flag);
            helper.assertTrue(synced, "connected 应作为布尔同步字段进入本轮批次");
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
        }
    }

    /**
     * 跨 {@code Part} 的两条平行边：解掉锁扣边再重接，门轴关节实例不变、图边始终恰好两条。
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void lockEdgeCanBeDetachedAndReattachedBesideHinge(GameTestHelper helper) {
        Fixture fixture = Fixture.create(helper);
        VehicleCore vehicle = fixture.vehicle();
        AdvancedConnector lock = fixture.activeLock();
        SimpleConnector passive = fixture.passiveLock();
        try {
            helper.assertTrue(vehicle.attachConnector(lock, passive, null), "锁扣边应能建立");
            var hingeJoint = connector(helper, fixture.hull(), HINGE_ADVANCED).getJoint();
            helper.assertTrue(hingeJoint != null, "门轴关节应已建立");

            vehicle.detachConnections(
                    java.util.List.of(com.mojang.datafixers.util.Pair.of((AbstractConnector) lock, passive)),
                    java.util.Map.of());
            helper.assertFalse(lock.hasPart() && passive.hasPart(), "解扣后锁扣双端应空闲");
            helper.assertTrue(connector(helper, fixture.hull(), HINGE_ADVANCED).getJoint() == hingeJoint,
                    "解扣锁扣不得改动门轴关节实例");
            helper.assertTrue(vehicle.getPartNet()
                            .edgesConnecting(fixture.hull(), fixture.door()).size() == 1,
                    "解扣后只应剩门轴边");

            helper.assertTrue(vehicle.attachConnector(lock, passive, null), "锁扣边应能重接");
            helper.assertTrue(vehicle.getPartNet()
                            .edgesConnecting(fixture.hull(), fixture.door()).size() == 2,
                    "重接后应恰好两条平行边，不得重复写图");
            helper.assertTrue(connector(helper, fixture.hull(), HINGE_ADVANCED).getJoint() == hingeJoint,
                    "重接锁扣不得重建门轴关节");
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
        }
    }

    /**
     * 只剩一条锁扣边的「配重」：断开后两件成为两个连通分量，原部件实例与 UUID 保留。
     *
     * <p>夹具刻意不建门轴边，也不让装配期自动补接：门先单独 {@code addPart}，再用
     * {@code newPart = null} 建立唯一的锁扣边。</p>
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void lockOnlyEdgeSplitsCounterweight(GameTestHelper helper) {
        RegularPart hull = createPart(helper, HULL, null);
        RegularPart door = createPart(helper, DOOR, "left_front");
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), hull);
        AdvancedConnector lock = (AdvancedConnector) connector(helper, hull, LOCK);
        SimpleConnector passive = (SimpleConnector) connector(helper, door, LOCK);
        try {
            vehicle.addPart(door);
            helper.assertTrue(vehicle.attachConnector(lock, passive, null), "唯一锁扣边应能建立");
            helper.assertTrue(vehicle.getPartNet().edgesConnecting(hull, door).size() == 1,
                    "夹具只应有一条锁扣边");

            vehicle.detachConnections(
                    java.util.List.of(com.mojang.datafixers.util.Pair.of((AbstractConnector) lock, passive)),
                    java.util.Map.of());

            helper.assertFalse(lock.hasPart() && passive.hasPart(), "解扣后双端引用应清空");
            helper.assertTrue(vehicle.getPartNet().nodes().size() == 1
                            && vehicle.getPartMap().get(hull.getUuid()) == hull,
                    "原载具应保留车体原实例");
            helper.assertTrue(door.assembly instanceof VehicleCore split && split != vehicle,
                    "最后一条锁扣边断开后，门应成为一个独立载具");
            VehicleCore split = (VehicleCore) door.assembly;
            try {
                helper.assertTrue(split.getPartMap().get(door.getUuid()) == door,
                        "配重件原实例与 UUID 必须保留");
            } finally {
                // 分裂出的载具已进入 ObjectManager 的 level 表；不注销会在下一个服务端 tick
                // 用尚未入世的刚体缓存包围盒算区块保活，包围盒退化为全范围。
                io.github.sweetzonzi.machine_max.common.mech.ObjectManager.removeVehicle(split);
            }
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
        }
    }

    /**
     * 完整状态恢复：跨 {@code Part} 的锁扣边随原 {@code ConnectionData} 重建，
     * {@code connected} 随子系统 NBT 恢复，不需要重新按几何捕获。
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 120)
    public static void fullStateRestoreKeepsLockEdgeAndFlag(GameTestHelper helper) {
        Fixture fixture = Fixture.create(helper);
        VehicleCore vehicle = fixture.vehicle();
        helper.assertTrue(vehicle.attachConnector(fixture.activeLock(), fixture.passiveLock(), null),
                "锁扣边应能建立");
        fixture.coupler().onTick();
        helper.assertTrue(fixture.coupler().isConnected(), "前置条件：逻辑配对应为已连接");

        var data = new io.github.sweetzonzi.machine_max.common.mech.vehicle.data.VehicleData(vehicle).normalized();
        vehicle.onRemoveFromLevel();

        VehicleCore restored = new VehicleCore(helper.getLevel(), data,
                io.github.sweetzonzi.machine_max.common.mech.part.PartRestoreMode.FULL_STATE);
        try {
            RegularPart hull = restored.getPartMap().get(fixture.hull().getUuid());
            RegularPart door = restored.getPartMap().get(fixture.door().getUuid());
            helper.assertTrue(hull != null && door != null, "恢复后应保留原部件身份");
            var subsystem = hull.findSubsystem(COUPLER);
            helper.assertTrue(subsystem instanceof CouplerSubsystem, "恢复后车体仍应安装锁扣");
            helper.assertTrue(((CouplerSubsystem) subsystem).isConnected(),
                    "FULL_STATE 应恢复 connected");
            helper.assertTrue(restored.getPartNet().edgesConnecting(hull, door).size() == 2,
                    "门轴边与锁扣边都应由原连接记录重建");
            helper.assertTrue(connector(helper, hull, LOCK).hasPart()
                            && connector(helper, door, LOCK).hasPart(),
                    "锁扣双端引用应随连接记录恢复");
        } finally {
            restored.onRemoveFromLevel();
        }
        helper.succeed();
    }

    /**
     * 重扣门控：解扣后门还停在锁止窗口内时，连续 tick 也不得重新扣合；只有端点离开重武装距离、
     * 再回到锁止窗口，才允许下一次扣合。
     *
     * <p>夹具没有跑门轴伺服，因此这里直接把门子零件的已发布位姿挪远／挪回来驱动状态机；
     * 真正的关节几何仍由 {@code sampleInPhysics} 按刚体位姿重摆后采样。</p>
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 200)
    public static void rearmWaitsForSeparationThenAllowsRelock(GameTestHelper helper) {
        Fixture fixture = Fixture.create(helper);
        VehicleCore vehicle = fixture.vehicle();
        CouplerSubsystem coupler = fixture.coupler();
        vehicle.onAddToLevel();
        var prepared = new java.util.concurrent.CompletableFuture<Void>();
        var sampled = new java.util.concurrent.CompletableFuture<Void>();
        var afterUnlockSample = new java.util.concurrent.CompletableFuture<Void>();
        var afterRearmSample = new java.util.concurrent.CompletableFuture<Void>();
        var saved = new com.jme3.math.Transform[1];
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> prepareInPhysics(fixture, prepared, true))
                .thenWaitUntil(() -> helper.assertTrue(prepared.isDone(), "等待门摆回关闭位"))
                .thenExecute(() -> sampleInPhysics(fixture, sampled))
                .thenWaitUntil(() -> helper.assertTrue(sampled.isDone(), "等待首次扣合采样"))
                .thenExecute(() -> {
                    coupler.onTick();
                    helper.assertTrue(coupler.isConnected(), "前置条件：首次扣合");
                    helper.assertTrue(coupler.onInteract(null)
                                    == io.github.sweetzonzi.machine_max.common.mech.signal.SignalResult.CONSUME,
                            "解扣应被接受并返回 CONSUME");
                    helper.assertFalse(coupler.isConnected(), "解扣后标记应清除");
                })
                // 仍停在锁止窗口内：等待分离，不得重新扣合
                .thenExecute(coupler::onTick)
                .thenExecute(() -> sampleInPhysics(fixture, afterUnlockSample))
                .thenWaitUntil(() -> helper.assertTrue(afterUnlockSample.isDone(), "等待解扣后采样"))
                .thenExecute(() -> {
                    coupler.onTick();
                    helper.assertFalse(coupler.isConnected(), "未离开重武装距离前不得重新扣合");
                })
                // 把门的已发布位姿挪远：第一次 tick 观察越界并退出等待，第二次 tick 才进入已武装
                .thenExecute(() -> {
                    saved[0] = fixture.door().getRootSubPart().transform.clone();
                    fixture.door().getRootSubPart().transform = new com.jme3.math.Transform(
                            new com.jme3.math.Vector3f(100f, 0f, 0f),
                            saved[0].getRotation().clone());
                    coupler.onTick();
                    coupler.onTick();
                })
                // 挪回关闭位，重新冻结候选、采样并扣合
                .thenExecute(() -> {
                    fixture.door().getRootSubPart().transform = saved[0];
                    coupler.onTick();
                })
                .thenExecute(() -> sampleInPhysics(fixture, afterRearmSample))
                .thenWaitUntil(() -> helper.assertTrue(afterRearmSample.isDone(), "等待重扣采样"))
                .thenExecute(() -> {
                    coupler.onTick();
                    helper.assertTrue(coupler.isConnected(), "越过重武装距离再回到锁止窗口后应允许重扣");
                })
                .thenExecute(vehicle::onRemoveFromLevel)
                .thenSucceed();
    }

    /**
     * 交互恒可用：门边交互区不因锁扣未连接而变暗；一次点击先切换动画，再请求解扣。
     *
     * <p>完整链路是「门上交互区 → 门轴端口 → 车体门轴端口 → 车体锁扣」；解扣成功后返回
     * {@code CONSUME}，双端引用、关节与锁扣图边一起消失，门轴边保留。</p>
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 120)
    public static void interactionUnlocksAndConsumes(GameTestHelper helper) {
        Fixture fixture = Fixture.create(helper);
        VehicleCore vehicle = fixture.vehicle();
        CouplerSubsystem coupler = fixture.coupler();
        vehicle.onAddToLevel();
        var prepared = new java.util.concurrent.CompletableFuture<Void>();
        var sampled = new java.util.concurrent.CompletableFuture<Void>();
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(fixture.hull().getRootSubPart().body.isInWorld()
                                    && fixture.door().getRootSubPart().body.isInWorld(),
                            "两个部件的刚体都应已入世");
                    prepareInPhysics(fixture, prepared, true);
                })
                .thenWaitUntil(() -> helper.assertTrue(prepared.isDone(), "等待门摆回关闭位"))
                .thenExecute(() -> sampleInPhysics(fixture, sampled))
                .thenWaitUntil(() -> helper.assertTrue(sampled.isDone(), "等待物理采样"))
                .thenExecute(() -> {
                    coupler.onTick(); // 消费样本并提交
                    helper.assertTrue(coupler.isConnected(), "门关闭到位后锁扣应扣合");
                    helper.assertTrue(fixture.activeLock().hasPart(), "扣合后主动端应已占用");

                    InteractBox box = fixture.door().findInteractBox(INTERACT_BOX);
                    helper.assertTrue(box != null, "左前门必须有拉手交互区");
                    helper.assertTrue(box.canInteract(), "门边交互区不随锁扣状态变暗");
                    helper.assertTrue(box.getInteractTargets().contains(coupler),
                            "门边交互区应沿门轴端口解析到车体锁扣，实际 "
                                    + box.getInteractTargets().stream().map(AbstractSubsystem::getName).toList());

                    int edgesBefore = vehicle.getPartNet().edgesConnecting(fixture.hull(), fixture.door()).size();
                    box.interact(null);
                    helper.assertFalse(coupler.isConnected(), "点击后锁扣应解开");
                    helper.assertFalse(fixture.activeLock().hasPart(), "解扣后主动端应空闲");
                    helper.assertFalse(fixture.passiveLock().hasPart(), "解扣后被动端应空闲");
                    int edgesAfter = vehicle.getPartNet().edgesConnecting(fixture.hull(), fixture.door()).size();
                    helper.assertTrue(edgesAfter == edgesBefore - 1,
                            "只应删除锁扣边，门轴边保留；之前 " + edgesBefore + "，之后 " + edgesAfter);
                })
                .thenExecute(vehicle::onRemoveFromLevel)
                .thenSucceed();
    }

    /**
     * 巡检发现半截引用（一端仍指向另一端，另一端已为空）时，统一断开把两端一起收干净，
     * 并清除 {@code connected}——不在漂开的位置补建关节，也不留下孤儿图边。
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 120)
    public static void halfReferenceIsCollectedByInspection(GameTestHelper helper) {
        Fixture fixture = Fixture.create(helper);
        VehicleCore vehicle = fixture.vehicle();
        CouplerSubsystem coupler = fixture.coupler();
        vehicle.onAddToLevel();
        var prepared = new java.util.concurrent.CompletableFuture<Void>();
        var sampled = new java.util.concurrent.CompletableFuture<Void>();
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> prepareInPhysics(fixture, prepared))
                .thenWaitUntil(() -> helper.assertTrue(prepared.isDone(), "等待门摆回关闭位"))
                .thenExecute(() -> coupler.onTick())
                .thenExecute(() -> sampleInPhysics(fixture, sampled))
                .thenWaitUntil(() -> helper.assertTrue(sampled.isDone(), "等待物理采样"))
                .thenExecute(() -> {
                    coupler.onTick();
                    helper.assertTrue(coupler.isConnected(), "前置条件：锁扣应已扣合");
                    fixture.passiveLock().setAttachedConnector(null); // 只拆掉被动端引用
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    coupler.onTick();
                    helper.assertFalse(coupler.isConnected(), "半截引用应被统一断开收尾并清除标记");
                    helper.assertFalse(fixture.activeLock().hasPart(), "主动端引用也应被清空");
                    helper.assertTrue(vehicle.getPartNet()
                                    .edgesConnecting(fixture.hull(), fixture.door()).size() == 1,
                            "锁扣图边应随统一断开删除，只留下门轴边");
                })
                .thenExecute(vehicle::onRemoveFromLevel)
                .thenSucceed();
    }

    /**
     * 测试专用：在物理相位开头把门重新摆回门轴位置并清零速度。
     *
     * <p>GameTest 里没有跑 {@code joint_driver} 的伺服，门轴关节的初应力会让门在几个 tick 内
     * 漂开；真实装配中伺服把门压在关闭位。这里在物理步前重摆一次，让随后的采样面对
     * 「门已关好」的真实几何。</p>
     */
    private static void prepareInPhysics(Fixture fixture, java.util.concurrent.CompletableFuture<Void> done) {
        prepareInPhysics(fixture, done, false);
    }

    private static void prepareInPhysics(Fixture fixture, java.util.concurrent.CompletableFuture<Void> done,
                                         boolean freezeCandidate) {
        fixture.coupler().getPhysicsLevel().submitImmediateTask(PPhase.PRE, () -> {
            reseat(fixture);
            // 候选冻结读的是刚体位姿；在同一个物理相位里冻结，避免中间的步进把门挪出捕获范围。
            if (freezeCandidate) fixture.coupler().onTick();
            done.complete(null);
            return null;
        });
    }

    /** 测试专用：重摆后立刻采样，再交给主线程消费。 */
    private static void sampleInPhysics(Fixture fixture, java.util.concurrent.CompletableFuture<Void> done) {
        fixture.coupler().getPhysicsLevel().submitImmediateTask(PPhase.PRE, () -> {
            reseat(fixture);
            fixture.coupler().onPostPhysicsTick();
            done.complete(null);
            return null;
        });
    }

    private static void reseat(Fixture fixture) {
        fixture.hullHinge().adjustTransform(fixture.doorHinge(), 0f);
        // 夹具没有门轴伺服：先停用门轴约束，避免下一步的约束位置修正把刚摆好的门再拉走。
        var hinge = fixture.hullHinge().getJoint();
        if (hinge != null) hinge.setEnabled(false);
        // 零平动／转动系数让刚体在下一步不再被位移积分（不是运动学模式，关节仍可求解），
        // 保证主线程冻结候选时门还停在刚摆好的关闭位。
        for (var subPart : fixture.door().subParts.values()) {
            subPart.body.setLinearVelocity(new com.jme3.math.Vector3f());
            subPart.body.setAngularVelocity(new com.jme3.math.Vector3f());
            subPart.body.setLinearFactor(new com.jme3.math.Vector3f());
            subPart.body.setAngularFactor(new com.jme3.math.Vector3f());
        }
        for (var subPart : fixture.hull().subParts.values()) {
            subPart.body.setLinearVelocity(new com.jme3.math.Vector3f());
            subPart.body.setAngularVelocity(new com.jme3.math.Vector3f());
            subPart.body.setLinearFactor(new com.jme3.math.Vector3f());
            subPart.body.setAngularFactor(new com.jme3.math.Vector3f());
        }
    }

    /** 桑塔纳夹具：车体加左前门，门轴边先接好；锁扣边由用例自行决定何时建立。 */
    private record Fixture(GameTestHelper helper, VehicleCore vehicle, RegularPart hull, RegularPart door,
                           CouplerSubsystem coupler, AdvancedConnector hullHinge, SimpleConnector doorHinge) {

        static Fixture create(GameTestHelper helper) {
            RegularPart hull = createPart(helper, HULL, null);
            RegularPart door = createPart(helper, DOOR, "left_front");
            VehicleCore vehicle = new VehicleCore(helper.getLevel(), hull);
            AdvancedConnector hullHinge = (AdvancedConnector) connector(helper, hull, HINGE_ADVANCED);
            SimpleConnector doorHinge = (SimpleConnector) connector(helper, door, HINGE_SIMPLE);
            // 与真实装配一致：先把新部件摆到目标连接点，再建立连接。少了这一步，两件都停在
            // 各自模型原点上，门轴关节会带着初应力把门拉开，锁扣自然对不上。
            hullHinge.adjustTransform(doorHinge, 0f);
            boolean hinge = vehicle.attachConnector(hullHinge, doorHinge, door);
            helper.assertTrue(hinge, "桑塔纳左前门轴必须能接上");
            // 测试里没有门轴伺服：入世前就停用门轴约束并归零平动／转动系数，避免约束位置修正在
            // 采样前把门拉离关闭位。真实载具由 joint_driver 把门压在目标角上。
            var hingeJoint = hullHinge.getJoint();
            if (hingeJoint != null) hingeJoint.setEnabled(false);
            for (var subPart : door.subParts.values()) {
                subPart.body.setLinearFactor(new com.jme3.math.Vector3f());
                subPart.body.setAngularFactor(new com.jme3.math.Vector3f());
            }
            for (var subPart : hull.subParts.values()) {
                subPart.body.setLinearFactor(new com.jme3.math.Vector3f());
                subPart.body.setAngularFactor(new com.jme3.math.Vector3f());
            }
            var subsystem = hull.findSubsystem(COUPLER);
            helper.assertTrue(subsystem instanceof CouplerSubsystem, "车体必须安装锁扣子系统 " + COUPLER);
            CouplerSubsystem coupler = (CouplerSubsystem) subsystem;
            helper.assertTrue(coupler.connector != null, "锁扣必须解析到主动连接点");
            return new Fixture(helper, vehicle, hull, door, coupler, hullHinge, doorHinge);
        }

        AdvancedConnector activeLock() {
            return (AdvancedConnector) connector(helper, hull, LOCK);
        }

        SimpleConnector passiveLock() {
            return (SimpleConnector) connector(helper, door, LOCK);
        }
    }

    private static RegularPart createPart(GameTestHelper helper, String id, String variant) {
        RegularPartType type = PartDefinitions.regular(false).get(ResourceLocation.parse(id));
        helper.assertTrue(type != null, id + " 的部件定义必须已加载");
        String resolved = variant != null ? variant : type.getVariants().keySet().iterator().next();
        return PartFactory.createRegular(type, resolved, helper.getLevel());
    }

    private static AbstractConnector connector(GameTestHelper helper, RegularPart part, String name) {
        AbstractConnector found = part.findConnector(name);
        helper.assertTrue(found != null, part.name + " 里必须有连接点 " + name);
        return found;
    }
}
