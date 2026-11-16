package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

/**
 * 弹药端的对象身份：宿主 SubPart 的 int ID ＋ 子系统在该子零件声明序中的位置。<br>
 * 一次装填作业的两个角色都用它指认——运输者（可能不存在，见下）与提供者。地址形状与其它网络载荷的
 * 子系统寻址一致（{@code SubPart#subsystemIndex}／{@code #subsystemAt}）：序号是对象在所属 {@code SubPart}
 * 容器里的声明序位置，容器只整体清空、不部分删减，位置对存活对象稳定；两端解同一份内容定义，因此构造序
 * 相同。名字仍是存档键、翻译键与日志身份，不进入本类型。
 * <p>
 * <b>作用域是本次会话内的这一台载具：</b>SubPart 的 int ID 由服务端创建载体时分配并随载具数据下发，
 * 客户端拿到的就是服务端那一份；子系统序号由内容包定义。来源关系不持久化（读档后作业重新受理），
 * 因此没有需要它在重启后保持稳定的地方。
 * <p>
 * 空值用 {@link #NONE} 表达（{@code subPartId} 与 {@code subsystemIndex} 都是 {@code -1}；真实的 SubPart ID
 * 与序号非负）。它在运输者位上有唯一含义：<b>人力运输者</b>（{@link LoaderRoster} 不是子系统，无法用序号
 * 指认）；在提供者位上也只有一个含义：<b>没有提供者</b>。
 * <p>
 * {@link #compareTo} 给出与内容无关的全序，供路由在代价相等时做稳定的决胜键。
 */
public record AmmoRef(int subPartId, int subsystemIndex) implements Comparable<AmmoRef> {

    /** 空值：人力运输者（运输者位上）或没有提供者（提供者位上） */
    public static final AmmoRef NONE = new AmmoRef(-1, -1);

    /** 随同步值传输的编解码器：宿主 ID ＋ 声明序序号 */
    public static final StreamCodec<ByteBuf, AmmoRef> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AmmoRef::subPartId,
            ByteBufCodecs.VAR_INT, AmmoRef::subsystemIndex,
            AmmoRef::new);

    /** 是否为空值 */
    public boolean isNone() {
        return subPartId < 0;
    }

    /**
     * 取某台提供者的身份。<br>
     * 提供者必须是子系统（库存持有者与物品架都是），否则返回 {@link #NONE} 并记一条警告——这类对象在内容里
     * 不存在，断言只防回归。
     */
    public static AmmoRef ofProvider(@Nullable IAmmoSupplier provider) {
        if (provider == null) return NONE;
        if (provider instanceof AbstractSubsystem subsystem) return of(subsystem);
        MachineMax.LOGGER.warn("弹药提供者 {} 不是子系统，无法按结构序号寻址", provider);
        return NONE;
    }

    /**
     * 取某台运输者的身份。<br>
     * 人力运输者没有对象可指认，结果是 {@link #NONE}；机械运输者必须是子系统。`isHuman()` 与"结果是
     * `NONE`"因此互为充要条件——非子系统的机械运输者是编码错误，记一条警告并按人力处理。
     */
    public static AmmoRef ofTransporter(@Nullable IAmmoTransporter transporter) {
        if (transporter == null || transporter.isHuman()) return NONE;
        if (transporter instanceof AbstractSubsystem subsystem) return of(subsystem);
        MachineMax.LOGGER.warn("机械运输者 {} 不是子系统，无法按结构序号寻址；按人力运输者处理", transporter);
        return NONE;
    }

    private static AmmoRef of(AbstractSubsystem subsystem) {
        return new AmmoRef(subsystem.getSubPart().getId(), subsystem.getSubPart().subsystemIndex(subsystem));
    }

    /**
     * 先按宿主 SubPart ID、再按子系统序号的升序全序。
     */
    @Override
    public int compareTo(AmmoRef other) {
        int bySubPart = Integer.compare(subPartId, other.subPartId);
        return bySubPart != 0 ? bySubPart : Integer.compare(subsystemIndex, other.subsystemIndex);
    }
}
