package io.github.sweetzonzi.machine_max.common.gametest;

import cn.solarmoon.spark_core.api.SparkLevel;
import com.jme3.math.Vector3f;
import com.mojang.serialization.JsonOps;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.MMServerConfig;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.part.PartRestoreMode;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPartType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.RegularPartData;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Comparator;
import java.util.List;

/**
 * 部件级摧毁状态与重力的服务端验证；刚体保持在物理世界之外。
 *
 * <p>倒计时是部件状态：子零件只携带网络镜像，因此这里同时校验「只有一个权威值」「子零件不自毁」
 * 「剩余时间进存档」与「三种状态下每个子零件的重力」。这些用例不需要客户端即可复现残骸悬空的缺陷。</p>
 */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PartDestroyStateGameTest {
    private static final String BATCH = "mmPartDestroyState";
    private static final float EPSILON = 1e-3f;

    private PartDestroyStateGameTest() {
    }

    private record Selection(RegularPartType type, String variant) {
    }

    /** 挑选一个具有多个子零件的普通部件，使「部分子零件死亡」可表达。 */
    /**
     * 挑选一个具有多个子零件的普通部件，使「部分子零件死亡」可表达。
     *
     * <p>候选按注册名与变体名排序后取第一个：定义表是哈希映射，遍历顺序随机，
     * 不排序会让用例在不同启动间校验到不同部件，断言随之抖动。</p>
     */
    private static Selection multiSubPart(GameTestHelper helper) {
        return PartDefinitions.regular(false).values().stream()
                .flatMap(type -> type.variants.entrySet().stream()
                        .filter(entry -> entry.getValue().subParts.size() > 1)
                        .map(entry -> new Selection(type, entry.getKey())))
                .sorted(Comparator.comparing((Selection selection) -> selection.type().getRegistryKey().toString())
                        .thenComparing(Selection::variant))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("需要具有多个子零件的普通部件定义"));
    }

    /** 按配置推导的倒计时初值，与实现口径一致。 */
    private static int initialDestroyTicks(RegularPart part) {
        int ticksByDurability = Math.round(part.getTotalMaxDurability()
                * MMServerConfig.getSubPartDestroyTicksPerDurability());
        return Math.max(ticksByDurability, MMServerConfig.getSubPartDestroyMinTicks());
    }

    private static void assertGravity(GameTestHelper helper, RegularPart part, float scale, String message) {
        float expectedY = SparkLevel.getPhysicsLevel(part.level).getWorld().getGravity(null).y * scale;
        for (SubPart subPart : part.subParts.values()) {
            float actualY = subPart.body.getGravity(null).y;
            helper.assertTrue(Math.abs(actualY - expectedY) <= 1e-2f,
                    message + "：期望 y=" + expectedY + "，实际 " + part.name + "/" + subPart.name + " y=" + actualY);
        }
    }

    /** 损坏配置：把存档里会影响摧毁/重力的字段改成指定值。 */
    private static RegularPartData variant(RegularPartData base, float progress, boolean assembling,
                                           boolean destroyed, int destroyTime, float durabilityRatio) {
        return new RegularPartData(base.registryKey, base.name, base.uuid, base.variant,
                progress, durabilityRatio, base.materialAssemblingProgress,
                assembling, destroyed, destroyTime, base.textureName, base.subParts);
    }

    /** 单个子零件被摧毁不构成部件摧毁，也不启动倒计时。 */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void oneDestroyedSubPartKeepsPartAlive(GameTestHelper helper) {
        Selection selected = multiSubPart(helper);
        RegularPart part = PartFactory.createRegular(selected.type(), selected.variant(), helper.getLevel());
        try {
            SubPart first = part.subParts.values().stream()
                    .filter(child -> child != part.rootSubPart)
                    .findFirst().orElseThrow();
            first.setDestroyed();
            helper.assertTrue(first.isDestroyed(), "命中的子零件应被标记摧毁");
            helper.assertTrue(!part.allSubPartsDestroyed() && !part.isDestroyed(),
                    "单个子零件摧毁不构成部件摧毁");
            helper.assertTrue(part.getDestroyTime() == 0, "未进入倒计时的部件没有剩余时间");
            part.tickDestroyState();
            helper.assertTrue(!part.isDestroyed() && part.getDestroyTime() == 0,
                    "部件未全毁时 tick 不得启动倒计时");
            helper.assertTrue(part.subParts.values().stream().noneMatch(SubPart::isRemoved),
                    "摧毁的子零件必须留在世界，部件仍是组装基本单位");
            helper.succeed();
        } finally {
            part.discardUnattached();
        }
    }

    /** 全部子零件摧毁后部件进入倒计时，初值按设计耐久上限换算。 */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void allDestroyedSubPartsStartPartCountdown(GameTestHelper helper) {
        Selection selected = multiSubPart(helper);
        RegularPart part = PartFactory.createRegular(selected.type(), selected.variant(), helper.getLevel());
        try {
            int expected = initialDestroyTicks(part);
            part.subParts.values().forEach(SubPart::setDestroyed);
            helper.assertTrue(part.isDestroyed(), "全部子零件摧毁即部件摧毁");
            helper.assertTrue(part.getDestroyTime() == expected,
                    "最后一个子零件被摧毁时应在同一 tick 写入初值 " + expected + "，实际 " + part.getDestroyTime());
            part.tickDestroyState();
            helper.assertTrue(part.getDestroyTime() == expected - 1,
                    "进入倒计时后每 tick 减一，实际 " + part.getDestroyTime());
            helper.succeed();
        } finally {
            part.discardUnattached();
        }
    }

    /** 全部子零件的倒计时镜像始终等于部件权威值。 */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void countdownMirrorMatchesPart(GameTestHelper helper) {
        Selection selected = multiSubPart(helper);
        RegularPart part = PartFactory.createRegular(selected.type(), selected.variant(), helper.getLevel());
        try {
            part.subParts.values().forEach(SubPart::setDestroyed);
            for (int tick = 0; tick < 3; tick++) {
                part.tickDestroyState();
                int authoritative = part.getDestroyTime();
                List<Integer> mirrored = part.subParts.values().stream().map(SubPart::getDestroyTime).toList();
                helper.assertTrue(mirrored.stream().allMatch(value -> value == authoritative),
                        "第 " + tick + " tick 的子零件镜像应全部等于 " + authoritative + "，实际 " + mirrored);
            }
            helper.succeed();
        } finally {
            part.discardUnattached();
        }
    }

    /** 倒计时归零不会让子零件自毁，移除只发生在部件被整体销毁时。 */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 200)
    public static void subPartsOutliveTheirCountdown(GameTestHelper helper) {
        Selection selected = multiSubPart(helper);
        RegularPart part = PartFactory.createRegular(selected.type(), selected.variant(), helper.getLevel());
        try {
            part.subParts.values().forEach(SubPart::setDestroyed);
            int total = part.getDestroyTime() + 5;
            for (int tick = 0; tick < total; tick++) part.tickDestroyState();
            helper.assertTrue(part.getDestroyTime() == 0, "倒计时应归零，实际 " + part.getDestroyTime());
            helper.assertTrue(part.subParts.values().stream().noneMatch(SubPart::isRemoved),
                    "归零后子零件仍须等待部件整体移除");
            part.destroy();
            helper.assertTrue(part.subParts.values().stream().allMatch(SubPart::isRemoved),
                    "部件移除才销毁子零件");
            helper.succeed();
        } finally {
            part.discardUnattached();
        }
    }

    /** 摧毁状态与剩余时间同时经 JSON 与网络线格式往返。 */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void destroyStateSurvivesSaveRoundTrip(GameTestHelper helper) {
        Selection selected = multiSubPart(helper);
        RegularPart part = PartFactory.createRegular(selected.type(), selected.variant(), helper.getLevel());
        try {
            part.subParts.values().forEach(SubPart::setDestroyed);
            part.tickDestroyState();
            RegularPartData saved = part.save();
            helper.assertTrue(saved.destroyed && saved.destroyTime == part.getDestroyTime(),
                    "存档应记录摧毁标记与剩余 tick");

            var json = RegularPartData.CODEC.encodeStart(JsonOps.INSTANCE, saved).getOrThrow();
            RegularPartData fromJson = RegularPartData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
            helper.assertTrue(fromJson.destroyed && fromJson.destroyTime == saved.destroyTime,
                    "JSON 往返应保留摧毁状态");

            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                    helper.getLevel().registryAccess());
            try {
                RegularPartData.STREAM_CODEC.encode(buffer, saved);
                RegularPartData fromBinary = RegularPartData.STREAM_CODEC.decode(buffer);
                helper.assertTrue(fromBinary.destroyed && fromBinary.destroyTime == saved.destroyTime,
                        "线格式往返应保留摧毁状态");
                helper.assertTrue(!buffer.isReadable(), "线格式长度必须与解码器的读取量一致");
            } finally {
                buffer.release();
            }
            helper.succeed();
        } finally {
            part.discardUnattached();
        }
    }

    /** 完整恢复延续倒计时，骨架恢复忽略摧毁状态。 */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void restoreModesHandleDestroyState(GameTestHelper helper) {
        Selection selected = multiSubPart(helper);
        RegularPart source = PartFactory.createRegular(selected.type(), selected.variant(), helper.getLevel());
        RegularPart full = null;
        RegularPart skeleton = null;
        try {
            source.subParts.values().forEach(SubPart::setDestroyed);
            source.tickDestroyState();
            int expected = Math.min(1000, initialDestroyTicks(source));
            RegularPartData wrecked = variant(source.save(), 0f, false, true, 1000, 0f);

            full = PartFactory.restoreRegular(wrecked, helper.getLevel(), PartRestoreMode.FULL_STATE);
            helper.assertTrue(full.isDestroyed(), "完整恢复应还原摧毁标记");
            int restoredTime = full.getDestroyTime();
            helper.assertTrue(restoredTime == expected,
                    "完整恢复的剩余 tick 应钳制到 " + expected + "，实际 " + restoredTime);
            helper.assertTrue(full.subParts.values().stream().allMatch(SubPart::isDestroyed),
                    "完整恢复应把全部子零件标记为摧毁");
            helper.assertTrue(full.subParts.values().stream()
                            .allMatch(child -> child.getDestroyTime() == restoredTime),
                    "恢复后的倒计时镜像应等于部件权威值");

            skeleton = PartFactory.restoreRegular(wrecked, helper.getLevel(), PartRestoreMode.STRUCTURE_ONLY);
            helper.assertTrue(!skeleton.isDestroyed() && skeleton.getDestroyTime() == 0,
                    "骨架恢复应清空摧毁状态");
            helper.assertTrue(skeleton.assembling, "骨架恢复应处于组装形态");
            helper.succeed();
        } finally {
            source.discardUnattached();
            if (full != null) full.discardUnattached();
            if (skeleton != null) skeleton.discardUnattached();
        }
    }

    /** 三种状态下每个子零件都拿到应有的重力，残骸不会悬空。 */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void restoreAppliesGravityToEverySubPart(GameTestHelper helper) {
        Selection selected = multiSubPart(helper);
        RegularPart source = PartFactory.createRegular(selected.type(), selected.variant(), helper.getLevel());
        RegularPart damaged = null;
        RegularPart wreck = null;
        RegularPart skeleton = null;
        try {
            RegularPartData base = source.save();
            // 受损未摧毁：进度在 (0,1)，不是骨架也不是残骸
            damaged = PartFactory.restoreRegular(variant(base, 0.4f, false, false, 0, 0.4f),
                    helper.getLevel(), PartRestoreMode.FULL_STATE);
            assertGravity(helper, damaged, 1f, "受损未摧毁的部件使用世界重力");
            helper.assertTrue(!damaged.isDestroyed() && !damaged.assembling, "受损件既不处于组装形态也未摧毁");

            // 已摧毁：残骸按世界重力下落
            wreck = PartFactory.restoreRegular(variant(base, 0f, false, true, 500, 0f),
                    helper.getLevel(), PartRestoreMode.FULL_STATE);
            helper.assertTrue(wreck.isDestroyed(), "残骸应处于摧毁状态");
            assertGravity(helper, wreck, 1f, "已摧毁的部件使用世界重力");

            // 骨架：折减重力
            skeleton = PartFactory.restoreRegular(variant(base, 0f, true, false, 0, 1f),
                    helper.getLevel(), PartRestoreMode.FULL_STATE);
            assertGravity(helper, skeleton, 0.1f, "骨架使用 10% 世界重力");
            helper.succeed();
        } finally {
            source.discardUnattached();
            if (damaged != null) damaged.discardUnattached();
            if (wreck != null) wreck.discardUnattached();
            if (skeleton != null) skeleton.discardUnattached();
        }
    }

    /** 追加伤害推进部件倒计时，但保留最后 20 tick。 */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void extraDamageAdvancesCountdownWithFloor(GameTestHelper helper) {
        Selection selected = multiSubPart(helper);
        RegularPart part = PartFactory.createRegular(selected.type(), selected.variant(), helper.getLevel());
        try {
            part.subParts.values().forEach(SubPart::setDestroyed);
            part.tickDestroyState();
            int initial = part.getDestroyTime();
            helper.assertTrue(initial > 70, "初值应足够大以验证推进，实际 " + initial);

            part.advanceDestroyTimer(50);
            helper.assertTrue(part.getDestroyTime() == initial - 50,
                    "追加伤害应推进 " + 50 + " tick，实际 " + part.getDestroyTime());
            helper.assertTrue(part.subParts.values().stream()
                            .allMatch(child -> child.getDestroyTime() == part.getDestroyTime()),
                    "推进后的镜像应同步");

            part.advanceDestroyTimer(1_000_000);
            helper.assertTrue(part.getDestroyTime() == 20,
                    "倒计时应保留 20 tick 下限，实际 " + part.getDestroyTime());
            helper.succeed();
        } finally {
            part.discardUnattached();
        }
    }
}
