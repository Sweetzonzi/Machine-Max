package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 弹药接收者接口。<br>
 * 发射器等需要装填的子系统实现此接口：它持有作业与进度、本管的发现结果与当前路由，并向两个角色各问一件事
 * ——向运输者要本段时长、执行资源与推进量，向提供者要整次作业的预留。
 * <p>
 * <b>两张发现表：</b>{@link #addTransporter}／{@link #addProvider} 是只读发现的登记入口，命中对象按角色
 * 分开保存（频道只是登记键，不进路由）。{@link #getRoutes()} 由这两张表按需合成"运输者 × 提供者"的路：
 * 机械运输者配它的喂弹线，人力运输者配本管自己发现到的架。配对放在查询时做，因此结构变化后立即生效。
 * <p>
 * <b>路由是接收者的决定，作业是权威：</b>{@link #getCurrentRoute()}／{@link #setCurrentRoute} 记录本管
 * 当前指认的那条路（服务端写、客户端跟随同步值）；作业上的两个 {@link AmmoRef} 在受理时冻结，收尾、推进
 * 与交付都按作业上那一对解析，路只是指认。
 */
public interface IAmmoConsumer {

    /**
     * 本管当前指认的那条路；{@code null} 表示还没有路由（管上无指认）。<br>
     * 服务端由武器控制器的路由设置；客户端不自行挑选，它的指认来自服务端同步过来的两个引用。
     */
    @Nullable AmmoPath getCurrentRoute();

    /**
     * 设置当前指认的路（{@code null} 表示没有路）。<br>
     * 指认变化时本管进行中的作业按身份收尾：那份作业是"点名给本管的"，本管一走它就无人认领。清理放在
     * 这里而不是交给调用方，是为了不存在"换路忘了归还"的路径。
     */
    void setCurrentRoute(@Nullable AmmoPath route);

    /**
     * 本管此刻可用的全部路：运输者 × 喂弹线，加人力路；现算，同一对对象只出一条。<br>
     * 结构变化后立即生效，没有缓存也没有跨 tick 状态。
     */
    List<AmmoPath> getRoutes();

    /**
     * 按身份解析一台提供者：先查机械运输者的喂弹线，再查本管自己发现到的架。<br>
     * 客户端按同步来的引用解析实例、以及运输者判断"作业上的预留还在吗"都走这一口。
     *
     * @param ref 提供者身份
     * @return 对应实例；本管的发现结果里还没有它时返回 {@code null}
     */
    @Nullable IAmmoSupplier resolveProvider(AmmoRef ref);

    /**
     * 登记一台被发现的运输者（装弹机）。
     *
     * @param transporter 运输者实例
     * @param channelName 本次发现命中的到达频道（只作登记键，不参与路由）
     */
    void addTransporter(IAmmoTransporter transporter, String channelName);

    /**
     * 登记一台被发现的提供者（弹药架）。<br>
     * 只在型号声明了完整人力流程（{@code human_reload_time > 0}）时才登记：库存持有者没有机械段，
     * 它在人力路上充当提供者，没有人工段的型号不能让"取弹不花时间"成立。
     *
     * @param provider    提供者实例
     * @param channelName 本次发现命中的到达频道（只作登记键，不参与路由）
     */
    void addProvider(IAmmoSupplier provider, String channelName);

    /**
     * 本接收者当前唯一的那一份装填作业；{@code null} 表示没有进行中的作业。<br>
     * 只读口：运输者（放行、占用校验）、人员附件与显示层都从这里读取同一份作业，不各自保存一份分配记录。
     */
    @Nullable ReloadJob getReloadJob();

    /**
     * 判断此接收者是否可以接受指定弹药类型。<br>
     * 兼容性校验由接收者端在运行时逐发执行。
     */
    boolean canAccept(ProjectileType type);
}
