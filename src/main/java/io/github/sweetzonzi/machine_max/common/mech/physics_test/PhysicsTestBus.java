package io.github.sweetzonzi.machine_max.common.mech.physics_test;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.physics_test.instance.PartSummonTest;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**物理测试底层总线 注册、管理、调用各个测试用例*/
public class PhysicsTestBus {

    /**注册的测试用例*/
    private final static ConcurrentMap<ResourceLocation, PhysicsTest> TEST_MAP = new ConcurrentHashMap<>();

    /**上一次运行的脚本记录*/
    public static ResourceLocation LAST_RUN = null;

    // 所有测试用例在这里注册
    static {
        register(new PartSummonTest());
    }

    /**注册方法*/
    private static void register(PhysicsTest testInstance) {
        if (TEST_MAP.containsKey(testInstance.path())) {
            MachineMax.LOGGER.error("The ResourceLocation of TestClass {} is already registered!", testInstance.path());
            return;
        }
        TEST_MAP.put(testInstance.path(), testInstance);
    }


    public static List<ResourceLocation> getKeys() {
        return new ArrayList<>(TEST_MAP.keySet());
    }

    public static List<PhysicsTest> getTests() {
        return new ArrayList<>(TEST_MAP.values());
    }

    /**根据包名获取测试用例，这里默认供命令系统使用，不建议硬编码调用*/
    public static PhysicsTest get(ResourceLocation location) {
        return TEST_MAP.get(location);
    }


}
