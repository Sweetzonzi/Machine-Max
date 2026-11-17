package io.github.sweetzonzi.machine_max.common.registry;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.datagen.MMLanguageRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
 * （见 docs/plan/已实现/抓取系统-详细设计文档.md §5.1）。
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
            () -> new RangedAttribute(MMLanguageRegistry.ATTRIBUTE_GRAB_STRENGTH.tag,
                    1.0,     // 默认值
                    0.0,     // 下限
                    1024.0)  // 上限
                    .setSyncable(true));

    /**
     * 乘员装填速度倍率：默认 1.0（无加成的普通人）。<br>
     * 越高人工装填越快——人工段的时间倍率取它的倒数（见
     * {@code CrewReloadAttachment#effectiveMultiplier}）。药水层（急迫／挖掘疲劳）在读取时乘上，
     * 不写进本属性。
     * <p>
     * 范围与时间倍率互为倒数：4.0 表示这个人只要标准人力的四分之一时间，0.25 表示要四倍时间。
     * 区间内是连续值，因此也可以表达 0.8、1.5 这类非整数档。
     */
    public static final DeferredHolder<Attribute, Attribute> CREW_RELOAD_SPEED = ATTRIBUTES.register(
            "crew_reload_speed",
            () -> new RangedAttribute(MMLanguageRegistry.ATTRIBUTE_CREW_RELOAD_SPEED.tag,
                    1.0,    // 默认值
                    0.25,   // 下限
                    4.0)    // 上限
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
     * 挂载自定义属性。
     * <p>
     * 缺少这一步，属性只存在于注册表里，实体身上取不到该属性的实例。
     * <p>
     * 抓取力量只对玩家有意义（只有玩家能抓零件）。乘员装填速度挂到<b>全部</b>生物类型上：座位接受任何
     * 生物，内容包与其它模组的 NPC 也可能被放上去，按宿主类型挑一遍既列不全也会漏掉后来注册的类型。
     */
    public static void onAttributeModification(EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, GRAB_STRENGTH);
        for (EntityType<? extends LivingEntity> type : event.getTypes()) {
            event.add(type, CREW_RELOAD_SPEED);
        }
    }
}
