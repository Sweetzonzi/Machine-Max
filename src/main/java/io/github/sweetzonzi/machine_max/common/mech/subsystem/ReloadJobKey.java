package io.github.sweetzonzi.machine_max.common.mech.subsystem;

/**
 * 一条装填作业的身份：{@code (宿主 SubPart ID, 发射器序号, 运输者, 提供者)}。<br>
 * <b>用途：</b>运输者的机械槽、提供者的弹药预留、乘员的认领记录都用这一个形状做键。前三项定位"哪根管上的
 * 哪条路"，第四项定位"这条路的另一半"——同一个运输者可以喂不同的架，同一个架也可以喂不同的管。
 * <p>
 * <b>身份由结构给出，不靠分配：</b>路由在两端各自的发现结果上跑同一份代码，选出的是同一对
 * {@link AmmoRef}，所以同一个"管 + 路"在两端得到同一个键，不需要任何计数、同步或下界覆盖。凑成这个键的是
 * 一次已经发生的选择，而不是一次需要两端协商的取号。
 * <p>
 * <b>一份作业只占一个键：</b>一根管同一时刻只有一个作业（受理之前没有作业，交付或中止之后作业清空），
 * 因此"同一根管的同一条路上同时存在两份交付"不会发生。编号因此没有存在的必要——它原本要回答的
 * "这是不是同一份装填"，路由已经答完了。同一根管的同一条路连续送很多轮的情况，由作业上的轮次
 * {@link ReloadJob#round()} 区分：轮次是样本与收据的先后标记，不进任何账本，因此轮次变化不会让机械槽与
 * 预留改键（改键要 release + claim + reserve 一整轮）。
 * <p>
 * <b>作用域：</b>只在本次会话内有效。{@code subPartId} 由服务端创建载体时分配，两端从载具数据拿到同一个
 * 值；{@code launcherIndex} 是发射器在所属 SubPart 子系统声明序中的位置（与其它网络载荷的子系统寻址
 * 同一口径，见 {@link AmmoRef}）。作业不持久化——读档后未完成的装填重新受理，身份由当时的路重新给出。
 * <p>
 * <b>顺序：</b>{@link #compareTo} 给出"宿主 ID → 发射器序号 → 运输者 → 提供者"的升序全序，实际架撤销整笔
 * 预留时按这个顺序排列后从末尾（键值最大者）开始撤销，因此撤销对象不依赖哈希顺序。
 * <p>
 * 空值用 {@link #NONE} 表达：宿主 ID 为 {@code -1}、序号为 {@code -1}、两个引用都是 {@link AmmoRef#NONE}。
 * 真实 SubPart 的 ID 由会话计数器分配，非负。
 */
public record ReloadJobKey(int subPartId, int launcherIndex, AmmoRef transporter, AmmoRef provider)
        implements Comparable<ReloadJobKey> {

    /** 没有作业 */
    public static final ReloadJobKey NONE = new ReloadJobKey(-1, -1, AmmoRef.NONE, AmmoRef.NONE);

    /** 是否为空值 */
    public boolean isNone() {
        return subPartId < 0;
    }

    /**
     * 先按宿主 SubPart ID、再按发射器序号、运输者与提供者的升序全序。
     */
    @Override
    public int compareTo(ReloadJobKey other) {
        int bySubPart = Integer.compare(subPartId, other.subPartId);
        if (bySubPart != 0) return bySubPart;
        int byLauncher = Integer.compare(launcherIndex, other.launcherIndex);
        if (byLauncher != 0) return byLauncher;
        int byTransporter = transporter.compareTo(other.transporter);
        return byTransporter != 0 ? byTransporter : provider.compareTo(other.provider);
    }
}
