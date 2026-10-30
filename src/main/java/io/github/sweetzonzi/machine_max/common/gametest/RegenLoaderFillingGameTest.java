package io.github.sweetzonzi.machine_max.common.gametest;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoSupplier;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.LauncherSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.RegenLoaderSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.AbstractSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.LauncherSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.RegenLoaderSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.BasicSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.RegenLoaderSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.module.FeedModuleAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.module.MagazineModuleAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPartType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.SubPartAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.VariantAttr;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Comparator;
import java.util.Map;

/**
 * 再生装弹机两种输弹粒度的服务端验证（T03—T06、T12、T13、T16），不进入物理世界。
 *
 * <p>三条契约：</p>
 * <ul>
 *   <li>逐发模式：每一发都付 {@code feed.reload_time}，取走才扣库存，下一发重新计时。</li>
 *   <li>整匣模式：首次取弹（对接）付一次 {@code reload_time}，之后每一发瞬时送达；
 *       换供给者、机构被打坏、读档都让对接作废，需要重新对接；打空回充不作废。</li>
 *   <li>回充与交付互斥：整批回充期间请求被拒、状态与进度报回充；回充完成后的首次取弹仍是瞬时的。</li>
 * </ul>
 *
 * <p>整匣与整批回充的第一方内容没有消费方，因此这几个用例把定义直接登记进静态属性表，
 * 再用它构造一个真实子系统；断言全部走对外方法，结束后把登记的型号移除。</p>
 */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RegenLoaderFillingGameTest {
    private static final String BATCH = "mmLoaderFilling";

    private RegenLoaderFillingGameTest() {
    }

    private record Selection(RegularPartType type, String variant) {
    }

    /**
     * 同时具备逐发送弹的再生装弹机与发射器的变体：装弹机是断言对象，发射器是真实消费者。<br>
     * 只认逐发送弹的那一型——整匣输弹的型号由一个确定的计时器主导，逐发时序断言要靠合成型号构造。
     */
    private static boolean hasLoaderAndLauncher(VariantAttr variant) {
        boolean loader = false;
        boolean launcher = false;
        for (SubPartAttr subPart : variant.subParts.values()) {
            for (AbstractSubsystemAttr attr : subPart.subsystems.values()) {
                if (attr instanceof RegenLoaderSubsystemAttr regenLoader
                        && regenLoader.getStaticAttribute().getFeed().reloadRoundByRound()) {
                    loader = true;
                }
                if (attr instanceof LauncherSubsystemAttr) launcher = true;
            }
        }
        return loader && launcher;
    }

    private static Selection loaderPart() {
        return PartDefinitions.regular(false).values().stream()
                .flatMap(type -> type.variants.entrySet().stream()
                        .filter(entry -> hasLoaderAndLauncher(entry.getValue()))
                        .map(entry -> new Selection(type, entry.getKey())))
                .sorted(Comparator.comparing((Selection selection) -> selection.type().getRegistryKey().toString())
                        .thenComparing(Selection::variant))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("需要一个既有再生装弹机又有发射器的部件"));
    }

    /** 部件从存档骨架形态进入可工作形态：子系统要靠它通过 {@code isActive()} 的组装许可。 */
    private static void markAssembled(RegularPart part) {
        part.assembling = false;
        part.assemblingProgress = 1f;
    }

    /** 逐发送弹的装弹机，按名字取最小的那台：集合遍历顺序不定，不排序会让用例换个型号跑。 */
    private static RegenLoaderSubsystem loader(RegularPart part) {
        return part.getAllSubsystems().stream()
                .filter(RegenLoaderSubsystem.class::isInstance)
                .map(RegenLoaderSubsystem.class::cast)
                .filter(RegenLoaderSubsystem::isReloadRoundByRound)
                .min(Comparator.comparing(AbstractSubsystem::getName))
                .orElseThrow(() -> new IllegalStateException("部件里没有逐发送弹的再生装弹机"));
    }

    /** 按名字取最小的发射器，与 {@link #loader(RegularPart)} 的选择口径一致。 */
    private static LauncherSubsystem launcher(RegularPart part) {
        return part.getAllSubsystems().stream()
                .filter(LauncherSubsystem.class::isInstance)
                .map(LauncherSubsystem.class::cast)
                .min(Comparator.comparing(AbstractSubsystem::getName))
                .orElseThrow(() -> new IllegalStateException("部件定义声明了发射器，实例表中却没有"));
    }

    private static ProjectileType firstRound(GameTestHelper helper, RegenLoaderSubsystem loader) {
        ProjectileType round = ProjectileType.get(helper.getLevel(), loader.getProjectileTypes().getFirst());
        helper.assertTrue(round != null, "弹链首发的投射物类型必须已注册");
        return round;
    }

    /** 用退弹入口灌库存：承诺阶段不扣库存，退弹是内容包之外唯一能加弹的公开入口。 */
    private static void fill(GameTestHelper helper, RegenLoaderSubsystem loader, ProjectileType round, int rounds) {
        for (int i = 0; i < rounds; i++) {
            helper.assertTrue(loader.returnRound(round), "空弹仓应接受第 " + (i + 1) + " 发退弹");
        }
    }

    /**
     * 登记一个整匣/整批回充型号并据此构造子系统。<br>
     * 型号写进静态属性表，因此子系统的 {@code getStaticAttribute()} 与真实内容包走同一条解析路径；
     * 调用方负责在 finally 里移除该型号。
     */
    private static RegenLoaderSubsystem syntheticLoader(RegularPart part, ResourceLocation definitionId,
                                                        FeedModuleAttr feed, MagazineModuleAttr magazine) {
        MMDynamicRes.STATIC_SUBSYSTEM_ATTRS.put(definitionId, new RegenLoaderSubsystemStaticAttr(
                new BasicSubsystemStaticAttr.BasicAttr(20f, false, false, false),
                RegenLoaderSubsystemStaticAttr.RegenLoaderSoundAttr.DEFAULT,
                feed, magazine));
        // 弹链沿用宿主部件装弹机的首发弹种：发射器的炮闩只吃它认识的弹种
        ResourceLocation round = loader(part).getProjectileTypes().getFirst();
        RegenLoaderSubsystemAttr attr = new RegenLoaderSubsystemAttr(
                definitionId, Map.of(round, 1), "TEST", Map.of());
        // 宿主取部件自身装弹机所在的子部件：新建的子系统不登记进子部件表，只作为独立实例参与断言
        return (RegenLoaderSubsystem) attr.createSubsystem(loader(part).getSubPart(),
                "gametest_" + definitionId.getPath());
    }

    /** 走真实存档接口复制一个子系统，读档后的对接状态因此与真实载入一致。 */
    private static RegenLoaderSubsystem reload(RegenLoaderSubsystem source, String name, CompoundTag data) {
        RegenLoaderSubsystem copy = (RegenLoaderSubsystem) source.attr.createSubsystem(source.getSubPart(), name);
        copy.loadData(data);
        return copy;
    }

    private static void tick(RegenLoaderSubsystem loader, int ticks) {
        for (int i = 0; i < ticks; i++) loader.onTick();
    }

    /** 逐发模式：每一发都付 {@code reload_time}，取走才扣库存（T03）。 */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void roundByRoundPaysPerRound(GameTestHelper helper) {
        Selection selected = loaderPart();
        RegularPart part = PartFactory.createRegular(selected.type(), selected.variant(), helper.getLevel());
        try {
            markAssembled(part);
            RegenLoaderSubsystem loader = loader(part);
            LauncherSubsystem launcher = launcher(part);
            helper.assertTrue(loader.isReloadRoundByRound(), "第一方再生装弹机是逐发输弹");

            int total = loader.getReloadTimeTicks();
            helper.assertTrue(total > 0, "逐发机器的输弹耗时应大于 0，实际 " + total);
            fill(helper, loader, firstRound(helper, loader), 3);

            helper.assertTrue(loader.requestRound(launcher), "有余弹时应受理请求");
            helper.assertTrue(loader.isRoundScheduled(launcher), "受理后应出现交付承诺");
            helper.assertTrue(!loader.isRoundReady(launcher), "刚开始计时时不得报就绪");

            tick(loader, total - 1);
            float progress = loader.getReloadProgress(launcher);
            helper.assertTrue(progress > 0f && progress < 1f, "未走满时进度应在 0 与 1 之间，实际 " + progress);
            helper.assertTrue(!loader.isRoundReady(launcher), "未走满耗时不得转入待取");

            loader.onTick();
            helper.assertTrue(loader.isRoundReady(launcher), "走满耗时后应转入待取");
            helper.assertTrue(loader.getStatus(launcher) == IAmmoSupplier.SupplierStatus.READY, "待取时报 READY");

            int before = loader.getRemainingCount();
            helper.assertTrue(loader.consumeReadyRound(launcher) != null, "就绪时应能取走");
            helper.assertTrue(loader.getRemainingCount() == before - 1, "库存只在取走时扣减");

            helper.assertTrue(loader.requestRound(launcher), "取走后应能再请求");
            helper.assertTrue(!loader.isRoundReady(launcher), "逐发模式下一发要重新计时");
            helper.succeed();
        } finally {
            part.discardUnattached();
        }
    }

    /** 整匣模式：首次请求付对接时间，取走后每一发瞬时（T04）。 */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void wholeMagazineDocksOnceThenIsInstant(GameTestHelper helper) {
        Selection selected = loaderPart();
        RegularPart part = PartFactory.createRegular(selected.type(), selected.variant(), helper.getLevel());
        ResourceLocation definitionId = definitionId("gametest_whole_magazine");
        try {
            markAssembled(part);
            RegenLoaderSubsystem loader = syntheticLoader(part, definitionId,
                    new FeedModuleAttr(1f, 0.5f, false, false),
                    new MagazineModuleAttr(1f, 4, 0f, true, 0f, 0f));
            LauncherSubsystem launcher = launcher(part);
            fill(helper, loader, firstRound(helper, loader), 2);

            int total = loader.getReloadTimeTicks();
            helper.assertTrue(total > 0, "整匣型号的对接耗时应大于 0，实际 " + total);
            helper.assertTrue(loader.requestRound(launcher), "有余弹时应受理请求");
            helper.assertTrue(loader.isRoundScheduled(launcher) && !loader.isRoundReady(launcher),
                    "首次请求要付对接时间");

            tick(loader, total);
            helper.assertTrue(loader.isRoundReady(launcher), "对接完成后那一发就绪");
            helper.assertTrue(loader.consumeReadyRound(launcher) != null, "对接完成后应能取走");

            helper.assertTrue(loader.requestRound(launcher), "已对接后应照常受理");
            helper.assertTrue(loader.isRoundReady(launcher), "已对接后不再计时，请求即就绪");
            helper.assertTrue(loader.getTicksToNextRound(launcher) == 0, "已对接的下一发是 0 tick");
            helper.assertTrue(loader.consumeReadyRound(launcher) != null, "瞬时交付的那一发应能取走");
            helper.succeed();
        } finally {
            MMDynamicRes.STATIC_SUBSYSTEM_ATTRS.remove(definitionId);
            part.discardUnattached();
        }
    }

    /** 换供给者（{@code cancelRound}）后要重新对接（T05）。 */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void rebindingDocksAgain(GameTestHelper helper) {
        Selection selected = loaderPart();
        RegularPart part = PartFactory.createRegular(selected.type(), selected.variant(), helper.getLevel());
        ResourceLocation definitionId = definitionId("gametest_rebind");
        try {
            markAssembled(part);
            RegenLoaderSubsystem loader = syntheticLoader(part, definitionId,
                    new FeedModuleAttr(1f, 0.5f, false, false),
                    new MagazineModuleAttr(1f, 4, 0f, true, 0f, 0f));
            LauncherSubsystem launcher = launcher(part);
            fill(helper, loader, firstRound(helper, loader), 2);
            int total = loader.getReloadTimeTicks();

            loader.requestRound(launcher);
            tick(loader, total);
            helper.assertTrue(loader.consumeReadyRound(launcher) != null, "首次取弹应完成对接");
            helper.assertTrue(loader.getTicksToNextRound(launcher) == 0, "对接后应报 0 tick");

            loader.cancelRound(launcher);
            helper.assertTrue(loader.requestRound(launcher), "释放绑定后应能重新请求");
            helper.assertTrue(!loader.isRoundReady(launcher), "换了供给者要重新付对接时间");
            helper.assertTrue(loader.getTicksToNextRound(launcher) == total, "重新对接应报整段耗时，实际 "
                    + loader.getTicksToNextRound(launcher));

            tick(loader, total);
            helper.assertTrue(loader.isRoundReady(launcher), "重新对接完成后应就绪");
            helper.succeed();
        } finally {
            MMDynamicRes.STATIC_SUBSYSTEM_ATTRS.remove(definitionId);
            part.discardUnattached();
        }
    }

    /** 打空再回充仍是同一个匣：对接标记保持，回充出来的一发瞬时送达（T06）。 */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void emptyMagazineKeepsTheDock(GameTestHelper helper) {
        Selection selected = loaderPart();
        RegularPart part = PartFactory.createRegular(selected.type(), selected.variant(), helper.getLevel());
        ResourceLocation definitionId = definitionId("gametest_empty_dock");
        try {
            markAssembled(part);
            RegenLoaderSubsystem loader = syntheticLoader(part, definitionId,
                    new FeedModuleAttr(1f, 0.5f, false, false),
                    // 每 tick 产一发，回充延迟 0：打空后很快就能观测到回充
                    new MagazineModuleAttr(1f, 2, 1200f, true, 0f, 0f));
            LauncherSubsystem launcher = launcher(part);
            fill(helper, loader, firstRound(helper, loader), 1);

            loader.requestRound(launcher);
            tick(loader, loader.getReloadTimeTicks());
            helper.assertTrue(loader.consumeReadyRound(launcher) != null, "首次取弹应完成对接");

            // 不打 tick 地把余弹取空：回充只在 onTick 里发生，因此取空的这一刻库存确实是零
            int guard = 0;
            while (loader.hasAmmo() && guard++ < 10) {
                helper.assertTrue(loader.requestRound(launcher), "已对接后应照常受理");
                helper.assertTrue(loader.consumeReadyRound(launcher) != null, "已对接后应瞬时取走");
            }
            helper.assertTrue(!loader.hasAmmo(), "取空后弹仓应没有余弹");
            helper.assertTrue(!loader.requestRound(launcher), "空弹仓不受理请求");

            tick(loader, 2);
            helper.assertTrue(loader.hasAmmo(), "回充后应有余弹，实际 " + loader.getRemainingCount());
            helper.assertTrue(loader.requestRound(launcher), "回充后应能请求");
            helper.assertTrue(loader.isRoundReady(launcher), "打空不断开对接：回充的第一发瞬时送达");
            helper.succeed();
        } finally {
            MMDynamicRes.STATIC_SUBSYSTEM_ATTRS.remove(definitionId);
            part.discardUnattached();
        }
    }

    /** 整批回充期间请求被拒、状态与进度是回充的；完成后首次取弹仍瞬时（T16）。 */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void batchRefillBlocksRequestsAndRestoresInstantly(GameTestHelper helper) {
        Selection selected = loaderPart();
        RegularPart part = PartFactory.createRegular(selected.type(), selected.variant(), helper.getLevel());
        ResourceLocation definitionId = definitionId("gametest_batch");
        try {
            markAssembled(part);
            RegenLoaderSubsystem loader = syntheticLoader(part, definitionId,
                    new FeedModuleAttr(1f, 0.5f, false, false),
                    // 整批回充：2 发 ÷ 600 发每分 = 4 tick
                    new MagazineModuleAttr(1f, 2, 600f, false, 0f, 0f));
            LauncherSubsystem launcher = launcher(part);
            fill(helper, loader, firstRound(helper, loader), 1);

            loader.requestRound(launcher);
            tick(loader, loader.getReloadTimeTicks());
            helper.assertTrue(loader.consumeReadyRound(launcher) != null, "首次取弹应完成对接");
            helper.assertTrue(loader.getRemainingCount() == 0, "打空后弹仓应为零");

            loader.onTick();
            helper.assertTrue(!loader.requestRound(launcher), "整批回充期间不受理请求");
            helper.assertTrue(loader.getStatus(launcher) == IAmmoSupplier.SupplierStatus.RELOADING,
                    "回充期间状态报 RELOADING");
            float progress = loader.getReloadProgress(launcher);
            helper.assertTrue(progress > 0f && progress <= 1f, "回充期间进度应报回充进度，实际 " + progress);

            int guard = 0;
            while (loader.getRemainingCount() < loader.getCapacity() && guard++ < 20) loader.onTick();
            helper.assertTrue(loader.getRemainingCount() == loader.getCapacity(), "整批回充应回满容量");

            helper.assertTrue(loader.requestRound(launcher), "回充完成后应能请求");
            helper.assertTrue(loader.isRoundReady(launcher), "回充完成后的首次取弹是瞬时的");
            helper.succeed();
        } finally {
            MMDynamicRes.STATIC_SUBSYSTEM_ATTRS.remove(definitionId);
            part.discardUnattached();
        }
    }

    /** 对接计时中打坏机构：交付作废、未置对接；修好后重新付一次对接时间（T12）。 */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void brokenFeedDuringDockReDocks(GameTestHelper helper) {
        Selection selected = loaderPart();
        RegularPart part = PartFactory.createRegular(selected.type(), selected.variant(), helper.getLevel());
        ResourceLocation definitionId = definitionId("gametest_broken_dock");
        try {
            markAssembled(part);
            RegenLoaderSubsystem loader = syntheticLoader(part, definitionId,
                    new FeedModuleAttr(1f, 0.5f, false, false),
                    new MagazineModuleAttr(1f, 4, 0f, true, 0f, 0f));
            LauncherSubsystem launcher = launcher(part);
            fill(helper, loader, firstRound(helper, loader), 2);

            loader.requestRound(launcher);
            helper.assertTrue(loader.isRoundScheduled(launcher), "对接计时应已开始");
            loader.routeDamage("feed", Float.MAX_VALUE);
            helper.assertTrue(!loader.isRoundScheduled(launcher), "机构损坏必须作废这次对接");
            helper.assertTrue(!loader.isRoundReady(launcher), "坏机构不得报就绪");

            loader.routeRepair("feed", loader.getModule("feed").getMaxDurability());
            helper.assertTrue(loader.isSupplyOperational(), "机构修好后应恢复交付能力");
            helper.assertTrue(loader.requestRound(launcher), "修好后应能重新请求");
            helper.assertTrue(!loader.isRoundReady(launcher), "对接没完成，修好后仍要按 reload_time 重新计时");
            helper.succeed();
        } finally {
            MMDynamicRes.STATIC_SUBSYSTEM_ATTRS.remove(definitionId);
            part.discardUnattached();
        }
    }

    /** 对接标记不持久化：读档后所有消费者重新对接一次（T13）。 */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void reloadedLoaderDocksAgain(GameTestHelper helper) {
        Selection selected = loaderPart();
        RegularPart part = PartFactory.createRegular(selected.type(), selected.variant(), helper.getLevel());
        ResourceLocation definitionId = definitionId("gametest_dock_persist");
        try {
            markAssembled(part);
            RegenLoaderSubsystem loader = syntheticLoader(part, definitionId,
                    new FeedModuleAttr(1f, 0.5f, false, false),
                    new MagazineModuleAttr(1f, 4, 0f, true, 0f, 0f));
            LauncherSubsystem launcher = launcher(part);
            fill(helper, loader, firstRound(helper, loader), 3);

            loader.requestRound(launcher);
            tick(loader, loader.getReloadTimeTicks());
            helper.assertTrue(loader.consumeReadyRound(launcher) != null, "首次取弹应完成对接");

            CompoundTag saved = loader.saveData(new CompoundTag());
            RegenLoaderSubsystem restored = reload(loader, "mm_gametest_dock_restored", saved);
            helper.assertTrue(restored.getRemainingCount() == loader.getRemainingCount(),
                    "库存应随存档恢复");
            helper.assertTrue(restored.requestRound(launcher), "读档后应能请求");
            helper.assertTrue(!restored.isRoundReady(launcher), "对接标记不持久化，读档后要重新对接");
            helper.succeed();
        } finally {
            MMDynamicRes.STATIC_SUBSYSTEM_ATTRS.remove(definitionId);
            part.discardUnattached();
        }
    }

    private static ResourceLocation definitionId(String path) {
        return ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, path);
    }
}
