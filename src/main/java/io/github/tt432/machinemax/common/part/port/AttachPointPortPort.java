package io.github.tt432.machinemax.common.part.port;

import com.mojang.datafixers.util.Pair;
import io.github.tt432.machinemax.common.registry.PartPortType;
import io.github.tt432.machinemax.common.sloarphys.body.AbstractPartBody;
import io.github.tt432.machinemax.util.data.PosRot;

/**
 * 无任何条件检查的固定槽位，用于连接两个部件。
 */
public final class AttachPointPortPort extends FixedPartPort {
    public AttachPointPortPort(String name, AbstractPartBody slotOwnerBody, PosRot childElementAttachPoint) {
        this(Pair.of(PartPortType.ATTACH_POINT_PART_PORT.get(), slotOwnerBody), Pair.of(name, childElementAttachPoint));
    }

    public AttachPointPortPort(Pair<PartPortType, AbstractPartBody> typeBodyPair, Pair<String, PosRot> nameAttachPointPair) {
        super(typeBodyPair, nameAttachPointPair);
    }

}
