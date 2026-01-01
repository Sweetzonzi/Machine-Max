package io.github.sweetzonzi.machine_max.common.vehicle.data;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AttachPointConnector;
import io.github.sweetzonzi.machine_max.util.data.PosRot;
import io.netty.buffer.ByteBuf;
import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;

@Getter
public class ConnectionData {
    public final String partUuidS;
    public final String subPartNameS;
    public final String specialConnectorName;//要链接的连接点中存在非连接点的特殊接口时，必须放置于第一个位置
    public final PosRot posRotS;
    public final String partUuidA;
    public final String subPartNameA;
    public final String attachPointConnectorName;
    public final PosRot posRotA;

    public static final Codec<ConnectionData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("partUuidS").forGetter(ConnectionData::getPartUuidS),
            Codec.STRING.fieldOf("subPartNameS").forGetter(ConnectionData::getSubPartNameS),
            Codec.STRING.fieldOf("specialConnectorName").forGetter(ConnectionData::getSpecialConnectorName),
            PosRot.CODEC.fieldOf("posRotS").forGetter(ConnectionData::getPosRotS),
            Codec.STRING.fieldOf("partUuidA").forGetter(ConnectionData::getPartUuidA),
            Codec.STRING.fieldOf("subPartNameA").forGetter(ConnectionData::getSubPartNameA),
            Codec.STRING.fieldOf("attachPointConnectorName").forGetter(ConnectionData::getAttachPointConnectorName),
            PosRot.CODEC.fieldOf("posRotA").forGetter(ConnectionData::getPosRotA)
    ).apply(instance, ConnectionData::new));

    public static final StreamCodec<FriendlyByteBuf, ConnectionData> STREAM_CODEC = new StreamCodec<FriendlyByteBuf, ConnectionData>() {
        @Override
        public @NotNull ConnectionData decode(FriendlyByteBuf buffer) {
            String partUuidS = buffer.readUtf();
            String subPartNameS = buffer.readUtf();
            String specialConnectorName = buffer.readUtf();
            PosRot posRotS = PosRot.STREAM_CODEC.decode(buffer);
            String partUuidA = buffer.readUtf();
            String subPartNameA = buffer.readUtf();
            String attachPointConnectorName = buffer.readUtf();
            PosRot posRotA = PosRot.STREAM_CODEC.decode(buffer);
            return new ConnectionData(partUuidS, subPartNameS, specialConnectorName, posRotS, partUuidA, subPartNameA, attachPointConnectorName, posRotA);
        }

        @Override
        public void encode(FriendlyByteBuf buffer, ConnectionData connectionData) {
            buffer.writeUtf(connectionData.partUuidS);
            buffer.writeUtf(connectionData.subPartNameS);
            buffer.writeUtf(connectionData.specialConnectorName);
            PosRot.STREAM_CODEC.encode(buffer, connectionData.posRotS);
            buffer.writeUtf(connectionData.partUuidA);
            buffer.writeUtf(connectionData.subPartNameA);
            buffer.writeUtf(connectionData.attachPointConnectorName);
            PosRot.STREAM_CODEC.encode(buffer, connectionData.posRotA);
        }
    };

    public ConnectionData(String partUuidS, String subPartNameS, String SpecialConnectorName, PosRot posRotS,
                          String partUuidA, String subPartNameA, String AttachPointConnectorName, PosRot posRotA) {
        this.partUuidS = partUuidS;
        this.subPartNameS = subPartNameS;
        this.specialConnectorName = SpecialConnectorName;
        this.posRotS = posRotS;
        this.partUuidA = partUuidA;
        this.subPartNameA = subPartNameA;
        this.attachPointConnectorName = AttachPointConnectorName;
        this.posRotA = posRotA;
    }

    public ConnectionData(AbstractConnector connectorA, AttachPointConnector connectorB){
        this.partUuidS = connectorA.subPart.part.uuid.toString();
        this.specialConnectorName = connectorA.name;
        this.subPartNameS = connectorA.subPart.name;
        this.posRotS = new PosRot(connectorA.actualTransform);
        this.partUuidA = connectorB.subPart.part.uuid.toString();
        this.attachPointConnectorName = connectorB.name;
        this.subPartNameA = connectorB.subPart.name;
        this.posRotA = new PosRot(connectorB.actualTransform);
    }

    public ConnectionData(Pair<AbstractConnector, AttachPointConnector> connectorPair){
        this(connectorPair.getFirst(), connectorPair.getSecond());
    }
}
