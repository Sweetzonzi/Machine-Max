package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.common.mech.ammo.AmmoBelt;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 已装弹链的投影契约：权威样本什么时候落地、什么时候保留本端副本。
 *
 * <p>样本余量与本端副本的余量是同一串弹上的两个读数，差出来的那几发只可能来自两处：本端的在途射击
 * （权威先打掉了、本端还要再打），与权威独有的动作（付费回充、外部退弹回收、强制减少）。前一处的
 * 数量有上界——本端比权威晚一个单程，这段时间里最多打掉几发——因此差额不大时按在途射击解释，超出
 * 上界就只能按权威收口。判宽了会把强制减少当成在途射击、本端一直多打几发；判窄了会把在途射击当成
 * 强制减少、抹掉本端这几发的声音与炮口特效。</p>
 *
 * <p>断言只依赖 {@link AmmoBelt} 的值语义，不需要服务端或物理世界。</p>
 */
class LauncherBeltProjectionTest {

    private static final ResourceLocation AMMO = ResourceLocation.parse("machine_max:test_ap");
    private static final ResourceLocation CONTAINER = ResourceLocation.parse("machine_max:test_rack");
    /** 夹具里的在途上界：相当于 3 tick 单程、600 RPM（2 tick 一发）折算出的两发 */
    private static final int IN_FLIGHT_BOUND = 2;

    private static AmmoBelt belt(int remaining) {
        return belt(CONTAINER, "test belt", AMMO, 8, remaining, new AmmoBelt.Recharge(30f, 0f, 0f));
    }

    private static AmmoBelt belt(ResourceLocation containerId, String label, ResourceLocation ammo,
                                 int length, int remaining, AmmoBelt.Recharge recharge) {
        return new AmmoBelt(containerId, label, List.of(new AmmoBelt.PatternEntry(ammo, 1)),
                length, remaining, recharge);
    }

    // ==================== 本端产得出来的差异：保留本端副本 ====================

    /**
     * 差额不超过在途上界：这就是本端还要打的那几发。照抄会让这几发只剩投射物、没有声音与炮口特效。
     */
    @Test
    void shortfallWithinTheInFlightBoundKeepsTheLocalCopy() {
        assertFalse(AmmoBelt.sampleOverrides(belt(6), belt(4), IN_FLIGHT_BOUND),
                "两发的差额解释得成在途射击");
        assertFalse(AmmoBelt.sampleOverrides(belt(6), belt(5), IN_FLIGHT_BOUND),
                "一发的差额同样解释得成在途射击");
    }

    /** 余量相同意味着没有待落地的差异：重建只会白抹掉本端的回充运行态。 */
    @Test
    void equalRemainingKeepsTheLocalCopy() {
        assertFalse(AmmoBelt.sampleOverrides(belt(5), belt(5), 0), "余量相同不是要落地的差异");
    }

    /**
     * 权威说这条弹链没了，而本端只剩末几发：这是它比本端早一个单程打完的形状，
     * 按它清空会让本端连末发都打不出去。
     */
    @Test
    void emptiedBeltKeepsTheTailRounds() {
        assertFalse(AmmoBelt.sampleOverrides(belt(1), null, IN_FLIGHT_BOUND),
                "末发还在本端手里，权威先打空不能连它一起清");
        assertFalse(AmmoBelt.sampleOverrides(belt(2), null, IN_FLIGHT_BOUND),
                "两发仍在在途上界以内");
    }

    // ==================== 本端产不出来的差异：按权威落地 ====================

    /**
     * 差额超出在途上界：本端不可能欠权威这么多发，只可能是权威做过强制减少（管理员清弹、殉爆）。
     * 不落地会让本端一直多打这几发。
     */
    @Test
    void shortfallBeyondTheInFlightBoundIsAdopted() {
        assertTrue(AmmoBelt.sampleOverrides(belt(6), belt(2), IN_FLIGHT_BOUND),
                "四发的差额超出两发的在途上界");
        assertFalse(AmmoBelt.sampleOverrides(belt(6), belt(2), 4),
                "同一组数值在上界够大时仍按在途射击解释");
    }

    /** 权威整条弹链都没了、而本端还剩很多发：只能是强制清空，不能当成在途射击。 */
    @Test
    void emptiedBeltIsAdoptedWhenTheLocalCopyCannotBeInFlight() {
        assertTrue(AmmoBelt.sampleOverrides(belt(7), null, IN_FLIGHT_BOUND),
                "七发不可能全是在途射击");
    }

    /**
     * 余量变大只可能来自本端不预测的动作：付费回充（客户端没有能源支出预测）与外部退弹回收。
     * 这一档不看在途上界——付费回充一次只补一发，与在途噪声同量级，判不掉会把回充一起判掉。
     */
    @Test
    void largerAuthorityRemainingIsAdopted() {
        assertTrue(AmmoBelt.sampleOverrides(belt(4), belt(5), IN_FLIGHT_BOUND), "付费回充补进来的那一发");
        assertTrue(AmmoBelt.sampleOverrides(belt(4), belt(9), IN_FLIGHT_BOUND), "外部退弹回收的一整笔");
    }

    /**
     * 换了一份内容就是换装：权威的余量可能比本端还小，仍然要落地，否则本端会继续打旧的那一份。
     */
    @Test
    void differentDeliverySpecIsAlwaysAdopted() {
        assertTrue(AmmoBelt.sampleOverrides(
                        belt(CONTAINER, "test belt", AMMO, 8, 6, null),
                        belt(CONTAINER, "test belt", AMMO, 8, 2, new AmmoBelt.Recharge(30f, 0f, 0f)),
                        Integer.MAX_VALUE),
                "回充配置不同即另一份交付");
        assertTrue(AmmoBelt.sampleOverrides(
                        belt(CONTAINER, "test belt", AMMO, 8, 6, null),
                        belt(CONTAINER, "test belt", ResourceLocation.parse("machine_max:test_he"), 8, 2, null),
                        Integer.MAX_VALUE),
                "弹种不同即另一份交付");
        assertTrue(AmmoBelt.sampleOverrides(
                        belt(CONTAINER, "test belt", AMMO, 8, 6, null),
                        belt(null, "test belt", AMMO, 8, 2, null),
                        Integer.MAX_VALUE),
                "容器身份不同即另一份交付");
    }

    /** 本端没有内容时按权威落地；两边都空时落地即空，没有副作用。 */
    @Test
    void emptyLocalContentIsAlwaysAdopted() {
        assertTrue(AmmoBelt.sampleOverrides(null, belt(7), IN_FLIGHT_BOUND), "本端没有副本时按权威建立");
        assertTrue(AmmoBelt.sampleOverrides(null, null, IN_FLIGHT_BOUND), "两边都空时落地为空");
    }

}
