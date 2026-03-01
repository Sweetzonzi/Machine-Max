package io.github.sweetzonzi.machine_max.client.input;

import lombok.Getter;
import lombok.Setter;

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
 * new ChainAbleInput("a").setEvent(() -> System.out.println("A pressed"));
 *
 * // 创建组合键事件
 * new ChainAbleInput("a")
 *         .chain(new ChainAbleInput("ctrl"))
 *         .setEvent(() -> System.out.println("Ctrl+A pressed"));
 *
 * // 创建长按事件
 * new ChainAbleInput("a")
 *         .hold(1, ChronoUnit.SECONDS)
 *         .setEvent(() -> System.out.println("A held for 1 second"));
 * </pre>
 *
 * @author ArcherLee
 * @version 1.0.0
 */
public class ChainAbleInput {
    @Getter
    private ChainAbleInput prev;
    @Getter
    private ChainAbleInput next;
    @Setter
    @Getter
    private String keyName;


    @Getter
    private InputContext context;

    @Getter
    @Setter
    private boolean lazyRegister = false;

    private String combinedKeyName;


    @Getter
    private Runnable event;

    @Getter
    private Integer pressTimes = 1;

    @Getter
    private long holdTimeNanos = 1;

    public ChainAbleInput(String keyName) {
        keyName = keyName.toLowerCase();
        InputListener.pressKeyInputs.computeIfAbsent(keyName, k -> new ArrayList<>()).add(this);
        this.keyName = keyName;
    }
    public ChainAbleInput(int glfwKey) {
        GLFWKeyContext glfwKeyContext = new GLFWKeyContext(glfwKey);
        context = glfwKeyContext;
        keyName = glfwKeyContext.getKeyText();
        // 若名称获取失败则走懒注册机制
        if (keyName == null) {
            lazyRegister = true;
            InputListener.lazyRegisterInputs.add(this);
        } else {
            InputListener.pressKeyInputs.computeIfAbsent(keyName, k -> new ArrayList<>()).add(this);
        }
    }

//    public ChainAbleInput(XInputButton button) {
//        if (!NativeKeyListener.PLATFORM.equals(Platform.Os.Windows)) return;
//        keyName = NativeKeyListener.xInputButton(button).toLowerCase();
//        NativeKeyListener.pressKeyInputs.computeIfAbsent(keyName, k -> new ArrayList<>()).add(this);
//    }
    public ChainAbleInput(String keyName, Integer pressTimes) {
        keyName = keyName.toLowerCase();
        InputListener.pressKeyInputs.computeIfAbsent(keyName, k -> new ArrayList<>()).add(this);
        this.keyName = keyName;
        this.pressTimes = pressTimes;
    }

    public ChainAbleInput setEvent(Runnable event) {
        this.event = event;
        return this;
    }

    public ChainAbleInput setEventOnce(Runnable event) {
        if (this.event == null) this.event = event;
        return this;
    }

    private void setPrev(ChainAbleInput prev) {
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

    private void setNext(ChainAbleInput next) {
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

    private boolean wouldCreateCycle(ChainAbleInput node) {
        // 检查从node出发是否能回到当前节点
        ChainAbleInput current = node;
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

    public Stream<ChainAbleInput> getAllConnectedNodes() {
        Set<ChainAbleInput> visited = new HashSet<>();
        collectNodes(this, visited);
        return visited.stream();
    }

    private void collectNodes(ChainAbleInput node, Set<ChainAbleInput> visited) {
        if (node == null || visited.contains(node)) {
            return;
        }
        visited.add(node);
        collectNodes(node.prev, visited);
        collectNodes(node.next, visited);
    }


    public ChainAbleInput chain(ChainAbleInput next) {
        ChainAbleInput last = this;
        while (last.next != null) {
            last = last.next;
        }
        // 检查 next.keyName 是否为 null
        if (next.keyName != null && last.keyName != null && next.keyName.equals(last.keyName)) {
            last.pressTimes += next.pressTimes;
            return last;
        }
        last.setNext(next);

        // 更新combinedKey
        if (keyName != null) {
            InputListener.pressKeyInputs.getOrDefault(keyName, new ArrayList<>()).removeIf(nativeInput -> nativeInput.equals(this));
        }
        String oldCombinedKeyName = getCombinedKeyName();
        if (oldCombinedKeyName != null && !oldCombinedKeyName.isEmpty()) {
            InputListener.combineKeyInputsMap.getOrDefault(oldCombinedKeyName, new ArrayList<>()).remove(this);
        }
        updateCombinedKeyName();
        String newCombinedKeyName = getCombinedKeyName();
        if (newCombinedKeyName != null && !newCombinedKeyName.isEmpty()) {
            InputListener.combineKeyInputsMap.computeIfAbsent(newCombinedKeyName, k -> new ArrayList<>()).add(this);
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

    public ChainAbleInput hold(long amount, ChronoUnit unit) {
        this.holdTimeNanos = Duration.of(amount, unit).toNanos();
        return this;
    }

    public ChainAbleInput hold(Duration duration) {
        this.holdTimeNanos = duration.toNanos();
        return this;
    }
}
