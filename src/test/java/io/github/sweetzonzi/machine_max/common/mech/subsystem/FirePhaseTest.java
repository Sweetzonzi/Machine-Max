package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 射速相位契约：整数周期、首发即时、缺弹不蓄积、松键保留防连点冷却、非整 tick 周期不丢余量。
 *
 * <p>这些断言只依赖 {@link FirePhase} 的业务 tick 计数，不需要服务端或物理世界，因此可以在纯 JUnit
 * 里固定"每 tick 该打几发"。测试用合成射速，不引用任何内容包的火炮数值。</p>
 */
class FirePhaseTest {

    private static final long U = FirePhase.TICK_UNITS;

    /** 一个最小发射器替身：只关心"有没有可发射内容"和实际发数。 */
    private static final class Launcher {
        final FirePhase phase = new FirePhase();
        int shots;

        /** @param available 本轮可用发数；{@code < 0} 表示无限 */
        int tick(float rpm, boolean triggerHeld, int available) {
            int[] left = {available};
            int fired = phase.tick(FirePhase.intervalUFor(rpm), triggerHeld, () -> {
                if (left[0] == 0) return false;
                if (left[0] > 0) left[0]--;
                shots++;
                return true;
            });
            return fired;
        }

        int tick(float rpm, boolean triggerHeld) {
            return tick(rpm, triggerHeld, -1);
        }
    }

    @Test
    void intervalIsQuantizedToIntegralPhaseUnits() {
        assertEquals(10 * U, FirePhase.intervalUFor(120f));
        assertEquals(1 * U, FirePhase.intervalUFor(1200f));
        assertEquals(200_000L, FirePhase.intervalUFor(6000f));
        assertEquals(1_333_333L, FirePhase.intervalUFor(900f));
        assertEquals(300_000L, FirePhase.intervalUFor(4000f));
    }

    @Test
    void absurdlyLowRpmStillHasARepresentableInterval() {
        long interval = FirePhase.intervalUFor(0.1f);
        // 0.1 RPM ≈ 1.2×10^10 相位单位，远超 int；long 相位必须能容纳
        assertTrue(interval > Integer.MAX_VALUE, "低射速周期必须用 long 承载，实际 " + interval);
        assertEquals(12_000_000_000.0, (double) interval, 1.0e6);
    }

    @Test
    void nonPositiveOrNonFiniteRpmIsUnusable() {
        assertEquals(0L, FirePhase.intervalUFor(0f));
        assertEquals(0L, FirePhase.intervalUFor(-600f));
        assertEquals(0L, FirePhase.intervalUFor(Float.NaN));
        assertEquals(0L, FirePhase.intervalUFor(Float.POSITIVE_INFINITY));
    }

    @Test
    void rpmTooHighForOnePhaseUnitIsUnusable() {
        // 周期量化后不足一个相位单位时判为不可用：截到 1 会让单个业务 tick 试图打出上百万发
        assertEquals(0L, FirePhase.intervalUFor(3_000_000_000f));
    }

    @Test
    void firstShotIsImmediateAndThenOnePerInterval() {
        Launcher launcher = new Launcher();
        // 120 RPM = 10 tick 一发
        for (int tick = 1; tick <= 21; tick++) {
            int fired = launcher.tick(120f, true);
            int expected = (tick == 1 || tick == 11 || tick == 21) ? 1 : 0;
            assertEquals(expected, fired, "第 " + tick + " 个业务 tick");
        }
        assertEquals(3, launcher.shots);
    }

    @Test
    void droughtKeepsThePhaseAndFiresOnTheTickAmmoArrives() {
        Launcher launcher = new Launcher();
        for (int tick = 1; tick <= 9; tick++) {
            assertEquals(0, launcher.tick(120f, true, 0), "缺弹期间不该补齐历史欠发");
        }
        assertTrue(launcher.phase.starved(), "到发射点却没有可发射内容时必须记录缺弹");

        // 第 10 个业务 tick 拿到一发：此刻就该打出去，而不是再等一个完整间隔
        assertEquals(1, launcher.tick(120f, true, 1));
        assertEquals(1, launcher.shots);
    }

    @Test
    void longDroughtDoesNotAccumulateBacklog() {
        Launcher launcher = new Launcher();
        for (int tick = 1; tick <= 100; tick++) launcher.tick(120f, true, 0);

        // 补上弹的那一 tick 只按当时的预算发射，不把 100 个 tick 的欠发一次性补齐
        assertEquals(1, launcher.tick(120f, true, 999));
        assertEquals(1, launcher.shots);
    }

