package io.github.sweetzonzi.machine_max.common.mech.vehicle.data;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.SimpleConnector;
import io.github.sweetzonzi.machine_max.util.data.PosRot;
import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;

@Getter
public class ConnectionData {
    public final String partUuidA;
    public final String subPartNameA;
    public final String advConnectorName;//要链接的连接点中存在非连接点的特殊接口时，必须放置于第一个位置
    public final PosRot posRotA;
    public final String partUuidS;
    public final String subPartNameS;
    public final String simpleConnectorName;
    public final PosRot posRotS;

    public static final Codec<ConnectionData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("partUuidA").forGetter(ConnectionData::getPartUuidA),
            Codec.STRING.fieldOf("subPartNameA").forGetter(ConnectionData::getSubPartNameA),
            Codec.STRING.fieldOf("advConnectorName").forGetter(ConnectionData::getAdvConnectorName),
            PosRot.CODEC.fieldOf("posRotA").forGetter(ConnectionData::getPosRotA),
            Codec.STRING.fieldOf("partUuidS").forGetter(ConnectionData::getPartUuidS),
            Codec.STRING.fieldOf("subPartNameS").forGetter(ConnectionData::getSubPartNameS),
            Codec.STRING.fieldOf("simplePointConnectorName").forGetter(ConnectionData::getSimpleConnectorName),
            PosRot.CODEC.fieldOf("posRotS").forGetter(ConnectionData::getPosRotS)
    ).apply(instance, ConnectionData::new));

    public static final StreamCodec<FriendlyByteBuf, ConnectionData> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull ConnectionData decode(FriendlyByteBuf buffer) {
            String partUuidA = buffer.readUtf();
            String subPartNameA = buffer.readUtf();
            String adv = buffer.readUtf();
            PosRot posRotA = PosRot.STREAM_CODEC.decode(buffer);
            String partUuidS = buffer.readUtf();
            String subPartNameS = buffer.readUtf();
            String simple = buffer.readUtf();
            PosRot posRotS = PosRot.STREAM_CODEC.decode(buffer);
            return new ConnectionData(partUuidA, subPartNameA, adv, posRotA, partUuidS, subPartNameS, simple, posRotS);
        }

        @Override
        public void encode(FriendlyByteBuf buffer, ConnectionData connectionData) {
            buffer.writeUtf(connectionData.partUuidA);
            buffer.writeUtf(connectionData.subPartNameA);
            buffer.writeUtf(connectionData.getAdvConnectorName());
            PosRot.STREAM_CODEC.encode(buffer, connectionData.posRotA);
            buffer.writeUtf(connectionData.partUuidS);
            buffer.writeUtf(connectionData.subPartNameS);
            buffer.writeUtf(connectionData.getSimpleConnectorName());
            PosRot.STREAM_CODEC.encode(buffer, connectionData.posRotS);
        }
    };

    public ConnectionData(String partUuidA, String subPartNameA, String advancedConnectorName, PosRot posRotA,
                          String partUuidS, String subPartNameS, String simpleConnectorName, PosRot posRotS) {
        this.partUuidA = partUuidA;
        this.subPartNameA = subPartNameA;
        this.advConnectorName = advancedConnectorName;
        this.posRotA = posRotA;
        this.partUuidS = partUuidS;
        this.subPartNameS = subPartNameS;
        this.simpleConnectorName = simpleConnectorName;
        this.posRotS = posRotS;
    }

    public ConnectionData(AbstractConnector connectorA, SimpleConnector connectorB){
        this.partUuidA = connectorA.subPart.part.uuid.toString();
        this.advConnectorName = connectorA.name;
        this.subPartNameA = connectorA.subPart.name;
        this.posRotA = new PosRot(connectorA.actualTransform);
        this.partUuidS = connectorB.subPart.part.uuid.toString();
        this.simpleConnectorName = connectorB.name;
        this.subPartNameS = connectorB.subPart.name;
        this.posRotS = new PosRot(connectorB.actualTransform);
    }

    public ConnectionData(Pair<AbstractConnector, SimpleConnector> connectorPair){
        this(connectorPair.getFirst(), connectorPair.getSecond());
    }

    @Override
    public String toString() {
        return "[" + Component.translatable(subPartNameA).getString()
                + "->" + Component.translatable(advConnectorName).getString()
                + ":" + Component.translatable(subPartNameS).getString()
                + "->" + Component.translatable(simpleConnectorName).getString() + "]";
    }
}
