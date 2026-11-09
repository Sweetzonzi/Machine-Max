package io.github.sweetzonzi.machine_max.common.gametest;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.LauncherSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.RegenLoaderSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.event.VehicleStructureChangeEvent;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;

/**
 * 真实内容接线的弹药登记：装弹机在结构变化阶段用只读发现直接登记到发射器，
 * 只接受 {@code ammo_inputs} 声明的频道，重复登记去重，结构变化后清空并重新登记。
 */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AmmoDiscoveryGameTest {
    private static final String PART = "machine_max:2s38_turret";
    private static final String LAUNCHER = "subsystem.machine_max.2a90";
    private static final String LOADER_AP = "subsystem.machine_max.2a90_ap_loader";
    private static final String LOADER_HE = "subsystem.machine_max.2a90_he_loader";
    private static final String CHANNEL = "ammo_2a90";

    @GameTest(template = "part_foundation", batch = "mmAmmoRouting", timeoutTicks = 100)
    public static void realWiringRegistersDeclaredChannelsOnly(GameTestHelper helper) {
        var type = PartDefinitions.regular(false).get(ResourceLocation.parse(PART));
        RegularPart part = PartFactory.createRegular(type, type.variants.keySet().iterator().next(), helper.getLevel());
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), part);
        try {
            LauncherSubsystem launcher = part.getAllSubsystems().stream()
                    .filter(LauncherSubsystem.class::isInstance)
                    .map(LauncherSubsystem.class::cast)
                    .filter(candidate -> candidate.getName().equals(LAUNCHER))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("2S38 炮塔没有 2A90 发射器"));
            RegenLoaderSubsystem apLoader = (RegenLoaderSubsystem) part.getAllSubsystems().stream()
                    .filter(candidate -> candidate.getName().equals(LOADER_AP))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("2S38 炮塔没有 AP 装弹机"));

            helper.assertTrue(launcher.getSupplierChannels().keySet().equals(Set.of(CHANNEL)),
                    "登记只落在 ammo_inputs 声明的到达频道，实际 " + launcher.getSupplierChannels().keySet());
            helper.assertTrue(launcher.getSuppliers().size() == 2
                            && launcher.getSuppliers().contains(apLoader),
                    "两台真实装弹机各登记一次，实际 " + launcher.getSuppliers().size());

            launcher.addSupplier(apLoader, "probe_undeclared");
            helper.assertTrue(!launcher.getSupplierChannels().containsKey("probe_undeclared")
                            && launcher.getSuppliers().size() == 2,
                    "声明外频道拒绝登记，且不建对应列表");

            launcher.addSupplier(apLoader, CHANNEL);
            helper.assertTrue(launcher.getSuppliers().size() == 2,
                    "同一供给者重复登记去重");

            vehicle.onStructureChanged(VehicleStructureChangeEvent.Reason.ATTACH);
            helper.assertTrue(launcher.getSupplierChannels().keySet().equals(Set.of(CHANNEL))
                            && launcher.getSuppliers().size() == 2,
                    "结构变化清空后按当前接线重新登记，数量精确一致");
            helper.assertTrue(launcher.getSuppliers().contains(apLoader), "重新登记仍包含原装弹机");

            vehicle.subSystemController.removeSubsystems(List.of(apLoader), false);
            vehicle.onStructureChanged(VehicleStructureChangeEvent.Reason.ATTACH);
            helper.assertTrue(launcher.getSuppliers().size() == 1 && !launcher.getSuppliers().contains(apLoader),
                    "拆下的装弹机退出候选，实际 " + launcher.getSuppliers().size());

            vehicle.subSystemController.addSubsystems(List.of(apLoader));
            vehicle.onStructureChanged(VehicleStructureChangeEvent.Reason.ATTACH);
            helper.assertTrue(launcher.getSuppliers().size() == 2 && launcher.getSuppliers().contains(apLoader),
                    "接回后重新登记且只有一条，实际 " + launcher.getSuppliers().size());
            helper.succeed();
        } finally {
            vehicle.onRemoveFromLevel();
        }
    }
}
