package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.common.mech.ammo.AmmoBelt;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 一条装填作业的<b>冻结分配</b>：轮次、运输者、提供者、受理时冻结的交付规格与预留份额。
 *
 * <p>这五项在作业存续期间不变——受理时一起定下，直到收尾。因此它只在作业开始、结束与全量补推时
 * 下发；进度、本阶段时长与阶段另由 {@link ReloadJobProgress} 承载，那里每次离散变化都要重发。</p>
 *
 * <p><b>为什么与进度分开：</b>{@link #spec()} 里的交付规格带着两三个内容 id（UTF-8 字符串），一份
 * 约百字节，而进度样本只有四五个变长整数。合并成一条记录时，换段、换人重定时这些"只有进度变"的
 * 时刻也要重发整份规格；分开之后这些时刻只发进度。</p>
 *
 * <p><b>轮次是两支样本的关联键：</b>作业身份（哪根管 + 哪条路）由接收者本身与 {@link #transporter()}、
 * {@link #provider()} 给出，因此这份分配不需要再带编号；同一根管的同一条路连续送很多轮，只有
 * {@link #round()} 能分出先后。客户端拿它把两支样本配成一份作业，并用它判断这一笔说的是不是手上这一轮。</p>
 *
 * <p><b>值而不持有：</b>{@link #spec()} 是受理时冻结规格的副本（见 {@link AmmoBelt#syncCopy()}），
 * 两端拿到的都是一份可以各自推着走的快照。</p>
 *
 * @param round       本管第几次交付
 * @param transporter 本作业的运输者身份；{@link AmmoRef#NONE} = 人力运输者
 * @param provider    本作业的提供者身份
 * @param spec        受理时冻结的交付规格
 * @param reserved    整次作业的预留份额
 */
public record ReloadJobSpec(
        int round,
        AmmoRef transporter,
        AmmoRef provider,
        AmmoBelt spec,
        int reserved) {

    /** 随同步值传输的编解码器：按声明顺序逐项写。 */
    public static final StreamCodec<ByteBuf, ReloadJobSpec> STREAM_CODEC =
            StreamCodec.of(ReloadJobSpec::write, ReloadJobSpec::read);

    /**
     * 取当前作业的冻结分配：规格复制一份（它是会被消耗的可变对象），其余按值。
     *
     * @param job 当前作业
     */
    public static ReloadJobSpec of(ReloadJob job) {
        return new ReloadJobSpec(job.round(), job.transporter(), job.provider(),
                job.spec().syncCopy(), job.reservedCount());
    }

    /**
     * 按这份分配与一份进度样本建一条本端作业，进度落在样本给的基线上。
     *
     * @param key      作业身份（宿主 SubPart ID 与发射器序号由本端补上，运输者与提供者取自本分配）
     * @param progress 同一轮次的进度样本
     */
    public ReloadJob toLocalJob(ReloadJobKey key, ReloadJobProgress progress) {
        ReloadJob job = new ReloadJob(key, round, spec, reserved,
                progress.stage(), progress.total(), 1.0);
        job.advanceTo(progress.elapsed());
        return job;
    }

    private static void write(ByteBuf buf, ReloadJobSpec value) {
        ByteBufCodecs.VAR_INT.encode(buf, value.round);
        AmmoRef.STREAM_CODEC.encode(buf, value.transporter);
        AmmoRef.STREAM_CODEC.encode(buf, value.provider);
        AmmoBelt.STREAM_CODEC.encode(buf, value.spec);
        ByteBufCodecs.VAR_INT.encode(buf, value.reserved);
    }

    private static ReloadJobSpec read(ByteBuf buf) {
        int round = ByteBufCodecs.VAR_INT.decode(buf);
        AmmoRef transporter = AmmoRef.STREAM_CODEC.decode(buf);
        AmmoRef provider = AmmoRef.STREAM_CODEC.decode(buf);
        AmmoBelt spec = AmmoBelt.STREAM_CODEC.decode(buf);
        int reserved = ByteBufCodecs.VAR_INT.decode(buf);
        return new ReloadJobSpec(round, transporter, provider, spec, reserved);
    }
}
