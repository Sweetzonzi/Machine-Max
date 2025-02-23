package io.github.tt432.machinemax.common.vehicle.data;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.tt432.machinemax.common.vehicle.connector.AbstractConnector;
import io.github.tt432.machinemax.common.vehicle.connector.AttachPointConnector;
import lombok.Getter;

@Getter
public class ConnectionData {
    public final String PartUuidS;
    public final String SubPartNameS;
    public final String SpecialConnectorName;//要链接的对接口中存在非连接点的特殊接口时，必须放置于第一个位置
    public final String PartUuidA;
    public final String SubPartNameA;
    public final String AttachPointConnectorName;

    public static final Codec<ConnectionData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("PartUuidS").forGetter(ConnectionData::getPartUuidS),
            Codec.STRING.fieldOf("SubPartNameS").forGetter(ConnectionData::getSubPartNameS),
            Codec.STRING.fieldOf("SpecialConnectorName").forGetter(ConnectionData::getSpecialConnectorName),
            Codec.STRING.fieldOf("PartUuidA").forGetter(ConnectionData::getPartUuidA),
            Codec.STRING.fieldOf("SubPartNameA").forGetter(ConnectionData::getSubPartNameA),
            Codec.STRING.fieldOf("AttachPointConnectorName").forGetter(ConnectionData::getAttachPointConnectorName)
    ).apply(instance, ConnectionData::new));

    public ConnectionData(String partUuidS, String subPartNameS, String SpecialConnectorName, String partUuidA, String subPartNameA, String AttachPointConnectorName) {
        this.PartUuidS = partUuidS;
        SubPartNameS = subPartNameS;
        this.SpecialConnectorName = SpecialConnectorName;
        this.PartUuidA = partUuidA;
        SubPartNameA = subPartNameA;
        this.AttachPointConnectorName = AttachPointConnectorName;
    }

    public ConnectionData(AbstractConnector connectorA, AttachPointConnector connectorB){
        this.PartUuidS = connectorA.subPart.part.uuid.toString();
        this.SpecialConnectorName = connectorA.name;
        this.SubPartNameS = connectorA.subPart.name;
        this.PartUuidA = connectorB.subPart.part.uuid.toString();
        this.AttachPointConnectorName = connectorB.name;
        this.SubPartNameA = connectorB.subPart.name;
    }

    public ConnectionData(Pair<AbstractConnector, AttachPointConnector> connectorPair){
        this(connectorPair.getFirst(), connectorPair.getSecond());
    }
}
