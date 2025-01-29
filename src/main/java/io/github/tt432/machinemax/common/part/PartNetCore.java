package io.github.tt432.machinemax.common.part;

import com.google.common.graph.MutableNetwork;
import com.google.common.graph.NetworkBuilder;
import com.mojang.datafixers.util.Pair;
import io.github.tt432.machinemax.common.entity.CoreEntity;
import io.github.tt432.machinemax.common.part.port.AbstractPortPort;
import io.github.tt432.machinemax.common.part.port.FixedPartPort;

public class PartNetCore {
    //存储所有零件的连接关系
    public final MutableNetwork<AbstractPart, Pair<AbstractPortPort, FixedPartPort>> partNet = NetworkBuilder.undirected().allowsParallelEdges(true).build();
    public final CoreEntity coreEntity;

    public PartNetCore(CoreEntity coreEntity) {
        this.coreEntity = coreEntity;
    }
}
