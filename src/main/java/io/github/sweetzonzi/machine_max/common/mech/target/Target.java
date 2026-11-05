package io.github.sweetzonzi.machine_max.common.mech.target;

import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * 可以被单独选中的目标：整辆载具、载具上的某个碰撞箱，以及后续的生物、诱饵与方向。
 *
 * <p>由领域对象直接实现，没有包装类型：{@code VehicleCore} 与 {@code HitBox} 实现它，
 * 原版实体由 mixin 补上。消费方只调这三个方法，不判断实现类型。</p>
 *
 * <p>{@code Target} 表示「可被锁定的对象类别」，不表示「对本传感器可锁」；某部传感器锁不上
 * 某个目标是传感器的判定，不是目标的属性。</p>
 *
 * <p>本接口留在共通代码里：实现中不得引用客户端类。</p>
 */
public interface Target {

    /** 显示名。 */
    Component label();

    /**
     * 真值点，世界坐标（米）。
     *
     * <p>显示与制导都从这里取点。传感器给出的估计值属于测量，不属于目标本身。</p>
     *
     * <p>点随刚体每 tick 更新，逐帧绘制必须带插值系数，否则标记会比画面里的载具慢半拍地跳。
     * 按 tick 使用（例如每 tick 上报一次的瞄准点）传 {@code 1}，即当 tick 末的状态。</p>
     *
     * @param partialTick 上一 tick 到本 tick 的插值系数，通常取 {@code DeltaTracker} 的
     *                    {@code getGameTimeDeltaPartialTick(false)}
     */
    Vec3 point(Number partialTick);

    /**
     * 对象侧此刻仍可作为锁定目标。
     *
     * <p>这是状态而非类型：被摧毁、被条件关闭、宿主部件已移除时返回 {@code false}。
     * 锁定方每 tick 校验一次，不缓存布尔值。</p>
     */
    boolean isLockable();
}
