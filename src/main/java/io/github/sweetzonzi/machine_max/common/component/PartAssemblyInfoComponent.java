package io.github.sweetzonzi.machine_max.common.component;

import com.mojang.datafixers.util.Pair;
import lombok.Getter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Objects;

@Getter
public class PartAssemblyInfoComponent{
    @NotNull
    public final String variant;
    public final boolean hasConnector;
    @Nullable
    public final Pair<String, String> connector;
    @Nullable
    public final String connectorType;
    @Nullable
    public final Vector3f offset;
    @Nullable
    public final Quaternionf rotation;
//        Transform extraTransform//TODO: 实现自定义零件安装角

    public static final StreamCodec<RegistryFriendlyByteBuf, PartAssemblyInfoComponent> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull PartAssemblyInfoComponent decode(RegistryFriendlyByteBuf buffer) {
            String variant = buffer.readUtf();
            boolean hasConnector = buffer.readBoolean();
            if (hasConnector) {
                String subPartName = buffer.readUtf();
                String connectorName = buffer.readUtf();
                String connectorType = buffer.readUtf();
                Vector3f offset = buffer.readVector3f();
                Quaternionf rotation = buffer.readQuaternion();
                return new PartAssemblyInfoComponent(variant, Pair.of(subPartName, connectorName), connectorType, offset, rotation);
            } else return new PartAssemblyInfoComponent(variant);
        }

        @Override
        public void encode(@NotNull RegistryFriendlyByteBuf buffer, PartAssemblyInfoComponent value) {
            if (!value.hasConnector) {
                buffer.writeUtf(value.variant);
                buffer.writeBoolean(false);
            } else if (value.connectorType != null && value.offset != null && value.rotation != null && value.connector != null) {
                buffer.writeUtf(value.variant);
                buffer.writeBoolean(true);
                buffer.writeUtf(value.connector.getFirst());
                buffer.writeUtf(value.connector.getSecond());
                buffer.writeUtf(value.connectorType);
                buffer.writeVector3f(value.offset);
                buffer.writeQuaternion(value.rotation);
            } else throw new IllegalArgumentException("Invalid PartAssemblyInfoComponent");
        }
    };

    public PartAssemblyInfoComponent(@NotNull String variant, @NotNull Pair<String, String> connector, @NotNull String connectorType, @NotNull Vector3f offset, @NotNull Quaternionf rotation) {
        this.variant = variant;
        this.hasConnector = true;
        this.connector = connector;
        this.connectorType = connectorType;
        this.offset = offset;
        this.rotation = rotation;
    }

    public PartAssemblyInfoComponent(@NotNull String variant) {
        this.variant = variant;
        this.hasConnector = false;
        this.connector = null;
        this.connectorType = null;
        this.offset = null;
        this.rotation = null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PartAssemblyInfoComponent that)) return false;
        return hasConnector == that.hasConnector && Objects.equals(variant, that.variant) && Objects.equals(connector, that.connector) && Objects.equals(connectorType, that.connectorType) && Objects.equals(offset, that.offset) && Objects.equals(rotation, that.rotation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(variant, hasConnector, connector, connectorType, offset, rotation);
    }
}
