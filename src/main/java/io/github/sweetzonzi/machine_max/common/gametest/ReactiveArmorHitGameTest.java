package io.github.sweetzonzi.machine_max.common.gametest;

import com.google.gson.JsonParser;
import com.jme3.bullet.collision.shapes.infos.ChildCollisionShape;
import com.mojang.serialization.JsonOps;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageApi;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageContext;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.BasicSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ReactiveArmorSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.AbstractSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.BasicSubsystemDynamicAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.ReactiveArmorSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.AbstractSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPartType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.HitBoxAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.MaterialAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.MMDamageExtensions;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.event.subpart.SubPartDamageEvent;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.HitBox;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 命中钩子、爆反砖阈值引爆与失效框活性翻转的服务端契约。
 *
 * <p>四条被测契约：</p>
 * <ol>
 *   <li>金额为 0 的命中照样走 {@link AbstractSubsystem#onHit}，不进入 {@code onHurt}
 *       （{@link io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart#settleAccumulatedDamage()}）；</li>
 *   <li>{@code onHit} 排在 {@code onHurt} 之前，读到的金额就是进入结算时的金额；</li>
 *   <li>爆反砖由进砖之前的穿深阈值决定失效，与金额、是否击穿无关；碰撞冲击与低于阈值的命中都不引爆，
 *       且不按伤害磨损；</li>
 *   <li>子系统摧毁后，带 {@code condition} 的命中箱在下一次活性重算时失效，严格映射
 *       {@link SubPart#findPickableHitBox(int)} 对它返回 {@code null}，爆炸射线走同一条映射因此跳过它。</li>
 * </ol>
 *
 * <p>探针型号使用 {@code machine_max_test:} 命名空间，只在用例内注册、退出时移除，不依赖真实内容包的
 * 型号构成；只有最后一条用例从内容里按条件挑选一份带 {@code condition} 的命中箱。</p>
 */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ReactiveArmorHitGameTest {
    private static final String BATCH = "mmReactiveArmor";

    /** 计数探针的型号定义。 */
    private static final ResourceLocation COUNTING_DEFINITION = ResourceLocation.parse("machine_max_test:counting_probe");
    /** 爆反探针的型号定义。 */
    private static final ResourceLocation ERA_DEFINITION = ResourceLocation.parse("machine_max_test:era_probe");
    /** 爆反探针的灵敏度阈值，与内容包取值无关。 */
    private static final float TRIGGER = 50f;

    private static final String COUNTING_NAME = "subsystem.machine_max_test.counting";
    private static final String ERA_NAME = "subsystem.machine_max_test.era";

    private ReactiveArmorHitGameTest() {
    }

    /** 一条用例所需的部件与探针宿主子零件。 */
    private record Fixture(RegularPart part, SubPart subPart) implements AutoCloseable {
        @Override
        public void close() {
            part.discardUnattached();
        }
    }

    /**
     * 挑一个带子系统的子零件作为宿主。型号表是哈希映射，按注册名与变体名排序后取第一个，
     * 让不同启动间校验到同一个部件。
     */
    private static Fixture fixture(GameTestHelper helper) {
        Level level = helper.getLevel();
        for (RegularPartType type : sortedTypes()) {
            for (String variant : type.variants.keySet().stream().sorted().toList()) {
                RegularPart part = PartFactory.createRegular(type, variant, level);
                SubPart host = part.subParts.values().stream()
                        .filter(child -> !child.subsystems.isEmpty())
                        .min(Comparator.comparing(SubPart::getName))
                        .orElse(null);
                if (host != null) return new Fixture(part, host);
                part.discardUnattached();
            }
        }
        throw new IllegalStateException("需要至少一个带子系统的子零件作为探针宿主");
    }

    /** 普通部件定义按注册名排序：定义表是哈希映射，遍历顺序随机，不排序会让用例选到不同部件。 */
    private static List<RegularPartType> sortedTypes() {
        return PartDefinitions.regular(false).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();
    }

    /** 注册计数探针型号并解码它的安装属性。 */
    private static BasicSubsystemDynamicAttr countingAttr() {
        AbstractSubsystemStaticAttr staticAttr = AbstractSubsystemStaticAttr.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"type\":\"machine_max:basic\",\"basic_durability\":100,"
                        + "\"pass_damage\":false,\"hidden\":true}")).getOrThrow();
        MMDynamicRes.STATIC_SUBSYSTEM_ATTRS.put(COUNTING_DEFINITION, staticAttr);
        return BasicSubsystemDynamicAttr.CODEC.codec().parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"definition\":\"" + COUNTING_DEFINITION + "\"}")).getOrThrow();
    }

    /** 注册爆反探针型号并解码它的安装属性（走完整的类型分派）。 */
    private static ReactiveArmorSubsystem eraSubsystem(SubPart host) {
        AbstractSubsystemStaticAttr staticAttr = AbstractSubsystemStaticAttr.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"type\":\"machine_max:reactive_armor\",\"basic_durability\":8,"
                        + "\"pass_damage\":false,\"hidden\":true,\"trigger_penetration\":" + TRIGGER + "}"))
                .getOrThrow();
        MMDynamicRes.STATIC_SUBSYSTEM_ATTRS.put(ERA_DEFINITION, staticAttr);
        AbstractSubsystemAttr attr = AbstractSubsystemAttr.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"type\":\"machine_max:reactive_armor\",\"definition\":\""
                        + ERA_DEFINITION + "\"}")).getOrThrow();
        if (!(attr instanceof ReactiveArmorSubsystemAttr eraAttr)) {
            throw new IllegalStateException("machine_max:reactive_armor 应解码成 ReactiveArmorSubsystemAttr，实际 " + attr.getClass());
        }
        return new ReactiveArmorSubsystem(host, ERA_NAME, eraAttr);
    }

    /** 命中箱属性：绑定到给定的子系统名，无附加条件。 */
    private static HitBoxAttr probeBoxAttr(String subsystemName) {
        return new HitBoxAttr("mm_gametest_hit", "box", subsystemName, "", MaterialAttr.DEFAULT_ID,
                30f, "true", false, null);
    }

    /**
     * 沿生产路径打一发：{@link BFDamageApi#hurt} 完成穿甲判定后进入 {@code SubPart#hurt} 入队，
     * 再做一次 {@code postTick} 触发主线程结算。
     *
     * @param amount          标称伤害，决定结算时的入口金额
     * @param penetration     进砖之前的等效穿深（mm）
     * @param collisionImpact 是否标记为碰撞冲击
     * @return 协议层认为造成的伤害量
     */
    private static float hit(GameTestHelper helper, SubPart host, HitBox hitBox, float amount, float penetration,
                             boolean collisionImpact) {
        DamageSource source = helper.getLevel().damageSources().generic();
        BFDamageContext ctx = BFDamageContext.builder()
                .source(source)
                .baseDamage(amount)
                .penetration(penetration)
                .hitVelocity(new Vec3(0, 0, 1))
                .hitNormal(new Vec3(0, 0, -1))
                .hitPoint(Vec3.ZERO)
                .build();
        ctx.extensions().set(MMDamageExtensions.HIT_BOX, hitBox);
        if (collisionImpact) ctx.extensions().set(MMDamageExtensions.COLLISION_IMPACT, true);
        float dealt = BFDamageApi.hurt(host, ctx);
        host.postTick();
        return dealt;
    }

    /** 计数探针：记录两条钩子的调用次数与各自读到的金额。 */
    private static final class CountingSubsystem extends BasicSubsystem {
        int hits;
        int hurts;
        float amountAtHit = Float.NaN;
        float amountAtHurt = Float.NaN;

        CountingSubsystem(ISubsystemHost owner, String name, BasicSubsystemDynamicAttr attr) {
            super(owner, name, attr);
        }

        @Override
        public void onHit(SubPartDamageEvent.Pre event) {
            hits++;
            amountAtHit = event.getDamageAmount();
        }

        @Override
        public void onHurt(SubPartDamageEvent.Pre event) {
            hurts++;
            amountAtHurt = event.getDamageAmount();
            // 把金额改小：若 onHit 排在 onHurt 之后，它读到的就不是入口金额
            event.setDamageAmount(1f);
        }
    }

    /** 金额为 0 的命中照样触发 onHit，且不触发 onHurt。 */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void zeroDamageHitStillReachesOnHit(GameTestHelper helper) {
        try (Fixture fixture = fixture(helper)) {
            try {
                CountingSubsystem probe = new CountingSubsystem(fixture.subPart(), COUNTING_NAME, countingAttr());
                fixture.subPart().subsystems.put(COUNTING_NAME, probe);
                HitBox box = new HitBox(fixture.subPart(), probeBoxAttr(COUNTING_NAME), "mm_gametest_probe", null);

                hit(helper, fixture.subPart(), box, 0f, 0f, false);

                helper.assertTrue(probe.hits == 1, "金额为 0 的命中必须走 onHit，实际 " + probe.hits + " 次");
                helper.assertTrue(probe.hurts == 0, "金额为 0 的命中不进 onHurt，实际 " + probe.hurts + " 次");
                helper.assertTrue(probe.amountAtHit == 0f, "onHit 读到的金额应为 0，实际 " + probe.amountAtHit);
                helper.succeed();
            } finally {
                MMDynamicRes.STATIC_SUBSYSTEM_ATTRS.remove(COUNTING_DEFINITION);
            }
        }
    }

    /** onHit 先于 onHurt，且读到进入结算时的金额。 */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void onHitRunsBeforeOnHurtWithEntryAmount(GameTestHelper helper) {
        try (Fixture fixture = fixture(helper)) {
            try {
                CountingSubsystem probe = new CountingSubsystem(fixture.subPart(), COUNTING_NAME, countingAttr());
                fixture.subPart().subsystems.put(COUNTING_NAME, probe);
                HitBox box = new HitBox(fixture.subPart(), probeBoxAttr(COUNTING_NAME), "mm_gametest_probe", null);

                float dealt = hit(helper, fixture.subPart(), box, 40f, 500f, false);

                helper.assertTrue(probe.hits == 1 && probe.hurts == 1,
                        "正金额的命中两条钩子各一次，实际 onHit=" + probe.hits + " onHurt=" + probe.hurts);
                helper.assertTrue(probe.amountAtHit == probe.amountAtHurt,
                        "onHit 必须先读到 onHurt 改写之前的金额，onHit=" + probe.amountAtHit
                                + " onHurt=" + probe.amountAtHurt);
                helper.assertTrue(probe.amountAtHit == dealt,
                        "钩子读到的金额应等于协议层交付的金额 " + dealt + "，实际 " + probe.amountAtHit);
                helper.succeed();
            } finally {
                MMDynamicRes.STATIC_SUBSYSTEM_ATTRS.remove(COUNTING_DEFINITION);
            }
        }
    }

    /** 达到阈值的零金额命中摧毁爆反砖，下一 tick 进入摧毁状态。 */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void thresholdPenetrationDestroysBrick(GameTestHelper helper) {
        try (Fixture fixture = fixture(helper)) {
            try {
                ReactiveArmorSubsystem era = eraSubsystem(fixture.subPart());
                fixture.subPart().subsystems.put(ERA_NAME, era);
                HitBox box = new HitBox(fixture.subPart(), probeBoxAttr(ERA_NAME), "mm_gametest_probe", null);
                float before = era.getDurability();

                hit(helper, fixture.subPart(), box, 0f, TRIGGER, false);

                helper.assertTrue(before > 0f, "探针砖应带正耐久，实际 " + before);
                helper.assertTrue(era.getDurability() == 0f, "达阈值的零金额命中应把耐久归零，实际 " + era.getDurability());
                helper.assertTrue(!era.isDestroyed(), "摧毁标记由下一个 tick 翻转");
                era.onTick();
                helper.assertTrue(era.isDestroyed(), "下一 tick 应进入摧毁状态");
                helper.succeed();
            } finally {
                MMDynamicRes.STATIC_SUBSYSTEM_ATTRS.remove(ERA_DEFINITION);
            }
        }
    }

    /** 阈值两侧与碰撞冲击：只有达到阈值的弹道命中引爆，且砖不会被伤害磨损。 */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void thresholdEdgesAndCollisionImpact(GameTestHelper helper) {
        try (Fixture fixture = fixture(helper)) {
            try {
                ReactiveArmorSubsystem era = eraSubsystem(fixture.subPart());
                fixture.subPart().subsystems.put(ERA_NAME, era);
                HitBox box = new HitBox(fixture.subPart(), probeBoxAttr(ERA_NAME), "mm_gametest_probe", null);
                float before = era.getDurability();

                // 阈值下一毫米：不引爆、不磨损，哪怕金额很大
                hit(helper, fixture.subPart(), box, 120f, TRIGGER - 1f, false);
                helper.assertTrue(era.getDurability() == before,
                        "低于阈值的命中不得改变耐久，期望 " + before + "，实际 " + era.getDurability());

                // 碰撞冲击：穿深再大也不引爆
                hit(helper, fixture.subPart(), box, 120f, 500f, true);
                helper.assertTrue(era.getDurability() == before,
                        "碰撞冲击不得引爆，期望 " + before + "，实际 " + era.getDurability());

                // 阈值本身：引爆
                hit(helper, fixture.subPart(), box, 0f, TRIGGER, false);
                helper.assertTrue(era.getDurability() == 0f,
                        "恰好达到阈值的命中应引爆，实际 " + era.getDurability());
                helper.succeed();
            } finally {
                MMDynamicRes.STATIC_SUBSYSTEM_ATTRS.remove(ERA_DEFINITION);
            }
        }
    }

    /** 子系统摧毁后，带 condition 的命中箱在活性重算时失效，严格映射对它返回 null。 */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void destroyedSubsystemDeactivatesConditionedHitBox(GameTestHelper helper) {
        for (RegularPartType type : sortedTypes()) {
            for (String variant : type.variants.keySet().stream().sorted().toList()) {
                RegularPart part = PartFactory.createRegular(type, variant, helper.getLevel());
                try {
                    if (verifyConditionFlip(helper, part)) {
                        helper.succeed();
                        return;
                    }
                } finally {
                    part.discardUnattached();
                }
            }
        }
        throw new IllegalStateException("需要至少一个绑定子系统且带 condition 的命中箱");
    }

    /**
     * 在给定部件里找第一个"绑定子系统 + 带 condition"的命中箱，摧毁它绑定的子系统后校验活性翻转。
     *
     * @return 找到并校验完成时返回 true；该部件没有合适的命中箱时返回 false
     */
    private static boolean verifyConditionFlip(GameTestHelper helper, RegularPart part) {
        for (SubPart subPart : part.subParts.values()) {
            for (Map.Entry<String, HitBox> entry : subPart.hitBoxes.entrySet()) {
                HitBox hitBox = entry.getValue();
                if (!hitBox.isActive()) continue;
                HitBoxAttr attr = hitBox.getAttr();
                if (attr.getCondition().equalsIgnoreCase("true")) continue;
                AbstractSubsystem subsystem = hitBox.getSubsystem();
                if (subsystem == null) continue;

                int childIndex = childIndexOf(subPart, entry.getKey());
                if (childIndex < 0) continue; // 没有物理子形状的框不参与射线判定

                subsystem.setDurability(0f);
                subsystem.onTick();
                helper.assertTrue(subsystem.isDestroyed(), "子系统耐久归零后应进入摧毁状态");
                helper.assertTrue(hitBox.isActive(), "摧毁标记翻转后、活性重算之前，命中箱仍是激活的");

                // 每物理步开头重算一次活性（SubPart#prePhysicsTick）
                subPart.prePhysicsTick();
                helper.assertTrue(!hitBox.isActive(), "条件依赖的子系统已摧毁时，命中箱应失效");
                helper.assertTrue(subPart.findPickableHitBox(childIndex) == null,
                        "严格映射必须对失效的命中箱返回 null，爆炸射线据此跳过它");
                return true;
            }
        }
        return false;
    }

    /**
     * 按子形状找到命中箱对应的索引：{@code SubPart#findPickableHitBox} 与
     * {@code ExplosionInstance#rayTestRigid} 都用这套索引。
     *
     * @return 匹配的子形状索引；没有对应形状时返回 -1
     */
    private static int childIndexOf(SubPart subPart, String hitBoxKey) {
        ChildCollisionShape[] children = subPart.collisionShape.listChildren();
        for (int index = 0; index < children.length; index++) {
            long shapeId = children[index].getShape().nativeId();
            if (hitBoxKey.equals(subPart.attr.getHitBoxNames().get(shapeId))) return index;
        }
        return -1;
    }
}
