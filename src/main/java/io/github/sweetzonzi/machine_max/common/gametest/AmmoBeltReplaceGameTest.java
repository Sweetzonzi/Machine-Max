package io.github.sweetzonzi.machine_max.common.gametest;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.ammo.AmmoBelt;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AmmoLoaderSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AmmoPath;
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
 * 打空的一份弹链要换新，可回充的那一种也不例外。
 *
 * <p>两条契约：① 已装的一份余量归零后，本管仍然会向路要一份新的（{@code needsBelt} 看"还有没有能打的
 * 一份"，不看"是不是挂着某一份"），因此换一份付的是运输者那一段，而不是等自身回充把一整条按发补回来；
 * ② 打空的那一份在受理换新的那一刻就退回提供者，换新在途时本管的已装为空——那一份若留在原位，自身回充
 * 补出来的一发会把"需要换"判成假，作业随即被"不再需要这一份"收尾、下一秒又打空重开，两份在同一格里来回。
 * 这条只在真实装配体上成立：要一台喂弹的机构、一台有余量的架与一份带自身回充的交付规格，{@code AmmoBelt}
 * 单测没有宿主也没有发现结果。</p>
 *
 * <p>夹具取 K17 炮塔的 2A42：它的交付规格带 {@code recharge}（100／分、延迟 0.3 秒），机构 4 秒，
 * 架容量 150 与一条弹链等长——正好是"整份交付 + 自身回充"这一组合。</p>
 */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AmmoBeltReplaceGameTest {
    private static final String BATCH = "mmAmmoBeltReplace";
    private static final String PART = "machine_max:k17_turret";
    /** K17 的部件 JSON 用单变体写法（variants 直接是一个配置），解码后的变体名是 "default"。 */
    private static final String VARIANT = "default";
    private static final String LAUNCHER = "subsystem.machine_max.2a42";
    private static final String AUTOLOADER = "subsystem.machine_max.2a42_ammo_autoloader";
    private static final String RACK = "subsystem.machine_max.2a42_ammo_loader";

    private AmmoBeltReplaceGameTest() {
    }

    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 200)
    public static void spentRechargeableBeltIsReplacedInsteadOfWaitingForItsRecharge(GameTestHelper helper) {
        RegularPartType type = PartDefinitions.regular(false).get(ResourceLocation.parse(PART));
        helper.assertTrue(type != null, "K17 炮塔的部件定义必须已加载");
        RegularPart part = PartFactory.createRegular(type, VARIANT, helper.getLevel());
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), part);
        try {
            Map<String, AbstractSubsystem> byName = collectSubsystems(part);
            LauncherSubsystem launcher = require(helper, byName, LAUNCHER, LauncherSubsystem.class);
            AmmoLoaderSubsystem autoloader = require(helper, byName, AUTOLOADER, AmmoLoaderSubsystem.class);
            RegenAmmoRackSubsystem rack = require(helper, byName, RACK, RegenAmmoRackSubsystem.class);

            // 发现结果先落地：受理要求运输者与提供者都在本管的发现表里
            vehicle.onStructureChanged(VehicleStructureChangeEvent.Reason.ATTACH);
            AmmoPath route = requireRoute(helper, launcher, autoloader);
            helper.assertTrue(route.provider() == rack,
                    LAUNCHER + " 的 " + AUTOLOADER + " 喂弹线必须是 " + RACK);

            AmmoBelt spec = rack.getDeliverySpec();
            helper.assertTrue(spec.hasRecharge(),
                    RACK + " 交付给本管的必须是一份带自身回充的弹链，本用例的前提");
            helper.assertTrue(rack.returnBelt(spec.freshCopy(spec.length())),
                    RACK + " 应接受一份与自身规格一致的弹药");
            helper.assertTrue(!launcher.hasRoundToFire(), "开局本管没有可发射内容");

            launcher.setCurrentRoute(route);
            launcher.onTick();
            helper.assertTrue(launcher.getReloadJob() != null, "有现货时应受理一份作业");
            for (int i = 0; i < 300 && launcher.getLoadedBelt() == null; i++) launcher.onTick();

            AmmoBelt first = launcher.getLoadedBelt();
            helper.assertTrue(first != null, "机构走完后应装入一份弹链");
            helper.assertTrue(first.hasRecharge(), "装入的那一份必须带自身回充");
            helper.assertTrue(first.remaining() == spec.length(), "库存够时必须整份交付");

            // 打空：余量归零后本管没有可发射内容
            while (first.remaining() > 0) first.consumeNext();
            helper.assertTrue(!launcher.hasRoundToFire(), "打空后本管没有可发射内容");

            // 给架补一份现货，模拟再生补出一条：打空的那一份应当被换新，而不是继续等自身回充
            int room = rack.getCapacity() - rack.getAmmoCount();
            helper.assertTrue(room > 0, RACK + " 此刻应当还有空位放下补充的现货");
            helper.assertTrue(rack.returnBelt(spec.freshCopy(Math.min(room, spec.length()))),
                    RACK + " 应接受补充的现货");
            launcher.onTick();

            ReloadJob job = launcher.getReloadJob();
            helper.assertTrue(job != null, "打空的可回充弹链同样要换一份：受理必须发生");
            helper.assertTrue(launcher.getLoadedBelt() == null,
                    "受理的那一刻就该把打空的一份退回去，换新在途时本管是空手");

            for (int i = 0; i < 300 && launcher.getReloadJob() != null; i++) launcher.onTick();
            helper.assertTrue(launcher.getReloadJob() == null, "第二份走完后作业必须收尾");
            helper.assertTrue(launcher.lastEndedRound() == job.round(), "结束结果必须带原作业轮次");
            helper.assertTrue(launcher.lastEndWasDelivered(), "换新必须按已交付收尾");

            AmmoBelt replaced = launcher.getLoadedBelt();
            helper.assertTrue(replaced != null && replaced != first, "交付后本管挂的必须是新的一份");
            helper.assertTrue(replaced.remaining() > 0, "新的一份必须带余量");
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
            part.discardUnattached();
        }
    }

    /**
     * 取一条以给定装弹机为运输者的路：发现结果必须已经把装弹机登记进来，并且它有一条非空的喂弹线。
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
        part.subParts.values().forEach(subPart -> subPart.ownSubsystems()
                .forEach(subsystem -> byName.put(subsystem.getName(), subsystem)));
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
