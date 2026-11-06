package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.MachineMax;
import net.neoforged.fml.loading.FMLPaths;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 通用录制器：把一次采样写成一行 CSV。使用方各自决定采样相位与列定义。
 *
 * <p>两个使用者：地面控制器的行车记录（采样在物理线程、{@link AbstractGroundControllerSubsystem#onPrePhysicsTick()}
 * 之后，写盘在主线程 {@link AbstractGroundControllerSubsystem#onTick()}）与客户端乘车链路的录制
 * （采样与写盘都在主线程，见 {@code io.github.sweetzonzi.machine_max.client.debug.RideTelemetry}）。
 * 采样方只把已经格式化的行放进无锁队列，写盘方每 tick 排空一次，所以磁盘慢不会拖住采样；写盘方卡住时
 * 队列会攒住，事后一并写盘，不丢样本。</p>
 *
 * <p>文件落在 {@code <gamedir>/logs/machine_max/}，一次录制一个文件，列名写在首行。本类不认识任何
 * 物理量：列名与每行的内容都由调用方给出。</p>
 */
public final class DriveTelemetry implements AutoCloseable {

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Path DIRECTORY = FMLPaths.GAMEDIR.get().resolve("logs").resolve("machine_max");
    /** 文件名里保留的主语长度上限，超长部分截掉。 */
    private static final int MAX_SUBJECT_LENGTH = 48;
    /** 文件名中需要替换的字符：路径分隔符、Windows 保留字符与控制字符。 */
    private static final String ILLEGAL_FILE_CHARS = "[\\\\/:*?\"<>|\\p{Cntrl}]";

    private final Path path;
    private final Queue<String> pending = new ConcurrentLinkedQueue<>();
    private final AtomicLong samples = new AtomicLong();
    private final long startedAtNanos = System.nanoTime();

    /** 主线程独占的写入口；只在本类的方法里使用。 */
    private final BufferedWriter writer;
    /** 队列是否已停止接收；物理线程读取。 */
    private volatile boolean closed;

    private DriveTelemetry(Path path, BufferedWriter writer) {
        this.path = path;
        this.writer = writer;
    }

    /**
     * 新建一个录制文件并写入列名。
     *
     * @param subject 文件名主语，通常是载具名；非法字符会被替换
     * @param header  首行列名，逗号分隔
     * @return 已打开的记录仪
     * @throws IOException 目录或文件无法创建
     */
    public static DriveTelemetry open(String subject, String header) throws IOException {
        Files.createDirectories(DIRECTORY);
        Path file = DIRECTORY.resolve(sanitize(subject) + "-" + FILE_STAMP.format(LocalDateTime.now()) + ".csv");
        BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
        writer.write(header);
        writer.newLine();
        writer.flush();
        return new DriveTelemetry(file, writer);
    }

    /**
     * 物理线程：提交一行采样。已关闭时丢弃。
     *
     * @param line 完整的一行，不带换行符
     */
    public void offer(String line) {
        if (closed) {
            return;
        }
        pending.add(line);
        samples.incrementAndGet();
    }

    /**
     * 主线程：把队列里的行写盘并 flush。没有排队行时不碰磁盘。
     */
    public void flush() {
        if (closed) {
            return;
        }
        try {
            writePending();
        } catch (IOException e) {
            MachineMax.LOGGER.error("行车记录仪写盘失败 {}，录制结束: {}", path, e.toString());
            close();
        }
    }

    private void writePending() throws IOException {
        String line = pending.poll();
        if (line == null) {
            return;
        }
        do {
            writer.write(line);
            writer.newLine();
        } while ((line = pending.poll()) != null);
        writer.flush();
    }

    /**
     * 主线程：写完剩余行并关闭文件。可重复调用。
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            writePending();
        } catch (IOException e) {
            MachineMax.LOGGER.error("行车记录仪收尾失败 {}: {}", path, e.toString());
        }
        try {
            writer.close();
        } catch (IOException e) {
            MachineMax.LOGGER.error("行车记录仪关闭失败 {}: {}", path, e.toString());
        }
    }

    public Path path() {
        return path;
    }

    /** 已提交的行数，含尚未写盘的。 */
    public long samples() {
        return samples.get();
    }

    /** 从打开到现在的真实毫秒数，写在每行里用于区分仿真时间与真实时间。 */
    public long elapsedMillis() {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }

    private static String sanitize(String subject) {
        String name = subject == null ? "" : subject.strip();
        if (name.isEmpty()) {
            name = "vehicle";
        }
        name = name.replaceAll(ILLEGAL_FILE_CHARS, "_").strip();
        if (name.length() > MAX_SUBJECT_LENGTH) {
            name = name.substring(0, MAX_SUBJECT_LENGTH);
        }
        return name;
    }
}
