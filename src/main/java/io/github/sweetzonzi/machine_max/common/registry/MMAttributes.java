package io.github.sweetzonzi.machine_max.common.registry;

import io.github.sweetzonzi.machine_max.MachineMax;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Machine-Max 的自定义实体属性注册。
 * <p>
 * 与 {@link MMEntities}、{@link MMDataComponents} 同属传统 {@code DeferredRegister} 注册类
 * （见 docs/抓取系统-详细设计文档.md §5.1）。
 */
public class MMAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, MachineMax.MOD_ID);

    /**
     * 抓取力量倍率：默认 1.0（无装备无药水的普通玩家）。
     * <p>
     * 属性层承载基础值与装备加成，力量药水的效果层在读取时乘上（见
     * {@code GrabPhysics#grabStrength}）。
     */
    public static final DeferredHolder<Attribute, Attribute> GRAB_STRENGTH = ATTRIBUTES.register(
            "grab_strength",
            () -> new RangedAttribute("attribute.machine_max.grab_strength",
                    1.0,     // 默认值
                    0.0,     // 下限
                    1024.0)  // 上限
                    .setSyncable(true));

    /**
     * 注册属性并把属性挂载监听挂到模组总线上。
     *
     * @param bus 模组事件总线
     */
    public static void register(IEventBus bus) {
        ATTRIBUTES.register(bus);
        // 属性挂载走模组总线上的 EntityAttributeModificationEvent
        bus.addListener(MMAttributes::onAttributeModification);
    }

    /**
     * 把自定义属性挂到玩家实体类型上。
     * <p>
     * 缺少这一步，属性只存在于注册表里，玩家身上取不到该属性的实例。
     */
    public static void onAttributeModification(EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, GRAB_STRENGTH);
    }
}
