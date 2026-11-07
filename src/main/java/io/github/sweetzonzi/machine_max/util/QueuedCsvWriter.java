package io.github.sweetzonzi.machine_max.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 有界 CSV 通道。生产线程调用 offer，唯一消费线程调用 flush 和 close。
 * offer 不访问磁盘；close 停止接收，等待正在入队的生产者退出，再排空并关闭文件。
 */
public final class QueuedCsvWriter implements AutoCloseable {
    private final Path path;
    private final BufferedWriter writer;
    private final int capacity;
    private final ConcurrentLinkedQueue<String> pending = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queued = new AtomicInteger();
    private final AtomicInteger producers = new AtomicInteger();
    private final AtomicLong samples = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private volatile boolean stopped;
    private volatile boolean failed;
    private boolean closed;

    private QueuedCsvWriter(Path path, BufferedWriter writer, int capacity) {
        this.path = path;
        this.writer = writer;
        this.capacity = capacity;
    }

    /** 创建独立文件；同名主语的多次打开不会覆盖录制。capacity 是待写行数上限。 */
    public static QueuedCsvWriter open(Path directory, String subject, String header, int capacity) throws IOException {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        String name = subject == null ? "recording" : subject.strip();
        name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        if (name.isBlank()) name = "recording";
        if (name.length() > 80) name = name.substring(0, 80);
        Files.createDirectories(directory);
        Path path = directory.resolve(name + "-" + UUID.randomUUID() + ".csv");
        BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        try {
            writer.write(header);
            writer.newLine();
            writer.flush();
            return new QueuedCsvWriter(path, writer, capacity);
        } catch (IOException failure) {
            try { writer.close(); } catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
    }

    /** 提交完整 CSV 行。队列已满或停止接收时返回 false；溢出计入 dropped。 */
    public boolean offer(String line) {
        producers.incrementAndGet();
        try {
            if (stopped) return false;
            int count;
            do {
                count = queued.get();
                if (count >= capacity) {
                    dropped.incrementAndGet();
                    return false;
                }
            } while (!queued.compareAndSet(count, count + 1));
            pending.add(line);
            samples.incrementAndGet();
            return true;
        } finally { producers.decrementAndGet(); }
    }

    /** 消费线程处理本次调用开始时的积压量；持续生产不会延长本次处理上限。 */
    public void flush() throws IOException {
        if (closed) return;
        try {
            int budget = queued.get();
            if (budget == 0) return;
            for (int i = 0; i < budget; i++) {
                String line = pending.poll();
                if (line == null) break;
                queued.decrementAndGet();
                writer.write(line);
                writer.newLine();
            }
            writer.flush();
        } catch (IOException failure) {
            failed = true;
            stopped = true;
            throw failure;
        }
    }

    @Override public void close() throws IOException {
        if (closed) return;
        stopped = true;
        while (producers.get() != 0) Thread.onSpinWait();
        try { flush(); }
        finally {
            closed = true;
            try { writer.close(); } catch (IOException failure) { failed = true; throw failure; }
        }
    }

    /** 编码 CSV 字段，包括逗号、引号和换行。 */
    public static String row(Object... fields) {
        StringBuilder line = new StringBuilder();
        boolean first = true;
        for (Object field : fields) {
            if (!first) line.append(',');
            first = false;
            String value = field == null ? "" : field.toString();
            if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)
                line.append('"').append(value.replace("\"", "\"\"")).append('"');
            else line.append(value);
        }
        return line.toString();
    }

    public Path path() { return path; }
    public long samples() { return samples.get(); }
    public long dropped() { return dropped.get(); }
    public boolean failed() { return failed; }
}
