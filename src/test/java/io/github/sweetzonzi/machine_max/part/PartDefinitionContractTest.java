package io.github.sweetzonzi.machine_max.part;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.part.*;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPartType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.RegularPartData;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PartDefinitionContractTest {
    static final String KIND = "test:body";
    static final Codec<TestType> TEST_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("body_field").forGetter(value -> value.body),
            ResourceLocation.CODEC.listOf().optionalFieldOf("requires", List.of()).forGetter(value -> value.required),
            Codec.BOOL.optionalFieldOf("valid", true).forGetter(value -> value.valid)
    ).apply(instance, TestType::new));

    static final class TestType extends AbstractPartType {
        final String body;
        final List<ResourceLocation> required;
        final boolean valid;
        TestType(String body, List<ResourceLocation> required, boolean valid) {
            super(ResourceLocation.withDefaultNamespace("missingno"), true, 1, Map.of());
            this.body = body;
            this.required = required;
            this.valid = valid;
        }
        @Override public String kindId() { return KIND; }
    }

    @BeforeAll static void registerExtension() {
        io.github.sweetzonzi.machine_max.MachineMax.REGISTER.register(net.neoforged.bus.api.BusBuilder.builder().build());
        PartKinds.register(KIND, new PartKind<>(TestType.class, TEST_CODEC, RegularPartData.class, RegularPartData.CODEC,
                (type, variant, level, context) -> { throw new AssertionError("No native construction during definition tests"); },
                (type, data, level, mode, context) -> { throw new AssertionError("No native construction during definition tests"); },
                (type, context) -> {
                    type.required.forEach(context::require);
                    if (!type.valid) throw new IllegalArgumentException("bad default connector");
                }));
    }
    @AfterEach void clear() { PartDefinitions.begin(true); PartDefinitions.begin(false); }
    private static ResourceLocation id(String name) { return ResourceLocation.parse("test:" + name); }
    private static TestType type(String name, boolean valid, String... requirements) {
        TestType type = new TestType(name, Arrays.stream(requirements).map(PartDefinitionContractTest::id).toList(), valid);
        type.setRegistryKey(id(name));
        return type;
    }
    private static AbstractPartType parse(String json) {
        return PartKinds.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    @Test void absentTypeAndExplicitRegularUseIdenticalLegacyDefaults() {
        var implicit = assertInstanceOf(RegularPartType.class, parse("{\"variants\":{}}"));
        var explicit = assertInstanceOf(RegularPartType.class, parse("{\"type\":\"regular\",\"variants\":{}}"));
        assertEquals(0.8f, implicit.vehicleDurabilityRate);
        assertEquals(1f, implicit.vehicleDamageRate);
        assertEquals(0.1f, implicit.vehicleDamageRateDestroyed);
        assertEquals(0.3f, implicit.functionalThreshold);
        assertEquals(PartKinds.CODEC.encodeStart(JsonOps.INSTANCE, implicit).getOrThrow(),
                PartKinds.CODEC.encodeStart(JsonOps.INSTANCE, explicit).getOrThrow());
    }

    @Test void extensionRoundTripPreservesTypeAndDedicatedFields() {
        var parsed = assertInstanceOf(TestType.class, parse("{\"type\":\"test:body\",\"body_field\":\"core\"}"));
        var json = PartKinds.CODEC.encodeStart(JsonOps.INSTANCE, parsed).getOrThrow();
        assertEquals(KIND, json.getAsJsonObject().get("type").getAsString());
        assertEquals("core", assertInstanceOf(TestType.class, PartKinds.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow()).body);
    }

    @Test void explicitInvalidTypesAndInvalidExtensionNeverFallBackToRegular() {
        for (String value : List.of("null", "42", "{}", "\"\"", "\"unknown:body\"")) {
            assertTrue(PartKinds.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseString("{\"type\":" + value + ",\"variants\":{}}" )).error().isPresent(), value);
        }
        assertTrue(PartKinds.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"type\":\"test:body\",\"variants\":{}}" )).error().isPresent());
    }

    @Test void candidatesAndTheirRequiredDependentsAreInvisibleUntilValidPublication() {
        var a = type("a", true, "b");
        var b = type("b", true, "c");
        var c = type("c", false);
        var d = type("d", true);
        PartDefinitions.begin(false);
        assertFalse(PartDefinitions.isReady(false));
        assertNull(PartDefinitions.get(false, id("d")));
        assertThrows(RuntimeException.class, () -> PartFactory.resolve(false, id("a")));
        var errors = PartDefinitions.validateAndPublish(false, Map.of(id("a"), a, id("b"), b, id("c"), c, id("d"), d));
        assertEquals(3, errors.size());
        assertTrue(PartDefinitions.isReady(false));
        for (String key : List.of("a", "b", "c")) {
            assertNull(PartDefinitions.get(false, id(key)));
            assertThrows(RuntimeException.class, () -> PartFactory.resolve(false, id(key)));
        }
        assertSame(d, PartFactory.resolve(false, id("d")));
        assertTrue(PartDefinitions.regular(false).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> PartDefinitions.all(false).clear());
        assertTrue(PartDefinitions.all(true).isEmpty());
    }

    @Test void reloadRejectsRetainedDefinitionAndRepublishesOnlyRepairedGeneration() {
        var first = type("core", true);
        PartDefinitions.validateAndPublish(false, Map.of(id("core"), first));
        assertSame(first, PartDefinitions.requireCurrent(false, first));
        PartDefinitions.begin(false);
        assertThrows(IllegalArgumentException.class, () -> PartDefinitions.requireCurrent(false, first));
        PartDefinitions.validateAndPublish(false, Map.of(id("core"), type("core", false)));
        assertTrue(PartDefinitions.isReady(false));
        assertThrows(RuntimeException.class, () -> PartFactory.resolve(false, id("core")));
        var repaired = type("core", true);
        PartDefinitions.validateAndPublish(false, Map.of(id("core"), repaired));
        assertSame(repaired, PartFactory.resolve(false, id("core")));
        assertThrows(IllegalArgumentException.class, () -> PartDefinitions.requireCurrent(false, first));
    }

    @Test void unexpectedPublicationFailureLeavesNoCandidateOrPreviousGeneration() {
        var first = type("core", true);
        PartDefinitions.validateAndPublish(false, Map.of(id("core"), first));
        Map<ResourceLocation, AbstractPartType> broken = new HashMap<>();
        broken.put(id("core"), null);
        assertThrows(NullPointerException.class, () -> PartDefinitions.validateAndPublish(false, broken));
        assertNull(PartDefinitions.get(false, id("core")));
    }

    @Test void extensionSavedStateUsesPublishedDefinitionCodecWithoutDuplicatedTypeField() {
        var definition = type("core", true);
        PartDefinitions.validateAndPublish(false, Map.of(id("core"), definition));
        var state = new RegularPartData(id("core"), "saved", UUID.randomUUID().toString(), "default",
                0.7f, 0.4f, 3, false, false, 0, "paint", Map.of());
        var json = PartFactory.encodeData(false, JsonOps.INSTANCE, state).getOrThrow();
        assertFalse(json.getAsJsonObject().has("type"));
        var restored = PartFactory.decodeData(false, JsonOps.INSTANCE, json).getOrThrow();
        assertEquals("paint", restored.textureName);
        assertEquals(state.uuid, restored.uuid);
        PartDefinitions.begin(false);
        assertTrue(PartFactory.decodeData(false, JsonOps.INSTANCE, json).error().isPresent());
    }
}
