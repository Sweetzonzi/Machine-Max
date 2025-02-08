package io.github.tt432.machinemax.common.component;

import com.mojang.serialization.Codec;
import io.github.tt432.machinemax.common.vehicle.port.AbstractPortPort;
import io.netty.buffer.ByteBuf;
import lombok.Setter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.StreamCodec;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Map;

public class PartPortIteratorComponent extends CompoundTag {
    @Setter
    private Iterator<Map.Entry<String, AbstractPortPort>> iterator;
    private AbstractPortPort currentPort;
    public static final StreamCodec<ByteBuf, PartPortIteratorComponent> STREAM_CODEC = StreamCodec.unit(
            new PartPortIteratorComponent(null)
    );
    public static final Codec<PartPortIteratorComponent> CODEC = Codec.unit(()->new PartPortIteratorComponent(null));
    public PartPortIteratorComponent(Iterator<Map.Entry<String, AbstractPortPort>> iterator) {
        this.iterator = iterator;
    }

    public AbstractPortPort getCurrentPort() {
        if (currentPort == null && iterator.hasNext()) return iterator.next().getValue();
        else return currentPort;
    }

    public AbstractPortPort getNextPort() {
        if (iterator.hasNext()) {
            currentPort = iterator.next().getValue();
            return currentPort;
        } else {
            iterator = null; // 重置迭代器
            return null;
        }
    }

    @Nullable
    public Iterator<Map.Entry<String, AbstractPortPort>> getIterator() {
        return iterator;
    }
}
