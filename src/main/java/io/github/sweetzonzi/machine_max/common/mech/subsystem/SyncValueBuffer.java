package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.network.payload.SubsystemSyncPayload;
import net.minecraft.network.syncher.SynchedEntityData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 客户端待应用的权威同步值：按 {@code (子零件 ID, 子系统序号, 数据项 ID)} 合并，后到的覆盖先到的。
 *
 * <p>同步值是<b>值表</b>而不是事件流，一个字段只有"当前值"这一个含义，因此缓冲不需要保存到达顺序：
 * 同一个字段在一轮里收到多次，只有最后那次的值有意义。合并后的条目数上界是本装配体已定义的同步
 * 字段总数，与网络积压多久无关。</p>
 *
 * <p>跨批次合并是安全的，因为服务端在主线程上"采样与发送成对发生"，发送序就是采样序；同一条连接
 * 内传输有序，所以后到的批次一定采样更晚。</p>
 *
 * <p>本类不判断调用端：写入只由客户端网络处理器调用，落地只由客户端的业务收尾调用。</p>
 *
 * <p><b>调用线程：</b>主线程。</p>
 */
public final class SyncValueBuffer {

    /** 一个同步值的身份：这三项唯一确定一个访问器。 */
    public record EntryKey(int subPartId, int subsystemIndex, int dataItemId) {
    }

    private final Map<EntryKey, SynchedEntityData.DataValue<?>> values = new HashMap<>();

    /**
     * 合入一批条目，同一字段以本次写入为准。
     *
     * @param entries 一份批次里的条目
     */
    public void merge(List<SubsystemSyncPayload.Entry> entries) {
        for (SubsystemSyncPayload.Entry entry : entries) {
            for (SynchedEntityData.DataValue<?> value : entry.values()) {
                values.put(new EntryKey(entry.subPartId(), entry.subsystemIndex(), value.id()), value);
            }
        }
    }

    /**
     * 按身份逐项交出当前待应用的值。
     *
     * @param consumer 接收方；遍历顺序不参与任何判定
     */
    public void forEach(BiConsumer<EntryKey, SynchedEntityData.DataValue<?>> consumer) {
        values.forEach(consumer);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    /** 落地完成后清空：这些值已成为各子系统同步数据的当前值。 */
    public void clear() {
        values.clear();
    }
}
