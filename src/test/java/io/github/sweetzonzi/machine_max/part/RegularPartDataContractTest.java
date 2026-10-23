package io.github.sweetzonzi.machine_max.part;

import com.google.gson.JsonParser;
import com.jme3.math.Vector3f;
import com.mojang.serialization.JsonOps;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.*;
import io.github.sweetzonzi.machine_max.util.data.PosRotVelVel;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RegularPartDataContractTest {
    static RegularPartData data() {
        CompoundTag resources = new CompoundTag();
        resources.putInt("stored_energy", 734);
        CompoundTag connector = new CompoundTag();
        connector.putFloat("integrity", 0.5f);
        var subPart = new SubPartData(123, 0.3f,
                new PosRotVelVel(new Vector3f(1, 2, 3), new Quaternionf(), new Vector3f(4, 5, 6), new Vector3f()),
                Map.of("joint", connector), Map.of("battery", resources));
        return new RegularPartData(ResourceLocation.parse("test:old_regular"), "legacy", "00000000-0000-0000-0000-000000000001",
                "alternate", 0.6f, 0.3f, 7, false, "paint", Map.of("main", subPart));
    }

    @Test void legacyMissingFieldsRetainHistoricalDefaults() {
        var json = JsonParser.parseString("""
                {"registry_key":"test:legacy","name":"legacy","uuid":"00000000-0000-0000-0000-000000000001",
                 "variant":"default","sub_parts":{}}
                """);
        var saved = RegularPartData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(1f, saved.assemblingProgress);
        assertEquals(1f, saved.sharedDurabilityRatio);
        assertEquals(99999, saved.materialAssemblingProgress);
        assertTrue(saved.renderWireframe);
        assertEquals("default", saved.textureName);
    }

    @Test void jsonAndBinaryPreserveAllStateWithoutChangingWireLayout() {
        var original = data();
        var encoded = RegularPartData.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        var decoded = RegularPartData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(encoded, RegularPartData.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow());
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            RegularPartData.STREAM_CODEC.encode(buffer, original);
            assertEquals(original.registryKey, buffer.readResourceLocation());
            assertEquals(original.name, buffer.readUtf());
            assertEquals(original.uuid, buffer.readUtf());
            assertEquals(original.variant, buffer.readUtf());
            assertEquals(original.assemblingProgress, buffer.readFloat());
            assertEquals(original.sharedDurabilityRatio, buffer.readFloat());
            assertEquals(original.materialAssemblingProgress, buffer.readInt());
            assertEquals(original.renderWireframe, buffer.readBoolean());
            assertEquals(original.textureName, buffer.readUtf());
            assertEquals(original.subParts.get("main").subsystemData,
                    SubPartData.MAP_STREAM_CODEC.decode(buffer).get("main").subsystemData);
            assertEquals(0, buffer.readableBytes());
            buffer.readerIndex(0);
            var binary = RegularPartData.STREAM_CODEC.decode(buffer);
            assertEquals(encoded, RegularPartData.CODEC.encodeStart(JsonOps.INSTANCE, binary).getOrThrow());
        } finally { buffer.release(); }
    }

    @Test void normalizationPreservesPartResourcesAndInputData() {
        var saved = data();
        var originalJson = RegularPartData.CODEC.encodeStart(JsonOps.INSTANCE, saved).getOrThrow();
        VehicleData vehicle = new VehicleData("legacy", UUID.randomUUID().toString(), new Vec3(10, 20, 30),
                Vec3.ZERO, Vec3.ZERO, 0.2f, Map.of(saved.uuid, saved), List.of(), List.of(), BlueprintMeta.EMPTY);
        var normalized = vehicle.normalized();
        var result = normalized.parts.get(saved.uuid);
        assertEquals(1f, normalized.hpRatio);
        assertEquals(saved.materialAssemblingProgress, result.materialAssemblingProgress);
        assertEquals(saved.assemblingProgress, result.assemblingProgress);
        assertEquals(saved.textureName, result.textureName);
        assertEquals(saved.subParts.get("main").subsystemData, result.subParts.get("main").subsystemData);
        assertEquals(originalJson, RegularPartData.CODEC.encodeStart(JsonOps.INSTANCE, saved).getOrThrow());
    }
}
