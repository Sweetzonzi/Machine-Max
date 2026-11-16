package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * 一段按发生顺序排列的作业结束结果：编号与"这一次交付成了没有"。
 *
 * <p><b>为什么是一条列表而不是两个标量字段：</b>一轮服务端 tick 里可以结束不止一条作业——零时长作业
 * 受理即交付、紧接着受理下一条并同样交付，是连发机构的常规路径。标量只能留下最后一次，前一条的终态
 * 在客户端看不到，本端的预测作业就会一直等一个永远不会再来的结果。列表让同一轮的多条终态都能被按序
 * 应用。</p>
 *
 * <p><b>为什么不无限增长：</b>这是同步值而不是日志，只保留最近若干条（见 {@code LauncherSubsystem} 的
 * 上限）。客户端用编号水位忽略已应用过的条目，因此被裁掉的一定是客户端早已确认过的旧结果；真正的
 * 缺口由作业快照与冻结分配判定，不由结果列表兜底。</p>
 *
 * <p>编号是本管范围内的作业身份，不需要再编码宿主 ID 与子系统名。</p>
 *
 * @param entries 按发生顺序排列的结果
 */
public record ReloadJobResults(List<Entry> entries) {

    /** 单条结果：作业编号与是否已交付。 */
    public record Entry(int jobId, boolean delivered) {
    }

    /** 没有任何结束结果。 */
    public static final ReloadJobResults EMPTY = new ReloadJobResults(List.of());

    /** 随同步值传输的编解码器。 */
    public static final StreamCodec<ByteBuf, ReloadJobResults> STREAM_CODEC =
            StreamCodec.of(ReloadJobResults::write, ReloadJobResults::read);

    /**
     * 最近一条结果的作业编号；{@code 0} 表示还没有结束过作业。
     */
    public int lastJobId() {
        return entries.isEmpty() ? 0 : entries.getLast().jobId();
    }

    /** 最近一条结果是否已交付；没有结果时为 {@code false}。 */
    public boolean lastDelivered() {
        return !entries.isEmpty() && entries.getLast().delivered();
    }

    private static void write(ByteBuf buf, ReloadJobResults value) {
        ByteBufCodecs.VAR_INT.encode(buf, value.entries.size());
        for (Entry entry : value.entries) {
            ByteBufCodecs.VAR_INT.encode(buf, entry.jobId());
            buf.writeBoolean(entry.delivered());
        }
    }

    private static ReloadJobResults read(ByteBuf buf) {
        int count = ByteBufCodecs.VAR_INT.decode(buf);
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int jobId = ByteBufCodecs.VAR_INT.decode(buf);
            entries.add(new Entry(jobId, buf.readBoolean()));
        }
        return new ReloadJobResults(List.copyOf(entries));
    }
}
