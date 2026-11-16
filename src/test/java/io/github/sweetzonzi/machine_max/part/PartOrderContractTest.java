package io.github.sweetzonzi.machine_max.part;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jme3.math.Vector3f;
import com.mojang.serialization.JsonOps;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.BlueprintMeta;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.RegularPartData;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.SubPartData;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.VehicleData;
import io.github.sweetzonzi.machine_max.util.data.PosRotVelVel;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 零件安装序的持久化与拷贝契约。
 *
 * <p>{@code parts} 只负责按键查找，顺序由 {@code part_order} 显式承载；JSON 对象与 NBT 复合标签的
 * 键序都不是可依赖的持久契约。这组用例固定的是"顺序能被写出来、读回来、并在拷贝中透传"，
 * 不固定任何具体内容包的零件构成。</p>
 */
class PartOrderContractTest {

    /** 故意不是 UUID 升序：顺序若被重排，断言立刻失败。 */
    private static final List<String> INSTALL_ORDER = List.of(
            "00000000-0000-0000-0000-0000000000c3",
            "00000000-0000-0000-0000-00000000000a",
            "00000000-0000-0000-0000-0000000000b1"
    );

    private static RegularPartData part(String uuid) {
        SubPartData subPart = new SubPartData(1, 1f,
                new PosRotVelVel(new Vector3f(), new Quaternionf(), new Vector3f(), new Vector3f()),
                Map.of(), Map.of());
        return new RegularPartData(ResourceLocation.parse("test:part"), "part", uuid, "default",
                1f, 1f, 99999, false, false, 0, "default", Map.of("main", subPart));
    }

    private static VehicleData vehicle(List<String> order) {
        Map<String, RegularPartData> parts = new LinkedHashMap<>();
        for (String uuid : order) parts.put(uuid, part(uuid));
        return new VehicleData("ordered", UUID.randomUUID(), Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 1f,
                parts, List.of(), List.of(), BlueprintMeta.EMPTY);
    }

    private static JsonObject encode(VehicleData data) {
        return VehicleData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow().getAsJsonObject();
    }

    private static List<String> keyOrder(Map<String, RegularPartData> parts) {
        return new ArrayList<>(parts.keySet());
    }

    @Test
    void jsonRoundTripKeepsInstallOrder() {
        JsonObject json = encode(vehicle(INSTALL_ORDER));
        assertEquals(INSTALL_ORDER.size(), json.getAsJsonArray("part_order").size());

        VehicleData decoded = VehicleData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(INSTALL_ORDER, keyOrder(decoded.parts));
    }

    @Test
    void dataWithoutPartOrderFallsBackToUuidOrder() {
        JsonObject json = encode(vehicle(INSTALL_ORDER));
        json.remove("part_order");

        VehicleData decoded = VehicleData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        List<String> expected = new ArrayList<>(INSTALL_ORDER);
        Collections.sort(expected);
        assertEquals(expected, keyOrder(decoded.parts));
    }

    @Test
    void partOrderMustMatchThePartSetExactly() {
        // 重复项
        assertRejected(orderWith(INSTALL_ORDER.get(0), INSTALL_ORDER.get(0), INSTALL_ORDER.get(1)));
        // 引用了不存在的部件
        assertRejected(orderWith(INSTALL_ORDER.get(0), INSTALL_ORDER.get(1),
                "00000000-0000-0000-0000-0000000000ff"));
        // 缺项（长度与 parts 条目数不符）
        assertRejected(orderWith(INSTALL_ORDER.get(0), INSTALL_ORDER.get(1)));
    }

    private static void assertRejected(JsonArray partOrder) {
        JsonObject json = encode(vehicle(INSTALL_ORDER));
        json.add("part_order", partOrder);
        assertTrue(VehicleData.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent(),
                "part_order 与 parts 键集合不一致时必须拒绝: " + partOrder);
    }

    private static JsonArray orderWith(String... uuids) {
        JsonArray array = new JsonArray();
        for (String uuid : uuids) array.add(uuid);
        return array;
    }

    @Test
    void coordinateAndIdentityCopiesPreserveInstallOrder() {
        VehicleData data = vehicle(INSTALL_ORDER);
        List<VehicleData> copies = List.of(
                data.rebased(),
                data.normalized(),
                data.withNewName("renamed"),
                data.withNewUUID(UUID.randomUUID()),
                data.withMeta(BlueprintMeta.EMPTY)
        );
        for (VehicleData copy : copies) {
            assertEquals(INSTALL_ORDER, keyOrder(copy.parts));
        }
    }

    @Test
    void wireFormatKeepsInstallOrder() {
        VehicleData data = vehicle(INSTALL_ORDER);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            VehicleData.STREAM_CODEC.encode(buffer, data);
            buffer.readerIndex(0);
            VehicleData decoded = VehicleData.STREAM_CODEC.decode(buffer);
            assertEquals(INSTALL_ORDER, keyOrder(decoded.parts));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }
}
