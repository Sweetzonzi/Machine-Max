package io.github.sweetzonzi.machine_max.common.mech.physics_test;

import com.jme3.math.Transform;
import com.mojang.datafixers.util.Pair;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/**
 * 根据入点进行物理测试的基类
 */
public abstract class BaseJoinPositionPhysicsTest<P> implements PhysicsTest {
    public final ServerPlayer player;

    /**入点：玩家面朝方向角 和 姿态栈*/
    @Setter
    @Getter
    protected Pair<Vec3, Transform> joinPosition = null;

    /**由运行状态管理的事件 true正在运行 false暂停，子类可以通过重写自定义逻辑*/
    @Getter
    private Consumer<Boolean> playingEvent = null;

    /**该入点测试用例的对象物品，通过泛型灵活指定*/
    @Getter
    private final P p;

    protected final ServerLevel level;
    /**
     * 是否初始化
     * <p>
     * 用于确保：玩家还没有初次运行命令的情况下，不应该被重放键触发测试*/
    boolean initiated = false;
    /**该测试用例正在运行*/
    boolean playing = false;

    public BaseJoinPositionPhysicsTest(ServerPlayer player) {
        this.level = (ServerLevel) player.level();
        this.player = player;
        joinPosition = setJoinPosition();
        p = setPhysicsTestingObject();

    }

    public abstract Pair<Vec3, Transform> setJoinPosition();

    protected abstract P setPhysicsTestingObject();
    protected P getPhysicsTestingObject() {
        return p;
    }

    /**底层总线调用的方法*/
    @Override
    public final void run() {
        initiated = true;
        playing = true;
        if (p == null) return;
        run(p);
    }

    public abstract void run(P physicsTestingObject);


    @Override
    public void onResume() {
        Consumer<Boolean> playingEvent = getPlayingEvent();
        if (initiated && playingEvent != null) {
            playingEvent.accept(playing);
            playing = !playing;
        }
    }


}
