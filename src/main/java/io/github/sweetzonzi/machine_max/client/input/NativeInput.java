package io.github.sweetzonzi.machine_max.client.input;

import com.github.strikerx3.jxinput.enums.XInputButton;
import com.jme3.system.Platform;
import lombok.Getter;
import lombok.Setter;
import org.checkerframework.checker.units.qual.A;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 按键输入处理类，支持单个按键、组合键、连按和长按
 * <p>
 * 该类用于处理键盘和鼠标按键的输入事件，支持通过链式调用设置事件回调、连按次数和长按时间。
 * 支持组合键功能，可以通过 chain() 方法链接多个按键创建组合键。
 * <p>
 * 使用示例：
 * <pre>
 * // 创建单个按键事件
 * new NativeInput("a").setEvent(() -> System.out.println("A pressed"));
 * 
 * // 创建组合键事件
 * new NativeInput("a")
 *         .chain(new NativeInput("ctrl"))
 *         .setEvent(() -> System.out.println("Ctrl+A pressed"));
 * 
 * // 创建长按事件
 * new NativeInput("a")
 *         .hold(1, ChronoUnit.SECONDS)
 *         .setEvent(() -> System.out.println("A held for 1 second"));
 * </pre>
 * 
 * @author ArcherLee
 * @version 1.0.0
 */
public class NativeInput {
    @Getter
    private NativeInput prev;
    @Getter
    private NativeInput next;
    @Setter
    @Getter
    private String keyName;
    
    private String combinedKeyName;


    @Getter
    private Runnable event;

    @Getter
    private Integer pressTimes = 1;
    
    @Getter
    private long holdTimeNanos = 1;

    public NativeInput(String keyName) {
        keyName = keyName.toLowerCase();
        NativeKeyListener.pressKeyInputs.computeIfAbsent(keyName, k -> new ArrayList<>()).add(this);
        this.keyName = keyName;
    }

    public NativeInput(XInputButton button) {
        if (!NativeKeyListener.PLATFORM.equals(Platform.Os.Windows)) return;
        keyName = NativeKeyListener.xInputButton(button).toLowerCase();
        NativeKeyListener.pressKeyInputs.computeIfAbsent(keyName, k -> new ArrayList<>()).add(this);
    }
    public NativeInput(String keyName, Integer pressTimes) {
        keyName = keyName.toLowerCase();
        NativeKeyListener.pressKeyInputs.computeIfAbsent(keyName, k -> new ArrayList<>()).add(this);
        this.keyName = keyName;
        this.pressTimes = pressTimes;
    }

    public NativeInput setEvent(Runnable event) {
        this.event = event;
        return this;
    }

    public NativeInput setEventOnce(Runnable event) {
        if (this.event == null) this.event = event;
        return this;
    }

    private void setPrev(NativeInput prev) {
        // 防止循环连接：检查设置prev是否会导致循环
        if (prev != null && wouldCreateCycle(prev)) {
            return;
        }
        
        // 双向设置：同时设置prev的next为this
        if (this.prev != null) {
            this.prev.next = null;
        }
        this.prev = prev;
        if (prev != null) {
            prev.next = this;
        }
    }

    private void setNext(NativeInput next) {
        // 防止循环连接：检查设置next是否会导致循环
        if (next != null && wouldCreateCycle(next)) {
            return;
        }
        
        // 双向设置：同时设置next的prev为this
        if (this.next != null) {
            this.next.prev = null;
        }
        this.next = next;
        if (next != null) {
            next.prev = this;
        }
    }
    
    private boolean wouldCreateCycle(NativeInput node) {
        // 检查从node出发是否能回到当前节点
        NativeInput current = node;
        while (current != null) {
            if (current == this) {
                return true;
            }
            current = current.next;
        }
        current = node;
        while (current != null) {
            if (current == this) {
                return true;
            }
            current = current.prev;
        }
        return false;
    }
    
    public Stream<NativeInput> getAllConnectedNodes() {
        Set<NativeInput> visited = new HashSet<>();
        collectNodes(this, visited);
        return visited.stream();
    }
    
    private void collectNodes(NativeInput node, Set<NativeInput> visited) {
        if (node == null || visited.contains(node)) {
            return;
        }
        visited.add(node);
        collectNodes(node.prev, visited);
        collectNodes(node.next, visited);
    }


    public NativeInput chain(NativeInput next) {
        NativeInput last = this;
        while (last.next != null) {
            last = last.next;
        }
        if (next.keyName.equals(last.keyName)) {
            last.pressTimes += next.pressTimes;
            return last;
        }
        last.setNext(next);

        // 更新combinedKey
        NativeKeyListener.pressKeyInputs.getOrDefault(keyName, new ArrayList<>()).removeIf(nativeInput -> nativeInput.equals(this));
        String oldCombinedKeyName = getCombinedKeyName();
        if (oldCombinedKeyName != null && !oldCombinedKeyName.isEmpty()) {
            NativeKeyListener.combineKeyInputsMap.getOrDefault(oldCombinedKeyName, new ArrayList<>()).remove(this);
        }
        updateCombinedKeyName();
        String newCombinedKeyName = getCombinedKeyName();
        if (newCombinedKeyName != null && !newCombinedKeyName.isEmpty()) {
            NativeKeyListener.combineKeyInputsMap.computeIfAbsent(newCombinedKeyName, k -> new ArrayList<>()).add(this);
        }
        return this;
    }

    private void updateCombinedKeyName() {
        combinedKeyName = getAllConnectedNodes()
                .filter(node -> node.getKeyName() != null && !node.getKeyName().isEmpty())
                .map(node -> node.keyName)
                .sorted((key1, key2) -> {
                    int lengthCompare = Integer.compare(key2.length(), key1.length());
                    if (lengthCompare != 0) {
                        return lengthCompare;
                    }
                    return key1.compareTo(key2);
                })
                .reduce((key1, key2) -> key1 + "-" + key2)
                .orElse("");
    }

    public String getCombinedKeyName() {
        if (combinedKeyName == null) {
            updateCombinedKeyName();
        }
        return combinedKeyName;
    }
    
    public NativeInput hold(long amount, ChronoUnit unit) {
        this.holdTimeNanos = Duration.of(amount, unit).toNanos();
        return this;
    }
    
    public NativeInput hold(Duration duration) {
        this.holdTimeNanos = duration.toNanos();
        return this;
    }
}
