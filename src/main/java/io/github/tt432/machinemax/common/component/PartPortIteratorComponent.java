package io.github.tt432.machinemax.common.component;

import com.mojang.serialization.Codec;
import io.github.tt432.machinemax.common.vehicle.connector.AbstractConnector;
import io.netty.buffer.ByteBuf;
import lombok.Setter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.StreamCodec;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Map;

public class PartPortIteratorComponent extends CompoundTag {
    @Setter
    private Iterator<Map.Entry<String, AbstractConnector>> iterator;
    private AbstractConnector currentConnector;
    public static final StreamCodec<ByteBuf, PartPortIteratorComponent> STREAM_CODEC = StreamCodec.unit(
            new PartPortIteratorComponent(null)
    );
    public static final Codec<PartPortIteratorComponent> CODEC = Codec.unit(()->new PartPortIteratorComponent(null));
    public PartPortIteratorComponent(Iterator<Map.Entry<String, AbstractConnector>> iterator) {
        this.iterator = iterator;
    }

    public AbstractConnector getCurrentConnector() {
        if (currentConnector == null && iterator.hasNext()) return iterator.next().getValue();
        else return currentConnector;
    }

    public AbstractConnector getNextPort() {
        if (iterator.hasNext()) {
            currentConnector = iterator.next().getValue();
            return currentConnector;
        } else {
            iterator = null; // 重置迭代器
            return null;
        }
    }

    @Nullable
    public Iterator<Map.Entry<String, AbstractConnector>> getIterator() {
        return iterator;
    }
}
