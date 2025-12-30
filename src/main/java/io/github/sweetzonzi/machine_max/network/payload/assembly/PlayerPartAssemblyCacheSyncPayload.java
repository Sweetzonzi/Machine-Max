package io.github.sweetzonzi.machine_max.network.payload.assembly;

import com.mojang.datafixers.util.Pair;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.attachment.VehicleAssemblyAttachment;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.vehicle.PartType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public record PlayerPartAssemblyCacheSyncPayload(
        @NotNull ResourceLocation registryKey,
        String variant,
        String subPart,
        String connector,
        float attachRotation,
        Quaternionf rotation,
        Vector3f offset
) implements CustomPacketPayload {
    public static final Type<PlayerPartAssemblyCacheSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "player_part_assembly_sync_payload")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerPartAssemblyCacheSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(RegistryFriendlyByteBuf buffer, PlayerPartAssemblyCacheSyncPayload payload) {
            buffer.writeResourceLocation(payload.registryKey);
            if (payload.subPart.isEmpty() || payload.connector.isEmpty()) {
                buffer.writeBoolean(false);
            } else {
                buffer.writeBoolean(true);
                buffer.writeUtf(payload.variant);
                buffer.writeUtf(payload.subPart);
                buffer.writeUtf(payload.connector);
                buffer.writeFloat(payload.attachRotation);
                buffer.writeQuaternion(payload.rotation);
                buffer.writeVector3f(payload.offset);
            }
        }

        @Override
        public @NotNull PlayerPartAssemblyCacheSyncPayload decode(RegistryFriendlyByteBuf buffer) {
            ResourceLocation registryKey = buffer.readResourceLocation();
            boolean valid = buffer.readBoolean();
            String variant = valid ? buffer.readUtf() : null;
            String subPart = valid ? buffer.readUtf() : null;
            String connector = valid ? buffer.readUtf() : null;
            float attachRotation = valid ? buffer.readFloat() : 0;
            Quaternionf quaternion = valid ? buffer.readQuaternion() : new Quaternionf();
            Vector3f offset = valid ? buffer.readVector3f() : new Vector3f();
            return new PlayerPartAssemblyCacheSyncPayload(registryKey, variant, subPart, connector, attachRotation, quaternion, offset);
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PlayerPartAssemblyCacheSyncPayload payload, IPayloadContext context) {
        Player player = context.player();
        Level level = player.level();
        context.enqueueWork(() -> {
            if (!player.hasData(MMAttachments.getVEHICLE_ASSEMBLY())) {
                player.setData(MMAttachments.getVEHICLE_ASSEMBLY(), new VehicleAssemblyAttachment(player));
            }
            var cache = player.getData(MMAttachments.getVEHICLE_ASSEMBLY());
            cache.setPartType(PartType.get(level, payload.registryKey));
            if (cache.getPartType() != null && payload.variant != null && payload.connector != null) {
                while (!payload.variant.equals(cache.getVariantName())) {
                    cache.getNextVariant();
                }
                Pair<String, String> connectorName = Pair.of(payload.subPart, payload.connector);
                while (!connectorName.equals(cache.getConnectorName())) {
                    cache.getNextConnector();
                }
                cache.setAttachRotation(payload.attachRotation);
                cache.setQuaternion(payload.rotation);
                cache.setOffset(payload.offset);
            }
        });
    }
}
