package io.github.sweetzonzi.machine_max.mixin_native;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.sweetzonzi.machine_max.common.mech.physics_test.PhysicsTestBus;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.collision.CollisionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 针对 {@link CollisionHandler#handleTerrainCollision} 的 AOP 织入。
 * <p>
 * SubPart 的碰撞回调在构造函数中通过 Lambda 注册给 CollisionHandler（无独立方法可注入），
 * 因此将注入点选在此处：SubPart 与原版区块方块发生碰撞时，`handleTerrainCollision` 在物理线程
 * 被调用，通过 HEAD 注入将事件记录到 {@link PhysicsTestBus}。
 * <p>
 * 注意：该方法在物理线程调用，禁止在此直接调用测试用例（测试用例可能执行增删载具等
 * 主线程级操作）。事件由 {@link PhysicsTestBus#recordTerrainCollision} /
 * {@link PhysicsTestBus#recordDestroyBlock} 记录，主线程在 LevelTickEvent 中消费并回调。
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

    @Inject(method = "applyBlockDamage", at = @At("HEAD"))
    private void native$onDestroyBlock(CallbackInfo ci,
                                       @Local BlockPos blockPos,
                                       @Local BlockState blockState) {

        PhysicsTestBus.subpart$onDestroyBlock(subPart);
    }
}
