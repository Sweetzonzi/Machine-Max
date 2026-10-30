package io.github.sweetzonzi.machine_max.common.gametest;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoSupplier;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.LauncherSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.RegenLoaderSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.WeaponControllerSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.RegenLoaderSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.BasicSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.RegenLoaderSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.module.FeedModuleAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.module.MagazineModuleAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPartType;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 武器控制器的供给绑定决策（T07—T11、T14、T15、T18、T19），不进入物理世界。
 *
 * <p>三条契约：</p>
 * <ul>
 *   <li>独占机器的有效认领最多一条：被别的活跃管认领的机器根本不进候选，两管一台时会摊成
 *       一管有供给、一管为 {@code null}，不进入逐 tick 轮抢。</li>
 *   <li>绑定只在两种情况下换人：当前供给者不再合法（机构坏了、弹种不被炮闩接受、不在选中弹链上），
 *       或候选严格更早拿到下一发（{@code getTicksToNextRound} 更小）。打空但无替代时保持绑定。</li>
 *   <li>换绑不动膛内那发；整匣已对接的机器（下一发 0 tick）从不被迁移。</li>
 * </ul>
 *
 * <p>夹具用 2S38 炮塔：真实的 2A90 发射器与 57mm 弹链装弹机，再按同一弹链登记所需机型的合成装弹机。
 * 控制器实例取自部件本身，两根管经与运行时相同的回调入口登记，路由因此走真实的弹药池与排名。</p>
 */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AmmoRoutingGameTest {
    private static final String BATCH = "mmAmmoRouting";
    private static final String CHANNEL = "ammo_2a90";
    private static final String PART = "2s38_turret";
    /** 子系统实例名就是部件 JSON 的键；发射器与装弹机的名字都带 {@code subsystem.} 前缀 */
    private static final String LAUNCHER = "subsystem.machine_max.2a90";
    private static final String LOADER = "subsystem.machine_max.2a90_ap_loader";

    private AmmoRoutingGameTest() {
    }

    /** 2S38 炮塔：真实的主武器控制器、2A90 发射器与 57mm 装弹机都在这一个部件里。 */
    private static RegularPart turret(GameTestHelper helper) {
        RegularPartType type = PartDefinitions.regular(false).values().stream()
                .filter(candidate -> candidate.getRegistryKey().getPath().equals(PART))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("需要部件 " + PART));
        String variant = type.variants.keySet().stream().sorted().findFirst()
                .orElseThrow(() -> new IllegalStateException("部件 " + PART + " 没有变体"));
        RegularPart part = PartFactory.createRegular(type, variant, helper.getLevel());
        part.assembling = false;
        part.assemblingProgress = 1f;
        return part;
    }

    /** 主武器控制器（名字最小者），它管 2A90。 */
    private static WeaponControllerSubsystem controller(RegularPart part) {
        return controllers(part).getFirst();
    }

    /** 部件里的全部武器控制器，按名字排序：结果与集合遍历顺序无关。 */
    private static List<WeaponControllerSubsystem> controllers(RegularPart part) {
        return part.getAllSubsystems().stream()
                .filter(WeaponControllerSubsystem.class::isInstance)
                .map(WeaponControllerSubsystem.class::cast)
                .sorted(Comparator.comparing(AbstractSubsystem::getName))
                .toList();
    }

    private static LauncherSubsystem launcher(RegularPart part, String name) {
        return part.getAllSubsystems().stream()
                .filter(LauncherSubsystem.class::isInstance)
                .map(LauncherSubsystem.class::cast)
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("部件里没有名为 " + name + " 的发射器"));
    }

    private static RegenLoaderSubsystem loader(RegularPart part, String name) {
        return part.getAllSubsystems().stream()
                .filter(RegenLoaderSubsystem.class::isInstance)
                .map(RegenLoaderSubsystem.class::cast)
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("部件里没有名为 " + name + " 的装弹机"));
    }

    /** 登记一根管到控制器：与握手回调同一入口（callback 频道 + 回调值里的控制频道名）。 */
    private static void register(WeaponControllerSubsystem controller, LauncherSubsystem launcher) {
        controller.getSignalChannel("callback").put(launcher, "fire_input_main");
        controller.onSignalUpdated("callback", launcher);
    }

    private static void fill(GameTestHelper helper, RegenLoaderSubsystem loader, int rounds) {
        ProjectileType round = ProjectileType.get(helper.getLevel(), loader.getProjectileTypes().getFirst());
        helper.assertTrue(round != null, "弹链首发的投射物类型必须已注册");
        for (int i = 0; i < rounds; i++) {
            helper.assertTrue(loader.returnRound(round), "空弹仓应接受第 " + (i + 1) + " 发退弹");
        }
    }

    /**
     * 按 57mm 弹链登记一个合成型号并据此构造装弹机，用于构造「同弹链的另一台机器」。
     * 型号写进静态属性表，因此走与内容包相同的静态解析路径；调用方负责在 finally 里移除。
     */
    private static RegenLoaderSubsystem syntheticLoader(RegularPart part, ResourceLocation definitionId,
                                                        FeedModuleAttr feed, MagazineModuleAttr magazine) {
        MMDynamicRes.STATIC_SUBSYSTEM_ATTRS.put(definitionId, new RegenLoaderSubsystemStaticAttr(
                new BasicSubsystemStaticAttr.BasicAttr(20f, false, false, false),
                RegenLoaderSubsystemStaticAttr.RegenLoaderSoundAttr.DEFAULT,
                feed, magazine));
        ResourceLocation round = loader(part, LOADER).getProjectileTypes().getFirst();
        RegenLoaderSubsystemAttr attr = new RegenLoaderSubsystemAttr(
                definitionId, Map.of(round, 1), "TEST", Map.of());
        return (RegenLoaderSubsystem) attr.createSubsystem(loader(part, LOADER).getSubPart(),
                "gametest_" + definitionId.getPath());
    }

    private static ResourceLocation definitionId(String path) {
        return ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, path);
    }

    private static int holdersOf(IAmmoSupplier supplier, LauncherSubsystem... launchers) {
        int count = 0;
        for (LauncherSubsystem launcher : launchers) {
            if (launcher.getCurrentSupplier() == supplier) count++;
        }
        return count;
    }

    /** 两管对一台独占机器：只有一根管拿到，另一根为 null，不进入逐 tick 轮抢（T07）。 */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void exclusiveMachineServesOneLauncher(GameTestHelper helper) {
        RegularPart a = turret(helper);
        RegularPart b = turret(helper);
        try {
            WeaponControllerSubsystem controller = controller(a);
            LauncherSubsystem first = launcher(a, LAUNCHER);
            LauncherSubsystem second = launcher(b, LAUNCHER);
            RegenLoaderSubsystem machine = loader(a, LOADER);
            fill(helper, machine, 5);
            first.addSupplier(machine, CHANNEL);
            second.addSupplier(machine, CHANNEL);
            register(controller, first);
            register(controller, second);

            controller.onTick();
            helper.assertTrue(holdersOf(machine, first, second) == 1,
                    "独占机器只能绑一根管，实际 " + holdersOf(machine, first, second) + " 根");
            helper.assertTrue(machine.getBoundConsumers().size() == 1,
                    "认领集合里应只有那根管，实际 " + machine.getBoundConsumers().size());

            // 再走一 tick：结果不变，不会在两根管之间来回换
            LauncherSubsystem bound = first.getCurrentSupplier() == machine ? first : second;
            controller.onTick();
            helper.assertTrue(bound.getCurrentSupplier() == machine, "已绑定的那根管应保持绑定");
            helper.assertTrue(holdersOf(machine, first, second) == 1, "不得出现第二根管同时绑上");

            // 让位后的那根管再来一次也抢不走
            controller.onTick();
            helper.assertTrue(holdersOf(machine, first, second) == 1, "被占的机器对后来者始终不可选");
            helper.succeed();
        } finally {
            b.discardUnattached();
            a.discardUnattached();
        }
    }

    /** 绑定者被失能后，机器在下一 tick 可被另一根管选中（T08）。 */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void releasingABindingFreesTheMachine(GameTestHelper helper) {
        RegularPart a = turret(helper);
        RegularPart b = turret(helper);
        try {
            WeaponControllerSubsystem controller = controller(a);
            LauncherSubsystem first = launcher(a, LAUNCHER);
            LauncherSubsystem second = launcher(b, LAUNCHER);
            RegenLoaderSubsystem machine = loader(a, LOADER);
            fill(helper, machine, 5);
            first.addSupplier(machine, CHANNEL);
            second.addSupplier(machine, CHANNEL);
            register(controller, first);
            register(controller, second);

            controller.onTick();
            LauncherSubsystem bound = first.getCurrentSupplier() == machine ? first : second;
            LauncherSubsystem other = bound == first ? second : first;
            helper.assertTrue(holdersOf(machine, first, second) == 1, "先建立独占绑定");

            bound.setActive(false);
            controller.onTick();
            helper.assertTrue(bound.getCurrentSupplier() == null, "失能的管必须释放绑定");
            helper.assertTrue(other.getCurrentSupplier() == machine,
                    "机器应被剩下的那根管接管，实际 " + other.getCurrentSupplier());
            helper.assertTrue(machine.getBoundConsumers().size() == 1,
                    "认领随之转移到新持有者，实际 " + machine.getBoundConsumers().size());
            helper.succeed();
        } finally {
            b.discardUnattached();
            a.discardUnattached();
        }
    }

    /** 当前供给者打空、场上另有余弹机器：换人（现货优先，不等回充）（T09、T19）。 */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void emptyCurrentLosesToAStockedCandidate(GameTestHelper helper) {
        RegularPart part = turret(helper);
        ResourceLocation definitionId = definitionId("gametest_stocked_candidate");
        try {
            WeaponControllerSubsystem controller = controller(part);
            LauncherSubsystem launcher = launcher(part, LAUNCHER);
            RegenLoaderSubsystem empty = loader(part, LOADER);
            helper.assertTrue(!empty.hasAmmo(), "空匣机器的前提是没有余弹");
            // 有现货的同弹链机器：对接耗时 0，因此拿到下一发是 0 tick
            RegenLoaderSubsystem stocked = syntheticLoader(part, definitionId,
                    new FeedModuleAttr(1f, 0f, true, false),
                    new MagazineModuleAttr(1f, 30, 0f, true, 0f, 0f));
            fill(helper, stocked, 10);
            launcher.addSupplier(empty, CHANNEL);
            launcher.addSupplier(stocked, CHANNEL);
            register(controller, launcher);

            // 上一 tick 绑在空匣机器上；它下一 tick 就会回充出一发，但判据不预测回充
            launcher.setCurrentSupplier(empty);
            helper.assertTrue(empty.getTicksToNextRound(launcher) == Integer.MAX_VALUE,
                    "空匣机器此刻给不出弹药");
            empty.onTick();

            controller.onTick();
            helper.assertTrue(launcher.getCurrentSupplier() == stocked,
                    "有余弹的候选应接管，实际 " + launcher.getCurrentSupplier());
            helper.assertTrue(!empty.getBoundConsumers().contains(launcher), "空匣机器应释放认领");
            helper.assertTrue(stocked.getBoundConsumers().contains(launcher), "新机器应记上认领");
            helper.succeed();
        } finally {
            MMDynamicRes.STATIC_SUBSYSTEM_ATTRS.remove(definitionId);
            part.discardUnattached();
        }
    }

    /** 当前供给者打空、场上无替代：保持绑定，不换人也不重复对接（T10）。 */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void emptyCurrentIsKeptWithoutAnotherCandidate(GameTestHelper helper) {
        RegularPart part = turret(helper);
        try {
            WeaponControllerSubsystem controller = controller(part);
            LauncherSubsystem launcher = launcher(part, LAUNCHER);
            RegenLoaderSubsystem empty = loader(part, LOADER);
            helper.assertTrue(!empty.hasAmmo(), "空匣机器的前提是没有余弹");
            launcher.addSupplier(empty, CHANNEL);
            register(controller, launcher);
            launcher.setCurrentSupplier(empty);

            controller.onTick();
            helper.assertTrue(launcher.getCurrentSupplier() == empty,
                    "没有替代品时不应丢掉仍有回充能力的机器，实际 " + launcher.getCurrentSupplier());
            helper.assertTrue(empty.getBoundConsumers().contains(launcher), "保持时认领必须在位");

            controller.onTick();
            helper.assertTrue(launcher.getCurrentSupplier() == empty, "连续 tick 不得抖动");
            helper.succeed();
        } finally {
            part.discardUnattached();
        }
    }

    /** 逐发机器在途交付、场上另有更快的同弹链机器：进度尚浅时当场换（T11）。 */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void shallowInFlightDeliveryYieldsToAFasterMachine(GameTestHelper helper) {
        RegularPart part = turret(helper);
        ResourceLocation definitionId = definitionId("gametest_faster");
        try {
            WeaponControllerSubsystem controller = controller(part);
            LauncherSubsystem launcher = launcher(part, LAUNCHER);
            RegenLoaderSubsystem slow = loader(part, LOADER);
            fill(helper, slow, 5);
            // 更快的那台：每发 0.1 秒（2 tick），而真实 2A90 是 0.45 秒（9 tick）
            RegenLoaderSubsystem fast = syntheticLoader(part, definitionId,
                    new FeedModuleAttr(1f, 0.1f, true, false),
                    new MagazineModuleAttr(1f, 30, 0f, true, 0f, 0f));
            fill(helper, fast, 10);
            helper.assertTrue(fast.getReloadTimeTicks() < slow.getReloadTimeTicks(),
                    "夹具要求候选更快：" + fast.getReloadTimeTicks() + " vs " + slow.getReloadTimeTicks());

            launcher.addSupplier(slow, CHANNEL);
            launcher.addSupplier(fast, CHANNEL);
            register(controller, launcher);
            launcher.setCurrentSupplier(slow);
            helper.assertTrue(slow.requestRound(launcher), "慢机器应受理请求");
            helper.assertTrue(slow.isRoundScheduled(launcher), "慢机器应有在途交付");

            controller.onTick();
            helper.assertTrue(launcher.getCurrentSupplier() == fast,
                    "进度尚浅时更快的机器应接管，实际 " + launcher.getCurrentSupplier());
            helper.assertTrue(!slow.isRoundScheduled(launcher), "被换掉的在途交付应被取消");
            helper.assertTrue(slow.hasAmmo(), "取消交付不扣库存");
            helper.succeed();
        } finally {
            MMDynamicRes.STATIC_SUBSYSTEM_ATTRS.remove(definitionId);
            part.discardUnattached();
        }
    }

    /** 两个控制器各管一根管、都能发现同一台独占机器：只有先 tick 的那个拿到机器（T17）。 */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void crossControllerBindingIsExclusive(GameTestHelper helper) {
        RegularPart a = turret(helper);
        RegularPart b = turret(helper);
        try {
            List<WeaponControllerSubsystem> all = controllers(a);
            helper.assertTrue(all.size() >= 2, "夹具要求部件至少有两个武器控制器");
            WeaponControllerSubsystem main = all.getFirst();
            WeaponControllerSubsystem secondary = all.get(1);
            LauncherSubsystem own = launcher(a, LAUNCHER);
            LauncherSubsystem other = launcher(b, LAUNCHER);
            RegenLoaderSubsystem machine = loader(a, LOADER);
            fill(helper, machine, 5);
            own.addSupplier(machine, CHANNEL);
            other.addSupplier(machine, CHANNEL);
            register(main, own);
            register(secondary, other);

            main.onTick();
            secondary.onTick();
            helper.assertTrue(holdersOf(machine, own, other) == 1,
                    "跨控制器同样只能有一根管绑上，实际 " + holdersOf(machine, own, other));
            helper.assertTrue(machine.getBoundConsumers().size() == 1,
                    "认领集合随供给者走，两个控制器看到同一份认领，实际 "
                            + machine.getBoundConsumers().size());

            main.onTick();
            secondary.onTick();
            helper.assertTrue(holdersOf(machine, own, other) == 1, "两个控制器交替 tick 也不得变成两管");
            helper.succeed();
        } finally {
            b.discardUnattached();
            a.discardUnattached();
        }
    }

    /** 旧存档里两根管指向同一台独占机器：按名字序保留一根，其余置空并重选（T14）。 */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void doubleBindingFromAnOldSaveIsResolved(GameTestHelper helper) {
        RegularPart a = turret(helper);
        RegularPart b = turret(helper);
        try {
            WeaponControllerSubsystem controller = controller(a);
            LauncherSubsystem first = launcher(a, LAUNCHER);
            LauncherSubsystem second = launcher(b, LAUNCHER);
            RegenLoaderSubsystem machine = loader(a, LOADER);
            fill(helper, machine, 5);
            first.addSupplier(machine, CHANNEL);
            second.addSupplier(machine, CHANNEL);
            register(controller, first);
            register(controller, second);

            // 读档后的状态：两根管的 currentSupplier 都恢复成同一台独占机器，认领集合为空
            first.setCurrentSupplier(machine);
            second.setCurrentSupplier(machine);
            helper.assertTrue(machine.getBoundConsumers().isEmpty(), "认领集合不入存档，读档后为空");

            controller.onTick();
            helper.assertTrue(holdersOf(machine, first, second) == 1,
                    "第一次路由应把双重绑定收敛成一条，实际 " + holdersOf(machine, first, second));
            helper.assertTrue(machine.getBoundConsumers().size() == 1, "认领集合应只有一根管");

            controller.onTick();
            helper.assertTrue(holdersOf(machine, first, second) == 1, "收敛后不得再抖回两根");
            helper.succeed();
        } finally {
            b.discardUnattached();
            a.discardUnattached();
        }
    }

    /** 整匣已对接的机器（下一发 0 tick）不被更快的候选迁移（T18）。 */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void dockedWholeMagazineIsNeverMigrated(GameTestHelper helper) {
        RegularPart part = turret(helper);
        ResourceLocation dockedId = definitionId("gametest_docked");
        ResourceLocation fasterId = definitionId("gametest_faster_choice");
        try {
            WeaponControllerSubsystem controller = controller(part);
            LauncherSubsystem launcher = launcher(part, LAUNCHER);
            RegenLoaderSubsystem docked = syntheticLoader(part, dockedId,
                    new FeedModuleAttr(1f, 0.5f, false, false),
                    new MagazineModuleAttr(1f, 10, 0f, true, 0f, 0f));
            RegenLoaderSubsystem faster = syntheticLoader(part, fasterId,
                    new FeedModuleAttr(1f, 0.05f, true, false),
                    new MagazineModuleAttr(1f, 30, 0f, true, 0f, 0f));
            fill(helper, docked, 5);
            fill(helper, faster, 10);

            launcher.addSupplier(docked, CHANNEL);
            launcher.addSupplier(faster, CHANNEL);
            register(controller, launcher);
            launcher.setCurrentSupplier(docked);

            // 完成对接：首次取弹
            helper.assertTrue(docked.requestRound(launcher), "整匣机器应受理请求");
            for (int i = 0; i < docked.getReloadTimeTicks(); i++) docked.onTick();
            helper.assertTrue(docked.consumeReadyRound(launcher) != null, "对接完成后应能取走");
            helper.assertTrue(docked.getTicksToNextRound(launcher) == 0, "已对接的下一发是 0 tick");

            controller.onTick();
            helper.assertTrue(launcher.getCurrentSupplier() == docked,
                    "已对接的机器不该被更快的候选迁移，实际 " + launcher.getCurrentSupplier());
            helper.succeed();
        } finally {
            MMDynamicRes.STATIC_SUBSYSTEM_ATTRS.remove(fasterId);
            MMDynamicRes.STATIC_SUBSYSTEM_ATTRS.remove(dockedId);
            part.discardUnattached();
        }
    }

    /** 换绑不清膛：膛内那发按原弹种留着，新供给者的对接在膛空后首次请求才开始（T15）。 */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void rebindingKeepsTheChamberedRound(GameTestHelper helper) {
        RegularPart part = turret(helper);
        ResourceLocation definitionId = definitionId("gametest_chamber");
        try {
            WeaponControllerSubsystem controller = controller(part);
            LauncherSubsystem launcher = launcher(part, LAUNCHER);
            RegenLoaderSubsystem empty = loader(part, LOADER);
            RegenLoaderSubsystem stocked = syntheticLoader(part, definitionId,
                    new FeedModuleAttr(1f, 0.5f, false, false),
                    new MagazineModuleAttr(1f, 30, 0f, true, 0f, 0f));
            fill(helper, stocked, 10);
            launcher.addSupplier(empty, CHANNEL);
            launcher.addSupplier(stocked, CHANNEL);
            register(controller, launcher);

            ProjectileType round = ProjectileType.get(helper.getLevel(), empty.getProjectileTypes().getFirst());
            helper.assertTrue(launcher.loadRound(round), "空膛时应能压入一发");
            helper.assertTrue(launcher.getCurrentAmmoType() == round, "膛内应有那一发");
            helper.assertTrue(launcher.getFreeCapacity() == 0, "膛满时没有空位");

            launcher.setCurrentSupplier(empty);
            controller.onTick();
            helper.assertTrue(launcher.getCurrentSupplier() == stocked, "换绑到有余弹的机器");
            helper.assertTrue(launcher.getCurrentAmmoType() == round, "换绑不动膛内那发");
            helper.assertTrue(launcher.getFreeCapacity() == 0, "膛内那发仍在位");
            helper.assertTrue(!stocked.isRoundScheduled(launcher), "膛空之前不该有新供给者的对接");
            helper.succeed();
        } finally {
            MMDynamicRes.STATIC_SUBSYSTEM_ATTRS.remove(definitionId);
            part.discardUnattached();
        }
    }
}
