package io.github.sweetzonzi.machine_max.common.gametest;

import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.VehicleData;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;

/**
 * 登录 / 换维度下发的载具数据取自服务端内存现状，不取 {@code LEVEL_VEHICLES} 里那份存档快照。
 *
 * <p>两条路径的口径必须一致：紧跟在载具数据包之后的
 * {@link ObjectManager#pushVehicleRuntimeState(VehicleCore, net.minecraft.server.level.ServerPlayer)}
 * 读的是内存里的当前值，并按其成员序按 {@code (子零件 ID, 声明序序号)} 寻址。若结构那半取自
 * 存档快照，而快照落后于内存，同步值就会落到客户端不存在（或已消失）的结构上，被处理器丢弃
 * 且不重发。</p>
 *
 * <p>快照落后是常态而非异常：{@code saveVehicles} 只在载具增删与世界存档时执行，
 * 往已有载具上焊接零件、拆零件都不经过这两处。</p>
 */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VehicleSnapshotGameTest {
    private static final String BATCH = "mmVehicleSnapshot";
    private static final String WHEEL = "k17:k17_wheel";

    private VehicleSnapshotGameTest() {
    }

    private static RegularPart wheel(GameTestHelper helper, BlockPos pos) {
        var type = PartDefinitions.regular(false).get(ResourceLocation.parse(WHEEL));
        RegularPart part = PartFactory.createRegular(type, "left", helper.getLevel());
        part.setTransform(new Transform().setTranslation(
                new Vector3f(pos.getX() + .5f, pos.getY(), pos.getZ() + .5f)));
        return part;
    }

    /** 本次快照里该载具的部件数；载具不在快照中时返回 -1。 */
    private static int partCount(Set<VehicleData> snapshot, VehicleCore vehicle) {
        for (VehicleData data : snapshot) {
            if (data.uuid.equals(vehicle.getUuid())) return data.parts.size();
        }
        return -1;
    }

    /**
     * 焊接新部件后，{@link ObjectManager#snapshotVehicles} 立刻反映新拓扑，而存档快照仍停留在
     * 上一次 {@code saveVehicles} 的时刻。
     *
     * <p>后半句是这条契约成立的原因，不是对实现的偶然断言：正是因为它会落后，发送路径才不能读它。</p>
     */
    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void snapshotFollowsInMemoryTopologyWhileSavedCopyLags(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos welded = helper.absolutePos(new BlockPos(4, 2, 2));
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), wheel(helper, start));
        ObjectManager.addVehicle(vehicle);
        try {
            helper.assertTrue(partCount(ObjectManager.snapshotVehicles(helper.getLevel()), vehicle) == 1,
                    "刚注册的载具应只有一个部件");

            // 焊接：只改内存拓扑，不经过 addVehicle / removeVehicle，因此不触发 saveVehicles
            vehicle.addPart(wheel(helper, welded));
            helper.assertTrue(vehicle.partMap.size() == 2, "内存拓扑应有两个部件");

            helper.assertTrue(partCount(ObjectManager.snapshotVehicles(helper.getLevel()), vehicle) == 2,
                    "载具数据必须按内存现状序列化，焊接的部件必须出现在其中");
            helper.assertTrue(partCount(helper.getLevel().getData(MMAttachments.getLEVEL_VEHICLES()), vehicle) == 1,
                    "存档快照只在上一次 saveVehicles 时重写，焊接不会刷新它");
        } finally {
            ObjectManager.removeVehicle(vehicle);
        }
        helper.succeed();
    }
}
