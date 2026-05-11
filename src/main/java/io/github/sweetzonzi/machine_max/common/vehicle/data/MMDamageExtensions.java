package io.github.sweetzonzi.machine_max.common.vehicle.data;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.vehicle.interact.HitBox;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageExtensionKey;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageExtensions;
import net.minecraft.resources.ResourceLocation;

/**
 * 注册 Machine-Max 在 BallisticsFramework 协议管线中使用的自定义扩展 key。
 */
public final class MMDamageExtensions {

    /** 命中的碰撞箱引用，SubPart 管线内据此获取 HitBox 属性。默认 null 表示无有效命中 */
    public static final BFDamageExtensionKey<HitBox> HIT_BOX =
            BFDamageExtensions.register(
                    ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "hit_box"),
                    HitBox.class, () -> null);

    /** 确保所有扩展 key 完成注册 */
    public static void init() {}
}