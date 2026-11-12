package io.github.sweetzonzi.machine_max.client.render.gui.hud.molang;

import cn.solarmoon.spark_core.molang.MolangContextRegistry;
import cn.solarmoon.spark_core.molang.runtime.value.Value;
import io.github.sweetzonzi.machine_max.common.mech.ammo.AmmoBelt;
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
        return tube(key, fill, false);
    }

    static HudWeaponSnapshot.Tube tube(int key, double fill, boolean aimed) {
        return new HudWeaponSnapshot.Tube(new SupplierRef(new UUID(0, key), "barrel", "launcher"),
                "AP", "reloading", "loading", fill, 2, "2.0s", "HE belt", false, true,
                new HudWeaponSnapshot.Chamber(true, "AP"),
                new HudWeaponSnapshot.Magazine(true, "HE belt", 4, 30),
                new HudWeaponSnapshot.Reserve(12, 100, false), aimed);
    }

    static HudWeaponSnapshot snapshot(List<HudWeaponSnapshot.Tube> tubes) {
        return new HudWeaponSnapshot("gun", "reloading", 12, 30, false, "12/30",
                tubes.stream().mapToInt(t -> t.magazine().remaining()).sum(),
                (int) tubes.stream().filter(t -> t.chamber().loaded()).count(),
                "AP", true, "2.0s", "test:ap", tubes,
                List.of(new HudWeaponSnapshot.Belt(
                        new AmmoBelt(null, "", List.of(new AmmoBelt.PatternEntry(ResourceLocation.parse("test:ap"), 1)), 1, 1, null),
                        "AP belt", 12, true)));
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
        assertEquals("12/30", MolangContextRegistry.compileString("weapon.reserve_text", a).evaluate(a));
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
                "AP", "reloading", "regenerating", 40, 12, "12.0s", "", false, true,
                HudWeaponSnapshot.Chamber.EMPTY, HudWeaponSnapshot.Magazine.EMPTY,
                new HudWeaponSnapshot.Reserve(30, 120, false), false);
        var orphan = new HudWeaponSnapshot.Tube(new SupplierRef(new UUID(0, 8), "barrel", "launcher"),
                "HE", "empty", "idle", 0, 0, "", "", false, false,
                HudWeaponSnapshot.Chamber.EMPTY, HudWeaponSnapshot.Magazine.EMPTY,
                HudWeaponSnapshot.Reserve.EMPTY, false);
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

    @Test void boreAlignmentIsReadPerTubeAndAggregatesOnlyWhenEveryTubeAligns() {
        var element = new WeaponHudElement(null, element());
        var onTarget = tube(1, 100, true);
        var offTarget = tube(2, 100, false);
        element.publish(HudPartSnapshot.EMPTY, snapshot(List.of(onTarget, offTarget)), true);
        var context = (WeaponHudMolangContext) element.getMolangContext();
        assertEquals(1, number("weapon.tube_aimed(0)", context));
        assertEquals(0, number("weapon.tube_aimed(1)", context));
        assertEquals(0, number("weapon.tube_aimed(9)", context), "越界索引按未对准处理");
        assertEquals(0, number("weapon.aimed", context), "还有一根管没到，聚合为 0");
        try (var ignored = context.enterItem("weapon.tubes", onTarget.key(), null)) {
            assertEquals(1, number("tube.aimed", context));
        }
        try (var ignored = context.enterItem("weapon.tubes", offTarget.key(), null)) {
            assertEquals(0, number("tube.aimed", context));
        }
        element.publish(HudPartSnapshot.EMPTY, snapshot(List.of(onTarget, tube(2, 100, true))), true);
        context = (WeaponHudMolangContext) element.getMolangContext();
        assertEquals(1, number("weapon.aimed", context), "全部管对准时聚合为 1");
        element.publish(HudPartSnapshot.EMPTY, HudWeaponSnapshot.EMPTY, true);
        context = (WeaponHudMolangContext) element.getMolangContext();
        assertEquals(0, number("weapon.aimed", context), "没有发射管时为 0");
    }

    @Test void chamberMagazineAndReserveStaySeparateNumbers() {
        var element = new WeaponHudElement(null, element());
        // 装填中的管：膛内 1 发、弹链 17 发、备弹 0（弹链是从备弹里取走的，两边不重叠）
        var loaded = new HudWeaponSnapshot.Tube(new SupplierRef(new UUID(0, 11), "barrel", "launcher"),
                "AP", "reloading", "loading", 60, 3, "3.0s", "AP belt", false, true,
                new HudWeaponSnapshot.Chamber(true, "AP"),
                new HudWeaponSnapshot.Magazine(true, "AP belt", 17, 150),
                new HudWeaponSnapshot.Reserve(0, 300, false), false);
        // 换装中的管：膛内仍压着 1 发，但此刻打不出去
        var swapping = new HudWeaponSnapshot.Tube(new SupplierRef(new UUID(0, 12), "barrel", "launcher"),
                "AP", "reloading", "loading", 30, 6, "6.0s", "HE belt", true, true,
                new HudWeaponSnapshot.Chamber(true, "AP"),
                new HudWeaponSnapshot.Magazine(true, "AP belt", 8, 150),
                new HudWeaponSnapshot.Reserve(200, 300, false), false);
        element.publish(HudPartSnapshot.EMPTY, new HudWeaponSnapshot("gun", "reloading", 200, 600, false, "200/600",
                25, 2, "AP", false, "6.0s", "test:ap", List.of(loaded, swapping), List.of()), true);
        var context = (WeaponHudMolangContext) element.getMolangContext();
        assertEquals(27, number("weapon.loaded", context), "待发 = 膛内 2 + 弹链 25");
        assertEquals(200, number("weapon.reserve_remaining", context));
        assertEquals(18, number("weapon.tube_loaded(0)", context));
        assertEquals(9, number("weapon.tube_loaded(1)", context));
        assertEquals(25, number("weapon.magazine_remaining", context));
        assertEquals(2, number("weapon.chambered_count", context));
        try (var ignored = context.enterItem("weapon.tubes", loaded.key(), null)) {
            assertEquals(1, number("tube.chambered", context));
            assertEquals(1, number("tube.magazine", context));
            assertEquals(17, number("tube.magazine_remaining", context));
            assertEquals(150, number("tube.magazine_capacity", context));
            assertEquals("AP belt", MolangContextRegistry.compileString("tube.magazine_label", context).evaluate(context));
            assertEquals(0, number("tube.reserve_remaining", context));
            assertEquals(0, number("tube.swapping", context));
        }
        try (var ignored = context.enterItem("weapon.tubes", swapping.key(), null)) {
            assertEquals(1, number("tube.chambered", context));
            assertEquals(1, number("tube.swapping", context));
            assertEquals(8, number("tube.magazine_remaining", context));
            assertEquals(200, number("tube.reserve_remaining", context));
        }
        assertEquals(0, number("weapon.tube_swapping(0)", context));
        assertEquals(1, number("weapon.tube_swapping(1)", context));
        assertEquals(17, number("weapon.tube_magazine_remaining(0)", context));
        assertEquals(0, number("weapon.tube_chambered(9)", context));
    }

    @Test void vehicleQueriesUseOneBasedSeatsAndPreserveThePublishedVehicleAcrossViewUpdates() {
        var ordinary = new HudRuntimeElement(null, element());
        var weapon = new WeaponHudElement(null, element());
        var elsewhere = new HudRuntimeElement(null, element());
        var seats = List.of(
                new HudVehicleSnapshot.Seat(11, "driver", "Driver", true, 0, 0, 0, true, false, false),
                new HudVehicleSnapshot.Seat(12, "gunner", "Gunner", true, -1, 2, 3, false, true, false),
                new HudVehicleSnapshot.Seat(11, "crew", "Crew", false, 0, 0, 0, false, false, true));
        var vehicle = new HudVehicleSnapshot(new UUID(0, 1), seats, 1, 2, 3, false, "R", true);
        ordinary.publish(HudPartSnapshot.EMPTY, HudWeaponSnapshot.EMPTY, vehicle, false);
        weapon.publish(HudPartSnapshot.EMPTY, HudWeaponSnapshot.EMPTY, vehicle, true);
        ordinary.publishView(new HudViewSnapshot(1, 2, 3, 10, 0, 0, 70, 1, 800, 600));
        assertSame(ordinary.frame().vehicle(), weapon.frame().vehicle());
        var context = ordinary.getMolangContext();
        assertEquals(3, number("vehicle.seat_count", context));
        assertEquals(1, number("vehicle.seat_occupied(1)", context));
        assertEquals(1, number("vehicle.seat_available(2)", context));
        assertEquals(-1, number("vehicle.seat_right(2)", context));
        assertEquals(2, number("vehicle.seat_up(2)", context));
        assertEquals(3, number("vehicle.seat_forward(2)", context));
        assertEquals(1, number("vehicle.seat_has_position(1)", context));
        assertEquals(0, number("vehicle.seat_has_position(3)", context));
        assertEquals(0, number("vehicle.seat_right(1.5)", context));
        assertEquals(0, number("vehicle.seat_has_position(0)", context));
        assertEquals(0, context.vehicleSeatHasPosition(Double.NaN));
        assertEquals(0, context.vehicleSeatHasPosition(Double.POSITIVE_INFINITY));
        assertEquals("", MolangContextRegistry.compileString("vehicle.seat_name(-1)", context).evaluate(context));
        assertEquals("Gunner", MolangContextRegistry.compileString(
                "vehicle.seat_name(vehicle.next_seat_number)", context).evaluate(context));
        assertEquals(1, number("vehicle.current_seat_number", context));
        assertEquals(3, number("vehicle.pending_seat_number", context));
        assertEquals(1, number("vehicle.seat_map_visible", context));
        assertEquals(0, number("vehicle.seat_count", elsewhere.getMolangContext()));
        assertEquals("", elsewhere.getMolangContext().vehicleSeatSwitchKey());
    }

    @Test void invalidQueriesAreDiagnosedBeforeEngineFallback() {
        var ordinary = new HudMolangContext();
        var weapon = new WeaponHudMolangContext();
        assertThrows(IllegalArgumentException.class, () -> HudExpression.validate("weapon.name", ordinary, null));
        assertThrows(IllegalArgumentException.class, () -> HudExpression.validate("vehicle.seat_right", ordinary, null));
        assertThrows(IllegalArgumentException.class, () -> HudExpression.validate("vehicle.seat_occupied(1, 2)", ordinary, null));
        assertThrows(IllegalArgumentException.class, () -> HudExpression.validate("vehicle.missing", ordinary, null));
        assertDoesNotThrow(() -> HudExpression.validate("vehicle.seat_name(vehicle.next_seat_number)", ordinary, null));
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
