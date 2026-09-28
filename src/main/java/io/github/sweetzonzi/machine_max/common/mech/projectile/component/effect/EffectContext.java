package io.github.sweetzonzi.machine_max.common.mech.projectile.component.effect;

import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * 效果执行上下文 — 展开为单一记录的"执行一次世界效果所需的全部信息"。
 * <p>
 * 不为 Entity / Block / {@link DestroyableObject} 提供多组 {@code apply} 重载，
 * 也不额外包装归属（Attribution）/ 目标（Target）层级：当前所需信息直接展平于此。
 * <p>
 * 三个 target 字段按命中类型三选一：
 * <ul>
 *   <li>空爆（无目标）→ 全部为 null</li>
 *   <li>Entity 命中 → {@link #targetEntity()}</li>
 *   <li>载具零件等 BF 对象命中 → {@link #targetObject()}</li>
 *   <li>地形命中 → {@link #targetBlockPos()}，由 {@code level + pos} 共同表示目标</li>
 * </ul>
 * <p>
 * 设计出处：《武器系统-组件化投射物与类型体系设计》§4.4。
 *
 * @param level          所在维度
 * @param origin         效果起点（世界坐标，如命中点 / 起爆点）
 * @param velocity       触发时的速度矢量（无速度语义时用 {@link Vec3#ZERO}）
 * @param directEntity   直接载体（如已创建的 {@code MMProjectileEntity}）；首物理步起爆时代理实体可能尚未创建，允许为 null
 * @param ownerEntity    射手 / 操作者，对应原版 DamageSource 的 causing entity
 * @param sourceObject   触发效果的对象（投射物、油箱、弹药架等 {@link DestroyableObject}）
 * @param targetEntity   命中的实体目标
 * @param targetObject   命中的 BF 对象目标（如载具零件）
 * @param targetBlockPos 命中的方块坐标
 */
public record EffectContext(
    Level level,
    Vec3 origin,
    Vec3 velocity,

    @Nullable Entity directEntity,
    @Nullable Entity ownerEntity,
    @Nullable DestroyableObject sourceObject,

    @Nullable Entity targetEntity,
    @Nullable DestroyableObject targetObject,
    @Nullable BlockPos targetBlockPos
) {}
