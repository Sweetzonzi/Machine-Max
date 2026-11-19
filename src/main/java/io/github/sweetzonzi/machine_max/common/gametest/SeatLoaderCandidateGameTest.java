package io.github.sweetzonzi.machine_max.common.gametest;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.registry.MMAttributes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 座位的装填候选快照（服务端一侧）：候选身份来自乘员本人，不是"座位有人"。
 *
 * <p>候选是两端同源的字段——服务端由乘员现算，客户端读同步值。本用例固定服务端那一侧的内容：
 * 没有乘员就没有候选、{@code occupied} 不能代替候选、个人倍率取自人物能力、乘员被移除后候选不再可用。</p>
 */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SeatLoaderCandidateGameTest {
    private static final String BATCH = "mmSeatCandidate";
    private static final String TURRET = "m1a2_sep:m1a2_turret";
    /** 本用例需要挡位倍率可参与比较的装填座位 */
    private static final String SEAT = "subsystem.machine_max.loader_seat";

    private SeatLoaderCandidateGameTest() {
    }

    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void candidateComesFromTheActualOperator(GameTestHelper helper) {
        var type = PartDefinitions.regular(false).get(ResourceLocation.parse(TURRET));
        RegularPart part = PartFactory.createRegular(type, "desert", helper.getLevel());
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), part);
        LivingEntity operator = null;
        try {
            SeatSubsystem seat = null;
            for (AbstractSubsystem subsystem : part.getAllSubsystems()) {
                if (subsystem instanceof SeatSubsystem candidate && candidate.getName().equals(SEAT)) {
                    seat = candidate;
                }
            }
            helper.assertTrue(seat != null, "夹具部件必须有装填座位 " + SEAT);

            helper.assertTrue(seat.loaderCandidate() == null, "空座位没有装填候选");

            // 只有 occupied 标记不算候选：那会凭空造出一个倍率 1.0 的装填手
            seat.occupied = true;
            helper.assertTrue(seat.loaderCandidate() == null, "occupied 不能代替候选身份");
            seat.occupied = false;

            operator = new ArmorStand(EntityType.ARMOR_STAND, helper.getLevel());
            helper.getLevel().addFreshEntity(operator);
            // 装填速度 2.0 = 用时减半（A_eff 0.5）：能力记在属性上，候选快照取的是它的倒数
            crewSpeed(operator).setBaseValue(2.0);
            seat.passenger = operator;
            // 与 onTick 里的逐轮采样走同一个入口：全量补推也经它刷新
            seat.refreshSyncBaseline();

            SeatSubsystem.LoaderCandidate candidate = seat.loaderCandidate();
            helper.assertTrue(candidate != null, "有乘员时应给出候选");
            helper.assertTrue(candidate.personId().equals(operator.getUUID()), "候选身份必须是乘员本人");
            helper.assertTrue(candidate.usable(), "存活的乘员应可作为执行者");
            helper.assertTrue(Math.abs(candidate.multiplier() - 0.5) < 1.0e-6,
                    "有效时间倍率取人物能力，实际 " + candidate.multiplier());

            // 人物能力变化不需要结构变化，下一次采样就跟随
            crewSpeed(operator).setBaseValue(0.5);
            seat.refreshSyncBaseline();
            helper.assertTrue(Math.abs(seat.loaderCandidate().multiplier() - 2.0) < 1.0e-6,
                    "倍率变化应被重新采样");

            // 乘员被移除后不再是可用执行者，但身份仍能报出来（用于校正时对齐）
            operator.discard();
            seat.refreshSyncBaseline();
            SeatSubsystem.LoaderCandidate removed = seat.loaderCandidate();
            helper.assertTrue(removed != null && !removed.usable(), "已移除的乘员不得作为执行者");

            seat.passenger = null;
            helper.assertTrue(seat.loaderCandidate() == null, "清空乘员后候选为空");
            helper.succeed();
        } finally {
            if (operator != null && !operator.isRemoved()) operator.discard();
            vehicle.onRemoveFromLevel();
        }
    }

    /** 乘员身上的装填速度属性；夹具必须能取到实例，取不到说明属性没挂到盔甲架上。 */
    private static AttributeInstance crewSpeed(LivingEntity operator) {
        AttributeInstance attribute = operator.getAttribute(MMAttributes.CREW_RELOAD_SPEED);
        if (attribute == null) throw new IllegalStateException("乘员身上没有 crew_reload_speed 属性实例");
        return attribute;
    }
}
