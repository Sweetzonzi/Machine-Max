package io.github.sweetzonzi.machine_max.common.gametest;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.SeatSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.registry.MMItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Comparator;

/** Crowbar edits must not remove or damage an occupied vehicle through any early mutation branch. */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CrowbarOccupancyGameTest {
    private static final String BATCH = "mmCrowbarOccupancy";

    private CrowbarOccupancyGameTest() {
    }

    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void survivalCrowbarCannotRemoveAnOccupiedSinglePartVehicle(GameTestHelper helper) {
        RegularPart part = createSingleSeatPart(helper.getLevel());
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), part);
        var passenger = EntityType.PIG.create(helper.getLevel());
        var attacker = helper.makeMockPlayer(GameType.SURVIVAL);
        SeatSubsystem seat = vehicle.getAllSeats().getFirst();
        try {
            helper.assertTrue(passenger != null, "passenger entity is available");
            helper.assertTrue(!vehicle.hasOccupiedSeats(), "vehicle starts with every seat empty");
            seat.attachForSwitch(passenger);
            helper.assertTrue(vehicle.hasOccupiedSeats(), "vehicle reports its occupied seat");

            var eyesight = attacker.getData(MMAttachments.getENTITY_EYESIGHT());
            eyesight.getSortedTargetBodyCache().add(part.rootSubPart.body);
            ItemStack crowbar = new ItemStack(MMItems.getCROWBAR_ITEM().get());
            attacker.setItemInHand(InteractionHand.MAIN_HAND, crowbar);
            crowbar.getItem().use(helper.getLevel(), attacker, InteractionHand.MAIN_HAND);

            helper.assertTrue(vehicle.partMap.get(part.uuid) == part,
                    "occupied single-part vehicle remains assembled after a survival crowbar use");
            helper.assertTrue(crowbar.getDamageValue() == 0,
                    "a rejected crowbar use does not consume durability");
            helper.succeed();
        } finally {
            if (passenger != null) seat.detachForSwitch(passenger);
            vehicle.onRemoveFromLevel();
        }
    }

    private static RegularPart createSingleSeatPart(Level level) {
        return PartDefinitions.regular(false).values().stream()
                .sorted(Comparator.comparing(type -> type.getRegistryKey().toString()))
                .flatMap(type -> type.variants.entrySet().stream()
                        .filter(entry -> entry.getValue().subParts.values().stream()
                                .flatMap(sub -> sub.getSubsystems().values().stream())
                                .filter(attr -> attr instanceof SeatSubsystemAttr)
                                .count() == 1)
                        .map(entry -> PartFactory.createRegular(type, entry.getKey(), level)))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No regular part with exactly one seat is available"));
    }
}
