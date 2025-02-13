package io.github.tt432.machinemax.common.vehicle.data;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.tt432.machinemax.common.vehicle.connector.AbstractConnector;
import io.github.tt432.machinemax.common.vehicle.connector.AttachPointConnector;
import lombok.Getter;

@Getter
public class ConnectionData {
    public final String PartUuidA;
    public final String SubPartNameA;
    public final String connectorNameA;
    public final String PartUuidB;
    public final String SubPartNameB;
    public final String connectorNameB;

    public static final Codec<ConnectionData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("PartUuidA").forGetter(ConnectionData::getPartUuidA),
            Codec.STRING.fieldOf("SubPartNameA").forGetter(ConnectionData::getSubPartNameA),
            Codec.STRING.fieldOf("connectorNameA").forGetter(ConnectionData::getConnectorNameA),
            Codec.STRING.fieldOf("PartUuidB").forGetter(ConnectionData::getPartUuidB),
            Codec.STRING.fieldOf("SubPartNameB").forGetter(ConnectionData::getSubPartNameB),
            Codec.STRING.fieldOf("connectorNameB").forGetter(ConnectionData::getConnectorNameB)
    ).apply(instance, ConnectionData::new));

    public ConnectionData(String partUuidA, String subPartNameA, String connectorNameA, String partUuidB, String subPartNameB, String connectorNameB) {
        this.PartUuidA = partUuidA;
        SubPartNameA = subPartNameA;
        this.connectorNameA = connectorNameA;
        this.PartUuidB = partUuidB;
        SubPartNameB = subPartNameB;
        this.connectorNameB = connectorNameB;
    }

    public ConnectionData(AbstractConnector connectorA, AttachPointConnector connectorB){
        this.PartUuidA = connectorA.subPart.part.uuid.toString();
        this.connectorNameA = connectorA.name;
        this.SubPartNameA = connectorA.subPart.name;
        this.PartUuidB = connectorB.subPart.part.uuid.toString();
        this.connectorNameB = connectorB.name;
        this.SubPartNameB = connectorB.subPart.name;
    }

    public ConnectionData(Pair<AbstractConnector, AttachPointConnector> connectorPair){
        this(connectorPair.getFirst(), connectorPair.getSecond());
    }
}
