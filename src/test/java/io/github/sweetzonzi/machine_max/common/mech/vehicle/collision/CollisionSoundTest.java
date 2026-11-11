package io.github.sweetzonzi.machine_max.common.mech.vehicle.collision;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 接触声音须覆盖正常滚动、锁死拖滑、普通刮擦以及低速静音，参数始终处于事件定义域内。 */
class CollisionSoundTest {
    private static final float EPSILON = 1e-5f;

    /** 参数映射不读取方块；空状态避免在纯数学用例中启动 Minecraft 注册表。 */
    private CollisionEffectManager.CollisionSnapshot contact(float omega, float radius, float slip) {
        return new CollisionEffectManager.CollisionSnapshot(null, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO,
                omega, radius, slip, 1f);
    }

    @Test
    void rollingWithoutSlipProducesMaterialTexture() {
        var rolling = contact(20f, 0.5f, 0f);
        assertEquals(10f, rolling.rollingSpeed(), EPSILON);
        assertTrue(rolling.rpm() > 0f);
        assertTrue(rolling.contact() > 0f);
        assertEquals(0f, rolling.screechAmount());
    }

    @Test
    void lockedWheelAndOrdinaryScrapeCanSoundAtZeroRpm() {
        var sliding = contact(0f, 0.5f, 5f);
        var scraping = contact(0f, 0f, 5f);
        assertEquals(0f, sliding.rpm());
        assertEquals(0f, scraping.rpm());
        assertTrue(sliding.contact() > 0f);
        assertTrue(sliding.contact() < scraping.contact());
        assertTrue(sliding.screechAmount() > 0f);
    }

    @Test
    void samePeripheralSpeedProducesSameTextureForDifferentWheelSizes() {
        var small = contact(40f, 0.25f, 0f);
        var large = contact(20f, 0.5f, 0f);
        assertEquals(small.rollingSpeed(), large.rollingSpeed());
        assertEquals(small.contact(), large.contact());
        assertTrue(small.rpm() > large.rpm());
    }

    @Test
    void rollingAndSlidingShareTheStrongerTextureInsteadOfAdding() {
        float roll = contact(20f, 0.5f, 0f).contact();
        float slip = contact(0f, 0.5f, 5f).contact();
        assertEquals(Math.max(roll, slip), contact(20f, 0.5f, 5f).contact());
    }

    @Test
    void stationaryAndCreepingContactsAreSilent() {
        assertEquals(0f, contact(0f, 0.5f, 0f).contact());
        assertEquals(0f, contact(0.4f, 0.5f, 0.2f).contact());
        assertEquals(0f, contact(0f, 0f, 0.2f).screechAmount());
    }

    @Test
    void highSlipRatioAloneDoesNotCauseAScreech() {
        var lowSpeed = contact(0f, 0.5f, 0.2f);
        assertEquals(1f, lowSpeed.slipRatio());
        assertEquals(0f, lowSpeed.screechAmount());
        assertTrue(contact(0f, 0.5f, 6f).screechAmount() > 0f);
    }

    @Test
    void parametersRemainBoundedAndMonotonicAtHighSpeed() {
        float previousRpm = 0f;
        float previousContact = 0f;
        float previousScreech = 0f;
        for (int i = 0; i <= 1000; i++) {
            var state = contact(i, 0.5f, i * 0.1f);
            assertTrue(state.rpm() >= previousRpm && state.rpm() <= 100f);
            assertTrue(state.contact() >= previousContact && state.contact() <= 1f);
            assertTrue(state.screechAmount() >= previousScreech && state.screechAmount() <= 1f);
            previousRpm = state.rpm();
            previousContact = state.contact();
            previousScreech = state.screechAmount();
        }
        assertEquals(100f, previousRpm);
        assertTrue(previousContact > 0f && previousContact < 1f);
        assertEquals(1f, contact(0f, 0f, 100f).contact());
        assertEquals(1f, previousScreech);
    }
}
