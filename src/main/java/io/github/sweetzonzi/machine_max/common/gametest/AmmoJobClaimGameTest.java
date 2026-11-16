package io.github.sweetzonzi.machine_max.common.gametest;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.ammo.AmmoBelt;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AmmoLoaderSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AmmoPath;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AmmoRef;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.LauncherSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.RegenAmmoRackSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ReloadJob;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ReloadJobKey;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPartType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.event.VehicleStructureChangeEvent;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一条装填作业的受理与占用归属，用 T90A 炮塔上真实相连的一组零件固定。
 *
 * <p>契约有五条：① 有现货时受理并建作业，作业带服务端分配的编号、来源与份额，进度记在管上；
 * ② 受理同时占用来源架的一份弹药——可分配库存减少而实际库存不动；③ 独占机构受理后对别的管表现为
 * 已占用，但不算自己；④ 释放与预留撤销都按身份匹配——旧编号的释放不能摘掉同一条输弹线上的新作业；
 * ⑤ 没有可分配的库存时不建作业，也就不会占住机器；预留被撤销时作业按失效收尾。</p>
 *
 * <p>收尾还发布一对结束结果（原 {@code jobId} ＋ 已交付／未交付），服务端的成功交付与预留撤销各走一条，
 * 客户端据它结束身份匹配的本地作业——两条都在 {@code deliveredJobFillsTheBreechAndPublishesItsResult}
 * 与上面这条用例里固定。</p>
 *
 * <p>这些只能在真实装配体上跑：受理要读上游弹药架的实际库存与发现结果，占用要读作业与指认，
 * 纯 JUnit 既没有子系统宿主也没有这两个子系统之间的接线。用例取 125mm 炮（机械段 7.5 秒）＋它的
 * 自动装弹机，受理后的作业因此在断言之间仍然存在；同车型的其他管用来验证独占机构的占用表现。</p>
 */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AmmoJobClaimGameTest {
    private static final String BATCH = "mmAmmoJobClaim";
    private static final String PART = "t90a:t90a_turret";
    private static final String VARIANT = "standard";
    private static final String LAUNCHER = "subsystem.t90a.launcher";
    private static final String OTHER_LAUNCHER = "subsystem.t90a.coax_launcher";
    private static final String AUTOLOADER = "subsystem.t90a.ammo_autoloader";
    private static final String RACK = "subsystem.t90a.ammo_loader";

    /** 人力直取用例的夹具：M1A2 SEP 炮塔的 120mm 炮，它的自动装弹机已从内容里移除 */
    private static final String MANUAL_PART = "m1a2_sep:m1a2_turret";
    private static final String MANUAL_VARIANT = "desert";
    private static final String MANUAL_LAUNCHER = "subsystem.m1a2_sep.launcher";
    private static final String MANUAL_RACK = "subsystem.m1a2_sep.ammo_loader";
    private static final String MANUAL_SEAT = "subsystem.m1a2_sep.loader_seat";

    private AmmoJobClaimGameTest() {
    }

    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void acceptedJobOwnsTheFeedLineAndReleaseMatchesItsIdentity(GameTestHelper helper) {
        RegularPartType type = PartDefinitions.regular(false).get(ResourceLocation.parse(PART));
        helper.assertTrue(type != null, "T90A 炮塔的部件定义必须已加载");
        RegularPart part = PartFactory.createRegular(type, VARIANT, helper.getLevel());
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), part);
        try {
            Map<String, AbstractSubsystem> byName = collectSubsystems(part);
            LauncherSubsystem launcher = require(helper, byName, LAUNCHER, LauncherSubsystem.class);
            LauncherSubsystem otherLauncher = require(helper, byName, OTHER_LAUNCHER, LauncherSubsystem.class);
            AmmoLoaderSubsystem autoloader = require(helper, byName, AUTOLOADER, AmmoLoaderSubsystem.class);
            RegenAmmoRackSubsystem rack = require(helper, byName, RACK, RegenAmmoRackSubsystem.class);

            // 发现结果先落地：发射器要认可这台装弹机确实是自己的运输者，否则作业走不到受理
            vehicle.onStructureChanged(VehicleStructureChangeEvent.Reason.ATTACH);
            AmmoPath route = requireRoute(helper, launcher, autoloader);
            helper.assertTrue(route.provider() == rack,
                    LAUNCHER + " 的 " + AUTOLOADER + " 喂弹线必须是 " + RACK);
            helper.assertTrue(launcher.isActive() && autoloader.isActive(),
                    "两个子系统都必须在可工作状态");

            // 架按自己的交付规格进一份现货：开局要能接单
            AmmoBelt spec = rack.getDeliverySpec();
            helper.assertTrue(rack.returnBelt(spec.freshCopy(spec.length())),
                    RACK + " 应接受一份与自身规格一致的弹药");
            helper.assertTrue(autoloader.isOperational() && rack.hasAmmo(),
                    AUTOLOADER + " 应能从上游架读到现货并可交付");

            launcher.setCurrentRoute(route);
            helper.assertTrue(launcher.getCurrentRoute() == route, "指认必须先落地");
            launcher.onTick();
            helper.assertTrue(launcher.getCurrentRoute() == route,
                    "受理机关会在发现结果里，tickReload 不得因此丢掉指认");
            ReloadJob job = launcher.getReloadJob();
            helper.assertTrue(job != null, "有现货时应受理一份作业并开始推进");
            helper.assertTrue(job.jobId() > 0, "受理的作业必须带服务端分配的编号");
            helper.assertTrue(job.elapsedTicks() == 1, "受理后的第一个 tick 由机构放行推进一次");
            helper.assertTrue(!autoloader.isClaimedByOther(launcher), "本管自己的占用不算别人的");
            helper.assertTrue(autoloader.isClaimedByOther(otherLauncher),
                    "独占机构受理一条作业后，对别的管必须表现为已占用");

            // 受理同时取得整次作业的预留：可分配库存减少、实际库存不动，来源与规格冻结在作业上
            int stock = rack.getAmmoCount();
            helper.assertTrue(job.reservedCount() > 0, "受理必须取得本次作业的预留份额");
            helper.assertTrue(rack.getReservedTotal() == job.reservedCount(),
                    "预留总量必须等于该作业的份额");
            helper.assertTrue(rack.getAvailableCount() == stock - job.reservedCount(),
                    "可分配库存必须是实际库存减去已预留总量");
            helper.assertTrue(rack.getAmmoCount() == stock, "开工预留不得提前扣减实际库存");
            helper.assertTrue(job.transporter().equals(AmmoRef.ofTransporter(autoloader)),
                    "作业的运输者必须是受理时的那台装弹机");
            helper.assertTrue(job.provider().equals(AmmoRef.ofProvider(rack)),
                    "作业的提供者必须是受理时挑中的那台架");
            helper.assertTrue(AmmoBelt.compareSpec(job.spec(), spec) == 0,
                    "作业的冻结规格必须与来源架的交付规格一致");
            helper.assertTrue(autoloader.holdsCommitment(launcher), "受理后机构必须仍持有这条作业的承诺");
            helper.assertTrue(rack.holdsReservation(job.key(), job.reservedCount()),
                    "受理后提供者必须承认这份预留");

            // 释放与撤销按身份匹配：旧编号不能摘掉同一条输弹线上的新作业
            ReloadJobKey staleKey = new ReloadJobKey(job.key().subPartId(), job.key().launcherIndex(),
                    job.jobId() + 1);
            autoloader.releaseClaim(launcher, staleKey);
            helper.assertTrue(autoloader.isClaimedByOther(otherLauncher),
                    "身份不匹配的释放不得摘掉占用");
            helper.assertTrue(launcher.getReloadJob() == job, "身份不匹配的释放不得影响作业本身");
            launcher.onTick();
            helper.assertTrue(job.elapsedTicks() == 2, "占用还在时机构继续放行推进");

            // 预留被撤销 → 提供者不再承认这份预留，管按失效收尾并让机器重新可用。
            // 承诺分两半：运输者那一半（占用）还在，消失的是提供者那一半
            rack.releaseReservation(job.key());
            helper.assertTrue(!rack.holdsReservation(job.key(), job.reservedCount()),
                    "预留撤销后提供者不得再承认这份预留");
            launcher.onTick();
            helper.assertTrue(launcher.getReloadJob() == null, "承诺失效时必须收尾作业");
            helper.assertTrue(launcher.lastEndedJobId() == job.jobId(),
                    "撤销导致的收尾必须带原作业编号");
            helper.assertTrue(!launcher.lastEndWasDelivered(), "预留被撤销时结束结果必须是未交付");
            helper.assertTrue(rack.getReservedTotal() == 0, "收尾后不得留下预留");
            helper.assertTrue(!autoloader.isClaimedByOther(otherLauncher), "收尾后机器必须重新可用");

            // 没有可分配的库存时不建作业、不占槽：等待供弹的请求不进占用表。
            // 清空库存按新入口重建夹具：一次预留只发一份，反复预留并兑现在直到没有现货
            ReloadJobKey scratch = new ReloadJobKey(launcher.getSubPart().getId(),
                    launcher.getSubPart().subsystemIndex(launcher), 9999);
            while (rack.hasAmmo()) {
                helper.assertTrue(rack.reserve(scratch) != null, "架上还有现货时应能预留在手");
                helper.assertTrue(rack.deliverReserved(scratch) != null, "兑现预留应扣掉库存");
            }
            helper.assertTrue(!rack.hasAmmo(), "本例需要把架清空");
            launcher.onTick();
            helper.assertTrue(launcher.getReloadJob() == null, "没有可分配库存时不得建作业");
            helper.assertTrue(!autoloader.isClaimedByOther(otherLauncher), "未被受理的请求不得占用机器");
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
        }
    }

    /**
     * 走完一条作业：交付成功的结论、原编号与落到本管的内容。
     *
     * <p>契约：① 机械段走完即兑现预留——实际库存按预留份额扣一次，可分配数量与已预留总量都归零；
     * ② 收尾发布带原 {@code jobId} 的已交付结论，客户端据它结束本地作业；③ 交付内容写进本管的膛室
     * （125mm 是单发装填），机器随之重新可用。这里只跑发射器自己的 tick：机构与架不进 tick，
     * 因此这 100 多个 tick 里不会有再生掺进来。</p>
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 300)
    public static void deliveredJobFillsTheBreechAndPublishesItsResult(GameTestHelper helper) {
        RegularPartType type = PartDefinitions.regular(false).get(ResourceLocation.parse(PART));
        helper.assertTrue(type != null, "T90A 炮塔的部件定义必须已加载");
        RegularPart part = PartFactory.createRegular(type, VARIANT, helper.getLevel());
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), part);
        try {
            Map<String, AbstractSubsystem> byName = collectSubsystems(part);
            LauncherSubsystem launcher = require(helper, byName, LAUNCHER, LauncherSubsystem.class);
            LauncherSubsystem otherLauncher = require(helper, byName, OTHER_LAUNCHER, LauncherSubsystem.class);
            AmmoLoaderSubsystem autoloader = require(helper, byName, AUTOLOADER, AmmoLoaderSubsystem.class);
            RegenAmmoRackSubsystem rack = require(helper, byName, RACK, RegenAmmoRackSubsystem.class);
            vehicle.onStructureChanged(VehicleStructureChangeEvent.Reason.ATTACH);

            AmmoBelt spec = rack.getDeliverySpec();
            helper.assertTrue(rack.returnBelt(spec.freshCopy(spec.length())), RACK + " 应接受一份现货");
            int stock = rack.getAmmoCount();
            launcher.setCurrentRoute(requireRoute(helper, launcher, autoloader));
            launcher.onTick();
            ReloadJob job = launcher.getReloadJob();
            helper.assertTrue(job != null, "有现货时应受理一份作业");
            helper.assertTrue(!launcher.lastEndWasDelivered(), "作业还在推进时不得报出已交付结论");

            for (int i = 0; i < 200 && launcher.getReloadJob() != null; i++) launcher.onTick();
            helper.assertTrue(launcher.getReloadJob() == null, "机械段走完后作业必须收尾");
            helper.assertTrue(launcher.lastEndedJobId() == job.jobId(),
                    "结束结果必须带原作业编号，客户端才能认出是哪一条作业结束了");
            helper.assertTrue(launcher.lastEndWasDelivered(), "兑现出一份时结束结果必须是已交付");
            helper.assertTrue(launcher.getChamberedType() != null,
                    "单发装填的管交付后膛内必须有一发");
            helper.assertTrue(rack.getAmmoCount() == stock - job.reservedCount(),
                    "成功交付按预留份额扣一次实际库存");
            helper.assertTrue(rack.getReservedTotal() == 0 && rack.getAvailableCount() == 0,
                    "兑现后既不留预留，也不留可分配库存");
            helper.assertTrue(!autoloader.isClaimedByOther(otherLauncher), "交付后机器必须重新可用");
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
        }
    }

    /**
     * 人力直取：炮塔上没有装弹机，乘员从弹药架取弹并全程装填。
     *
     * <p>契约：① 只有武器型号声明了人工段（{@code human_reload_time}）时弹药架才作为交付目标被登记——
     * 这根管的发现结果里没有自动装弹机，却有自己的弹药架；② 受理的是人工段：标准人力的时长按岗位倍率与
     * 个人倍率折算，机械段尚未开始；③ 人工段走完后才进入机械段，库存持有者没有机械动作，因此紧接着的
     * 那一 tick 就兑现；④ 开工只取预留、不扣实际库存，交付时按预留份额扣一次，膛内有一发，结束结果带原
     * 编号与已交付结论。</p>
     *
     * <p>这里用 M1A2 SEP 炮塔的 120mm 炮：它的自动装弹机已从内容里移除，装填手座位上放一个假乘员即可推进
     * 人工段。人工段只读实际乘员的引用与人工装填能力，因此不必走完整的登乘流程。</p>
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 400)
    public static void manualPathTakesRoundsStraightFromTheRack(GameTestHelper helper) {
        RegularPartType type = PartDefinitions.regular(false).get(ResourceLocation.parse(MANUAL_PART));
        helper.assertTrue(type != null, "M1A2 SEP 炮塔的部件定义必须已加载");
        RegularPart part = PartFactory.createRegular(type, MANUAL_VARIANT, helper.getLevel());
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), part);
        ArmorStand loaderEntity = null;
        try {
            Map<String, AbstractSubsystem> byName = collectSubsystems(part);
            LauncherSubsystem launcher = require(helper, byName, MANUAL_LAUNCHER, LauncherSubsystem.class);
            RegenAmmoRackSubsystem rack = require(helper, byName, MANUAL_RACK, RegenAmmoRackSubsystem.class);
            SeatSubsystem seat = require(helper, byName, MANUAL_SEAT, SeatSubsystem.class);
            vehicle.onStructureChanged(VehicleStructureChangeEvent.Reason.ATTACH);

            helper.assertTrue(launcher.getHumanReloadTimeTicks() > 0, "本管必须声明完整人力流程");

            AmmoBelt spec = rack.getDeliverySpec();
            helper.assertTrue(rack.returnBelt(spec.freshCopy(spec.length())), MANUAL_RACK + " 应接受一份现货");
            int stock = rack.getAmmoCount();

            loaderEntity = new ArmorStand(EntityType.ARMOR_STAND, helper.getLevel());
            helper.getLevel().addFreshEntity(loaderEntity);
            seat.passenger = loaderEntity;

            AmmoPath manual = launcher.getRoutes().stream()
                    .filter(candidate -> candidate.transporter().isHuman())
                    .findFirst().orElse(null);
            helper.assertTrue(manual != null && manual.provider() == rack,
                    "声明了人工段的管必须有一条从弹药架直取的人力路");
            launcher.setCurrentRoute(manual);
            launcher.onTick();
            ReloadJob job = launcher.getReloadJob();
            helper.assertTrue(job != null, "有人与现货时应受理一份作业");
            helper.assertTrue(job.stage() == ReloadJob.Stage.HUMAN, "人力直取的作业从人工段开始");
            helper.assertTrue(job.transporter().isNone(), "人力直取的运输者身份必须为空值（人力运输者）");
            helper.assertTrue(job.provider().equals(AmmoRef.ofProvider(rack)),
                    "人力直取的提供者必须是那台弹药架");
            helper.assertTrue(job.totalTicks() == launcher.getHumanReloadTimeTicks(),
                    "标准人力的本阶段时长等于型号的基准时长");
            helper.assertTrue(job.reservedCount() > 0 && rack.getAmmoCount() == stock,
                    "开工只取预留，不扣实际库存");

            for (int i = 0; i < 300 && launcher.getReloadJob() != null; i++) launcher.onTick();
            helper.assertTrue(launcher.getReloadJob() == null, "走完后作业必须收尾");
            helper.assertTrue(launcher.lastEndedJobId() == job.jobId(), "结束结果必须带原作业编号");
            helper.assertTrue(launcher.lastEndWasDelivered(), "兑现出一份时结束结果必须是已交付");
            helper.assertTrue(launcher.getChamberedType() != null, "单发装填的管交付后膛内必须有一发");
            helper.assertTrue(rack.getAmmoCount() == stock - job.reservedCount(),
                    "成功交付按预留份额扣一次实际库存");
            helper.assertTrue(rack.getReservedTotal() == 0, "兑现后不得留下预留");
            helper.succeed();
        } finally {
            if (loaderEntity != null) loaderEntity.discard();
            vehicle.onRemoveFromLevel();
        }
    }

    /**
     * 取一条以给定装弹机为运输者的路：发现结果必须已经把装弹机登记进来，并且它有一条非空的喂弹线。
     *
     * @param launcher    发射器（路的一方）
     * @param transporter 期望的运输者
     * @return 命中的路；没有时让用例失败
     */
    private static AmmoPath requireRoute(GameTestHelper helper, LauncherSubsystem launcher,
                                         AmmoLoaderSubsystem transporter) {
        AmmoPath route = launcher.getRoutes().stream()
                .filter(candidate -> candidate.transporter() == transporter)
                .findFirst().orElse(null);
        helper.assertTrue(route != null,
                launcher.name + " 必须有一条以 " + transporter.name + " 为运输者的路");
        return route;
    }

    private static Map<String, AbstractSubsystem> collectSubsystems(RegularPart part) {
        Map<String, AbstractSubsystem> byName = new LinkedHashMap<>();
        part.subParts.values().forEach(subPart -> subPart.ownSubsystems().forEach(subsystem -> byName.put(subsystem.getName(), subsystem)));
        return byName;
    }

    private static <T extends AbstractSubsystem> T require(GameTestHelper helper,
                                                          Map<String, AbstractSubsystem> byName,
                                                          String name, Class<T> type) {
        AbstractSubsystem subsystem = byName.get(name);
        helper.assertTrue(subsystem != null, "子系统必须存在：" + name);
        helper.assertTrue(type.isInstance(subsystem), "子系统类型不符：" + name);
        return type.cast(subsystem);
    }
}
