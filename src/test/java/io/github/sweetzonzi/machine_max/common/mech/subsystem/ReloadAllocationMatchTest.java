package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.common.mech.ammo.AmmoBelt;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分叉判定的两条判据：交付规格的同一性与作业冻结分配的同一性。
 *
 * <p>两者判的都是"这一份权威数据说的还是同一条作业吗"。判宽了会把两条不同的作业当成一条，
 * 客户端于是按另一份规格继续推进、交付内容与权威不同；判窄了会把同一条作业的离散更新当成分叉，
 * 每次进度更新都触发一次重建。因此断言既有"必须相同"的一侧，也有"必须不同"的一侧。</p>
 *
 * <p>这些断言只依赖 {@link AmmoBelt} 与 {@link ReloadJob} 的值语义，不需要服务端或物理世界。</p>
 */
class ReloadAllocationMatchTest {

    private static final ResourceLocation AMMO = ResourceLocation.parse("machine_max:test_ap");
    private static final ResourceLocation CONTAINER = ResourceLocation.parse("machine_max:test_rack");
    private static final AmmoRef TRANSPORTER = new AmmoRef(4, 2);
    private static final AmmoRef PROVIDER = new AmmoRef(7, 1);
    private static final ReloadJobKey KEY = new ReloadJobKey(12, 0, 42);

    private static AmmoBelt belt(ResourceLocation containerId, String label, int length,
                                 int remaining, AmmoBelt.Recharge recharge) {
        return new AmmoBelt(containerId, label, List.of(new AmmoBelt.PatternEntry(AMMO, 1)),
                length, remaining, recharge);
    }

    private static AmmoBelt belt() {
        return belt(CONTAINER, "test belt", 8, 8, new AmmoBelt.Recharge(30f, 0f, 0f));
    }

    private static ReloadJob job(AmmoBelt spec, int reserved, AmmoRef provider) {
        return new ReloadJob(KEY, TRANSPORTER, provider, spec, reserved, 60);
    }

    private static ReloadJobSpec snapshot(AmmoBelt spec, int reserved, AmmoRef provider) {
        return new ReloadJobSpec(KEY.jobId(), TRANSPORTER, provider, spec, reserved);
    }

    // ==================== 交付规格同一性 ====================

    /**
     * 同一条作业的重复确认只改余量与回充运行态，规格本身不变，因此必须判成同一份交付。
     */
    @Test
    void identicalSpecWithLessRemainingIsStillTheSameDelivery() {
        AmmoBelt full = belt(CONTAINER, "test belt", 8, 8, new AmmoBelt.Recharge(30f, 0f, 0f));
        AmmoBelt partiallyDrained = belt(CONTAINER, "test belt", 8, 3, new AmmoBelt.Recharge(30f, 0f, 0f));

        assertTrue(full.sameDeliverySpec(partiallyDrained), "余量不参与交付规格的比较");
        assertTrue(full.sameDeliverySpec(full), "同一实例应判成同一份交付");
    }

    /**
     * 回充配置必须比到数值本身：排序用的全序只区分"有没有回充"，用它当收据判据会把回充速率不同的
     * 两条作业当成同一条。
     */
    @Test
    void rechargeConfigurationIsPartOfTheDeliverySpec() {
        AmmoBelt slow = belt(CONTAINER, "test belt", 8, 8, new AmmoBelt.Recharge(30f, 0f, 0f));
        AmmoBelt fast = belt(CONTAINER, "test belt", 8, 8, new AmmoBelt.Recharge(120f, 0f, 0f));
        AmmoBelt withDelay = belt(CONTAINER, "test belt", 8, 8, new AmmoBelt.Recharge(30f, 40f, 0f));
        AmmoBelt withCost = belt(CONTAINER, "test belt", 8, 8, new AmmoBelt.Recharge(30f, 0f, 500f));
        AmmoBelt plain = belt(CONTAINER, "test belt", 8, 8, null);

        assertEquals(0, AmmoBelt.compareSpec(slow, fast), "排序用的全序不区分回充速率");
        assertFalse(slow.sameDeliverySpec(fast), "交付规格必须比到回充速率");
        assertFalse(slow.sameDeliverySpec(withDelay), "交付规格必须比到回充延迟");
        assertFalse(slow.sameDeliverySpec(withCost), "交付规格必须比到每发耗电");
        assertFalse(slow.sameDeliverySpec(plain), "有无回充是规格的一部分");
    }

