package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.util.QueuedCsvWriter;
import net.neoforged.fml.loading.FMLPaths;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 调试记录的目录与日志适配，排队和写盘由 QueuedCsvWriter 承担。 */
public final class DriveTelemetry implements AutoCloseable {
    private final QueuedCsvWriter channel;
    private final long startedAtNanos = System.nanoTime();
    private boolean overflowReported;

    private DriveTelemetry(QueuedCsvWriter channel) { this.channel = channel; }

    public static DriveTelemetry open(String subject, String header) throws IOException {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        return new DriveTelemetry(QueuedCsvWriter.open(
                FMLPaths.GAMEDIR.get().resolve("logs/machine_max"), subject + "-" + stamp, header, 65536));
    }

    public void offer(String line) { channel.offer(line); }

    public void flush() {
        try { channel.flush(); }
        catch (IOException failure) {
            MachineMax.LOGGER.error("记录写盘失败 {}", path(), failure);
            close();
        }
        if (!overflowReported && channel.dropped() > 0) {
            overflowReported = true;
            MachineMax.LOGGER.warn("记录队列溢出，文件不完整 {}", path());
        }
    }

    @Override public void close() {
        try { channel.close(); }
        catch (IOException failure) { MachineMax.LOGGER.error("记录关闭失败 {}", path(), failure); }
    }

    public Path path() { return channel.path(); }
    public long samples() { return channel.samples(); }
    public long elapsedMillis() { return (System.nanoTime() - startedAtNanos) / 1_000_000L; }
    long dropped() { return channel.dropped(); }
    boolean failed() { return channel.failed(); }
}
