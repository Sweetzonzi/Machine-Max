package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 一条装填作业的身份：{@code (宿主 SubPart ID, 发射器子系统名, jobId)}。
 * <p>
 * <b>用途：</b>机构占用、实际架弹药预留与独占机构的槽位拥有者都用这一个形状做键。三项一起用是必要的：
 * 同一个 {@code jobId} 会在不同发射器上重复出现（各管自己从 1 开始编号），只看编号分不清两条作业；
 * 只看发射器分不清同一台管先后两次作业，旧作业的回调就会摘掉新作业的占用。
 * <p>
 * <b>作用域：</b>只在本次会话内有效。{@code subPartId} 由服务端创建载体时分配，两端从载具数据拿到同一个值；
 * {@code launcherName} 由内容包定义；{@code jobId} 由发射器在受理时自增。因此这个身份不持久化——读档后
 * 未完成的装填重新受理，编号从 1 重新分配（见 {@link ReloadJob}）。
 * <p>
 * <b>顺序：</b>{@link #compareTo} 给出"宿主 ID → 名称 → 作业编号"的升序全序，实际架撤销整笔预留时按这个
 * 顺序排列后从末尾（键值最大者）开始撤销，因此撤销对象不依赖哈希顺序。
 * <p>
 * 空值用 {@link #NONE} 表达：宿主 ID 为 {@code -1}、名称为空串、编号为 {@code 0}。真实 SubPart 的 ID 由
 * 会话计数器分配，非负。
 */
public record ReloadJobKey(int subPartId, String launcherName, int jobId)
        implements Comparable<ReloadJobKey> {

    /** 没有作业 */
    public static final ReloadJobKey NONE = new ReloadJobKey(-1, "", 0);

    /** 随同步值传输的编解码器：宿主 ID + UTF-8 名称 + 作业编号 */
    public static final StreamCodec<ByteBuf, ReloadJobKey> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ReloadJobKey::subPartId,
            ByteBufCodecs.STRING_UTF8, ReloadJobKey::launcherName,
            ByteBufCodecs.VAR_INT, ReloadJobKey::jobId,
            ReloadJobKey::new);

    /** 是否为空值 */
    public boolean isNone() {
        return jobId <= 0;
    }

    /**
     * 先按宿主 SubPart ID、再按发射器名与作业编号的升序全序。
     */
    @Override
    public int compareTo(ReloadJobKey other) {
        int bySubPart = Integer.compare(subPartId, other.subPartId);
        if (bySubPart != 0) return bySubPart;
        int byName = launcherName.compareTo(other.launcherName);
        return byName != 0 ? byName : Integer.compare(jobId, other.jobId);
    }
}
