package io.github.sweetzonzi.machine_max.common.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import cn.solarmoon.spark_core.physics.PenetrationKey;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageContext;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.explosion.ExplosionManager;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoSupplier;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.LauncherSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.RegenLoaderSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SubModule;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.AbstractSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.LauncherSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.RegenLoaderSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.RegenLoaderSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.module.FeedModuleAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.module.MagazineModuleAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPartType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.HitBoxAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.MaterialAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.SubPartAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.VariantAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.MMDamageExtensions;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.event.subpart.SubPartDamageEvent;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.HitBox;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Comparator;
import java.util.Set;

/**
 * 模块化子系统的伤害入口、模块摧毁规则、装弹机的再生清理与退弹边界（T01、T13—T17、T23）的服务端验证；
 * 不进入物理世界。
 *
 * <p>六项契约：</p>
 * <ul>
 *   <li>{@code pass_damage=false} 只取消事件的向下传递，子系统仍按模块吸收伤害（§3.5）；
 *       事件取消是零件耐久与载具伤害的唯一闸门，因此本类断言取消标志与模块耐久两条。</li>
 *   <li>命中箱标注的 {@code module} 决定哪个模块承担伤害；未标注时按模块最大耐久比例分摊。</li>
 *   <li>摧毁与恢复按模块判定：任一关键模块摧毁即整机摧毁；任何模块（关键与否）都要修到
 *       {@link AbstractSubsystem#RECOVERY_RATIO} 才脱离摧毁，0%—30% 之间保持摧毁且随存档保留。</li>
 *   <li>弹仓归零作废进行中的再生：判毁边沿清一次，锁存态读档再清一次；机构单独损坏不清。</li>
 *   <li>退弹只看弹仓是否可储存：机构损坏照收，弹仓归零或停在恢复锁存时拒收，满仓拒收。</li>
 *   <li>库存殉爆的定位与豁免只认弹仓模块自己的最近有效受击；宿主零件被摧毁后弹仓归零仍起爆。</li>
 *   <li>{@code feed}/{@code magazine} 可省略并落到默认实例，权重与 {@code basic_durability}
 *       的非法取值在属性边界报错。</li>
 *   <li>旧存档只有一个 {@code durability_ratio}：写入整机耐久时按缺失容量分摊进模块，
 *       聚合仍等于旧比例；非有限比例按满耐久载入。</li>
 * </ul>
 *
 * <p>模块耐久分配读 {@code SynchedEntityData} 与真实子系统实例，纯 JUnit 无法加载静态属性类
 * （{@code AbstractSubsystemStaticAttr} 的类初始化要 Spark 注册总线），因此这些用例落在 GameTest。</p>
 */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ModularDamageGameTest {
    private static final String BATCH = "mmModularDamage";
    private static final float EPSILON = 1e-3f;

    private ModularDamageGameTest() {
    }

    private record Selection(RegularPartType type, String variant) {
    }

    /**
     * 变体同时具备两样东西：写 {@code pass_damage=false} 的再生装弹机（伤害入口契约不依赖发射器参数，
     * 用装弹机验证即可）与至少一个发射器（交付门控需要一个真实消费者）。
     */
    private static boolean hasLoaderAndLauncher(VariantAttr variant) {
        boolean loader = false;
        boolean launcher = false;
        for (SubPartAttr subPart : variant.subParts.values()) {
            for (AbstractSubsystemAttr attr : subPart.subsystems.values()) {
                if (attr instanceof RegenLoaderSubsystemAttr regenLoader
                        && !regenLoader.getStaticAttribute().getBasicAttr().passDamage()) {
                    loader = true;
                }
                if (attr instanceof LauncherSubsystemAttr) {
                    launcher = true;
                }
            }
        }
        return loader && launcher;
    }

    /**
     * 挑选一台不向零件传伤害的再生装弹机，按注册名与变体名排序后取第一个。
     *
     * <p>定义表是哈希映射，遍历顺序随机；不排序会让用例在不同启动间校验到不同部件，断言随之抖动。</p>
     */
    private static Selection loaderPart() {
        return PartDefinitions.regular(false).values().stream()
                .flatMap(type -> type.variants.entrySet().stream()
                        .filter(entry -> hasLoaderAndLauncher(entry.getValue()))
                        .map(entry -> new Selection(type, entry.getKey())))
                .sorted(Comparator.comparing((Selection selection) -> selection.type().getRegistryKey().toString())
                        .thenComparing(Selection::variant))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("需要一个既有 pass_damage=false 再生装弹机又有发射器的部件"));
    }

    /** 部件从存档骨架形态进入可工作形态：子系统要靠它通过 {@code isActive()} 的组装许可。 */
    private static void markAssembled(RegularPart part) {
        part.assembling = false;
        part.assemblingProgress = 1f;
    }

    private static RegenLoaderSubsystem loader(RegularPart part) {
        return part.getAllSubsystems().stream()
                .filter(RegenLoaderSubsystem.class::isInstance)
                .map(RegenLoaderSubsystem.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("部件定义声明了再生装弹机，实例表中却没有"));
    }

    private static LauncherSubsystem launcher(RegularPart part) {
        return part.getAllSubsystems().stream()
                .filter(LauncherSubsystem.class::isInstance)
                .map(LauncherSubsystem.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("部件定义声明了发射器，实例表中却没有"));
    }

    /**
     * 构造一次命中事件；{@code module} 非空时附带指向该模块的命中箱，模拟内容包里的 {@code module} 标注。
     * <p>
     * 探针命中箱沿用宿主子部件最强命中箱的材质与厚度，只替换子系统名与模块名：
     * 材质按注册表解析，另写一个 id 会触发“材质不存在”的回退警告。
     * </p>
     */
    private static SubPartDamageEvent.Pre damageEvent(GameTestHelper helper, AbstractSubsystem subsystem,
                                                      String module, float amount) {
        BFDamageContext ctx = BFDamageContext.builder()
                .source(helper.getLevel().damageSources().generic())
                .baseDamage(amount)
                .build();
        if (module != null) {
            HitBox source = subsystem.getSubPart().findStrongestHitBox();
            HitBoxAttr attr = new HitBoxAttr("mm_gametest_probe", "box", subsystem.getName(), module,
                    source != null ? source.getAttr().getMaterial() : MaterialAttr.DEFAULT_ID,
                    source != null ? source.getAttr().getThickness() : 1f,
                    "true", null);
            ctx.extensions().set(MMDamageExtensions.HIT_BOX, new HitBox(subsystem.getSubPart(), attr, null));
        }
        return new SubPartDamageEvent.Pre(subsystem.getSubPart(), ctx, amount);
    }

    private static DataResult<RegenLoaderSubsystemStaticAttr> decodeAttribute(String json) {
        JsonElement element = JsonParser.parseString(json);
        return RegenLoaderSubsystemStaticAttr.CODEC.codec().parse(JsonOps.INSTANCE, element);
    }

    /** 模块耐久按权重分配：{@code basic_durability × 权重 / 权重合计}。 */
    private static void assertAllocation(GameTestHelper helper, RegenLoaderSubsystem loader) {
        float base = loader.attr.getStaticAttribute().getBasicAttr().basicDurability();
        float feedWeight = loader.attr.getStaticAttribute().getFeed().durabilityWeight();
        float magazineWeight = loader.attr.getStaticAttribute().getMagazine().durabilityWeight();
        float totalWeight = feedWeight + magazineWeight;
        SubModule feed = loader.getModule("feed");
        SubModule magazine = loader.getModule("magazine");
        helper.assertTrue(feed != null && magazine != null, "机构与弹仓必须都存在");
        helper.assertTrue(Math.abs(feed.getMaxDurability() - base * feedWeight / totalWeight) < EPSILON,
                "机构最大耐久应按权重分配，实际 " + feed.getMaxDurability());
        helper.assertTrue(Math.abs(magazine.getMaxDurability() - base * magazineWeight / totalWeight) < EPSILON,
                "弹仓最大耐久应按权重分配，实际 " + magazine.getMaxDurability());
        helper.assertTrue(Math.abs(loader.getMaxDurability() - base) < EPSILON,
                "模块最大耐久之和应等于 basic_durability，实际 " + loader.getMaxDurability());
    }

    /** {@code pass_damage=false}：事件被取消，命中模块照常掉耐久，子系统自身吸收伤害。 */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void cancelledHitStillDamagesTheHitModule(GameTestHelper helper) {
        Selection selected = loaderPart();
        RegularPart part = PartFactory.createRegular(selected.type(), selected.variant(), helper.getLevel());
        try {
            RegenLoaderSubsystem loader = loader(part);
            assertAllocation(helper, loader);
            SubModule feed = loader.getModule("feed");
            SubModule magazine = loader.getModule("magazine");
            float feedBefore = feed.getDurability();
            float magazineBefore = magazine.getDurability();
            float aggregateBefore = loader.getDurability();

            SubPartDamageEvent.Pre event = damageEvent(helper, loader, "magazine", 4f);
            loader.onHurt(event);

            helper.assertTrue(event.isCanceled(), "pass_damage=false 必须取消事件，伤害不传给零件与载具");
            helper.assertTrue(Math.abs(feed.getDurability() - feedBefore) < EPSILON,
                    "指向弹仓的命中不应降低机构耐久，实际 " + feed.getDurability());
            helper.assertTrue(Math.abs(magazine.getDurability() - (magazineBefore - 4f)) < EPSILON,
                    "被命中的弹仓应扣掉本次伤害，实际 " + magazine.getDurability());
            helper.assertTrue(loader.getDurability() < aggregateBefore - EPSILON,
                    "子系统必须自己吸收被取消向下传递的伤害");
            helper.assertTrue(!loader.isDestroyed(), "4 点伤害不应摧毁满耐久的弹仓");
            helper.succeed();
        } finally {
            part.discardUnattached();
        }
    }

    /** 未标注模块的命中按模块最大耐久比例分摊，两个模块各承担一半。 */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void unlabelledHitSplitsByMaxDurability(GameTestHelper helper) {
        Selection selected = loaderPart();
        RegularPart part = PartFactory.createRegular(selected.type(), selected.variant(), helper.getLevel());
        try {
            RegenLoaderSubsystem loader = loader(part);
            SubModule feed = loader.getModule("feed");
            SubModule magazine = loader.getModule("magazine");
            float totalMax = feed.getMaxDurability() + magazine.getMaxDurability();
            float feedBefore = feed.getDurability();
            float magazineBefore = magazine.getDurability();
            float amount = 4f;

            SubPartDamageEvent.Pre event = damageEvent(helper, loader, null, amount);
            loader.onHurt(event);

            helper.assertTrue(event.isCanceled(), "没有命中箱时事件仍按子系统的 pass_damage 取消");
            helper.assertTrue(Math.abs(feed.getDurability() - (feedBefore - amount * feed.getMaxDurability() / totalMax)) < EPSILON,
                    "机构应承担与最大耐久成正比的份额，实际 " + feed.getDurability());
            helper.assertTrue(Math.abs(magazine.getDurability() - (magazineBefore - amount * magazine.getMaxDurability() / totalMax)) < EPSILON,
                    "弹仓应承担与最大耐久成正比的份额，实际 " + magazine.getDurability());
            helper.succeed();
        } finally {
            part.discardUnattached();
        }
    }

    /**
     * 机构损坏后：交付承诺全部释放、三个交付入口拒绝、库存不动。
     *
     * <p>库存用退弹入口灌一发：承诺阶段不扣库存，退弹是内容包之外唯一能加弹的公开入口；
     * 弹链指针没有公开读法，{@code consumeReadyRound} 返回 null 即证明它没有被推进。</p>
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void brokenFeedReleasesDeliveryAndKeepsStock(GameTestHelper helper) {
        Selection selected = loaderPart();
        RegularPart part = PartFactory.createRegular(selected.type(), selected.variant(), helper.getLevel());
        try {
            markAssembled(part);
            RegenLoaderSubsystem loader = loader(part);
            LauncherSubsystem launcher = launcher(part);
            helper.assertTrue(loader.isActive(), "组装完成的部件里的子系统应处于可工作状态");

            ProjectileType round = ProjectileType.get(helper.getLevel(), loader.getProjectileTypes().getFirst());
            helper.assertTrue(round != null, "弹链首发的投射物类型必须已注册");
            helper.assertTrue(loader.returnRound(round), "空弹仓应接受一发退弹");
            int stockBefore = loader.getRemainingCount();
            helper.assertTrue(stockBefore > 0, "退弹后弹仓应有库存");

            helper.assertTrue(loader.requestRound(launcher), "健康机构应受理请求");
            helper.assertTrue(loader.isRoundScheduled(launcher), "受理后应出现交付承诺");

            loader.routeDamage("feed", Float.MAX_VALUE);

            helper.assertTrue(!loader.isSupplyOperational(), "机构归零即失去交付能力");
            helper.assertTrue(loader.hasAmmo(), "机构损坏不消耗库存");
            helper.assertTrue(!loader.isRoundScheduled(launcher) && !loader.hasScheduledRound(),
                    "机构损坏必须释放全部交付承诺");
            helper.assertTrue(!loader.requestRound(launcher), "坏机构不得受理新请求");
            helper.assertTrue(!loader.isRoundReady(launcher), "坏机构不得报就绪");
            helper.assertTrue(loader.consumeReadyRound(launcher) == null, "坏机构不得取走弹药");
            helper.assertTrue(loader.getRemainingCount() == stockBefore, "库存计数不得变化");
            helper.assertTrue(loader.getStatus(launcher) == IAmmoSupplier.SupplierStatus.EMPTY,
                    "无交付能力的供给者对消费者报 EMPTY");
            helper.assertTrue(loader.getReloadProgress(launcher) == 0f, "无交付能力时进度按 0 报");
            helper.assertTrue(loader.getModule("magazine").getDurability() > 0f, "只打坏机构，弹仓保持完好");
            helper.succeed();
        } finally {
            part.discardUnattached();
        }
    }

    /**
     * 摧毁状态随模块存档走，并在载入时与耐久归一（T15—T17 的持久化部分）。
     *
     * <p>载入读的是一个新建的子系统实例，走真实 {@code saveData} / {@code loadData} 路径，覆盖五条：</p>
     * <ol>
     *   <li>弹仓归零的摧毁状态进存档，读档后立刻呈现，不等首 tick 重新判定（否则会重放摧毁回调）；</li>
     *   <li>整机摧毁状态不单独存一份：它由各模块 {@code destroyed} 汇总，多存只会多一个分叉来源；</li>
     *   <li>修到 10% 的恢复锁存跨存档保留——这正是不能只靠耐久推断的那一段；</li>
     *   <li>修过 30% 之后模块与整机都恢复；</li>
     *   <li>“零耐久 + 未摧毁”“满耐久 + 摧毁”这类手改组合在载入时按耐久归一。</li>
     * </ol>
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void destroyedStateSurvivesSaveAndIsValidatedOnLoad(GameTestHelper helper) {
        Selection selected = loaderPart();
        RegularPart part = PartFactory.createRegular(selected.type(), selected.variant(), helper.getLevel());
        try {
            markAssembled(part);
            RegenLoaderSubsystem loader = loader(part);
            loader.routeDamage("magazine", Float.MAX_VALUE);
            loader.onTick(); // 走一次摧毁边沿，把历史摧毁状态落到同步位
            helper.assertTrue(loader.isDestroyed(), "弹仓归零后摧毁边沿应置摧毁位");

            CompoundTag saved = loader.saveData(new CompoundTag());
            helper.assertTrue(saved.contains("modules"), "模块状态必须进存档");
            helper.assertTrue(!saved.contains("destroyed"),
                    "模块化子系统不存整机摧毁键：它是各模块摧毁标志的汇总，单独存一份只会有分叉风险");
            helper.assertTrue(saved.getCompound("modules").getCompound("magazine").getBoolean("destroyed"),
                    "弹仓的摧毁状态必须随模块进存档");
            helper.assertTrue(saved.getFloat("durability_ratio") > 0f,
                    "durability_ratio 是模块耐久之和的比例：机构没坏，弹仓归零时它并不为 0，"
                            + "所以摧毁状态不能只读这个比例推断");

            RegenLoaderSubsystem restored = reload(loader, "mm_gametest_restored", saved);
            helper.assertTrue(restored.isDestroyed(), "载入后应立即呈现摧毁状态，不等第一 tick 重新判定");
            helper.assertTrue(restored.getModule("magazine").getDurability() == 0f
                            && restored.getModule("magazine").isDestroyed(),
                    "弹仓的零耐久与摧毁标志应按存档恢复");
            helper.assertTrue(restored.getModule("feed").getDurability() > 0f,
                    "机构没有受损，不应被弹仓的状态连带清零");
            helper.assertTrue(!restored.isSupplyOperational(), "摧毁状态下的装弹机不交付");

            // 修到 10%：模块仍在 0%—30% 的锁存区间，模块与整机都还是摧毁，且跨存档保留
            SubModule magazine = restored.getModule("magazine");
            float tenPercent = magazine.getMaxDurability() * 0.1f;
            restored.routeRepair("magazine", tenPercent);
            helper.assertTrue(magazine.isDestroyed(), "修到 10% 的模块必须仍处于摧毁状态，实际 " + magazine.getDurability());
            helper.assertTrue(restored.isDestroyed(), "整机摧毁状态跟随关键模块，不因修到 10% 提前解除");

            RegenLoaderSubsystem twice = reload(loader, "mm_gametest_latched", restored.saveData(new CompoundTag()));
            SubModule twiceMagazine = twice.getModule("magazine");
            helper.assertTrue(Math.abs(twiceMagazine.getDurability() - tenPercent) < EPSILON,
                    "修复后的弹仓耐久应按存档恢复，实际 " + twiceMagazine.getDurability());
            helper.assertTrue(twiceMagazine.isDestroyed() && twice.isDestroyed(),
                    "0%—30% 之间的恢复锁存必须跨存档保留");

            // 修过 30%：模块脱离摧毁，整机在下一次 tick 边沿恢复
            twice.routeRepair("magazine", twiceMagazine.getMaxDurability() * AbstractSubsystem.RECOVERY_RATIO);
            helper.assertTrue(!twiceMagazine.isDestroyed(), "越过恢复门槛后模块应脱离摧毁");
            twice.onTick();
            helper.assertTrue(!twice.isDestroyed(), "关键模块全部恢复后整机应在 tick 边沿解除摧毁");
            helper.assertTrue(twice.isSupplyOperational(), "恢复后的装弹机重新具备交付能力");

            // 归一：零耐久却写着未摧毁 → 摧毁
            CompoundTag zeroForged = loader.saveData(new CompoundTag());
            CompoundTag zeroMagazine = zeroForged.getCompound("modules").getCompound("magazine");
            zeroMagazine.putFloat("durability", 0f);
            zeroMagazine.putBoolean("destroyed", false);
            RegenLoaderSubsystem zeroBroken = reload(loader, "mm_gametest_zero_forged", zeroForged);
            helper.assertTrue(zeroBroken.getModule("magazine").isDestroyed() && zeroBroken.isDestroyed(),
                    "零耐久却写着未摧毁的模块在载入时应归一为摧毁");

            // 归一：满耐久却写着摧毁 → 未摧毁
            CompoundTag forged = loader.saveData(new CompoundTag());
            CompoundTag forgedMagazine = forged.getCompound("modules").getCompound("magazine");
            forgedMagazine.putFloat("durability", magazine.getMaxDurability());
            forgedMagazine.putBoolean("destroyed", true);
            RegenLoaderSubsystem normalized = reload(loader, "mm_gametest_forged", forged);
            helper.assertTrue(!normalized.getModule("magazine").isDestroyed() && !normalized.isDestroyed(),
                    "耐久已到恢复门槛的模块，存档里的摧毁位不生效");
            helper.succeed();
        } finally {
            part.discardUnattached();
        }
    }

    /**
     * 非关键模块同样套用恢复锁存：机构归零后修到 20% 仍不可工作，越过 30% 才恢复交付，
     * 而整机摧毁位始终不受非关键模块影响。
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void nonCriticalModuleKeepsTheRecoveryLatch(GameTestHelper helper) {
        Selection selected = loaderPart();
        RegularPart part = PartFactory.createRegular(selected.type(), selected.variant(), helper.getLevel());
        try {
            markAssembled(part);
            RegenLoaderSubsystem loader = loader(part);
            SubModule feed = loader.getModule("feed");
            helper.assertTrue(!feed.getAttr().critical(), "机构是非关键模块，本用例验证它同样受恢复门槛约束");
            helper.assertTrue(loader.isSupplyOperational(), "满耐久的两个模块应构成工作能力");

            loader.routeDamage("feed", Float.MAX_VALUE);
            helper.assertTrue(feed.isDestroyed(), "机构归零即摧毁");
            helper.assertTrue(!loader.isSupplyOperational(), "机构摧毁时整机失去交付能力");
            helper.assertTrue(!loader.isDestroyed(), "非关键模块损坏不判整机摧毁");

            loader.routeRepair("feed", feed.getMaxDurability() * 0.2f);
            helper.assertTrue(feed.isDestroyed(), "修到 20% 仍在锁存区间，机构必须仍不可工作");
            helper.assertTrue(!loader.isSupplyOperational(), "锁存期间不得交付");

            loader.routeRepair("feed", feed.getMaxDurability() * 0.2f);
            helper.assertTrue(!feed.isDestroyed(), "越过恢复门槛后机构脱离摧毁");
            helper.assertTrue(loader.isSupplyOperational(), "机构恢复后交付能力回来");
            helper.assertTrue(!loader.isDestroyed(), "非关键模块的修复不改变整机摧毁位");
            helper.succeed();
        } finally {
            part.discardUnattached();
        }
    }

    /**
     * 起爆定位只认弹仓模块自己的最近有效受击（T09、T06/T07 的定位部分）。
     *
     * <p>装配两个登记到宿主上的探针命中箱，分别指向机构与弹仓，逐条固定：</p>
     * <ol>
     *   <li>只打机构时弹仓没有记录，因此不豁免任何命中箱（起爆点回退质心）；</li>
     *   <li>打爆弹仓后，豁免恰好是弹仓那个命中箱；</li>
     *   <li>随后命中机构不会改写弹仓的来源；</li>
     *   <li>零耐久的追加命中同样不会改写；</li>
     *   <li>弹仓修到恢复门槛后记录被清空，下一次生命周期从零开始。</li>
     * </ol>
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void cookoffSourceFollowsTheMagazineModule(GameTestHelper helper) {
        Selection selected = loaderPart();
        RegularPart part = PartFactory.createRegular(selected.type(), selected.variant(), helper.getLevel());
        try {
            markAssembled(part);
            RegenLoaderSubsystem loader = loader(part);
            SubPart host = loader.getSubPart();
            HitBox magazineProbe = registerProbe(loader, "mm_gametest_magazine", "magazine");
            HitBox feedProbe = registerProbe(loader, "mm_gametest_feed", "feed");

            loader.onHurt(hitEvent(helper, loader, feedProbe, 1f));
            helper.assertTrue(loader.getModule("feed").getLastHitEvent() != null, "机构应记住打到它的那一击");
            helper.assertTrue(loader.getModule("magazine").getLastHitEvent() == null, "只打机构不得在弹仓上留记录");
            helper.assertTrue(loader.getCookoffIgnoredKeys().isEmpty(),
                    "弹仓没有有效受击来源时不得豁免任何命中箱（不把宿主整块装甲放行）");

            loader.onHurt(hitEvent(helper, loader, magazineProbe, Float.MAX_VALUE));
            helper.assertTrue(loader.getModule("magazine").isDestroyed(), "弹仓应被这一击打爆");
            assertZone(helper, loader.getCookoffIgnoredKeys(), "mm_gametest_magazine", "爆点应来自弹仓命中箱");

            loader.onHurt(hitEvent(helper, loader, feedProbe, 1f));
            assertZone(helper, loader.getCookoffIgnoredKeys(), "mm_gametest_magazine", "机构命中不得覆盖弹仓来源");

            loader.onHurt(hitEvent(helper, loader, magazineProbe, 2f));
            assertZone(helper, loader.getCookoffIgnoredKeys(), "mm_gametest_magazine", "零耐力追加命中不得改写来源");

            SubModule magazine = loader.getModule("magazine");
            loader.routeRepair("magazine", magazine.getMaxDurability() * AbstractSubsystem.RECOVERY_RATIO);
            helper.assertTrue(!magazine.isDestroyed(), "修到恢复门槛后弹仓脱离摧毁");
            helper.assertTrue(magazine.getLastHitEvent() == null, "脱离摧毁即结束一次生命周期，受击记录应清空");
            helper.succeed();
        } finally {
            part.discardUnattached();
        }
    }

    /**
     * 残骸上的弹药架仍可被引爆（§6.1）：入口条件只有"服务端 + 弹仓摧毁 + 库存非空 + 合成非空"。
     *
     * <p>宿主子零件被摧毁时只把子系统 {@code setActive(false)}（{@code SubPart#setDestroyed}），
     * 子系统照常 tick，因此弹仓归零的判毁边沿照常发生并提交一次殉爆；零件整体移除后子系统不再 tick，
     * 自然不会起爆。</p>
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void wreckedMagazineStillDetonates(GameTestHelper helper) {
        Selection selected = loaderPart();
        RegularPart part = PartFactory.createRegular(selected.type(), selected.variant(), helper.getLevel());
        try {
            markAssembled(part);
            RegenLoaderSubsystem loader = loader(part);
            loader.loadData(stock(8));
            helper.assertTrue(loader.getAmmoCount() == 8, "前置：残骸的库存应非空");

            SubPart host = loader.getSubPart();
            host.setDestroyed();
            helper.assertTrue(host.isDestroyed(), "宿主子零件应处于摧毁态");
            helper.assertTrue(!loader.isActive(), "零件摧毁后子系统不再激活");

            ExplosionManager explosions = ExplosionManager.get(helper.getLevel());
            int before = explosions.activeInstances().size();

            loader.routeDamage("magazine", Float.MAX_VALUE);
            loader.onTick(); // 判毁边沿

            helper.assertTrue(loader.isDestroyed(), "残骸上的弹仓归零仍应走判毁边沿");
            helper.assertTrue(loader.getAmmoCount() == 0, "库存在殉爆中被消耗，实际 " + loader.getAmmoCount());
            helper.assertTrue(explosions.activeInstances().size() == before + 1,
                    "残骸应提交一次殉爆，活跃实例 " + before + " → " + explosions.activeInstances().size());
            helper.succeed();
        } finally {
            part.discardUnattached();
        }
    }

    /**
     * 弹仓归零作废进行中的产出（§5.2、§7.3）：判毁边沿清一次，锁存态读档再清一次。
     *
     * <p>四个字段没有对外的读取口，但它们正是 {@code saveData} 写出的键，因此直接读存档断言；
     * 机构单独损坏时这些状态必须保留。</p>
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void magazineDamageClearsInProgressRegen(GameTestHelper helper) {
        Selection selected = loaderPart();
        RegularPart part = PartFactory.createRegular(selected.type(), selected.variant(), helper.getLevel());
        try {
            markAssembled(part);
            RegenLoaderSubsystem loader = loader(part);

            CompoundTag inProgress = stock(1);
            inProgress.putFloat("regen_progress", 0.9f);
            inProgress.putBoolean("is_batch_reloading", true);
            inProgress.putInt("batch_elapsed", 7);
            inProgress.putInt("regen_delay_remaining", 40);
            loader.loadData(inProgress);
            helper.assertTrue(Math.abs(loader.saveData(new CompoundTag()).getFloat("regen_progress") - 0.9f) < EPSILON,
                    "前置：再生进度应已载入");

            loader.routeDamage("magazine", Float.MAX_VALUE);
            loader.onTick(); // 判毁边沿：弹仓归零即整机摧毁
            helper.assertTrue(loader.isDestroyed(), "弹仓归零后整机应判毁");

            CompoundTag cleared = loader.saveData(new CompoundTag());
            helper.assertTrue(cleared.getFloat("regen_progress") == 0f,
                    "判毁边沿应清零再生进度，实际 " + cleared.getFloat("regen_progress"));
            helper.assertTrue(!cleared.getBoolean("is_batch_reloading"), "判毁边沿应结束批量冷却");
            helper.assertTrue(cleared.getInt("batch_elapsed") == 0, "判毁边沿应清零批量已消耗量");
            helper.assertTrue(cleared.getInt("regen_delay_remaining") == 0, "判毁边沿应清零再生延迟");

            // 锁存态存档：读档不会补发摧毁回调，因此载入边界必须自己清
            CompoundTag latched = stock(3);
            latched.putFloat("durability_ratio", 0f);
            latched.putFloat("regen_progress", 0.6f);
            latched.putBoolean("is_batch_reloading", true);
            latched.putInt("regen_delay_remaining", 25);
            RegenLoaderSubsystem restored = reload(loader, "mm_gametest_latched_regen", latched);
            helper.assertTrue(restored.isDestroyed(), "零比例存档应读出弹仓摧毁");
            CompoundTag restoredData = restored.saveData(new CompoundTag());
            helper.assertTrue(restoredData.getFloat("regen_progress") == 0f,
                    "锁存态读档不得保留上一段生命周期的进度");
            helper.assertTrue(!restoredData.getBoolean("is_batch_reloading"), "锁存态读档不得保留批量冷却");
            helper.assertTrue(restoredData.getInt("regen_delay_remaining") == 0, "锁存态读档不得保留再生延迟");

            // 机构单独损坏：只清交付承诺，再生状态原样保留
            loader.loadData(inProgress);
            loader.routeDamage("feed", Float.MAX_VALUE);
            CompoundTag kept = loader.saveData(new CompoundTag());
            helper.assertTrue(Math.abs(kept.getFloat("regen_progress") - 0.9f) < EPSILON,
                    "机构损坏不得清除再生进度，实际 " + kept.getFloat("regen_progress"));
            helper.assertTrue(kept.getBoolean("is_batch_reloading"), "机构损坏不得结束批量冷却");
            helper.succeed();
        } finally {
            part.discardUnattached();
        }
    }

    /**
     * 退弹边界（T14）：机构损坏但弹仓可储存时照收；满仓拒收；弹仓归零或停在 0%—30% 恢复锁存时拒收。
     *
     * <p>退弹按"已被取走的一发重新记入储备"处理，不模拟逆向输弹机械，因此机构状态与启停开关
     * 都不进入判定；判定只看弹仓模块是否可储存。</p>
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void returnRoundFollowsTheMagazineState(GameTestHelper helper) {
        Selection selected = loaderPart();
        RegularPart part = PartFactory.createRegular(selected.type(), selected.variant(), helper.getLevel());
        try {
            markAssembled(part);
            RegenLoaderSubsystem loader = loader(part);
            helper.assertTrue(!loader.getProjectileTypes().isEmpty(), "前置：弹链应非空");
            ProjectileType round = ProjectileType.get(helper.getLevel(), loader.getProjectileTypes().getFirst());
            helper.assertTrue(round != null, "弹链首发弹种应能在测试世界里解码");

            loader.loadData(stock(2));
            loader.routeDamage("feed", Float.MAX_VALUE);
            helper.assertTrue(loader.returnRound(round), "机构损坏而弹仓可储存时应收下退弹");
            helper.assertTrue(loader.getAmmoCount() == 3, "退弹应让库存 +1，实际 " + loader.getAmmoCount());

            loader.loadData(stock(loader.getCapacity()));
            helper.assertTrue(!loader.returnRound(round), "满仓必须拒收退弹");
            helper.assertTrue(loader.getAmmoCount() == loader.getCapacity(), "拒收不得改变库存");

            loader.loadData(stock(2));
            loader.routeDamage("magazine", Float.MAX_VALUE);
            helper.assertTrue(!loader.returnRound(round), "弹仓归零后必须拒收退弹");
            helper.assertTrue(loader.getAmmoCount() == 2, "拒收不得改变库存，实际 " + loader.getAmmoCount());

            SubModule magazine = loader.getModule("magazine");
            loader.routeRepair("magazine", magazine.getMaxDurability() * 0.1f);
            helper.assertTrue(magazine.isDestroyed(), "前置：10% 仍在恢复锁存");
            helper.assertTrue(!loader.returnRound(round), "恢复锁存期间必须拒收退弹");

            loader.routeRepair("magazine", magazine.getMaxDurability() * AbstractSubsystem.RECOVERY_RATIO);
            helper.assertTrue(!magazine.isDestroyed(), "越过恢复门槛后弹仓脱离摧毁");
            helper.assertTrue(loader.returnRound(round), "弹仓恢复后可储存，应重新接受退弹");
            helper.succeed();
        } finally {
            part.discardUnattached();
        }
    }

    /** 只带库存的存档片段：模块耐久按缺省比例（满）载入。 */
    private static CompoundTag stock(int count) {
        CompoundTag data = new CompoundTag();
        data.putInt("ammo_count", count);
        return data;
    }

    /** 在宿主上登记一个探针命中箱：材质与厚度借用现有最强命中箱，只换 id、子系统与模块名。 */
    private static HitBox registerProbe(AbstractSubsystem subsystem, String id, String module) {
        SubPart host = subsystem.getSubPart();
        HitBox source = host.findStrongestHitBox();
        HitBoxAttr attr = new HitBoxAttr(id, "box", subsystem.getName(), module,
                source != null ? source.getAttr().getMaterial() : MaterialAttr.DEFAULT_ID,
                source != null ? source.getAttr().getThickness() : 1f,
                "true", null);
        HitBox probe = new HitBox(host, attr, null);
        host.hitBoxes.put(id, probe);
        return probe;
    }

    /** 构造一次命中给定的已登记命中箱的事件。 */
    private static SubPartDamageEvent.Pre hitEvent(GameTestHelper helper, AbstractSubsystem subsystem,
                                                   HitBox hitBox, float amount) {
        BFDamageContext ctx = BFDamageContext.builder()
                .source(helper.getLevel().damageSources().generic())
                .baseDamage(amount)
                .build();
        ctx.extensions().set(MMDamageExtensions.HIT_BOX, hitBox);
        return new SubPartDamageEvent.Pre(subsystem.getSubPart(), ctx, amount);
    }

    private static void assertZone(GameTestHelper helper, Set<PenetrationKey> keys, String expected, String message) {
        helper.assertTrue(keys.size() == 1, message + "：应只豁免一个命中箱，实际 " + keys.size());
        helper.assertTrue(expected.equals(keys.iterator().next().getZoneId()),
                message + "：豁免的命中箱应为 " + expected + "，实际 " + keys.iterator().next().getZoneId());
    }

    /** 用同一个静态定义新建一个子系统实例并载入给定存档，模拟读档。 */
    private static RegenLoaderSubsystem reload(RegenLoaderSubsystem source, String name, CompoundTag data) {
        RegenLoaderSubsystem copy = (RegenLoaderSubsystem) source.attr.createSubsystem(source.getSubPart(), name);
        copy.loadData(data);
        return copy;
    }

    /**
     * 旧存档只有一个 {@code durability_ratio}（T15）：比例分摊进两个模块，聚合等于旧比例；
     * 非有限比例按满耐久载入；零比例读出弹仓摧毁；直接写整机耐久同样按分摊口径落到模块。
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void legacyDurabilityRatioMapsIntoModules(GameTestHelper helper) {
        Selection selected = loaderPart();
        RegularPart part = PartFactory.createRegular(selected.type(), selected.variant(), helper.getLevel());
        try {
            markAssembled(part);
            RegenLoaderSubsystem loader = loader(part);
            float max = loader.getMaxDurability();

            CompoundTag legacy = new CompoundTag();
            legacy.putFloat("durability_ratio", 0.4f);
            RegenLoaderSubsystem restored = reload(loader, "mm_gametest_legacy", legacy);
            SubModule feed = restored.getModule("feed");
            SubModule magazine = restored.getModule("magazine");
            helper.assertTrue(Math.abs(feed.getDurability() - feed.getMaxDurability() * 0.4f) < EPSILON,
                    "机构应按旧比例分摊，实际 " + feed.getDurability());
            helper.assertTrue(Math.abs(magazine.getDurability() - magazine.getMaxDurability() * 0.4f) < EPSILON,
                    "弹仓应按旧比例分摊，实际 " + magazine.getDurability());
            helper.assertTrue(Math.abs(restored.getDurability() - max * 0.4f) < EPSILON,
                    "聚合耐久应等于旧比例对应的耐久，实际 " + restored.getDurability());
            helper.assertTrue(!feed.isDestroyed() && !magazine.isDestroyed() && !restored.isDestroyed(),
                    "40% 高于恢复门槛，旧存档不应读出摧毁状态");

            CompoundTag broken = new CompoundTag();
            broken.putFloat("durability_ratio", Float.NaN);
            RegenLoaderSubsystem sanitized = reload(loader, "mm_gametest_nan", broken);
            helper.assertTrue(Math.abs(sanitized.getDurability() - max) < EPSILON,
                    "非有限比例应按满耐久载入，实际 " + sanitized.getDurability());

            CompoundTag dead = new CompoundTag();
            dead.putFloat("durability_ratio", 0f);
            RegenLoaderSubsystem zeroed = reload(loader, "mm_gametest_zero_ratio", dead);
            helper.assertTrue(zeroed.getModule("magazine").isDestroyed() && zeroed.isDestroyed(),
                    "零比例的旧存档应读出弹仓摧毁");
            helper.assertTrue(!zeroed.isSupplyOperational(), "零比例的装弹机不交付");

            // 直接写整机耐久：按缺失容量分摊，已满的机构不拿份额，坏掉的弹仓被补回来
            loader.routeDamage("magazine", Float.MAX_VALUE);
            loader.setDurability(loader.getMaxDurability() * 0.75f);
            helper.assertTrue(Math.abs(loader.getDurability() - loader.getMaxDurability() * 0.75f) < EPSILON,
                    "整机耐久写入后聚合应等于请求值，实际 " + loader.getDurability());
            helper.assertTrue(loader.getModule("feed").getDurability() == loader.getModule("feed").getMaxDurability(),
                    "已满的机构不应分到新增耐久");
            helper.assertTrue(loader.getModule("magazine").getDurability() > 0f, "坏掉的弹仓应被补回耐久");
            helper.succeed();
        } finally {
            part.discardUnattached();
        }
    }

    /** 两个模块字段可省略、权重可显式、非法取值在属性边界报错。 */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void moduleFieldsDecodeWithDefaultsAndValidation(GameTestHelper helper) {
        RegenLoaderSubsystemStaticAttr defaults =
                decodeAttribute("{ \"magazine_capacity\": 10, \"reload_time\": 0 }").getOrThrow();
        helper.assertTrue(defaults.getFeed() == FeedModuleAttr.DEFAULT
                        && defaults.getMagazine() == MagazineModuleAttr.DEFAULT,
                "省略的模块字段应落到默认实例");
        helper.assertTrue(!defaults.getFeed().critical(), "机构是非关键模块");
        helper.assertTrue(defaults.getMagazine().critical(), "弹仓是唯一关键模块");

        RegenLoaderSubsystemStaticAttr weighted = decodeAttribute("""
                {
                  "magazine_capacity": 30,
                  "reload_time": 1.5,
                  "basic_durability": 60,
                  "feed": { "durability_weight": 3.0, "critical": true },
                  "magazine": { "durability_weight": 1.0 }
                }
                """).getOrThrow();
        helper.assertTrue(weighted.getFeed().durabilityWeight() == 3.0f
                        && weighted.getMagazine().durabilityWeight() == 1.0f,
                "显式权重应被保留");
        helper.assertTrue(!weighted.getFeed().critical(), "critical 不开放 JSON 配置，机构恒为非关键");

        helper.assertTrue(decodeAttribute("{ \"magazine_capacity\": 10, \"reload_time\": 0, \"feed\": { \"durability_weight\": 0 } }")
                .error().isPresent(), "零权重应被拒绝");
        helper.assertTrue(decodeAttribute("{ \"magazine_capacity\": 10, \"reload_time\": 0, \"magazine\": { \"durability_weight\": -1.0 } }")
                .error().isPresent(), "负权重应被拒绝");
        helper.assertTrue(decodeAttribute("{ \"magazine_capacity\": 10, \"reload_time\": 0, \"magazine\": { \"durability_weight\": \"NaN\" } }")
                .error().isPresent(), "非有限权重应被拒绝");
        helper.assertTrue(decodeAttribute("{ \"magazine_capacity\": 10, \"reload_time\": 0, \"basic_durability\": 0 }")
                .error().isPresent(), "非正的 basic_durability 应被拒绝");
        // 两个权重各自有限为正，合计仍可溢出到正无穷，那会让模块最大耐久全部塌成 0
        helper.assertTrue(decodeAttribute("""
                {
                  "magazine_capacity": 10,
                  "reload_time": 0,
                  "feed": { "durability_weight": 3.4e38 },
                  "magazine": { "durability_weight": 3.4e38 }
                }
                """).error().isPresent(), "权重合计溢出应被拒绝");
        helper.succeed();
    }
}
