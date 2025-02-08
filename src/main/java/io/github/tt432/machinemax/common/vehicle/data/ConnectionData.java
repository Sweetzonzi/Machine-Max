package io.github.tt432.machinemax.common.vehicle.data;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.tt432.machinemax.common.vehicle.connector.AbstractConnector;
import io.github.tt432.machinemax.common.vehicle.connector.AttachPointConnector;
import lombok.Getter;

@Getter
public class ConnectionData {
    public final String SubPartUuidA;
    public final String connectorNameA;
    public final String SubPartUuidB;
    public final String connectorNameB;

    public static final Codec<ConnectionData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("SubPartUuidA").forGetter(ConnectionData::getSubPartUuidA),
            Codec.STRING.fieldOf("connectorNameA").forGetter(ConnectionData::getConnectorNameA),
            Codec.STRING.fieldOf("SubPartUuidB").forGetter(ConnectionData::getSubPartUuidB),
            Codec.STRING.fieldOf("connectorNameB").forGetter(ConnectionData::getConnectorNameB)
    ).apply(instance, ConnectionData::new));

    public ConnectionData(String subPartUuidA, String connectorNameA, String subPartUuidB, String connectorNameB) {
        this.SubPartUuidA = subPartUuidA;
        this.connectorNameA = connectorNameA;
        this.SubPartUuidB = subPartUuidB;
        this.connectorNameB = connectorNameB;
    }

    public ConnectionData(AbstractConnector connectorA, AttachPointConnector connectorB){
        this.SubPartUuidA = connectorA.subPart.part.uuid.toString();
        this.connectorNameA = connectorA.name;
        this.SubPartUuidB = connectorB.subPart.part.uuid.toString();
        this.connectorNameB = connectorB.name;
    }

    public ConnectionData(Pair<AbstractConnector, AttachPointConnector> connectorPair){
        this.SubPartUuidA = connectorPair.getFirst().subPart.part.uuid.toString();
        this.connectorNameA = connectorPair.getFirst().name;
        this.SubPartUuidB = connectorPair.getSecond().subPart.part.uuid.toString();
        this.connectorNameB = connectorPair.getSecond().name;
    }
}
