package io.github.sweetzonzi.machine_max.common.mech.physics_test;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.physics_test.instance.BlueprintVehicleSummonTest;
import io.github.sweetzonzi.machine_max.common.mech.physics_test.instance.PartSummonTest;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**物理测试底层总线 注册、管理、调用各个测试用例*/
public class PhysicsTestBus {

    /**注册的测试用例*/
    public final static ConcurrentMap<ResourceLocation, Class<? extends BaseJoinPositionPhysicsTest>> TEST_MAP = new ConcurrentHashMap<>();
    public final static LinkedHashSet<ResourceLocation> TEST_SET = new LinkedHashSet<>();

    public final static List<PhysicsTest> INSTANCE_LIST = new ArrayList<>();

    /**上一次运行的脚本记录*/
    public static PhysicsTest LAST_RUN = null;

    public static void vehicle_core$prePhysicsTick(VehicleCore core) {
        INSTANCE_LIST.stream().filter(physicsTest -> !physicsTest.isDisabled())
                .forEach(physicsTest -> physicsTest.vehicle_core$prePhysicsTick(core));
    }

    public static void subpart$onTerrainCollision(SubPart subPart) {
        INSTANCE_LIST.stream().filter(physicsTest -> !physicsTest.isDisabled())
                .forEach(physicsTest -> physicsTest.subpart$onTerrainCollision(subPart));
    }

    public static void subpart$onDestroyBlock(SubPart subPart) {
        INSTANCE_LIST.stream().filter(physicsTest -> !physicsTest.isDisabled())
                .forEach(physicsTest -> physicsTest.subpart$onDestroyBlock(subPart));
    }

    // 所有测试用例在这里注册
    static {
        register(PartSummonTest.class);
        register(BlueprintVehicleSummonTest.class);
    }

    /**注册方法*/
    private static void register(Class<? extends BaseJoinPositionPhysicsTest> testInstance) {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, testInstance.getSimpleName().replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase());
        if (TEST_MAP.containsKey(location)) {
            MachineMax.LOGGER.error("The ResourceLocation of TestClass {} is already registered!", location);
            return;
        }
        TEST_MAP.put(location, testInstance);
        TEST_SET.add(location);
    }


}
