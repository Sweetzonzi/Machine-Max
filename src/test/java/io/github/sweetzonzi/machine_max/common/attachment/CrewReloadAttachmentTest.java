package io.github.sweetzonzi.machine_max.common.attachment;

import io.github.sweetzonzi.machine_max.common.mech.ammo.AmmoBelt;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AmmoPath;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AmmoRef;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoConsumer;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoSupplier;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoTransporter;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ReloadJob;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ReloadJobKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CrewReloadAttachment} 的两条契约：个人倍率被规整到可用区间，认领一次只服务一条作业。<br>
 * 认领带自检——被认领的消费者收尾或换了作业时，这条认领按不存在处理，因此漏掉一次释放不会把一个人
 * 永久锁在旧作业上；释放与查询都按作业身份，另一条路上的释放摘不掉这条路上的认领。
 */
class CrewReloadAttachmentTest {

    private static final AmmoRef PROVIDER = new AmmoRef(3, 1);
    private static final ReloadJobKey KEY = new ReloadJobKey(3, 2, AmmoRef.NONE, PROVIDER);
    /** 同一根管、另一台架：结构身份不同的一条路 */
    private static final ReloadJobKey OTHER_KEY = new ReloadJobKey(3, 2, AmmoRef.NONE, new AmmoRef(3, 2));
    private static final AmmoBelt SPEC = new AmmoBelt(null, "test_belt",
            List.of(new AmmoBelt.PatternEntry(ResourceLocation.parse("machine_max:test_round"), 5)), 5, 5, null);

    @Test
    void multiplierIsClampedAndNonFiniteFallsBackToStandard() {
        assertEquals(1.0, CrewReloadAttachment.sanitizeMultiplier(Double.NaN));
        assertEquals(1.0, CrewReloadAttachment.sanitizeMultiplier(Double.POSITIVE_INFINITY));
        assertEquals(CrewReloadAttachment.MIN_MULTIPLIER, CrewReloadAttachment.sanitizeMultiplier(0.1));
        assertEquals(CrewReloadAttachment.MAX_MULTIPLIER, CrewReloadAttachment.sanitizeMultiplier(10));
        assertEquals(1.5, CrewReloadAttachment.sanitizeMultiplier(1.5));
    }

    @Test
    void aClaimedCrewMemberIsBusyForEveryOtherJob() {
        FakeConsumer owner = new FakeConsumer();
        FakeConsumer other = new FakeConsumer();
        CrewReloadAttachment crew = new CrewReloadAttachment();

        assertTrue(crew.claim(owner, KEY), "空闲的人可以被认领");
        assertTrue(crew.holdsClaim(owner), "认领对认领者本人生效");
        assertTrue(crew.isClaimedByOther(other), "认领后对别的作业表现为忙");
        assertFalse(crew.claim(other, OTHER_KEY), "一个人一次只能推进一段人工装填");
    }

    @Test
    void releaseMatchesTheJobIdentityOnly() {
        FakeConsumer owner = new FakeConsumer();
        FakeConsumer other = new FakeConsumer();
        CrewReloadAttachment crew = new CrewReloadAttachment();
        crew.claim(owner, KEY);

        assertFalse(crew.release(OTHER_KEY), "身份不匹配的释放无效");
        assertFalse(crew.isIdle(), "无效释放之后人仍然是忙的");

        assertTrue(crew.release(KEY));
        assertTrue(crew.isIdle());
        assertFalse(crew.isClaimedByOther(other), "释放之后别人可以认领");
        assertTrue(crew.claim(other, OTHER_KEY));
    }

    @Test
    void aClaimWhoseJobIsGoneCountsAsDeadAndDoesNotBlockAnyone() {
        FakeConsumer owner = new FakeConsumer();
        FakeConsumer other = new FakeConsumer();
        CrewReloadAttachment crew = new CrewReloadAttachment();
        crew.claim(owner, KEY);
        assertTrue(crew.isClaimedByOther(other));

        owner.job = null;   // 消费者收尾了，但没人来释放这条认领
        assertFalse(crew.isClaimedByOther(other), "作业不在了的认领按不存在处理，不把别人锁在门外");
        assertFalse(crew.holdsClaim(owner));
        assertTrue(crew.claim(other, OTHER_KEY));
    }

    /** 只满足认领自检所需的消费者：当前作业可写，其余入口不需要。 */
    private static final class FakeConsumer implements IAmmoConsumer {
        ReloadJob job = new ReloadJob(KEY, 1, SPEC, 5, 100);

        @Override
        public AmmoPath getCurrentRoute() {
            return null;
        }

        @Override
        public void setCurrentRoute(AmmoPath route) {
        }

        @Override
        public List<AmmoPath> getRoutes() {
            return List.of();
        }

        @Override
        public IAmmoSupplier resolveProvider(AmmoRef ref) {
            return null;
        }

        @Override
        public void addTransporter(IAmmoTransporter transporter, String channelName) {
        }

        @Override
        public void addProvider(IAmmoSupplier provider, String channelName) {
        }

        @Override
        public ReloadJob getReloadJob() {
            return job;
        }

        @Override
        public boolean canAccept(ProjectileType type) {
            return true;
        }
    }
}
