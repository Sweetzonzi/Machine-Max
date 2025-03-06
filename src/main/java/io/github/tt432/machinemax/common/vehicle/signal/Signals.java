package io.github.tt432.machinemax.common.vehicle.signal;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;


public class Signals {
    //各发送者发来的信号
    public ConcurrentMap<ISignalSender, Object> signals = new ConcurrentHashMap<>();

    public void put(ISignalSender signalSender, Object signal){
        signals.put(signalSender, signal);
    }

    public Object getFirst(){
        if (signals.isEmpty()) return null;
        else return signals.values().iterator().next();
    }

    public ConcurrentMap<ISignalSender, Object> get(){
        return signals;
    }

    public Object get(ISignalSender signalSender){
        return signals.get(signalSender);
    }

    public void replaceAll(BiFunction<? super ISignalSender, ? super Object, ?> function){
        signals.replaceAll(function);
    }
}
