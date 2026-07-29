package io.github.sweetzonzi.machine_max.common.mech.physics_test;

import com.jme3.math.Transform;
import com.mojang.datafixers.util.Pair;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 根据入点进行物理测试的基类
 */
public abstract class BaseJoinPositionPhysicsTest implements PhysicsTest {
    /**所有的入点，命令可以从首尾修改访问*/
    public final static LinkedList<Pair<Vec3, Transform>> JOIN_POSITIONS = new LinkedList<>();

    /**该测试用例代理的所有载具 todo 需要粽子审查代码，决定清理时机，避免内存溢出*/
    protected final List<VehicleCore> vehicles = new ArrayList<>();
    /**
     * 是否初始化
     * <p>
     * 用于确保：玩家还没有初次运行命令的情况下，不应该被重放键触发测试*/
    boolean initiated = false;
    /**该测试用例正在运行*/
    boolean playing = false;

    /**供子类继承使用的封装方法，提供了
     * @param player 运行测试用例的玩家
     * @param joinPosition 入点位置与部件姿态栈
     * */
    public abstract void runWithJoinPosition(Player player, Pair<Vec3, Transform> joinPosition);


    /**底层总线调用的方法*/
    @Override
    public final Component run(Player player) {
        initiated = true;
        playing = true;
        getUnremovedVehicleStream().forEach(ObjectManager::removeVehicle);
        var status = Component.literal("未知错误");
        int count = 0;
        for (Pair<Vec3, Transform> pair : JOIN_POSITIONS) {
            runWithJoinPosition(player, pair);
            count++;
        }
        status = count != 0 ?
                Component.literal("%s个入点，召唤成功".formatted(count)):
                Component.literal("你还没有设置入点");
        return status;
    }


    /**重放方法*/
    @Override
    public Component onResume() {
        Component msg = Component.literal("\n"+(playing ? "暂停测试" : "继续测试"));
        if (initiated) {
            getUnremovedVehicleStream().forEach(vehicleCore ->
                    {
                        if (playing) {
                            vehicleCore.freezeAllPhysics(this);
                        } else {
                            vehicleCore.unfreezeAllPhysics(this);
                        }
                    }
            );
            playing = !playing;
        }
        return msg;
    }

    /**用于遍历未删除状态的载具核心*/
    protected @NotNull Stream<VehicleCore> getUnremovedVehicleStream() {
        return vehicles.stream().filter(vehicleCore -> !vehicleCore.isRemoved);
    }

    /**全自动注册测试用例包名
     * <p>
     * 由于底层总线会调用它注册测试用例，所以该方法应该属于初始基类*/
    @Override
    public final ResourceLocation path() {
        return ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, getClass().getSimpleName().replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase());
    }
}
