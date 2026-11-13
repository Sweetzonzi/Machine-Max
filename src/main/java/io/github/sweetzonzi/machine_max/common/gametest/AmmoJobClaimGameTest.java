package io.github.sweetzonzi.machine_max.common.gametest;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.ammo.AmmoBelt;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AmmoLoaderSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.LauncherSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.RegenAmmoRackSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ReloadJob;
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
 * <p>契约有四条：① 有现货时受理并建作业，作业带服务端分配的编号，进度记在管上；② 独占机构受理后
 * 对别的管表现为已占用，但不算自己；③ 释放按编号匹配——旧编号的释放不能摘掉同一条输弹线上的新作业；
 * ④ 没有可交付的库存时不建作业，也就不会占住机器。</p>
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

            // 释放按编号匹配：旧编号不能摘掉同一条输弹线上的新作业
            autoloader.releaseClaim(launcher, job.jobId() + 1);
            helper.assertTrue(autoloader.isClaimedByOther(otherLauncher),
                    "编号不匹配的释放不得摘掉占用");
            helper.assertTrue(launcher.getReloadJob() == job, "编号不匹配的释放不得影响作业本身");
            launcher.onTick();
            helper.assertTrue(job.elapsedTicks() == 2, "占用还在时机构继续放行推进");

            // 收尾：换人或取消先按身份释放占用，机器随即对别人可用
            launcher.setCurrentSupplier(null);
            helper.assertTrue(launcher.getReloadJob() == null, "解除指认必须收尾作业");
            helper.assertTrue(!autoloader.isClaimedByOther(otherLauncher), "收尾后机器必须重新可用");

            // 没有可交付的库存时不建作业、不占槽：等待供弹的请求不进占用表
            for (int i = 0; i < 8 && rack.hasAmmo(); i++) {
                rack.consumeBelt();
            }
            helper.assertTrue(!rack.hasAmmo(), "本例需要把架清空");
            launcher.setCurrentSupplier(autoloader);
            launcher.onTick();
            helper.assertTrue(launcher.getReloadJob() == null, "没有现货时不得建作业");
            helper.assertTrue(!autoloader.isClaimedByOther(otherLauncher), "未被受理的请求不得占用机器");
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
