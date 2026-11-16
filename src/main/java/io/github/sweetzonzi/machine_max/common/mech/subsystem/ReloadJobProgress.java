package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 一条装填作业的<b>动态样本</b>：编号、进度基线、本阶段时长与阶段。
 *
 * <p>这四项在作业存续期间会变——换段、换人重定时、暂停恢复都改写时长与基线。它们是同步里最常
 * 变化的部分，因此单独成一条记录：四五字节就能表达，不必每次带上 {@link ReloadJobSpec} 里那份
 * 白字节级的交付规格。</p>
 *
 * <p><b>编号是两支样本的关联键：</b>本记录必须与同批权威缓存里的静态作业对应，客户端拿它匹配
 * 当前本地作业的编号；编号不一致说明静态部分还没跟上，本记录不被应用。</p>
 *
 * <p><b>时长为 0 是合法值：</b>没有机械段的机构申报 {@code reload_time: 0}，作业在同一 tick 内受理、
 * 推进与兑现，{@link ReloadJob#progress()} 恒为 100%。因此 0 不能兼作"无作业"的哨兵——那是外层
 * {@code Optional} 的职责。</p>
 *
 * <p><b>基线不是镜像：</b>这是进度坐标的起点，两端各自在上面累计，不是每 tick 的当前进度。起点取服务端
 * 写下这一笔的那一 tick <b>末尾</b>的进度：客户端要等下一个业务 tick 才开始按它推进，早一拍的起点会让
 * 镜像作业永远比服务端晚一拍。</p>
 *
 * @param jobId   本管范围内的作业编号
 * @param elapsed 进度基线（进度坐标）
 * @param total   本阶段实际时长
 * @param stage   当前阶段
 */
public record ReloadJobProgress(
        int jobId,
        int elapsed,
        int total,
        ReloadJob.Stage stage) {

    /** 随同步值传输的编解码器：阶段编成一个布尔。 */
    public static final StreamCodec<ByteBuf, ReloadJobProgress> STREAM_CODEC =
            StreamCodec.of(ReloadJobProgress::write, ReloadJobProgress::read);

    /**
     * 取当前作业的进度样本。
     *
     * @param job 当前作业
     */
    public static ReloadJobProgress of(ReloadJob job) {
        return new ReloadJobProgress(job.jobId(), job.elapsedTicks(), job.totalTicks(), job.stage());
    }

    /**
     * 换一条进度基线，其余字段照抄。<br>
     * 用在两个点：服务端 tick 收尾把本 tick 写过的基线定稿成末尾值，以及全量补推前把新加入的客户端要的
     * "现在"写进去。两处都是"样本停在写下它的那一刻，而客户端要的是另一个时刻的进度"。
     *
     * @param newElapsed 当前的进度坐标
     */
    public ReloadJobProgress withElapsed(int newElapsed) {
        return new ReloadJobProgress(jobId, newElapsed, total, stage);
    }

    private static void write(ByteBuf buf, ReloadJobProgress value) {
        ByteBufCodecs.VAR_INT.encode(buf, value.jobId);
        ByteBufCodecs.VAR_INT.encode(buf, value.elapsed);
        ByteBufCodecs.VAR_INT.encode(buf, value.total);
        buf.writeBoolean(value.stage == ReloadJob.Stage.MACHINE);
    }

    private static ReloadJobProgress read(ByteBuf buf) {
        int jobId = ByteBufCodecs.VAR_INT.decode(buf);
        int elapsed = ByteBufCodecs.VAR_INT.decode(buf);
        int total = ByteBufCodecs.VAR_INT.decode(buf);
        ReloadJob.Stage stage = buf.readBoolean() ? ReloadJob.Stage.MACHINE : ReloadJob.Stage.HUMAN;
        return new ReloadJobProgress(jobId, elapsed, total, stage);
    }
}
