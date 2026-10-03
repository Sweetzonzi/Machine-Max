package io.github.sweetzonzi.machine_max.common.mech.vehicle;

import io.github.sweetzonzi.ballistics_framework.api.BFDamageContext;

/**
 * 一次命中在零件层的结算结果。
 * <p>
 * 由 {@link SubPart#settleAccumulatedDamage()} 在每次结算时为每条通过的命中构造一条，
 * 随 {@link IPartAssembly#onPartDamage(Part, java.util.List)} 一起通告给装配体，
 * 使装配体无需回查即可拿到逐次命中的金额与协议上下文。
 * </p>
 *
 * @param damage        该次命中在子系统事件之后的最终金额（被取消或折算后金额为 0 的命中不产生记录）
 * @param vehicleDamage 按零件类型的传递率折算后的装配体伤害
 * @param ctx           该次命中的协议上下文，携带伤害来源与命中几何
 * @param part          产生该次结算的零件，供扁平批次回溯来源
 */
public record SubPartHitDamage(float damage, float vehicleDamage, BFDamageContext ctx, Part part) {
}
