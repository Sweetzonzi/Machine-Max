package io.github.sweetzonzi.machinemax.network.payload;

import cn.solarmoon.spark_core.animation.anim.play.ModelIndex;
import cn.solarmoon.spark_core.animation.model.origin.OLocator;
import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.common.attachment.LivingEntityEyesightAttachment;
import io.github.sweetzonzi.machinemax.common.component.PartAssemblyCacheComponent;
import io.github.sweetzonzi.machinemax.common.component.PartAssemblyInfoComponent;
import io.github.sweetzonzi.machinemax.common.entity.MMPartEntity;
import io.github.sweetzonzi.machinemax.common.item.prop.MMPartItem;
import io.github.sweetzonzi.machinemax.common.registry.MMAttachments;
import io.github.sweetzonzi.machinemax.common.registry.MMDataComponents;
import io.github.sweetzonzi.machinemax.common.registry.MMItems;
import io.github.sweetzonzi.machinemax.common.vehicle.PartType;
import io.github.sweetzonzi.machinemax.common.vehicle.attr.ConnectorAttr;
import io.github.sweetzonzi.machinemax.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machinemax.common.vehicle.connector.AttachPointConnector;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machinemax.external.js.hook.Hook;
import io.github.sweetzonzi.machinemax.mixin_interface.IEntityMixin;
import io.github.sweetzonzi.machinemax.util.data.KeyInputMapping;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;

public record ScriptablePayload(CompoundTag nbt) implements CustomPacketPayload {
    public static final Type<ScriptablePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "scriptable_payload"));
    public static final StreamCodec<FriendlyByteBuf, ScriptablePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.TRUSTED_COMPOUND_TAG,
            ScriptablePayload::nbt,
            ScriptablePayload::new
    );
    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void clientHandler(final ScriptablePayload payload, final IPayloadContext context) {
        Player player = context.player();
        Level level = player.level();
        Hook.run(payload.nbt, context, player, level);
    }

    public static void serverHandler(final ScriptablePayload payload, final IPayloadContext context) {
        Player player = context.player();
        Level level = player.level();
        Hook.run(payload.nbt, context, player, level);
        PacketDistributor.sendToPlayersInDimension((ServerLevel) player.level(), payload);
    }





}
