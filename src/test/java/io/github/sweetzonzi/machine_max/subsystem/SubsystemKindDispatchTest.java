package io.github.sweetzonzi.machine_max.subsystem;

import cn.solarmoon.spark_core.pack.diagnostic.PackContentException;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemKinds;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.AbstractSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.BasicSubsystemDynamicAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.AbstractSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 子系统类型登记与分派 Codec 的契约。
 *
 * <p>覆盖：内置登记两侧配对、类型身份从登记表派生、未知类型与非法 {@code type} 在解码入口
 * 被拒绝、型号缺失与型号类型不匹配在解码构造期被拒绝、编码回写权威类型 ID。</p>
 *
 * <p>断言只统计 {@code machine_max:} 命名空间的内置 ID；探针型号使用
 * {@code machine_max_test:}，不依赖真实内容包。</p>
 */
class SubsystemKindDispatchTest {

    /** 已开放的内置类型 ID，用于核对登记集合而不是内容构成。 */
    static final List<String> BUILTIN_IDS = List.of(
            "basic", "seat", "item_storage", "engine", "motor", "car_controller",
            "motorbike_controller", "track_controller", "track_drive", "gearbox", "transmission",
            "wheel_driver", "turret_driver", "lighting",
            "launcher", "ammo_loader", "ammo_rack", "weapon_controller", "camera", "sight",
            "regen_ammo_rack");

    /** 字段契约未定稿、因此没有登记的开发中类型。 */
    static final List<String> WIP_IDS = List.of("joint_driver", "scriptable", "battery");

    static final ResourceLocation PROBE_DEFINITION = ResourceLocation.parse("machine_max_test:probe");

    @AfterEach
    void clearProbeDefinition() {
        MMDynamicRes.STATIC_SUBSYSTEM_ATTRS.remove(PROBE_DEFINITION);
    }

    @Test
    void builtinTypesAreRegisteredOnBothCodecSlots() {
        assertEquals(BUILTIN_IDS.size(), new HashSet<>(BUILTIN_IDS).size(), "内置类型 ID 不应重复");
        for (String name : BUILTIN_IDS) {
            ResourceLocation id = ResourceLocation.parse("machine_max:" + name);
            SubsystemKinds.Kind kind = SubsystemKinds.get(id, SubsystemKinds.Slot.INSTALL);
            assertNotNull(kind.installCodec(), name);
            assertNotNull(kind.staticCodec(), name);
            assertEquals(id, SubsystemKinds.nameOf(kind.installCodec(), SubsystemKinds.Slot.INSTALL), name);
            assertEquals(id, SubsystemKinds.nameOf(kind.staticCodec(), SubsystemKinds.Slot.STATIC), name);
        }
    }

    @Test
    void wipTypesAreNotRegistered() {
        for (String name : WIP_IDS) {
            ResourceLocation id = ResourceLocation.parse("machine_max:" + name);
            assertThrows(PackContentException.class,
                    () -> SubsystemKinds.get(id, SubsystemKinds.Slot.INSTALL), name);
            assertThrows(PackContentException.class,
                    () -> parseInstall("{\"type\":\"" + id + "\"}"), name);
        }
    }

    @Test
    void unknownTypeIsAContentError() {
        PackContentException failure = assertThrows(PackContentException.class,
                () -> parseInstall("{\"type\":\"machine_max:not_a_type\"}"));
        assertTrue(failure.getUserMessage().getString().contains("machine_max:not_a_type"),
                failure.getUserMessage().getString());
    }

    @Test
    void typeFieldMustBePresentAndNonEmptyString() {
        for (String json : List.of("{}", "{\"type\":null}", "{\"type\":\"\"}", "{\"type\":42}", "[]")) {
            assertThrows(PackContentException.class, () -> parseInstall(json), "install: " + json);
            assertThrows(PackContentException.class, () -> parseStatic(json), "static: " + json);
        }
    }

    @Test
    void codecMustBeRegisteredInItsOwnSlot() {
        assertThrows(PackContentException.class,
                () -> SubsystemKinds.nameOf(BasicSubsystemDynamicAttr.CODEC, SubsystemKinds.Slot.STATIC));
    }

    @Test
    void installAttributeBindsDefinitionAndRoundTrips() {
        MMDynamicRes.STATIC_SUBSYSTEM_ATTRS.put(PROBE_DEFINITION,
                parseStatic("{\"type\":\"machine_max:basic\",\"basic_durability\":12}"));

        AbstractSubsystemAttr attr = parseInstall(
                "{\"type\":\"machine_max:basic\",\"definition\":\"" + PROBE_DEFINITION + "\"}");

        assertInstanceOf(BasicSubsystemDynamicAttr.class, attr);
        assertEquals(ResourceLocation.parse("machine_max:basic"), attr.getType());
        assertEquals(12.0f, attr.getBasicDurability());

        var encoded = AbstractSubsystemAttr.CODEC.encodeStart(JsonOps.INSTANCE, attr).getOrThrow().getAsJsonObject();
        assertEquals("machine_max:basic", encoded.get("type").getAsString());
        assertEquals(PROBE_DEFINITION.toString(), encoded.get("definition").getAsString());
    }

    @Test
    void definitionOfAnotherTypeIsAContentError() {
        MMDynamicRes.STATIC_SUBSYSTEM_ATTRS.put(PROBE_DEFINITION,
                parseStatic("{\"type\":\"machine_max:item_storage\"}"));

        PackContentException failure = assertThrows(PackContentException.class, () -> parseInstall(
                "{\"type\":\"machine_max:basic\",\"definition\":\"" + PROBE_DEFINITION + "\"}"));
        assertTrue(failure.getUserMessage().getString().contains(PROBE_DEFINITION.toString()),
                failure.getUserMessage().getString());
    }

    @Test
    void missingDefinitionIsAContentError() {
        PackContentException failure = assertThrows(PackContentException.class, () -> parseInstall(
                "{\"type\":\"machine_max:basic\",\"definition\":\"machine_max_test:absent\"}"));
        assertTrue(failure.getUserMessage().getString().contains("machine_max_test:absent"),
                failure.getUserMessage().getString());
    }

    private static AbstractSubsystemAttr parseInstall(String json) {
        return AbstractSubsystemAttr.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    private static AbstractSubsystemStaticAttr parseStatic(String json) {
        return AbstractSubsystemStaticAttr.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }
}
