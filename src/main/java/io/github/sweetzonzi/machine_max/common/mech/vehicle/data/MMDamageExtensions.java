package io.github.sweetzonzi.machine_max.common.mech.vehicle.data;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.HitBox;
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

    /** 折减前的原始穿深（mm RHA），供能量法使用。失稳弹头在上下文穿深打折后，能量计算仍需用此值 */
    public static final BFDamageExtensionKey<Float> ORIGINAL_PENETRATION =
            BFDamageExtensions.register(
                    ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "original_penetration"),
                    Float.class, () -> 0f);

    /** 当前命中的物理厚度（mm），供稳定性判定中计算视厚度（LOS）使用 */
    public static final BFDamageExtensionKey<Float> HIT_PHYSICAL_THICKNESS =
            BFDamageExtensions.register(
                    ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "hit_physical_thickness"),
                    Float.class, () -> 0f);

}