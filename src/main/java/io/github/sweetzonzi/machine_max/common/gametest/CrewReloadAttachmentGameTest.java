package io.github.sweetzonzi.machine_max.common.gametest;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.attachment.CrewReloadAttachment;
import io.github.sweetzonzi.machine_max.common.mech.ammo.AmmoBelt;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AmmoPath;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AmmoRef;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoConsumer;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoSupplier;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoTransporter;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ReloadJob;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ReloadJobKey;
import io.github.sweetzonzi.machine_max.common.registry.MMAttributes;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * 人物的人工装填能力：有效时间倍率怎么算、认领一次只服务一条作业。
 *
 * <p>两件事都只能在真实实体上验证：倍率要读活体身上的属性实例与药水效果，认领要读消费者此刻的作业身份。
 * 夹具用盔甲架——它是最小的活体，不带 AI，也不会把测试变成一场战斗。</p>
 *
 * <p>倍率契约：属性层与药水层相乘、时间取速度的倒数。属性 4.0 是上限（用时压到四分之一），药水层的净等级
 * 按每级 20% 折算，急迫 V 与挖掘疲劳 III 各撞一侧边界（速度 2.0 与 0.5），急迫 II 加挖掘疲劳 II 的净等级
 * 为 0 所以正好回到标准人力。</p>
 *
 * <p>认领契约：一个人一次只推进一段人工装填，被占着时对别的作业表现为忙；释放与查询都按作业身份，
 * 另一条路上的释放摘不掉这条路上的认领。认领还带自检——被认领的消费者收尾或换了作业时这条认领按不存在
 * 处理，因此漏掉一次释放不会把一个人永久锁在旧作业上。</p>
 */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CrewReloadAttachmentGameTest {
    private static final String BATCH = "mmCrewReload";
    private static final String TEMPLATE = "part_foundation";
    private static final AmmoRef PROVIDER = new AmmoRef(3, 1);
    /** 这条作业的身份 */
    private static final ReloadJobKey KEY = new ReloadJobKey(3, 2, AmmoRef.NONE, PROVIDER);
    /** 同一根管、另一台架：结构身份不同的一条路 */
    private static final ReloadJobKey OTHER_KEY = new ReloadJobKey(3, 2, AmmoRef.NONE, new AmmoRef(3, 2));
    private static final AmmoBelt SPEC = new AmmoBelt(null, "test_belt",
            List.of(new AmmoBelt.PatternEntry(ResourceLocation.parse("machine_max:test_round"), 5)), 5, 5, null);
    /** 浮点比较的容差：倍率由倒数算出，1/1.2 这类值落不到二进制整数上 */
    private static final double EPSILON = 1.0e-6;

    private CrewReloadAttachmentGameTest() {
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void effectiveMultiplierComposesAttributeAndPotions(GameTestHelper helper) {
        LivingEntity operator = spawnCrew(helper);
        try {
            CrewReloadAttachment crew = CrewReloadAttachment.of(operator);
            assertMultiplier(helper, crew, 1.0, "无加成时是标准人力");

            attributeOf(operator).setBaseValue(2.0);
            assertMultiplier(helper, crew, 0.5, "装填速度翻倍，用时减半");

            attributeOf(operator).setBaseValue(1.0);
            addEffect(helper, operator, MobEffects.DIG_SPEED, 0);
            assertMultiplier(helper, crew, 1.0 / 1.2, "急迫 I 每级 +20% 装填速度");

            addEffect(helper, operator, MobEffects.DIG_SPEED, 1);
            addEffect(helper, operator, MobEffects.DIG_SLOWDOWN, 1);
            assertMultiplier(helper, crew, 1.0, "急迫 II 与挖掘疲劳 II 净等级为 0，正好回到标准人力");

            operator.removeEffect(MobEffects.DIG_SPEED);
            addEffect(helper, operator, MobEffects.DIG_SLOWDOWN, 2);
            assertMultiplier(helper, crew, 2.0, "挖掘疲劳 III 撞下限，用时压到标准人力的两倍");

            operator.removeEffect(MobEffects.DIG_SLOWDOWN);
            addEffect(helper, operator, MobEffects.DIG_SPEED, 4);
            assertMultiplier(helper, crew, 0.5, "急迫 V 撞上限，用时压到标准人力的一半");

            attributeOf(operator).setBaseValue(0.5);
            assertMultiplier(helper, crew, 1.0, "属性 0.5 × 药水 2.0 抵消，回到标准人力");
            helper.succeed();
        } finally {
            operator.discard();
        }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void aClaimedCrewMemberIsBusyForEveryOtherJob(GameTestHelper helper) {
        LivingEntity operator = spawnCrew(helper);
        try {
            CrewReloadAttachment crew = CrewReloadAttachment.of(operator);
            FakeConsumer owner = new FakeConsumer();
            FakeConsumer other = new FakeConsumer();

            helper.assertTrue(crew.claim(owner, KEY), "空闲的人可以被认领");
            helper.assertTrue(crew.holdsClaim(owner), "认领对认领者本人生效");
            helper.assertTrue(crew.isClaimedByOther(other), "认领后对别的作业表现为忙");
            helper.assertFalse(crew.claim(other, OTHER_KEY), "一个人一次只能推进一段人工装填");
            helper.succeed();
        } finally {
            operator.discard();
        }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void releaseMatchesTheJobIdentityOnly(GameTestHelper helper) {
        LivingEntity operator = spawnCrew(helper);
        try {
            CrewReloadAttachment crew = CrewReloadAttachment.of(operator);
            FakeConsumer owner = new FakeConsumer();
            FakeConsumer other = new FakeConsumer();
            crew.claim(owner, KEY);

            helper.assertFalse(crew.release(OTHER_KEY), "身份不匹配的释放无效");
            helper.assertFalse(crew.isIdle(), "无效释放之后人仍然是忙的");

            helper.assertTrue(crew.release(KEY), "身份匹配的释放摘掉认领");
            helper.assertTrue(crew.isIdle(), "释放之后人空闲");
            helper.assertFalse(crew.isClaimedByOther(other), "释放之后别人可以认领");
            helper.assertTrue(crew.claim(other, OTHER_KEY), "释放之后别人确实认领得到");
            helper.succeed();
        } finally {
            operator.discard();
        }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void aClaimWhoseJobIsGoneCountsAsDead(GameTestHelper helper) {
        LivingEntity operator = spawnCrew(helper);
        try {
            CrewReloadAttachment crew = CrewReloadAttachment.of(operator);
            FakeConsumer owner = new FakeConsumer();
            FakeConsumer other = new FakeConsumer();
            crew.claim(owner, KEY);
            helper.assertTrue(crew.isClaimedByOther(other), "刚认领的人对别人是忙的");

            // 消费者收尾了，但没人来释放这条认领
            owner.job = null;
            helper.assertFalse(crew.isClaimedByOther(other), "作业不在了的认领按不存在处理，不把别人锁在门外");
            helper.assertFalse(crew.holdsClaim(owner), "残骸认领不算持有着作业");
            helper.assertTrue(crew.claim(other, OTHER_KEY), "残骸认领不妨碍新人接手");
            helper.succeed();
        } finally {
            operator.discard();
        }
    }

    /** 夹具乘员：盔甲架，落在测试结构里，用完由调用方移除。 */
    private static LivingEntity spawnCrew(GameTestHelper helper) {
        ArmorStand operator = new ArmorStand(EntityType.ARMOR_STAND, helper.getLevel());
        helper.getLevel().addFreshEntity(operator);
        return operator;
    }

    private static AttributeInstance attributeOf(LivingEntity operator) {
        AttributeInstance attribute = operator.getAttribute(MMAttributes.CREW_RELOAD_SPEED);
        if (attribute == null) throw new IllegalStateException("乘员身上没有 crew_reload_speed 属性实例");
        return attribute;
    }

    private static void addEffect(GameTestHelper helper, LivingEntity operator,
                                  Holder<MobEffect> effect, int amplifier) {
        helper.assertTrue(operator.addEffect(new MobEffectInstance(effect, 600, amplifier)),
                "夹具必须能对乘员施加药水效果");
    }

    private static void assertMultiplier(GameTestHelper helper, CrewReloadAttachment crew,
                                         double expected, String message) {
        double actual = crew.effectiveMultiplier();
        helper.assertTrue(Math.abs(actual - expected) < EPSILON,
                message + "：期望 " + expected + "，实际 " + actual);
    }

    /** 只满足认领自检所需的消费者：当前作业可写，其余入口在认领路径上不会被读到。 */
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
