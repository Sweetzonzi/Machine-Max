package io.github.sweetzonzi.machine_max.mixin_native;

import io.github.sweetzonzi.machine_max.common.mech.physics_test.PhysicsTest;
import io.github.sweetzonzi.machine_max.common.mech.physics_test.PhysicsTestBus;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 针对 {@link VehicleCore#prePhysicsTick()} 的 AOP 织入。
 * <p>
 * 将物理测试用例（{@link PhysicsTest}）的执行从方法体内提取到此处，通过 HEAD 注入保持原有调用顺序：
 * 物理测试 → 子系统 → 零件。测试逻辑作为横切关注点不再污染载具热路径源码。
 * <p>
 * 注意：该方法在物理线程每物理刻调用，注入逻辑必须保持线程安全且避免耗时操作。
 */
@Mixin(VehicleCore.class)
public abstract class VehicleCoreMixin {

    @Inject(method = "prePhysicsTick", at = @At("HEAD"))
    private void native$prePhysicsTick(CallbackInfo ci) {
        VehicleCore self = (VehicleCore) (Object) this;
        PhysicsTestBus.vehicle_core$prePhysicsTick(self);
    }
}
