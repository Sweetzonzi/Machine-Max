package io.github.sweetzonzi.machine_max.common.gametest;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPartType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.recipe.PartFabricatingRecipe;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.SimpleContainer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 组装进度、材料档位与子零件耐久三者的服务端契约；刚体保持在物理世界之外。
 *
 * <p>三条规则在这里固定：进度决定耐久写入上限；装配推进按增量补耐久而不是补到上限；受伤只按材料档位
 * 压进度，档位之内的损伤留在耐久上。蓝图放出的 skeleton 与零件物品放置共用同一套进度写入，
 * 因此未组装的部件以 5% 耐久出生。</p>
 */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PartAssemblyDurabilityGameTest {
    private static final String BATCH = "mmPartAssemblyDurability";
    private static final float EPSILON = 1e-3f;
    /** 耐久写入上限的地板，与 {@link RegularPart#durabilityLimitRatio()} 的口径一致。 */
    private static final float DURABILITY_FLOOR = 0.05f;

    private PartAssemblyDurabilityGameTest() {
    }

    private record Selection(RegularPartType type, String variant, PartFabricatingRecipe recipe) {
        int materials() {
            return recipe.getManualAssembleIngredientList().size();
        }
    }

    /**
     * 挑选一个手动组装清单至少有三种材料的普通部件。
     *
     * <p>档位按材料项数均分，三种以上才能在耐久 0.5 时表达「掉档但没掉光」；候选排序后再取，
     * 避免定义表的哈希顺序让用例在不同启动间校验到不同部件。清单与材料项数都从内容包自身读出，
     * 用例不固定具体是哪个部件、有几项材料。</p>
     */
    private static Selection select(GameTestHelper helper) {
        List<Selection> candidates = new ArrayList<>();
        for (RegularPartType type : PartDefinitions.regular(false).values()) {
            var holder = MMDynamicRes.getPartRecipe(helper.getLevel(), type.getRegistryKey());
            if (holder == null || holder.value().getManualAssembleIngredientList().size() < 3) continue;
            for (String variant : type.variants.keySet()) {
                candidates.add(new Selection(type, variant, holder.value()));
            }
        }
        return candidates.stream()
                .min(Comparator.comparing((Selection selection) -> selection.type().getRegistryKey().toString())
                        .thenComparing(Selection::variant))
                .orElseThrow(() -> new IllegalStateException("需要一个手动组装清单至少含三种材料的普通部件"));
    }

    /** 读取某个子零件的耐久比例，共享耐久与非共享耐久取到同一个口径。 */
    private static float durabilityRatio(SubPart subPart) {
        return subPart.getMaxDurability() <= 0f ? 0f : subPart.getDurability() / subPart.getMaxDurability();
    }

    /** 把部件的全部子零件耐久写成同一个比例。 */
    private static void damageTo(RegularPart part, float ratio) {
        for (SubPart subPart : part.subParts.values()) {
            subPart.setDurability(subPart.getMaxDurability() * ratio);
        }
    }

    /** 断言全部子零件的耐久比例都等于期望值。 */
    private static void assertDurabilityRatio(GameTestHelper helper, RegularPart part, float ratio, String message) {
        for (SubPart subPart : part.subParts.values()) {
            float actual = durabilityRatio(subPart);
            helper.assertTrue(Math.abs(actual - ratio) <= EPSILON,
                    message + "：期望 " + ratio + "，实际 " + part.name + "/" + subPart.name + " = " + actual);
        }
    }

    /**
     * 未组装的部件以 5% 耐久出生。
     *
     * <p>蓝图与零件制造蓝图放置路径先构造完整部件、再把材料与进度清零；耐久必须跟着新上限落到地板，
     * 否则同一份 blueprint 会放出两倍耐久的 skeleton。</p>
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void unassembledPartStartsAtDurabilityFloor(GameTestHelper helper) {
        Selection selected = select(helper);
        RegularPart part = PartFactory.createRegular(selected.type(), selected.variant(), helper.getLevel());
        try {
            assertDurabilityRatio(helper, part, 1f, "新建完整部件应满耐久");
            part.setMaterialProgress(0);
            part.setAssemblingProgress(0f);
            helper.assertTrue(part.getAssemblingProgress() == 0f && part.getMaterialProgress() == 0,
                    "放置路径应把两项进度都清零");
            helper.assertTrue(part.isAssembling(), "零进度部件处于组装形态");
            assertDurabilityRatio(helper, part, DURABILITY_FLOOR, "清零进度后耐久应落到上限地板");
            helper.succeed();
        } finally {
            part.discardUnattached();
        }
    }

    /** 受伤只按材料档位压进度，一档之内的损伤留在耐久上。 */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void damageLowersProgressByMaterialTierOnly(GameTestHelper helper) {
        Selection selected = select(helper);
        int materials = selected.materials();
        RegularPart part = PartFactory.createRegular(selected.type(), selected.variant(), helper.getLevel());
        try {
            part.setMaterialProgress(materials);
            part.setAssemblingProgress(1f);
            damageTo(part, 0.5f);
            part.onDurabilityChanged();
            helper.assertTrue(part.getMaterialProgress() < materials,
                    "耐久跌破整档后材料进度应跟着掉，实际 " + part.getMaterialProgress() + "/" + materials);
            helper.assertTrue(Math.abs(part.getAssemblingProgress() - (float) part.getMaterialProgress() / materials) <= EPSILON,
                    "组装进度应等于材料档位比例，实际 " + part.getAssemblingProgress()
                            + "，材料 " + part.getMaterialProgress() + "/" + materials);
            helper.assertTrue(part.getAssemblingProgress() > 0.5f,
                    "档位内的损伤不得把进度压到耐久同值，实际 " + part.getAssemblingProgress());
            assertDurabilityRatio(helper, part, 0.5f, "受伤只扣耐久，进度下调不回头改耐久");
            helper.succeed();
        } finally {
            part.discardUnattached();
        }
    }

    /** 装配推进按增量补耐久，原有缺口原样保留。 */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void assemblyRaisesDurabilityByTheSameIncrement(GameTestHelper helper) {
        Selection selected = select(helper);
        int materials = selected.materials();
        RegularPart part = PartFactory.createRegular(selected.type(), selected.variant(), helper.getLevel());
        try {
            part.setMaterialProgress(materials);
            part.setAssemblingProgress(0.5f);
            damageTo(part, 0.4f);
            boolean assembled = part.assemble(new SimpleContainer(0), selected.recipe().getProcessingTime() * 0.1f);
            helper.assertTrue(assembled, "材料齐备时应能推进组装");
            helper.assertTrue(Math.abs(part.getAssemblingProgress() - 0.6f) <= EPSILON,
                    "本拍应推进到 0.6，实际 " + part.getAssemblingProgress());
            assertDurabilityRatio(helper, part, 0.5f, "进度涨 0.1 应同步补 0.1 耐久");
            helper.succeed();
        } finally {
            part.discardUnattached();
        }
    }

    /** 拆解下调进度后，超出新上限的耐久由进度写入一并钳回。 */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void disassemblyClampsDurabilityToTheNewLimit(GameTestHelper helper) {
        Selection selected = select(helper);
        int materials = selected.materials();
        RegularPart part = PartFactory.createRegular(selected.type(), selected.variant(), helper.getLevel());
        try {
            part.setMaterialProgress(materials);
            part.setAssemblingProgress(1f);
            assertDurabilityRatio(helper, part, 1f, "满进度部件应满耐久");
            boolean disassembled = part.disassemble(new SimpleContainer(materials + 1),
                    selected.recipe().getProcessingTime() * 0.5f);
            helper.assertTrue(disassembled, "满进度部件应能拆解");
            helper.assertTrue(Math.abs(part.getAssemblingProgress() - 0.5f) <= EPSILON,
                    "本拍应拆到 0.5，实际 " + part.getAssemblingProgress());
            assertDurabilityRatio(helper, part, 0.5f, "拆解下调上限应与装配抬升对称");
            helper.succeed();
        } finally {
            part.discardUnattached();
        }
    }

    /** 维修止于组装上限，且不会挡住随后的装配推进。 */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void repairStopsAtTheAssemblyCap(GameTestHelper helper) {
        Selection selected = select(helper);
        int materials = selected.materials();
        RegularPart part = PartFactory.createRegular(selected.type(), selected.variant(), helper.getLevel());
        try {
            part.setMaterialProgress(materials);
            part.setAssemblingProgress(0.5f);
            // 0.45 的耐久一定落在整档之上：材料总数 n 满足 floor(0.45n)+1 ≤ n-1，因此上限必然低于 1，
            // 用例不依赖具体是哪个部件、有几项材料
            damageTo(part, 0.45f);
            part.onDurabilityChanged();
            float cap = part.getAssemblingProgress();
            helper.assertTrue(cap < 1f, "受伤后组装上限必须低于满值，实际 " + cap);
            // 每个子零件都焊到停：共享耐久时第一个就补满，非共享时逐个补
            boolean progressed = true;
            int guard = 0;
            while (progressed) {
                helper.assertTrue(guard++ < 1000, "维修必须在耐久到达上限时停下，而不是每拍都报告有进展");
                progressed = false;
                for (SubPart child : part.subParts.values()) progressed |= child.repair(5f, 5f, 5f);
            }
            assertDurabilityRatio(helper, part, cap, "维修最多补到组装上限");
            for (SubPart child : part.subParts.values()) {
                helper.assertTrue(!child.repair(5f, 5f, 5f), "已在上限时维修必须返回假：" + child.name);
            }
            // 补回材料＝玩家又把材料递到焊枪上；维修封顶不该把这一拍从装配手里抢走
            part.setMaterialProgress(materials);
            helper.assertTrue(part.assemble(new SimpleContainer(0), selected.recipe().getProcessingTime() * 0.1f),
                    "维修封顶后同一拍仍应能推进装配");
            helper.assertTrue(part.getAssemblingProgress() > cap,
                    "装配进度应继续上涨，实际 " + cap + " → " + part.getAssemblingProgress());
            helper.succeed();
        } finally {
            part.discardUnattached();
        }
    }
}
