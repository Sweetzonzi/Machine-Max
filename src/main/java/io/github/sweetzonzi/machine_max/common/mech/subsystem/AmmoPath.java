package io.github.sweetzonzi.machine_max.common.mech.subsystem;

/**
 * 一条装填路：一个运输者加一个提供者。<br>
 * 路是路由与同步的单位，不是状态对象——它由两张发现表按需合成（{@link IAmmoConsumer#getRoutes()}），
 * 不在发现阶段缓存，因此结构变化后当场生效。作业上冻结的是这条路的两个身份（{@link AmmoRef}），
 * 本记录只是把它们解析回实例后的一次组合。
 * <p>
 * 相等性是两个成员的 {@code equals}，而子系统不覆写它，所以同一对对象构成的记录相等、不同对象即使内容
 * 相同也不相等——路由用它判断"还是那条路"。
 *
 * @param transporter 本段的执行者（机械运输者或人力运输者）
 * @param provider    本段的弹药来源
 */
public record AmmoPath(IAmmoTransporter transporter, IAmmoSupplier provider) {
}
