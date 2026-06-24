package io.github.sweetzonzi.machine_max.common.registry;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.entity.MMProjectileEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * 实体类型注册（传统 DeferredRegister 方式）
 */
public class MMEntities {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MachineMax.MOD_ID);

    /**
     * 部件实体
     */
    public static final DeferredHolder<EntityType<?>, EntityType<MMPartEntity>> PART_ENTITY = ENTITY_TYPES.register(
            "part",
            () -> EntityType.Builder.<MMPartEntity>of(MMPartEntity::new, MobCategory.MISC)
                    .noSummon()
                    .noSave()
                    .updateInterval(Integer.MAX_VALUE)
                    .fireImmune() //TODO: 检查为什么放出来瞬间有可能被判定处于岩浆中
                    .eyeHeight(0.0F)
                    .setShouldReceiveVelocityUpdates(false)
                    .build("part")
    );

    /**
     * 投射物实体兼容层
     */
    public static final DeferredHolder<EntityType<?>, EntityType<MMProjectileEntity>> PROJECTILE_ENTITY = ENTITY_TYPES.register(
            "projectile",
            () -> EntityType.Builder.<MMProjectileEntity>of(MMProjectileEntity::new, MobCategory.MISC)
                    .noSummon()
                    .noSave()
                    .fireImmune()
                    .eyeHeight(0.0F)
                    .setShouldReceiveVelocityUpdates(false)
                    .updateInterval(Integer.MAX_VALUE)
                    .sized(0.1f, 0.1f)
                    .build("projectile")
    );

    /**
     * 将实体类型注册到事件总线
     */
    public static void register(net.neoforged.bus.api.IEventBus bus) {
        ENTITY_TYPES.register(bus);
    }
}