    @Test
    void highRpmConsumesTheWholeTickBudget() {
        Launcher launcher = new Launcher();
        for (int tick = 1; tick <= 20; tick++) {
            assertEquals(5, launcher.tick(6000f, true), "6000 RPM 每业务 tick 5 发，第 " + tick + " tick");
        }
        assertEquals(100, launcher.shots);
    }

    @Test
    void nonIntegralPeriodsKeepTheirRemainder() {
        // 900 与 4000 RPM 都不是整 tick 周期；丢掉余量会退化成 600／3600 RPM
        assertEquals(900, totalShots(900f, 1200), 1, "900 RPM 每分钟发数");
        assertEquals(4000, totalShots(4000f, 1200), 1, "4000 RPM 每分钟发数");
    }

    private static int totalShots(float rpm, int ticks) {
        Launcher launcher = new Launcher();
        for (int tick = 1; tick <= ticks; tick++) launcher.tick(rpm, true);
        return launcher.shots;
    }

    @Test
    void releaseKeepsTheAntiRapidClickCooldown() {
        Launcher launcher = new Launcher();
        assertEquals(1, launcher.tick(120f, true));   // 第 1 tick 打出首发
        launcher.tick(120f, false);                   // 松开一个 tick：相位归零，冷却递减一档

        assertFalse(launcher.phase.isBurstActive());
        assertTrue(launcher.phase.cooldownU() > 0L, "松键后防连点冷却仍在");

        // 立刻重按不会提前发射：从首发算起恰好再满一个间隔才打第二发
        int firedBeforeInterval = 0;
        int tickOfSecondShot = -1;
        for (int tick = 3; tick <= 21; tick++) {
            int fired = launcher.tick(120f, true);
            if (tick <= 10) firedBeforeInterval += fired;
            if (fired > 0 && tickOfSecondShot < 0) tickOfSecondShot = tick;
        }
        assertEquals(0, firedBeforeInterval, "冷却未到期前连按不能发射");
        assertEquals(11, tickOfSecondShot, "第二发应在首发之后满一个间隔时打出");
    }

    @Test
    void pauseWithoutBusinessTicksAdvancesNothing() {
        Launcher launcher = new Launcher();
        launcher.tick(120f, true);
        long phaseBefore = launcher.phase.phaseU();
        long cooldownBefore = launcher.phase.cooldownU();

        // 暂停期间没有任何业务 tick 调用；恢复后相位与冷却与暂停前完全一致
        assertEquals(phaseBefore, launcher.phase.phaseU());
        assertEquals(cooldownBefore, launcher.phase.cooldownU());

        // 相同相位与冷却产生相同的后续调度
        Launcher twin = new Launcher();
        twin.tick(120f, true);
        for (int tick = 1; tick <= 30; tick++) {
            assertEquals(launcher.tick(120f, true), twin.tick(120f, true));
        }
    }

    @Test
    void inactiveWeaponDecaysTheCooldownAndStopsTheBurst() {
        Launcher launcher = new Launcher();
        launcher.tick(120f, true);
        long cooldown = launcher.phase.cooldownU();

        int ticksToReady = 0;
        while (cooldown > 0L) {
            launcher.tick(120f, false);
            cooldown = launcher.phase.cooldownU();
            ticksToReady++;
            assertTrue(ticksToReady <= 10, "冷却必须在不超过一个间隔的业务 tick 内归零");
        }
        assertEquals(0L, launcher.phase.cooldownU());
        // 冷却归零后重按立即回到"刚好到期"，首发即时
        assertEquals(1, launcher.tick(120f, true));
        assertEquals(2, launcher.shots);
    }

    @Test
    void shrinkingIntervalClampsTheOldCooldown() {
        Launcher launcher = new Launcher();
        // 先在低射速下打出一发，产生一个大于新周期的冷却
        launcher.tick(6f, true);
        assertTrue(launcher.phase.cooldownU() > FirePhase.intervalUFor(600f));

        // 换成高射速：播种夹紧到 [0, intervalU]，不会得到负相位
        launcher.tick(600f, true);
        assertTrue(launcher.phase.phaseU() >= 0L);
        assertTrue(launcher.phase.phaseU() <= FirePhase.intervalUFor(600f));
    }

    @Test
    void unusableIntervalBehavesLikeAnInactiveWeapon() {
        Launcher launcher = new Launcher();
        assertEquals(0, launcher.tick(0f, true));
        assertFalse(launcher.phase.isBurstActive());
        assertEquals(0L, launcher.phase.phaseU());
    }
}
