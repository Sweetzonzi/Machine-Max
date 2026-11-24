package io.github.sweetzonzi.machine_max.common.gametest;

import cn.solarmoon.spark_core.physics.body.CollisionGroups;
import cn.solarmoon.spark_core.util.PPhase;
import com.google.gson.JsonParser;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.serialization.JsonOps;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.part.AbstractPartType;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.CouplerSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.CouplerSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.BasicSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.CouplerSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPartType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AdvancedConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.SimpleConnector;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * 同件双 {@code SubPart} 的动态锁扣入口。
 *
 * <p>现有第一方内容没有“未占用的高级连接点 + 不同子零件的未占用简单连接点”组合——所有高级连接点
 * 都被驱动子系统占着。这里用桑塔纳模型的几何拼一份测试专用部件定义：两个子零件分别覆盖车体与
 * 左前门，一对内部连接点验证构造期自动配对，另一对暴露给运行时锁扣。测试定义只发布在 GameTest
 * 进程内，不写入内容包。</p>
 */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CouplerSamePartGameTest {

    private static final String BATCH = "mmCouplerSamePart";
    private static final ResourceLocation FIXTURE_ID =
            ResourceLocation.parse("machine_max:coupler_same_part_fixture");
    private static final String HULL_SUB = "sub_part.machine_max.hull";
    private static final String DOOR_SUB = "sub_part.machine_max.door";
    private static final String ACTIVE = "connector.machine_max.test_active";
    private static final String PASSIVE = "connector.machine_max.test_passive";
    private static final String INTERNAL_ACTIVE = "connector.machine_max.test_internal_active";
    private static final String COUPLER = "subsystem.machine_max.test_same_part_coupler";
    private static final String FORCE_COUPLER = "subsystem.machine_max.test_force_coupler";

    private static final String DEFINITION_JSON = """
            {
              "icon": "machine_max:textures/icon/santana/santana_hull.png",
              "variants": {
                "test": {
                  "model": "machine_max:santana.geo",
                  "textures": "machine_max:textures/part/santana/santana.png",
                  "tags": ["machine_max:type/land", "machine_max:category/structural"],
                  "sub_parts": {
                    "sub_part.machine_max.hull": {
                      "start_bone": "AllHull",
                      "end_bones": [],
                      "mass": 900,
                      "hit_boxes": {
                        "HullHitBox": {"type": "box", "material": "machine_max:structural_steel"}
                      },
                      "connectors": {
                        "connector.machine_max.test_active": {
                          "locator": "hull_hinge_door_lf",
                          "definition": "machine_max:fixed_rigid"
                        },
                        "connector.machine_max.test_internal_active": {
                          "locator": "hull_door_lf_latch",
                          "definition": "machine_max:fixed_rigid",
                          "internal": true
                        }
                      },
                      "subsystems": {
                        "subsystem.machine_max.test_same_part_coupler": {
                          "type": "machine_max:coupler",
                          "definition": "machine_max:santana_latch",
                          "connector": "connector.machine_max.test_active"
                        }
                      }
                    },
                    "sub_part.machine_max.door": {
                      "start_bone": "AllLeftFrontDoor",
                      "end_bones": [],
                      "mass": 24,
                      "hit_boxes": {
                        "LeftFrontDoorCollision": {"type": "box", "material": "machine_max:structural_steel"}
                      },
                      "connectors": {
                        "connector.machine_max.test_passive": {
                          "locator": "door_lf_hinge",
                          "definition": "machine_max:fixed_left"
                        },
                        "connector.machine_max.test_internal_passive": {
                          "locator": "door_lf_latch",
                          "definition": "machine_max:fixed_left",
                          "internal": true
                        }
                      }
                    }
                  }
                }
              }
            }
            """;

    private CouplerSamePartGameTest() {
    }

    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void samePartLockWritesSelfLoopEdge(GameTestHelper helper) {
        RegularPartType type = fixtureType(helper);
        RegularPart part = PartFactory.createRegular(type, "test", helper.getLevel());
        SubPart hullSub = part.subParts.get(HULL_SUB);
        SubPart doorSub = part.subParts.get(DOOR_SUB);
        helper.assertTrue(hullSub != null && doorSub != null, "测试件应有两个子零件");
        AdvancedConnector active = (AdvancedConnector) hullSub.connectors.get(ACTIVE);
        SimpleConnector passive = (SimpleConnector) doorSub.connectors.get(PASSIVE);
        AdvancedConnector internalActive = (AdvancedConnector) hullSub.connectors.get(INTERNAL_ACTIVE);
        helper.assertTrue(active != null && passive != null, "测试件应有对外的一对连接点");
        helper.assertTrue(internalActive != null && internalActive.hasPart(),
                "构造期应自动配对内部连接点");
        helper.assertFalse(internalActive.detach(false), "永久内部连接必须拒绝普通解扣");
        helper.assertTrue(internalActive.hasPart(), "拒绝后内部配对应保持");

        CouplerSubsystem coupler = (CouplerSubsystem) hullSub.findOwnSubsystem(COUPLER);
        helper.assertTrue(coupler != null && coupler.connector == active,
                "测试件应在构造期安装锁扣并解析到主动端");

        VehicleCore vehicle = new VehicleCore(helper.getLevel(), part);
        try {
            helper.assertTrue(vehicle.attachConnector(active, passive, null),
                    "同件动态锁扣应通过装配体入口建立");
            helper.assertTrue(active.hasPart() && passive.hasPart(), "双端引用应互指");
            helper.assertTrue(active.getJoint() != null && active.getJoint() == passive.getJoint(),
                    "同件连接应共享一个关节");
            helper.assertTrue(vehicle.getPartNet().edgesConnecting(part, part).size() == 1,
                    "同件动态连接应写成恰好一条自环边");
            coupler.onTick();
            helper.assertTrue(coupler.isConnected(), "对账应把同件配对写成 connected");

            vehicle.detachConnections(java.util.List.of(
                    com.mojang.datafixers.util.Pair.of(active, passive)), java.util.Map.of());
            helper.assertFalse(active.hasPart() || passive.hasPart(), "解扣应释放双端引用");
            helper.assertTrue(vehicle.getPartNet().edgesConnecting(part, part).isEmpty(),
                    "解扣应删除同件自环边");
        } finally {
            vehicle.onRemoveFromLevel();
        }
        helper.succeed();
    }

    /**
     * 同件锁扣的完整状态恢复：自环边随 {@code ConnectionData} 重建，不依赖锁止距离内的几何猜测。
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 120)
    public static void fullStateRestoreKeepsSamePartLatch(GameTestHelper helper) {
        RegularPartType type = fixtureType(helper);
        RegularPart part = PartFactory.createRegular(type, "test", helper.getLevel());
        SubPart hullSub = part.subParts.get(HULL_SUB);
        AdvancedConnector active = (AdvancedConnector) hullSub.connectors.get(ACTIVE);
        SimpleConnector passive = (SimpleConnector) part.subParts.get(DOOR_SUB).connectors.get(PASSIVE);
        CouplerSubsystem coupler = (CouplerSubsystem) hullSub.findOwnSubsystem(COUPLER);
        helper.assertTrue(coupler != null, "测试件应在构造期安装锁扣");

        VehicleCore vehicle = new VehicleCore(helper.getLevel(), part);
        helper.assertTrue(vehicle.attachConnector(active, passive, null), "同件锁扣应能建立");
        coupler.onTick();
        helper.assertTrue(coupler.isConnected(), "前置条件：逻辑配对应为已连接");
        var data = new io.github.sweetzonzi.machine_max.common.mech.vehicle.data.VehicleData(vehicle).normalized();
        vehicle.onRemoveFromLevel();

        VehicleCore restored = new VehicleCore(helper.getLevel(), data,
                io.github.sweetzonzi.machine_max.common.mech.part.PartRestoreMode.FULL_STATE);
        try {
            RegularPart restoredPart = restored.getPartMap().values().iterator().next();
            AdvancedConnector restoredActive = (AdvancedConnector) restoredPart.subParts
                    .get(HULL_SUB).connectors.get(ACTIVE);
            SimpleConnector restoredPassive = (SimpleConnector) restoredPart.subParts
                    .get(DOOR_SUB).connectors.get(PASSIVE);
            helper.assertTrue(restoredActive != null && restoredPassive != null, "恢复后应保留连接点");
            helper.assertTrue(restoredActive.hasPart()
                            && restoredActive.getAttachedConnector() == restoredPassive,
                    "同件锁扣双端引用应随连接记录恢复");
            helper.assertTrue(restored.getPartNet().edgesConnecting(restoredPart, restoredPart).size() == 1,
                    "同件锁扣应恢复恰好一条自环边");
            var subsystem = restoredPart.findSubsystem(COUPLER);
            helper.assertTrue(subsystem instanceof CouplerSubsystem restoredCoupler
                            && restoredCoupler.isConnected(),
                    "FULL_STATE 应恢复 connected");
        } finally {
            restored.onRemoveFromLevel();
        }
        helper.succeed();
    }

    /**
     * 合拢力的物理效果：范围内两端被拉向彼此；范围外不施力。
     *
     * <p>运行时注册一个 `max_capture_force = 100` 的测试型号，装在同一个同件夹具上。
     * 两个刚体清掉重力、碰撞与旧关节，只留合拢力作用，读一个物理步后的端点速度。</p>
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 120)
    public static void captureForcePullsOnlyInsideRange(GameTestHelper helper) {
        RegularPartType type = fixtureType(helper);
        RegularPart part = PartFactory.createRegular(type, "test", helper.getLevel());
        SubPart hullSub = part.subParts.get(HULL_SUB);
        SubPart doorSub = part.subParts.get(DOOR_SUB);
        AdvancedConnector active = (AdvancedConnector) hullSub.connectors.get(ACTIVE);
        AdvancedConnector internalActive = (AdvancedConnector) hullSub.connectors.get(INTERNAL_ACTIVE);
        helper.assertTrue(active != null && internalActive != null, "测试件应有两对连接点");
        helper.assertTrue(internalActive.hasPart() && internalActive.getJoint() != null,
                "内部连接点应已自动配对");
        internalActive.getJoint().setEnabled(false);

        ResourceLocation model = ResourceLocation.parse("machine_max:coupler_force_test");
        CouplerSubsystemStaticAttr staticAttr = new CouplerSubsystemStaticAttr(
                new BasicSubsystemStaticAttr.BasicAttr(20f, true, false, false),
                0.04f, 0.001f, 100f, 3f, 0.25f, 2f,
                // 本用例只测合拢力，从不解扣；分离冲量取默认值
                CouplerSubsystemStaticAttr.DEFAULT_SEPARATION_IMPULSE, null, false, false,
                CouplerSubsystemStaticAttr.CouplerSoundAttr.DEFAULT);
        MMDynamicRes.STATIC_SUBSYSTEM_ATTRS.put(model, staticAttr);
        MMDynamicRes.SERVER_STATIC_SUBSYSTEM_ATTRS.put(model, staticAttr);
        CouplerSubsystem coupler = new CouplerSubsystemAttr(model, ACTIVE)
                .createSubsystem(hullSub, FORCE_COUPLER);
        hullSub.installSubsystem(FORCE_COUPLER, coupler);

        part.setTransform(new Transform(new Vector3f(0f, 10f, 0f), new Quaternion()));
        shift(doorSub, 0.01f);
        for (var subPart : part.subParts.values()) {
            subPart.body.setGravity(new Vector3f());
            subPart.body.setLinearVelocity(new Vector3f());
            subPart.body.setAngularVelocity(new Vector3f());
            subPart.body.setCollideWithGroups(CollisionGroups.NONE);
        }

        VehicleCore vehicle = new VehicleCore(helper.getLevel(), part);
        vehicle.onAddToLevel();
        var inside = new CompletableFuture<Vector3f>();
        var outside = new CompletableFuture<Vector3f>();
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(coupler::onTick)
                .thenExecute(() -> applyAndRead(coupler, doorSub, inside))
                .thenWaitUntil(() -> helper.assertTrue(inside.isDone(), "等待范围内施力采样"))
                .thenExecute(() -> {
                    Vector3f velocity = inside.join();
                    helper.assertTrue(velocity.x < -0.005f,
                            "捕获范围内的门应被拉向车体，实际速度 " + velocity);
                    // 挪出捕获范围，清零后再次施力
                    coupler.getPhysicsLevel().submitImmediateTask(PPhase.PRE, () -> {
                        for (var subPart : part.subParts.values()) {
                            subPart.body.setLinearVelocity(new Vector3f());
                            subPart.body.setAngularVelocity(new Vector3f());
                        }
                        shift(doorSub, 0.1f);
                        return null;
                    });
                })
                .thenIdle(1)
                .thenExecute(coupler::onTick)
                .thenExecute(() -> applyAndRead(coupler, doorSub, outside))
                .thenWaitUntil(() -> helper.assertTrue(outside.isDone(), "等待范围外采样"))
                .thenExecute(() -> {
                    Vector3f velocity = outside.join();
                    helper.assertTrue(velocity.length() < 0.005f,
                            "捕获范围外不得施力，实际速度 " + velocity);
                })
                .thenExecute(() -> io.github.sweetzonzi.machine_max.common.mech.ObjectManager
                        .removeVehicle(vehicle))
                .thenSucceed();
    }

    /** 物理相位里施力，并在同一个 PRE 任务里把读取排到紧随其后的 POST。 */
    private static void applyAndRead(CouplerSubsystem coupler, SubPart doorSub,
                                     CompletableFuture<Vector3f> result) {
        var physics = coupler.getPhysicsLevel();
        physics.submitImmediateTask(PPhase.PRE, () -> {
            coupler.onPrePhysicsTick();
            // 从物理线程排队，保证读取一定落在本次施力之后的 POST 相位。
            physics.submitImmediateTask(PPhase.POST, () -> {
                result.complete(doorSub.body.getLinearVelocity(null));
                return null;
            });
            return null;
        });
    }

    /** 平移一个子零件的刚体与已发布位姿缓存。 */
    private static void shift(SubPart subPart, float dx) {
        Vector3f position = subPart.body.getPhysicsLocation(null);
        position.x += dx;
        subPart.body.setPhysicsLocation(position);
        Transform transform = subPart.transform;
        subPart.transform = new Transform(transform.getTranslation().add(dx, 0f, 0f), transform.getRotation());
    }

    /** 解码并发布测试专用部件定义；已发布时直接复用同一实例。 */
    private static RegularPartType fixtureType(GameTestHelper helper) {
        var published = PartDefinitions.regular(false).get(FIXTURE_ID);
        if (published != null) return published;
        var decoded = RegularPartType.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString(DEFINITION_JSON)).getOrThrow();
        decoded.setRegistryKey(FIXTURE_ID);
        LinkedHashMap<ResourceLocation, AbstractPartType> all = new LinkedHashMap<>(PartDefinitions.all(false));
        all.put(FIXTURE_ID, decoded);
        var invalid = PartDefinitions.validateAndPublish(false, all);
        helper.assertTrue(invalid.isEmpty(), "重新发布不得剔除既有定义：" + invalid.keySet());
        helper.assertTrue(PartDefinitions.regular(false).get(FIXTURE_ID) == decoded,
                "测试专用定义应通过发布校验");
        return decoded;
    }
}
