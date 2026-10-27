package io.github.sweetzonzi.machine_max.mixin_native;

import io.github.sweetzonzi.machine_max.common.mech.physics_test.PhysicsTestBus;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.collision.CollisionHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 针对 {@link CollisionHandler} 的 AOP 织入：把物理线程的地形碰撞事件转发给 {@link PhysicsTestBus}。
 * <p>
 * SubPart 的碰撞回调在构造函数中以 Lambda 注册给 CollisionHandler（没有独立方法可注入），
 * 因此注入点选在 `handleTerrainCollision`：SubPart 与原版区块方块发生碰撞时它在物理线程被调用。
 * <p>
 * 注意：这两个方法都在物理线程执行，禁止在此直接运行测试用例（用例可能增删载具等主线程操作）。
 * 事件只做记录，由主线程在 LevelTickEvent 中消费并回调。
 */
@Mixin(CollisionHandler.class)
public abstract class CollisionHandlerMixin {

    @Shadow
    @Final
    private SubPart subPart;

    @Inject(method = "handleTerrainCollision", at = @At("HEAD"))
    private void native$onTerrainCollision(CallbackInfo ci) {
        PhysicsTestBus.subpart$onTerrainCollision(subPart);
    }

    /**
     * 撞击允许破坏方块时的回调。
     *
     * <p>注入点选在单点即时撞碎入口：它只在“破坏总开关开启、材质允许破坏、目标硬度非负”时被调用，
     * 与原先的方块伤害入口条件一致。Post 聚合破坏由结算器直接排入删除任务，不经过本织入点。</p>
     */
    @Inject(method = "tryInstantBreak", at = @At("HEAD"))
    private void native$onDestroyBlock(CallbackInfoReturnable<Boolean> cir) {
        PhysicsTestBus.subpart$onDestroyBlock(subPart);
    }
}
