package io.github.tt432.machinemax.common.vehicle;

import io.github.tt432.machinemax.common.vehicle.attr.SubPartAttr;

public class SubPart {
    public String name;
    public final Part part;
    public SubPart parent;
    public SubPart(String name, SubPartAttr attr, Part part){
        this.part = part;
        this.name = name;

    }
}