    /** 容器身份、标签、模式与长度各自都是规格的一部分。 */
    @Test
    void containerLabelPatternAndLengthArePartOfTheDeliverySpec() {
        AmmoBelt base = belt();
        AmmoBelt.Recharge recharge = new AmmoBelt.Recharge(30f, 0f, 0f);

        assertFalse(base.sameDeliverySpec(belt(null, "test belt", 8, 8, recharge)), "容器身份不同即不同规格");
        assertFalse(base.sameDeliverySpec(belt(CONTAINER, "other label", 8, 8, recharge)), "标签不同即不同规格");
        assertFalse(base.sameDeliverySpec(belt(CONTAINER, "test belt", 12, 8, recharge)), "长度不同即不同规格");
        assertFalse(base.sameDeliverySpec(new AmmoBelt(CONTAINER, "test belt",
                List.of(new AmmoBelt.PatternEntry(AMMO, 2)), 8, 8, recharge)), "模式条目不同即不同规格");
    }

    /** 没有可比的另一份规格时不是同一份交付。 */
    @Test
    void missingSpecIsNeverTheSameDelivery() {
        assertFalse(belt().sameDeliverySpec(null));
    }

    // ==================== 作业冻结分配的同一性 ====================

    /** 编号、运输者、提供者、预留份额与规格都一致时，这是同一条作业的离散更新。 */
    @Test
    void matchingSnapshotKeepsTheLocalJob() {
        ReloadJob local = job(belt(), 6, PROVIDER);

        assertTrue(local.allocationMatches(snapshot(belt(), 6, PROVIDER)),
                "同一条作业的进度更新不应判成分叉");
    }

    /** 编号相同但分配不同，说明是另一条作业碰巧用了同一个编号。 */
    @Test
    void sameJobIdWithDifferentAllocationIsADivergence() {
        ReloadJob local = job(belt(), 6, PROVIDER);

        assertFalse(local.allocationMatches(new ReloadJobSpec(KEY.jobId(), TRANSPORTER, new AmmoRef(7, 2), belt(), 6)), "提供者不同即分叉");
        assertFalse(local.allocationMatches(new ReloadJobSpec(KEY.jobId(), new AmmoRef(4, 3), PROVIDER, belt(), 6)), "运输者不同即分叉");
        assertFalse(local.allocationMatches(snapshot(belt(), 7, PROVIDER)), "预留份额不同即分叉");
        assertFalse(local.allocationMatches(new ReloadJobSpec(KEY.jobId() + 1, TRANSPORTER, PROVIDER, belt(), 6)), "作业编号不同即不同作业");
    }

    /** 规格里的回充配置不同也必须是分叉：沿用本地进度会让本端按另一份规格交付。 */
    @Test
    void sameJobIdWithDifferentRechargeSpecIsADivergence() {
        ReloadJob local = job(belt(CONTAINER, "test belt", 8, 8, new AmmoBelt.Recharge(30f, 0f, 0f)), 6, PROVIDER);

        assertFalse(local.allocationMatches(snapshot(
                belt(CONTAINER, "test belt", 8, 8, new AmmoBelt.Recharge(120f, 0f, 0f)), 6, PROVIDER)),
                "回充配置不同即分叉");
    }

    /**
     * 冻结分配与动态样本是两支独立的样本：换段与进度基线变化只改动态样本，不改变分配判据。
     */
    @Test
    void stageAndProgressLiveInTheProgressSample() {
        ReloadJob local = job(belt(), 6, PROVIDER);
        ReloadJobProgress humanStageAtZero = new ReloadJobProgress(KEY.jobId(), 0, 120, ReloadJob.Stage.HUMAN);

        assertTrue(local.allocationMatches(snapshot(belt(), 6, PROVIDER)), "换段与进度基线变化不是分叉");
        assertNotEquals(local.stage(), humanStageAtZero.stage(), "夹具应确实换了阶段");
        assertEquals(0, humanStageAtZero.elapsed(), "进度基线在动态样本里表达");
    }

}


