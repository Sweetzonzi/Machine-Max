package io.github.sweetzonzi.machine_max.common.gametest;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.ammo.AmmoBelt;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AmmoLoaderSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoSupplier.SupplierRef;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.LauncherSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.RegenAmmoRackSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ReloadJob;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ReloadJobKey;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPartType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.event.VehicleStructureChangeEvent;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一条装填作业的受理与占用归属，用 M1A2 SEP 炮塔上真实相连的一组零件固定。
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
 * 纯 JUnit 既没有子系统宿主也没有这两个子系统之间的接线。用例取 120mm 炮（机械段 5 秒），
 * 受理后的作业因此在断言之间仍然存在。</p>
 */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AmmoJobClaimGameTest {
    private static final String BATCH = "mmAmmoJobClaim";
    private static final String PART = "m1a2_sep:m1a2_turret";
    private static final String LAUNCHER = "subsystem.m1a2_sep.launcher";
    private static final String OTHER_LAUNCHER = "subsystem.m1a2_sep.coax_launcher";
    private static final String AUTOLOADER = "subsystem.m1a2_sep.ammo_autoloader";
    private static final String RACK = "subsystem.m1a2_sep.ammo_loader";

    private AmmoJobClaimGameTest() {
    }

    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void acceptedJobOwnsTheFeedLineAndReleaseMatchesItsIdentity(GameTestHelper helper) {
        RegularPartType type = PartDefinitions.regular(false).get(ResourceLocation.parse(PART));
        helper.assertTrue(type != null, "M1A2 SEP 炮塔的部件定义必须已加载");
        RegularPart part = PartFactory.createRegular(type, "desert", helper.getLevel());
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), part);
        try {
            Map<String, AbstractSubsystem> byName = collectSubsystems(part);
            LauncherSubsystem launcher = require(helper, byName, LAUNCHER, LauncherSubsystem.class);
            LauncherSubsystem otherLauncher = require(helper, byName, OTHER_LAUNCHER, LauncherSubsystem.class);
            AmmoLoaderSubsystem autoloader = require(helper, byName, AUTOLOADER, AmmoLoaderSubsystem.class);
            RegenAmmoRackSubsystem rack = require(helper, byName, RACK, RegenAmmoRackSubsystem.class);

            // 发现结果先落地：发射器要认可这台装弹机确实是自己的来源，否则作业走不到受理
            vehicle.onStructureChanged(VehicleStructureChangeEvent.Reason.ATTACH);
            helper.assertTrue(launcher.getSuppliers().contains(autoloader),
                    LAUNCHER + " 必须发现 " + AUTOLOADER);
            helper.assertTrue(launcher.isActive() && autoloader.isActive(),
                    "两个子系统都必须在可工作状态");

            // 架按自己的交付规格进一份现货：开局要能接单
            AmmoBelt spec = rack.getDeliverySpec();
            helper.assertTrue(rack.returnBelt(spec.freshCopy(spec.length())),
                    RACK + " 应接受一份与自身规格一致的弹药");
            helper.assertTrue(autoloader.isSupplyOperational() && autoloader.hasAmmo(),
                    AUTOLOADER + " 应能从上游架读到现货并可交付");

            launcher.setCurrentSupplier(autoloader);
            helper.assertTrue(launcher.getCurrentSupplier() == autoloader, "指认必须先落地");
            launcher.onTick();
            helper.assertTrue(launcher.getCurrentSupplier() == autoloader,
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
            helper.assertTrue(job.upstreamOrigin().equals(SupplierRef.of(rack)),
                    "作业的实际来源必须是受理时挑中的那台架");
            helper.assertTrue(AmmoBelt.compareSpec(job.spec(), spec) == 0,
                    "作业的冻结规格必须与来源架的交付规格一致");
            helper.assertTrue(autoloader.holdsCommitment(launcher), "受理后机构必须仍持有这条作业的承诺");

            // 释放与撤销按身份匹配：旧编号不能摘掉同一条输弹线上的新作业
            ReloadJobKey staleKey = new ReloadJobKey(job.key().subPartId(), job.key().launcherName(),
                    job.jobId() + 1);
            autoloader.releaseClaim(launcher, staleKey);
            helper.assertTrue(autoloader.isClaimedByOther(otherLauncher),
                    "身份不匹配的释放不得摘掉占用");
            helper.assertTrue(launcher.getReloadJob() == job, "身份不匹配的释放不得影响作业本身");
            launcher.onTick();
            helper.assertTrue(job.elapsedTicks() == 2, "占用还在时机构继续放行推进");

            // 预留被撤销 → 机构不再承诺这条作业，管按失效收尾并让机器重新可用
            rack.releaseReservation(job.key());
            helper.assertTrue(!autoloader.holdsCommitment(launcher), "预留撤销后不得再声称持有承诺");
            launcher.onTick();
            helper.assertTrue(launcher.getReloadJob() == null, "承诺失效时必须收尾作业");
            helper.assertTrue(launcher.lastEndedJobId() == job.jobId(),
                    "撤销导致的收尾必须带原作业编号");
            helper.assertTrue(!launcher.lastEndWasDelivered(), "预留被撤销时结束结果必须是未交付");
            helper.assertTrue(rack.getReservedTotal() == 0, "收尾后不得留下预留");
            helper.assertTrue(!autoloader.isClaimedByOther(otherLauncher), "收尾后机器必须重新可用");

            // 没有可分配的库存时不建作业、不占槽：等待供弹的请求不进占用表
            ReloadJobKey scratch = new ReloadJobKey(launcher.getSubPart().getId(), LAUNCHER, 9999);
            helper.assertTrue(rack.reserve(scratch, Integer.MAX_VALUE) > 0, "清空库存前应能预留整架");
            helper.assertTrue(rack.deliverReserved(scratch) != null, "兑现预留应扣掉整架库存");
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
     * （120mm 是单发装填），机器随之重新可用。这里只跑发射器自己的 tick：机构与架不进 tick，
     * 因此这 100 多个 tick 里不会有再生掺进来。</p>
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 300)
    public static void deliveredJobFillsTheBreechAndPublishesItsResult(GameTestHelper helper) {
        RegularPartType type = PartDefinitions.regular(false).get(ResourceLocation.parse(PART));
        helper.assertTrue(type != null, "M1A2 SEP 炮塔的部件定义必须已加载");
        RegularPart part = PartFactory.createRegular(type, "desert", helper.getLevel());
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
            launcher.setCurrentSupplier(autoloader);
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

    private static Map<String, AbstractSubsystem> collectSubsystems(RegularPart part) {
        Map<String, AbstractSubsystem> byName = new LinkedHashMap<>();
        part.subParts.values().forEach(subPart -> byName.putAll(subPart.subsystems));
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
