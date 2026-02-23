package io.github.sweetzonzi.machine_max.common.vehicle.subsystem;

import io.github.sweetzonzi.machine_max.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.dynamic_attr.BasicSubsystemDynamicAttr;

import java.util.List;
import java.util.Map;

public class BasicSubsystem extends AbstractSubsystem {

    public BasicSubsystem(ISubsystemHost owner, String name, BasicSubsystemDynamicAttr attr) {
        super(owner, name, attr);
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        return Map.of(); // 不涉及信号传输，无目标
    }
}