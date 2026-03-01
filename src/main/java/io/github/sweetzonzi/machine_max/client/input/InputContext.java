package io.github.sweetzonzi.machine_max.client.input;

import lombok.Getter;

public abstract class InputContext<T> {
    @Getter
    protected T data;
    public InputContext(T data) {
        this.data = data;
    }
    public String getKeyText(T args){
        return null;
    }

    public abstract String getKeyText();
}
