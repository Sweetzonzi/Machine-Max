package io.github.sweetzonzi.machine_max.client.input;

import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

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

    public NativeInput(String keyName) {
        NativeKeyListener.pressKeyInputs.computeIfAbsent(keyName, k -> new HashSet<>()).add(this);
        this.keyName = keyName;
    }
    public NativeInput(String keyName, Integer pressTimes) {
        NativeKeyListener.pressKeyInputs.computeIfAbsent(keyName, k -> new HashSet<>()).add(this);
        this.keyName = keyName;
        this.pressTimes = pressTimes;
    }

    public NativeInput setEvent(Runnable event) {
        this.event = event;
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
        NativeKeyListener.pressKeyInputs.getOrDefault(keyName, new HashSet<>()).removeIf(nativeInput -> nativeInput.equals(this));
        NativeKeyListener.combineKeyInputs.getOrDefault(combinedKeyName, new HashSet<>()).removeIf(nativeInput -> nativeInput.equals(this));
        updateCombinedKeyName();
        NativeKeyListener.combineKeyInputs.computeIfAbsent(getCombinedKeyName(), k -> new HashSet<>()).add(this);
        return this;
    }

    private void updateCombinedKeyName() {
        combinedKeyName = getAllConnectedNodes()
                .filter(node -> node.getKeyName() != null && !node.getKeyName().isEmpty())
                .sorted((node1, node2) -> {
                    String key1 = node1.getKeyName();
                    String key2 = node2.getKeyName();
                    int lengthCompare = Integer.compare(key2.length(), key1.length());
                    if (lengthCompare != 0) {
                        return lengthCompare;
                    }
                    return key1.compareTo(key2);
                })
                .map(node -> node.getKeyName() + ":" + node.getPressTimes())
                .reduce((key1, key2) -> key1 + "-" + key2)
                .orElse("");
    }

    public String getCombinedKeyName() {
        if (combinedKeyName == null) {
            updateCombinedKeyName();
        }
        return combinedKeyName;
    }

    public static void main(String[] args) {
        NativeInput a = new NativeInput("A")
        .chain(new NativeInput("Shift"))
        .chain(new NativeInput("Ctrl")).setEvent(() -> {
                    System.out.println("SCA");
                });
        System.out.println(a.getCombinedKeyName()); // Shift-Ctrl-A
    }

}
