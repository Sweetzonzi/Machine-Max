package io.github.tt432.machinemax.common.vehicle.signal;

import java.util.concurrent.ConcurrentHashMap;


public class SignalChannel extends ConcurrentHashMap<ISignalSender, Object>{
    public Object getFirst(){
        if (values().iterator().hasNext()) return values().iterator().next();
        else return null;
    }
}
