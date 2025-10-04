package io.github.sweetzonzi.machine_max.common.vehicle.data;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AttachPointConnector;
import io.netty.buffer.ByteBuf;
import lombok.Getter;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

@Getter
public class ConnectionData {
    public final String partUuidS;
    public final String subPartNameS;
    public final String specialConnectorName;//要链接的对接口中存在非连接点的特殊接口时，必须放置于第一个位置
    public final String partUuidA;
    public final String subPartNameA;
    public final String attachPointConnectorName;

    public static final Codec<ConnectionData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("partUuidS").forGetter(ConnectionData::getPartUuidS),
            Codec.STRING.fieldOf("subPartNameS").forGetter(ConnectionData::getSubPartNameS),
            Codec.STRING.fieldOf("specialConnectorName").forGetter(ConnectionData::getSpecialConnectorName),
            Codec.STRING.fieldOf("partUuidA").forGetter(ConnectionData::getPartUuidA),
            Codec.STRING.fieldOf("subPartNameA").forGetter(ConnectionData::getSubPartNameA),
            Codec.STRING.fieldOf("attachPointConnectorName").forGetter(ConnectionData::getAttachPointConnectorName)
    ).apply(instance, ConnectionData::new));

    public static final StreamCodec<ByteBuf, ConnectionData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ConnectionData::getPartUuidS,
            ByteBufCodecs.STRING_UTF8, ConnectionData::getSubPartNameS,
            ByteBufCodecs.STRING_UTF8, ConnectionData::getSpecialConnectorName,
            ByteBufCodecs.STRING_UTF8, ConnectionData::getPartUuidA,
            ByteBufCodecs.STRING_UTF8, ConnectionData::getSubPartNameA,
            ByteBufCodecs.STRING_UTF8, ConnectionData::getAttachPointConnectorName,
            ConnectionData::new
    );

    public ConnectionData(String partUuidS, String subPartNameS, String SpecialConnectorName, String partUuidA, String subPartNameA, String AttachPointConnectorName) {
        this.partUuidS = partUuidS;
        this.subPartNameS = subPartNameS;
        this.specialConnectorName = SpecialConnectorName;
        this.partUuidA = partUuidA;
        this.subPartNameA = subPartNameA;
        this.attachPointConnectorName = AttachPointConnectorName;
    }

    public ConnectionData(AbstractConnector connectorA, AttachPointConnector connectorB){
        this.partUuidS = connectorA.subPart.part.uuid.toString();
        this.specialConnectorName = connectorA.name;
        this.subPartNameS = connectorA.subPart.name;
        this.partUuidA = connectorB.subPart.part.uuid.toString();
        this.attachPointConnectorName = connectorB.name;
        this.subPartNameA = connectorB.subPart.name;
    }

    public ConnectionData(Pair<AbstractConnector, AttachPointConnector> connectorPair){
        this(connectorPair.getFirst(), connectorPair.getSecond());
    }
}
