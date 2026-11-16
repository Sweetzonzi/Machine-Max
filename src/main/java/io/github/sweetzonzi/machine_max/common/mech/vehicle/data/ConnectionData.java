package io.github.sweetzonzi.machine_max.common.mech.vehicle.data;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.SimpleConnector;
import io.github.sweetzonzi.machine_max.util.data.PosRot;
import lombok.Getter;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@Getter
public class ConnectionData {
    public final UUID partUuidA;
    public final String subPartNameA;
    public final String advConnectorName;//要链接的连接点中存在非连接点的特殊接口时，必须放置于第一个位置
    public final PosRot posRotA;
    public final UUID partUuidS;
    public final String subPartNameS;
    public final String simpleConnectorName;
    public final PosRot posRotS;

    /** JSON 存档/蓝图编解码：UUID 字段用 {@link UUIDUtil#STRING_CODEC} 落盘为字符串，兼容既有模板。 */
    public static final Codec<ConnectionData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("partUuidA").forGetter(ConnectionData::getPartUuidA),
            Codec.STRING.fieldOf("subPartNameA").forGetter(ConnectionData::getSubPartNameA),
            Codec.STRING.fieldOf("advConnectorName").forGetter(ConnectionData::getAdvConnectorName),
            PosRot.CODEC.fieldOf("posRotA").forGetter(ConnectionData::getPosRotA),
            UUIDUtil.STRING_CODEC.fieldOf("partUuidS").forGetter(ConnectionData::getPartUuidS),
            Codec.STRING.fieldOf("subPartNameS").forGetter(ConnectionData::getSubPartNameS),
            Codec.STRING.fieldOf("simplePointConnectorName").forGetter(ConnectionData::getSimpleConnectorName),
            PosRot.CODEC.fieldOf("posRotS").forGetter(ConnectionData::getPosRotS)
    ).apply(instance, ConnectionData::new));

    /**
     * 网络编码：部件 UUID 以双 long 传输（16 字节），字段本身即为 UUID，无需字符串转换。
     * JSON 落盘/蓝图走 {@link #CODEC}，保持字符串 UUID 不变。
     */
    public static final StreamCodec<FriendlyByteBuf, ConnectionData> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull ConnectionData decode(FriendlyByteBuf buffer) {
            UUID partUuidA = buffer.readUUID();
            String subPartNameA = buffer.readUtf();
            String adv = buffer.readUtf();
            PosRot posRotA = PosRot.STREAM_CODEC.decode(buffer);
            UUID partUuidS = buffer.readUUID();
            String subPartNameS = buffer.readUtf();
            String simple = buffer.readUtf();
            PosRot posRotS = PosRot.STREAM_CODEC.decode(buffer);
            return new ConnectionData(partUuidA, subPartNameA, adv, posRotA, partUuidS, subPartNameS, simple, posRotS);
        }

        @Override
        public void encode(FriendlyByteBuf buffer, ConnectionData connectionData) {
            buffer.writeUUID(connectionData.partUuidA);
            buffer.writeUtf(connectionData.subPartNameA);
            buffer.writeUtf(connectionData.getAdvConnectorName());
            PosRot.STREAM_CODEC.encode(buffer, connectionData.posRotA);
            buffer.writeUUID(connectionData.partUuidS);
            buffer.writeUtf(connectionData.subPartNameS);
            buffer.writeUtf(connectionData.getSimpleConnectorName());
            PosRot.STREAM_CODEC.encode(buffer, connectionData.posRotS);
        }
    };

    public ConnectionData(UUID partUuidA, String subPartNameA, String advancedConnectorName, PosRot posRotA,
                          UUID partUuidS, String subPartNameS, String simpleConnectorName, PosRot posRotS) {
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
        this.partUuidA = connectorA.subPart.part.uuid;
        this.advConnectorName = connectorA.name;
        this.subPartNameA = connectorA.subPart.name;
        this.posRotA = new PosRot(connectorA.actualTransform);
        this.partUuidS = connectorB.subPart.part.uuid;
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
