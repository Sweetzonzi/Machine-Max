package io.github.tt432.machinemax.common.entity;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.entity.old.entity.AE86Entity;
import io.github.tt432.machinemax.common.entity.old.entity.TestCarEntity;
import io.github.tt432.machinemax.common.entity.part.MMPartEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * 此类为实体注册器
 * 收录了模组添加的所有实体，并定义一些基本属性
 * @author 甜粽子
 */
public class MMEntities {
    public static void register() {}
    //以下为注册的实体列表
    //测试用车辆
    public static final Supplier<EntityType<TestCarEntity>> TEST_CAR_ENTITY =
            MachineMax.REGISTER.<TestCarEntity>entityType()
                    .id("test_car")
                    .builder(EntityType.Builder.of(TestCarEntity::new, MobCategory.MISC)
                    .fireImmune())
                    .build();
    //测试用越野车辆
    public static final Supplier<EntityType<AE86Entity>> AE86_ENTITY =
            MachineMax.REGISTER.<AE86Entity>entityType()
                    .id("ae86")
                    .builder(EntityType.Builder.of(AE86Entity::new, MobCategory.MISC)
                    .fireImmune())
                    .build();
    //测试用实体方块
    public static final Supplier<EntityType<MMPartEntity>> MM_PART_ENTITY =
            MachineMax.REGISTER.<MMPartEntity>entityType()
                    .id("part")
                    .builder(EntityType.Builder.<MMPartEntity>of(MMPartEntity::new, MobCategory.MISC)
                    .fireImmune()
                    .noSummon())
                    .build();
    //以上为注册的实体列表
}
