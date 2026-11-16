package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.common.mech.ammo.AmmoBelt;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 一条装填作业的同步快照：身份、冻结分配与当前阶段的时间坐标，作为一个同步值整包下发。
 * <p>
 * <b>为什么整包：</b>这八项只在同一批事件里一起变（受理、换段、换人重定时、收尾），而客户端要用其中
 * 好几项才能建出一条作业。逐项下发时"字段到齐了没有"是一个真实可观察的中间状态，落地处必须为它写
 * 防御分支；字段一旦需要扩展，又会推动同类访问器编号整体后移、迫使协议版本升级。整包之后前者由
 * {@code Optional.empty()} 表达（没有作业），后者只是本记录的字段变化。
 * <p>
 * <b>时长为 0 是合法值：</b>没有机械段的机构申报 {@code reload_time: 0}，作业在同一 tick 内受理、推进
 * 与兑现，{@link ReloadJob#progress()} 恒为 100%。因此 0 不能兼作"无作业"的哨兵——那是外层
 * {@code Optional} 的职责。
 * <p>
 * <b>值而不持有：</b>{@link #spec()} 是受理时冻结规格的副本（见 {@link AmmoBelt#syncCopy()}），
 * {@link #elapsed()} 是进度基线而非每 tick 的镜像。两端拿到的都是一份可以各自推着走的快照。
 *
 * @param jobId       本管范围内的作业编号
 * @param transporter 本作业的运输者身份；{@link AmmoRef#NONE} = 人力运输者
 * @param provider    本作业的提供者身份；{@link AmmoRef#NONE} = 没有作业（仅与空快照同时出现）
 * @param spec        受理时冻结的交付规格
 * @param reserved    整次作业的预留份额
 * @param elapsed     进度基线（进度坐标）
 * @param total       本阶段实际时长
 * @param stage       当前阶段
 */
public record ReloadJobSync(
        int jobId,
        AmmoRef transporter,
        AmmoRef provider,
        AmmoBelt spec,
        int reserved,
        int elapsed,
        int total,
        ReloadJob.Stage stage) {

    /** 随同步值传输的编解码器：按声明顺序逐项写，阶段编成一个布尔。 */
    public static final StreamCodec<ByteBuf, ReloadJobSync> STREAM_CODEC =
            StreamCodec.of(ReloadJobSync::write, ReloadJobSync::read);

    /**
     * 取当前作业的快照：规格复制一份（它是会被消耗的可变对象），其余按值。
     *
     * @param job 当前作业
     */
    public static ReloadJobSync of(ReloadJob job) {
        return new ReloadJobSync(job.jobId(), job.transporter(), job.provider(),
                job.spec().syncCopy(), job.reservedCount(), job.elapsedTicks(), job.totalTicks(), job.stage());
    }

    /**
     * 换一条进度基线，其余字段照抄。<br>
     * 全量补推前用：基线只在作业开始与离散重建时写过，新加入的客户端要的却是"现在"。不走
     * {@link #of(ReloadJob)} 是因为那会重新复制 {@link #spec()}——值没变却让整包看起来变过。
     *
     * @param newElapsed 当前的进度坐标
     */
    public ReloadJobSync withElapsed(int newElapsed) {
        return new ReloadJobSync(jobId, transporter, provider, spec, reserved, newElapsed, total, stage);
    }

    /**
     * 按这份快照建一条本端作业，进度落在基线上。
     *
     * @param key 作业身份（宿主 SubPart ID 与发射器序号由本端补上，编号取自快照）
     */
    public ReloadJob toLocalJob(ReloadJobKey key) {
        ReloadJob job = new ReloadJob(key, transporter, provider, spec, reserved, stage, total, 1.0);
        job.advanceTo(elapsed);
        return job;
    }

    private static void write(ByteBuf buf, ReloadJobSync value) {
        ByteBufCodecs.VAR_INT.encode(buf, value.jobId);
        AmmoRef.STREAM_CODEC.encode(buf, value.transporter);
        AmmoRef.STREAM_CODEC.encode(buf, value.provider);
        AmmoBelt.STREAM_CODEC.encode(buf, value.spec);
        ByteBufCodecs.VAR_INT.encode(buf, value.reserved);
        ByteBufCodecs.VAR_INT.encode(buf, value.elapsed);
        ByteBufCodecs.VAR_INT.encode(buf, value.total);
        buf.writeBoolean(value.stage == ReloadJob.Stage.MACHINE);
    }

    private static ReloadJobSync read(ByteBuf buf) {
        int jobId = ByteBufCodecs.VAR_INT.decode(buf);
        AmmoRef transporter = AmmoRef.STREAM_CODEC.decode(buf);
        AmmoRef provider = AmmoRef.STREAM_CODEC.decode(buf);
        AmmoBelt spec = AmmoBelt.STREAM_CODEC.decode(buf);
        int reserved = ByteBufCodecs.VAR_INT.decode(buf);
        int elapsed = ByteBufCodecs.VAR_INT.decode(buf);
        int total = ByteBufCodecs.VAR_INT.decode(buf);
        ReloadJob.Stage stage = buf.readBoolean() ? ReloadJob.Stage.MACHINE : ReloadJob.Stage.HUMAN;
        return new ReloadJobSync(jobId, transporter, provider, spec, reserved, elapsed, total, stage);
    }
}
