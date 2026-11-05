package io.github.sweetzonzi.machine_max.common.gametest;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.target.Target;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPartType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.HitBoxAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.MaterialAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.HitBox;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * {@link Target} 的锁定判据：哪些碰撞箱可以成为锁定目标、摧毁之后何时退出候选。
 *
 * <p>这里不涉及客户端：锁定状态与标注都在客户端，由 runClient 验收。本文件固定的是
 * {@link HitBox#isLockable()} 这一条契约——没有声明 {@code lockable} 的碰撞箱不可锁、声明了的
 * 在子系统被摧毁后退出候选，而载具本身仍可锁。</p>
 *
 * <p>探针命中框按 {@link ModularDamageGameTest} 的做法就地构造，不依赖内容包里有可锁定的碰撞箱。</p>
 */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TargetLockGameTest {

    private static final String BATCH = "mmTargetLock";
    private static final String TEMPLATE = "part_foundation";

    private TargetLockGameTest() {
    }

    private record Selection(RegularPartType type, String variant) {
    }

    /** 找一个至少带一个子系统的普通部件型号，用来挂探针命中框。 */
    private static Selection partWithSubsystem() {
        return PartDefinitions.regular(false).values().stream()
                .flatMap(type -> type.variants.entrySet().stream()
                        .filter(entry -> entry.getValue().subParts.values().stream()
                                .anyMatch(sub -> !sub.subsystems.isEmpty()))
                        .map(entry -> new Selection(type, entry.getKey())))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("内容包里没有带子系统的普通部件"));
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void notDeclaredHitBoxIsNotLockable(GameTestHelper helper) {
        VehicleCore vehicle = createVehicle(helper);
        try {
            Probe probe = probe(vehicle, false);
            helper.assertTrue(!probe.hitBox().isLockable(), "没有声明 lockable 的碰撞箱不可锁");
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
        }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void lockableHitBoxStopsWhenSubsystemDestroyed(GameTestHelper helper) {
        VehicleCore vehicle = createVehicle(helper);
        try {
            helper.assertTrue(vehicle.isLockable(), "新建的载具本身可作为锁定目标");
            helper.assertTrue(vehicle.point(1).equals(vehicle.getPosition()),
                    "插值系数为 1 时载具真值点即当前形心");

            Probe probe = probe(vehicle, true);
            helper.assertTrue(probe.hitBox().isLockable(), "声明了 lockable 且子系统存活时可锁");
            helper.assertTrue(!probe.hitBox().label().getString().isEmpty(), "命中框标签非空");
            helper.assertTrue(Double.isFinite(probe.hitBox().point(1).x)
                    && Double.isFinite(probe.hitBox().point(1).y)
                    && Double.isFinite(probe.hitBox().point(1).z), "命中框真值点有限");

            probe.subsystem().onDestroyed();
            helper.assertTrue(!probe.hitBox().isLockable(), "子系统被摧毁后同一命中框退出候选");
            helper.assertTrue(vehicle.isLockable(), "子系统摧毁不影响载具本身的锁定");
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
        }
    }

    private static VehicleCore createVehicle(GameTestHelper helper) {
        Selection selected = partWithSubsystem();
        Level level = helper.getLevel();
        RegularPart part = PartFactory.createRegular(selected.type(), selected.variant(), level);
        return new VehicleCore(level, part);
    }

    /** 在载具里挑第一个带子系统的子部件，就地造一个探针命中框。 */
    private static Probe probe(VehicleCore vehicle, boolean lockable) {
        for (RegularPart part : vehicle.partMap.values()) {
            for (SubPart subPart : part.subParts.values()) {
                if (subPart.subsystems.isEmpty()) continue;
                AbstractSubsystem subsystem = subPart.subsystems.values().iterator().next();
                HitBoxAttr attr = new HitBoxAttr("mm_gametest_lock", "box", subsystem.getName(), "",
                        MaterialAttr.DEFAULT_ID, 1f, "true", lockable, null);
                return new Probe(new HitBox(subPart, attr, "mm_gametest_lock", null), subsystem);
            }
        }
        throw new IllegalStateException("选中的部件没有带子系统的子部件");
    }

    private record Probe(HitBox hitBox, AbstractSubsystem subsystem) {
    }
}
