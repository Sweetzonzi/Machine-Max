package io.github.sweetzonzi.machine_max.common.gametest;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.ammo.AmmoBelt;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AmmoRef;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoSupplier;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.RegenAmmoRackSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ReloadJobKey;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 再生弹药架的两端账本契约（服务端一侧）：预留不扣库存、兑现只扣一次、撤销不补弹、退货入账，
 * 任意时刻 {@code 0 ≤ R = Σq ≤ N}。
 *
 * <p>客户端的投影是同一份账本的另一个实例，两端的规则在这里固定为同一套判据；客户端那一侧由
 * 双端实机验收覆盖。</p>
 */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AmmoRackLedgerGameTest {
    private static final String BATCH = "mmAmmoRackLedger";
    private static final String TURRET = "t90a:t90a_turret";
    private static final String RACK = "subsystem.t90a.ammo_loader";

    private AmmoRackLedgerGameTest() {
    }

    /**
     * 一条路的作业身份：同一根管（宿主 SubPart 与发射器序号 0）、给定的运输者与提供者两个结构序号。
     */
    private static ReloadJobKey routeKey(RegularPart part, int transporterIndex, int providerIndex) {
        int subPartId = part.rootSubPart.getId();
        return new ReloadJobKey(subPartId, 0, new AmmoRef(subPartId, transporterIndex),
                new AmmoRef(subPartId, providerIndex));
    }

    private static RegenAmmoRackSubsystem rackOf(GameTestHelper helper, RegularPart part) {
        for (Map.Entry<String, AbstractSubsystem> entry : byName(part).entrySet()) {
            if (entry.getKey().equals(RACK)) return (RegenAmmoRackSubsystem) entry.getValue();
        }
        throw new IllegalStateException("夹具部件缺少弹药架 " + RACK);
    }

    private static Map<String, AbstractSubsystem> byName(RegularPart part) {
        Map<String, AbstractSubsystem> map = new LinkedHashMap<>();
        for (AbstractSubsystem subsystem : part.getAllSubsystems()) map.put(subsystem.getName(), subsystem);
        return map;
    }

    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void reserveDeliverAndReturnKeepTheLedgerBalanced(GameTestHelper helper) {
        var type = PartDefinitions.regular(false).get(ResourceLocation.parse(TURRET));
        RegularPart part = PartFactory.createRegular(type, "standard", helper.getLevel());
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), part);
        try {
            RegenAmmoRackSubsystem rack = rackOf(helper, part);
            AmmoBelt spec = rack.getDeliverySpec();
            int oneBelt = spec.length();
            helper.assertTrue(oneBelt > 0, "交付规格应有非零发数");

            // ① 退货入账：库存与"收回"同步上涨
            helper.assertTrue(rack.returnBelt(spec.freshCopy(oneBelt)), "与自身规格一致的一份应被收下");
            int stocked = rack.getAmmoCount();
            helper.assertTrue(stocked == oneBelt, "退货应整份计入库存，实际 " + stocked);

            // ② 预留：库存不动，可分配减少
            ReloadJobKey first = routeKey(part, 1, 2);
            IAmmoSupplier.Grant grant = rack.reserve(first);
            helper.assertTrue(grant != null, "有可分配库存时应授出预留");
            int reserved = grant.reservedCount();
            helper.assertTrue(reserved == Math.min(stocked, oneBelt), "份额取 min(可分配, 规格长度)");
            helper.assertTrue(rack.getAmmoCount() == stocked, "预留不扣实际库存");
            helper.assertTrue(rack.getReservedTotal() == reserved, "R 应等于该作业的份额");
            helper.assertTrue(rack.getAvailableCount() == stocked - reserved, "可分配应为 N − R");
            helper.assertTrue(rack.holdsReservation(first, reserved), "该作业的预留应在");

            // 重复申请沿用原份额，不叠加
            helper.assertTrue(rack.reserve(first).reservedCount() == reserved, "同一身份重复申请不叠加份额");

            // ③ 兑现：库存减 q、R 归零、同身份只兑现一次
            AmmoBelt delivered = rack.deliverReserved(first);
            helper.assertTrue(delivered != null && delivered.remaining() == reserved,
                    "交付实例的发数应等于该作业的份额");
            helper.assertTrue(rack.getAmmoCount() == stocked - reserved, "兑现恰好扣一次库存");
            helper.assertTrue(rack.getReservedTotal() == 0, "兑现后 R 归零");
            helper.assertTrue(rack.deliverReserved(first) == null, "同一身份只兑现一次");
            helper.assertTrue(!rack.holdsReservation(first, reserved), "兑现后承诺结束");

            // ③b 同一根管的同一条路可以连做多轮：账本键不变，上一轮收尾后同一个键能重新授出份额
            if (rack.getAvailableCount() == 0) {
                helper.assertTrue(rack.returnBelt(spec.freshCopy(oneBelt)), "空架应能再收下一份");
            }
            helper.assertTrue(rack.reserve(first) != null, "同一条路上下一轮应能在同一个键上重新受理");
            helper.assertTrue(rack.getReservedTotal() > 0, "新一轮按当轮可分配库存重新取份额");
            rack.releaseReservation(first);
            helper.assertTrue(rack.getReservedTotal() == 0, "撤销新一轮后 R 归零");

            // ④ 撤销：只减少承诺，不补弹
            if (rack.getAvailableCount() == 0) {
                // 交付可能刚好把整份库存取空（容量等于一份的发数）；补一份再验证撤销不改库存
                helper.assertTrue(rack.returnBelt(spec.freshCopy(oneBelt)), "空架应能再收下一份");
            }
            int beforeRelease = rack.getAmmoCount();
            ReloadJobKey second = routeKey(part, 1, 3);
            helper.assertTrue(rack.reserve(second) != null, "撤销量之前应能再授出预留");
            int secondReserved = rack.getReservedTotal();
            rack.releaseReservation(second);
            helper.assertTrue(rack.getReservedTotal() == 0, "撤销后 R 归零");
            helper.assertTrue(rack.getAmmoCount() == beforeRelease, "撤销不补弹");
            helper.assertTrue(secondReserved > 0, "撤销前确实有份额");

            // ⑤ 不变量：0 ≤ R ≤ N
            helper.assertTrue(rack.getReservedTotal() >= 0 && rack.getReservedTotal() <= rack.getAmmoCount(),
                    "R 必须落在 [0, N]");
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
        }
    }

    /**
     * 没有可分配库存时不受理：份额为 0 → 不建作业、不改 R。
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void emptyRackGrantsNothing(GameTestHelper helper) {
        var type = PartDefinitions.regular(false).get(ResourceLocation.parse(TURRET));
        RegularPart part = PartFactory.createRegular(type, "standard", helper.getLevel());
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), part);
        try {
            RegenAmmoRackSubsystem rack = rackOf(helper, part);
            helper.assertTrue(rack.getAmmoCount() == 0, "夹具起始库存为零");
            ReloadJobKey key = routeKey(part, 1, 2);
            helper.assertTrue(rack.reserve(key) == null, "空架不授出预留");
            helper.assertTrue(rack.getReservedTotal() == 0, "失败尝试不产生份额");
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
        }
    }
}
