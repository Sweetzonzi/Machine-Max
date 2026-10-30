package io.github.sweetzonzi.machine_max.client.render.gui.hud.molang;

import cn.solarmoon.spark_core.molang.MolangContextRegistry;
import cn.solarmoon.spark_core.molang.runtime.value.Value;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoSupplier.SupplierRef;
import io.github.sweetzonzi.machine_max.common.visual.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class HudMolangContextTest {
    static HudElement element() {
        return new HudElement(ResourceLocation.parse("machine_max:test"), false,
                ScopeBehavior.SCREEN_FIXED, true, Vec3.ZERO, Vec3.ZERO, new Vec3(1, 1, 1), HudAnchor.CENTER);
    }

    static HudWeaponSnapshot.Tube tube(int key, double fill) {
        return new HudWeaponSnapshot.Tube(new SupplierRef(new UUID(0, key), "barrel", "launcher"),
                "AP", "reloading", "loading", fill, 2, "2.0s", "", "HE belt", true);
    }

    static HudWeaponSnapshot snapshot(List<HudWeaponSnapshot.Tube> tubes) {
        return new HudWeaponSnapshot("gun", "reloading", 12, 30, false, "12/30", "AP",
                true, "2.0s", "test:ap", tubes,
                List.of(new HudWeaponSnapshot.Belt(List.of(ResourceLocation.parse("test:ap")), "AP belt", 12, true)));
    }

    @Test void compiledQueriesShareCodeButKeepElementAndObserverValuesSeparate() {
        var first = new WeaponHudElement(null, element());
        var second = new WeaponHudElement(null, element());
        var data = snapshot(List.of(tube(1, 25), tube(2, 75)));
        first.publish(new HudPartSnapshot(Map.of("global.get:vehicle_speed", 8.5), Map.of()), data, true);
        second.publish(HudPartSnapshot.EMPTY, data, false);
        first.publishView(new HudViewSnapshot(1, 2, 3, 45, 10, 5, 40, 2, 900, 600));
        var a = first.getMolangContext();
        var b = second.getMolangContext();
        assertSame(MolangContextRegistry.compile("weapon.selected", a), MolangContextRegistry.compile("weapon.selected", b));
        assertEquals(1, number("weapon.selected", a));
        assertEquals(0, number("weapon.selected", b));
        assertEquals(45, number("hud.view_heading_deg", a));
        assertEquals(0, number("hud.view_heading_deg", b));
        assertEquals(8.5, number("global.get('vehicle_speed')", a));
        assertEquals("12/30", MolangContextRegistry.compileString("weapon.ammo_text", a).evaluate(a));
        assertEquals(75, number("weapon.tube_fill(1)", a));
        assertEquals(0, number("weapon.tube_fill(-1)", a));
        assertEquals(0, number("weapon.tube_fill(0.5)", a));
        assertEquals("", MolangContextRegistry.compileString("weapon.belt_label(9)", a).evaluate(a));
        assertNotSame(a, CompletableFuture.supplyAsync(first::getMolangContext).join());
    }

    @Test void itemScopesResolveIdentityInEachFrameAndRestoreOnExceptions() {
        var element = new WeaponHudElement(null, element());
        var one = tube(1, 25); var two = tube(2, 75);
        element.publish(HudPartSnapshot.EMPTY, snapshot(List.of(one, two)), true);
        var context = (WeaponHudMolangContext) element.getMolangContext();
        context.getVariableStorage().set("row", Value.of(42));
        assertThrows(IllegalStateException.class, () -> {
            try (var ignored = context.enterItem("weapon.tubes", two.key(), "row")) {
                assertEquals(75, number("tube.fill", context));
                assertEquals(1, number("tube.index", context));
                assertEquals(1, number("v.row", context));
                assertThrows(IllegalArgumentException.class, () -> context.getVariableStorage().set("row", Value.of(9)));
                assertThrows(IllegalStateException.class, context::belt_label);
                throw new IllegalStateException("slot failure");
            }
        });
        assertEquals(42, number("variable.row", context));
        assertThrows(IllegalStateException.class, context::tube_fill);
        element.publish(HudPartSnapshot.EMPTY, snapshot(List.of(two, one)), false);
        assertEquals(1, number("weapon.selected", context), "the previous evaluation frame stays pinned");
        var next = (WeaponHudMolangContext) element.getMolangContext();
        try (var ignored = next.enterItem("weapon.tubes", two.key(), null)) {
            assertEquals(0, number("tube.index", next));
            assertEquals(75, number("tube.fill", next));
        }
    }

    @Test void tubePhaseAndSourceAreReadableInsideAndOutsideTheScope() {
        var element = new WeaponHudElement(null, element());
        var stalled = new HudWeaponSnapshot.Tube(new SupplierRef(new UUID(0, 7), "barrel", "launcher"),
                "AP", "reloading", "regenerating", 40, 12, "12.0s", "", "", true);
        var orphan = new HudWeaponSnapshot.Tube(new SupplierRef(new UUID(0, 8), "barrel", "launcher"),
                "HE", "empty", "idle", 0, 0, "", "", "", false);
        element.publish(HudPartSnapshot.EMPTY, snapshot(List.of(stalled, orphan)), true);
        var context = (WeaponHudMolangContext) element.getMolangContext();
        try (var ignored = context.enterItem("weapon.tubes", stalled.key(), null)) {
            assertEquals("reloading", MolangContextRegistry.compileString("tube.state", context).evaluate(context));
            assertEquals("regenerating", MolangContextRegistry.compileString("tube.phase", context).evaluate(context));
            assertEquals(1, number("tube.has_source", context));
        }
        assertEquals("regenerating", MolangContextRegistry.compileString("weapon.tube_phase(0)", context).evaluate(context));
        assertEquals("idle", MolangContextRegistry.compileString("weapon.tube_phase(1)", context).evaluate(context));
        assertEquals(1, number("weapon.tube_has_source(0)", context));
        assertEquals(0, number("weapon.tube_has_source(1)", context));
        assertEquals("", MolangContextRegistry.compileString("weapon.tube_phase(5)", context).evaluate(context));
        assertEquals(0, number("weapon.tube_has_source(5)", context));
    }

    @Test void invalidQueriesAreDiagnosedBeforeEngineFallback() {
        var ordinary = new HudMolangContext();
        var weapon = new WeaponHudMolangContext();
        assertThrows(IllegalArgumentException.class, () -> HudExpression.validate("weapon.name", ordinary, null));
        assertThrows(IllegalArgumentException.class, () -> HudExpression.validate("tube.fill", weapon, null));
        assertThrows(IllegalArgumentException.class, () -> HudExpression.validate("belt.label", weapon, "weapon.tubes"));
        assertThrows(IllegalArgumentException.class, () -> HudExpression.validate("weapon.tube_fill()", weapon, null));
        assertThrows(IllegalArgumentException.class, () -> HudExpression.validate("v.row = 4", weapon, "weapon.tubes", "row"));
        assertDoesNotThrow(() -> HudExpression.validate("weapon.tube_fill(math.max(0, 1))", weapon, null));
        assertDoesNotThrow(() -> HudExpression.validate("'weapon.missing'", ordinary, null));
        assertDoesNotThrow(() -> HudExpression.validate("variable.row == 4", weapon, "weapon.tubes", "row"));
    }

    @Test void physicsEvaluationPinsOneFrameAcrossResetsAndRestoresAfterFailure() throws Exception {
        var element = new HudRuntimeElement(null, element());
        element.publishView(new HudViewSnapshot(1, 2, 3, 10, 0, 0, 70, 1, 800, 600));
        var pinned = new CountDownLatch(1);
        var published = new CountDownLatch(1);
        try (var physics = Executors.newSingleThreadExecutor()) {
            var result = physics.submit(() -> {
                try {
                    try (var outer = element.pinFrame()) {
                        pinned.countDown();
                        assertTrue(published.await(5, TimeUnit.SECONDS));
                        try (var nested = element.pinFrame()) {
                            assertEquals(10, number("hud.view_heading_deg", element.getMolangContext()));
                        }
                        assertEquals(10, number("hud.view_heading_deg", element.getMolangContext()));
                        throw new IllegalStateException("expression failed");
                    }
                } catch (IllegalStateException expected) {
                    assertEquals("expression failed", expected.getMessage());
                }
                return number("hud.view_heading_deg", element.getMolangContext());
            });
            try {
                assertTrue(pinned.await(5, TimeUnit.SECONDS));
                element.publishView(new HudViewSnapshot(1, 2, 3, 20, 0, 0, 70, 1, 800, 600));
                assertEquals(20, number("hud.view_heading_deg", element.getMolangContext()));
            } finally {
                published.countDown();
            }
            assertEquals(20, result.get(5, TimeUnit.SECONDS));
        }
    }

    @Test void viewAxesAndSelectionPolicyHaveExplicitConventions() {
        var north = HudViewSnapshot.fromCamera(8, 9, 10, new org.joml.Vector3f(0, 0, -1),
                new org.joml.Vector3f(0, 1, 0), 40, 2, 800, 600);
        assertEquals(0, north.heading()); assertEquals(0, north.pitch()); assertEquals(0, north.roll());
        var east = HudViewSnapshot.fromCamera(0, 0, 0, new org.joml.Vector3f(1, 0, 0),
                new org.joml.Vector3f(0, 1, 0), 70, 1, 800, 600);
        assertEquals(90, east.heading());
        var bank = HudViewSnapshot.fromCamera(0, 0, 0, new org.joml.Vector3f(0, 0, -1),
                new org.joml.Vector3f(1, 0, 0), 70, 1, 800, 600);
        assertEquals(90, bank.roll());
        assertFalse(HudSelectionVisibility.SELECTED_ONLY.visible(true, false));
        assertTrue(HudSelectionVisibility.ALWAYS.visible(true, false));
        assertTrue(HudSelectionVisibility.SELECTED_ONLY.visible(false, false));
    }

    private static double number(String expression, HudMolangContext context) {
        return MolangContextRegistry.compile(expression, context).evaluate(context);
    }
}
