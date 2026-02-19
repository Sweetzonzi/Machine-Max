package io.github.sweetzonzi.machine_max.client.input;

import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
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

    public NativeInput(String keyName) {
        this.keyName = keyName;
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

    public NativeInput register() {
        NativeKeyListener.nativeInputs.computeIfAbsent(getCombinedKeyName(), k -> new HashSet<>()).add(this);
        return this;
    }

    public NativeInput chain(NativeInput next) {
        NativeInput last = this;
        while (last.next != null) {
            last = last.next;
        }
        last.setNext(next);
        
        // 更新combinedKeyName
        updateCombinedKeyName();
        
        return next;
    }

    private void updateCombinedKeyName() {
        combinedKeyName = getAllConnectedNodes()
                .map(NativeInput::getKeyName)
                .filter(keyName -> keyName != null && !keyName.isEmpty())
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
                }).register();
        System.out.println(a.getCombinedKeyName()); // Shift-Ctrl-A
    }

}
