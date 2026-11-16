package io.github.sweetzonzi.machine_max.common.gametest;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.part.PartRestoreMode;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SyncValueBuffer;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.RegularPartData;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.VehicleData;
import io.github.sweetzonzi.machine_max.network.payload.SubsystemSyncPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 同步边界的服务端契约：一轮批次携带多个子系统条目并能原样往返；零件安装序经持久化往返后
 * 仍然决定控制器成员序。
 *
 * <p>比较用的是<b>持久成员身份</b>（子零件名 + 子系统名），不是运行时对象 ID：恢复会分配新的
 * 运行 ID，用 ID 比较会把"顺序没变"误报成"顺序变了"。</p>
 */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SyncBoundaryGameTest {
    private static final String BATCH = "mmSyncBoundary";
    private static final String TURRET = "t90a:t90a_turret";

    private SyncBoundaryGameTest() {
    }

    private static RegularPart turret(GameTestHelper helper) {
        var type = PartDefinitions.regular(false).get(ResourceLocation.parse(TURRET));
        return PartFactory.createRegular(type, type.variants.keySet().iterator().next(), helper.getLevel());
    }

    /** 控制器成员序的持久身份序列：子零件名 / 子系统名。 */
    private static List<String> memberOrder(VehicleCore vehicle) {
        List<String> order = new ArrayList<>();
        for (AbstractSubsystem subsystem : vehicle.getSubsystemController().getAllSubsystems()) {
            order.add(subsystem.getSubPart().name + "/" + subsystem.getName());
        }
        return order;
    }

    /**
     * 一轮批次把同一控制器的多个子系统收进一份包，装配体身份与每个条目的变化值都能原样往返。
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void batchCarriesEveryDirtyEntryAndRoundTrips(GameTestHelper helper) {
        RegularPart part = turret(helper);
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), part);
        try {
            List<SubsystemSyncPayload.Entry> entries = new ArrayList<>();
            List<Object> expectedValues = new ArrayList<>();
            for (AbstractSubsystem subsystem : vehicle.getSubsystemController().getAllSubsystems()) {
                // 写一个与默认值不同的耐久，确保本子系统确实有脏字段
                subsystem.setDurability(subsystem.getMaxDurability() * 0.25f);
                List<SynchedEntityData.DataValue<?>> dirty = subsystem.packDirtyValues();
                if (dirty.isEmpty()) continue;
                entries.add(new SubsystemSyncPayload.Entry(subsystem.getSubPart().getId(),
                        subsystem.getSubPart().subsystemIndex(subsystem), dirty));
                expectedValues.add(dirty.getFirst().value());
            }
            helper.assertTrue(entries.size() > 1, "一份批次应能携带多个子系统的条目");
            helper.assertTrue(expectedValues.size() == entries.size(), "抽样值与条目必须一一对应");

            SubsystemSyncPayload batch = new SubsystemSyncPayload(vehicle.getAssemblyId(), entries);
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                    helper.getLevel().registryAccess());
            try {
                SubsystemSyncPayload.STREAM_CODEC.encode(buffer, batch);
                buffer.readerIndex(0);
                SubsystemSyncPayload decoded = SubsystemSyncPayload.STREAM_CODEC.decode(buffer);
                helper.assertTrue(decoded.assemblyId().equals(vehicle.getAssemblyId()), "装配体身份必须往返");
                helper.assertTrue(decoded.entries().size() == entries.size(), "条目数必须往返");
                for (int i = 0; i < entries.size(); i++) {
                    SubsystemSyncPayload.Entry source = entries.get(i);
                    SubsystemSyncPayload.Entry copy = decoded.entries().get(i);
                    helper.assertTrue(copy.subPartId() == source.subPartId()
                                    && copy.subsystemIndex() == source.subsystemIndex(),
                            "条目寻址必须往返：" + source.subPartId() + ":" + source.subsystemIndex());
                    helper.assertTrue(copy.values().size() == source.values().size(), "条目的值个数必须往返");
                    helper.assertTrue(copy.values().getFirst().value().equals(expectedValues.get(i)),
                            "条目的值必须往返：" + expectedValues.get(i));
                }
                helper.assertTrue(buffer.readableBytes() == 0, "整包读完不应有剩余字节");
            } finally {
                buffer.release();
            }
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
        }
    }

    /**
     * 待应用值表按 {@code (子零件, 子系统序号, 数据项)} 合并：同一字段后到的覆盖先到的，不同字段各自保留。
     *
     * <p>这是"同步值是值表而不是事件流"的落点：一次网络积压里同一个字段可能到多次，落地时只应
     * 保留最后那次的值，且合并后的条目数不超过本控制器的字段总数。</p>
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void mergedValuesKeepOnlyLatestPerField(GameTestHelper helper) {
        RegularPart part = turret(helper);
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), part);
        try {
            var members = new ArrayList<>(vehicle.getSubsystemController().getAllSubsystems());
            helper.assertTrue(members.size() > 1, "夹具应至少有两个子系统");
            AbstractSubsystem first = members.getFirst();
            int subPartId = first.getSubPart().getId();
            int index = first.getSubPart().subsystemIndex(first);

            first.setDurability(first.getMaxDurability() * 0.25f);
            SynchedEntityData.DataValue<?> stale = first.packDirtyValues().getFirst();
            first.setDurability(first.getMaxDurability() * 0.75f);
            SynchedEntityData.DataValue<?> fresh = first.packDirtyValues().stream()
                    .filter(value -> value.id() == stale.id())
                    .findFirst().orElseThrow(() -> new IllegalStateException("耐久字段应连续两轮都是脏的"));

            SyncValueBuffer buffer = new SyncValueBuffer();
            buffer.merge(List.of(new SubsystemSyncPayload.Entry(subPartId, index, List.of(stale))));
            buffer.merge(List.of(new SubsystemSyncPayload.Entry(subPartId, index, List.of(fresh))));
            Map<SyncValueBuffer.EntryKey, SynchedEntityData.DataValue<?>> merged = new LinkedHashMap<>();
            buffer.forEach(merged::put);
            helper.assertTrue(merged.size() == 1, "同一字段合并后只应留一条，实际 " + merged.size());
            SyncValueBuffer.EntryKey key = merged.keySet().iterator().next();
            helper.assertTrue(key.dataItemId() == stale.id(), "合并键应带上数据项身份");
            helper.assertTrue(merged.get(key).value().equals(fresh.value()), "同一字段应保留后到的值");

            AbstractSubsystem other = members.get(1);
            other.setDurability(other.getMaxDurability() * 0.5f);
            List<SynchedEntityData.DataValue<?>> otherDirty = other.packDirtyValues();
            buffer.merge(List.of(new SubsystemSyncPayload.Entry(other.getSubPart().getId(),
                    other.getSubPart().subsystemIndex(other), otherDirty)));
            Map<SyncValueBuffer.EntryKey, SynchedEntityData.DataValue<?>> mergedAgain = new LinkedHashMap<>();
            buffer.forEach(mergedAgain::put);
            helper.assertTrue(mergedAgain.size() == 1 + otherDirty.size(),
                    "不同子系统的字段应各自保留，实际 " + mergedAgain.size() + " / 期望 " + (1 + otherDirty.size()));

            buffer.clear();
            helper.assertTrue(buffer.isEmpty(), "落地完成后应清空待应用值表");
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
        }
    }

    /**
     * 全量样本覆盖增量可能携带的每一个字段：同一子系统的 {@code packDirtyValues()} 键集是其
     * {@code forEachSyncedValue()} 键集的子集。
     *
     * <p>客户端不需要"是不是全量"的线上标记，靠的正是这条结构事实：全量样本与变化批次只是覆盖的
     * 字段多少不同，合并语义相同。</p>
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void fullSnapshotCoversEveryDirtyField(GameTestHelper helper) {
        RegularPart part = turret(helper);
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), part);
        try {
            int checkedSubsystems = 0;
            int checkedFields = 0;
            for (AbstractSubsystem subsystem : vehicle.getSubsystemController().getAllSubsystems()) {
                subsystem.setDurability(subsystem.getMaxDurability() * 0.4f);
                Set<Integer> snapshot = new HashSet<>();
                subsystem.forEachSyncedValue(value -> snapshot.add(value.id()));
                List<SynchedEntityData.DataValue<?>> dirty = subsystem.packDirtyValues();
                for (SynchedEntityData.DataValue<?> value : dirty) {
                    helper.assertTrue(snapshot.contains(value.id()),
                            subsystem.getName() + " 的脏字段 " + value.id() + " 未出现在全量快照里");
                }
                if (!dirty.isEmpty()) checkedSubsystems++;
                checkedFields += dirty.size();
            }
            helper.assertTrue(checkedSubsystems > 0 && checkedFields > 0, "夹具应让至少一个字段进入增量集合");
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
        }
    }

    /**
     * 两个零件的安装序写在 {@code part_order} 里；JSON 往返并恢复后，控制器成员序仍按该序展开。
     *
     * <p>映射故意按 UUID 字符串<b>降序</b>插入：若顺序回退到 UUID 升序或哈希序，断言必然失败。</p>
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void restoreExpandsMembersInPersistedInstallOrder(GameTestHelper helper) {
        RegularPart first = turret(helper);
        RegularPart second = turret(helper);
        VehicleCore restored = null;
        try {
            Map<String, RegularPartData> parts = new LinkedHashMap<>();
            String a = first.getUuid().toString();
            String b = second.getUuid().toString();
            boolean descending = a.compareTo(b) > 0;
            String head = descending ? a : b;
            String tail = descending ? b : a;
            RegularPart headPart = descending ? first : second;
            RegularPart tailPart = descending ? second : first;
            parts.put(head, new RegularPartData(headPart));
            parts.put(tail, new RegularPartData(tailPart));
            helper.assertTrue(head.compareTo(tail) > 0, "夹具的零件映射必须按 UUID 降序插入");

            VehicleData data = new VehicleData("ordered", UUID.randomUUID(),
                    net.minecraft.world.phys.Vec3.ZERO, net.minecraft.world.phys.Vec3.ZERO,
                    net.minecraft.world.phys.Vec3.ZERO, 1f, parts, List.of());
            VehicleData roundTripped = VehicleData.parseFromJson(VehicleData.serializeToJsonString(data))
                    .orElseThrow(() -> new IllegalStateException("载具数据 JSON 往返失败"));
            helper.assertTrue(new ArrayList<>(roundTripped.getParts().keySet()).equals(List.of(head, tail)),
                    "part_order 必须让降序插入的零件序穿过 JSON 往返");

            restored = new VehicleCore(helper.getLevel(), roundTripped.normalized(), PartRestoreMode.FULL_STATE);
            List<String> order = memberOrder(restored);
            List<String> expected = new ArrayList<>(memberOrderOf(headPart));
            expected.addAll(memberOrderOf(tailPart));
            helper.assertTrue(order.equals(expected),
                    "恢复后的成员序应按安装序展开，实际 " + order + " / 期望 " + expected);
            helper.succeed();
        } finally {
            if (restored != null) restored.onRemoveFromLevel();
            first.discardUnattached();
            second.discardUnattached();
        }
    }

    /** 单个零件按声明序展开的成员身份序列。 */
    private static List<String> memberOrderOf(RegularPart part) {
        List<String> order = new ArrayList<>();
        for (AbstractSubsystem subsystem : part.getAllSubsystems()) {
            order.add(subsystem.getSubPart().name + "/" + subsystem.getName());
        }
        return order;
    }

    /**
     * 分裂判定的分量序与分量内节点序都按旧节点序展开。
     *
     * <p>两个同型号零件没有连接，因此是两个质量相同的分量：决胜依据只能是"最早成员在旧节点序中的
     * 位置"，不是哈希或质量。分量内节点序同样必须保持安装序——{@code addEdge} 会隐式加入缺失端点，
     * 先加边会把端点排到不该在的位置。</p>
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void splitComponentsFollowInstallOrder(GameTestHelper helper) {
        RegularPart first = turret(helper);
        RegularPart second = turret(helper);
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), first);
        try {
            vehicle.addPart(second);
            List<String> installed = new ArrayList<>();
            for (RegularPart part : vehicle.partNet.nodes()) installed.add(part.getUuid().toString());
            helper.assertTrue(installed.equals(List.of(first.getUuid().toString(), second.getUuid().toString())),
                    "节点序应按安装序：" + installed);

            List<String> partOrder = new ArrayList<>(vehicle.getPartData().keySet());
            helper.assertTrue(partOrder.equals(installed), "getPartData 的键序应等于节点序：" + partOrder);

            var components = vehicle.partNetSpiltCheck();
            helper.assertTrue(components.size() == 2, "两个未连接的零件应判成两个分量");
            List<String> componentHeads = new ArrayList<>();
            for (var component : components) {
                componentHeads.add(component.nodes().iterator().next().getUuid().toString());
            }
            helper.assertTrue(componentHeads.equals(List.of(first.getUuid().toString(), second.getUuid().toString())),
                    "同质量分量的决胜依据是最早成员位置：" + componentHeads);
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
        }
    }
}
