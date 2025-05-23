package io.github.sweetzonzi.machinemax.common.vehicle.subsystem;

import io.github.sweetzonzi.machinemax.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machinemax.common.vehicle.attr.subsystem.CameraSubsystemAttr;

import java.util.List;
import java.util.Map;

public class CameraSubsystem extends AbstractSubsystem{
    public final CameraSubsystemAttr attr;

    public CameraSubsystem(ISubsystemHost owner, String name, CameraSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
    }

    /**
     * 在此填入各个信号名对应的接收者名列表，用于自动组织信号传输关系。<p>
     * Return a map of signal names to a list of receiver names here, to automatically organize signal transfer.
     *
     * @return 信号名称->接收者名称列表 Map of signal names to a list of receiver names.
     */
    @Override
    public Map<String, List<String>> getTargetNames() {
        return Map.of();
    }
}
