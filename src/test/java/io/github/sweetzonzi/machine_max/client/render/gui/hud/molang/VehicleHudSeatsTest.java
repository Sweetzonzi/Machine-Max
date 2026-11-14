package io.github.sweetzonzi.machine_max.client.render.gui.hud.molang;

import cn.solarmoon.spark_core.molang.MolangContextRegistry;
import cn.solarmoon.spark_core.molang.runtime.value.Value;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.parser.HTML;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VehicleHudSeatsTest {
    private Document document;
    private HudPageBinding binding;
    private HudRuntimeElement runtime;

    private static HudVehicleSnapshot.Seat seat(int id, String key, String name, boolean occupied, String entity) {
        return new HudVehicleSnapshot.Seat(id, key, name, true, 0, 0, 0, occupied, !occupied, false, entity);
    }

    private void publish(UUID vehicle, List<HudVehicleSnapshot.Seat> seats, int current, int next) {
        runtime.publish(new HudPartSnapshot(java.util.Map.of("global.hp", 60d, "global.max_hp", 100d), java.util.Map.of()),
                HudWeaponSnapshot.EMPTY, new HudVehicleSnapshot(vehicle, seats, current, next, 0, false, "V", true), false);
    }

    private void create(String markup) {
        String page = "test://vehicle-seats/" + UUID.randomUUID() + ".html";
        HTML.putTemple(page, markup);
        document = ApricityUI.createDocument(page);
        assertNotNull(document);
        runtime = new HudRuntimeElement(null, HudMolangContextTest.element());
        binding = new HudPageBinding(document, runtime, "vehicle-seats");
    }

    private void refresh() { Document.runWithContext(document, binding::refresh); }

    @AfterEach void close() {
        if (binding != null) binding.close();
        if (document != null) document.remove();
        Size.clearViewportOverride();
    }

    @Test void seatRowsReuseIdentityAndRefreshNamesWithoutAWeaponHost() {
        create("<html><body><div class='row' data-mm-repeat='vehicle.seats' data-mm-index='row' data-mm-class='seat.state'>"
                + "<b data-mm-text='seat.number'></b><i data-mm-text='v.row'></i>"
                + "<span data-mm-text='seat.entity_name'></span></div></body></html>");
        var vehicle = UUID.randomUUID();
        var driver = seat(1, "driver", "Driver", true, "Pilot");
        var gunner = seat(1, "gunner", "Gunner", false, "");
        publish(vehicle, List.of(driver, gunner), 1, 2); refresh();
        var first = document.querySelectorAll(".row").getFirst();
        var second = document.querySelectorAll(".row").getLast();
        assertTrue(first.getClassList().contains("is-current"));
        assertTrue(second.getClassList().contains("is-next"));
        assertEquals("Pilot", document.querySelector(".row span").innerText);
        publish(vehicle, List.of(gunner, seat(1, "driver", "Driver", true, "Renamed <pilot>")), 2, 1); refresh();
        assertSame(second, document.querySelectorAll(".row").getFirst());
        assertSame(first, document.querySelectorAll(".row").getLast());
        assertEquals("Renamed <pilot>", document.querySelectorAll(".row span").getLast().innerText);
        assertEquals("2", document.querySelectorAll(".row b").getLast().innerText);
        assertEquals("1", document.querySelectorAll(".row i").getLast().innerText);
        publish(UUID.randomUUID(), List.of(driver), 1, 0); refresh();
        assertSame(first, document.querySelector(".row"), "changing assembly ownership preserves the same seat row");
        assertFalse(second.isConnected());
        publish(vehicle, List.of(seat(2, "driver", "Driver", true, "Other pilot")), 1, 0); refresh();
        assertFalse(first.isConnected(), "a different SubPart with the same seat name has a different identity");
        assertEquals("Other pilot", document.querySelector(".row span").innerText);
        publish(vehicle, List.of(), 0, 0); refresh();
        assertTrue(document.querySelectorAll(".row").isEmpty());
    }

    @Test void seatScopeRestoresAliasAndWorksInWeaponPages() {
        var element = new WeaponHudElement(null, HudMolangContextTest.element());
        var vehicle = UUID.randomUUID();
        var occupied = seat(8, "crew", "Crew", true, "Test entity");
        element.publish(HudPartSnapshot.EMPTY, HudWeaponSnapshot.EMPTY,
                new HudVehicleSnapshot(vehicle, List.of(occupied), 1, 0, 0, false, "V", true), false);
        var context = element.getMolangContext();
        context.getVariableStorage().set("row", Value.of(42));
        assertThrows(IllegalStateException.class, () -> {
            try (var ignored = context.enterRepeat("vehicle.seats", occupied.identity(), "row")) {
                assertEquals(0, context.seatIndex());
                assertEquals(1, context.seatNumber());
                assertEquals(1, context.seatCurrent());
                assertEquals("Test entity", context.seatEntityName());
                assertThrows(IllegalArgumentException.class, () -> context.getVariableStorage().set("row", Value.of(1)));
                throw new IllegalStateException("restore");
            }
        });
        assertEquals(42, context.getVariableStorage().get("row").getAsNumber());
        assertThrows(IllegalStateException.class, context::seatEntityName);
        assertThrows(IllegalArgumentException.class, () -> HudExpression.validate("seat.name", context, null));
        assertEquals("Test entity", MolangContextRegistry.compileString("vehicle.seat_entity_name(1)", context).evaluate(context));
        assertEquals("", context.vehicleSeatEntityName(Double.NaN));
    }

    @Test void builtinPagesBindRealSnapshotsAndMeasureTheirContents() throws Exception {
        var directory = Path.of("src/main/resources/spark_modules/Machine-Max_Builtin/machine_max/documents/vehicle_hud");
        String css = Files.readString(directory.resolve("hud.css"));
        for (String skin : List.of("heading", "camera", "topdown", "seat_list")) {
            create(Files.readString(directory.resolve(skin + ".html"))
                    .replace("<link rel=\"stylesheet\" href=\"hud.css\">", "<style>" + css + "</style>"));
            publish(UUID.randomUUID(), List.of(seat(1, "driver", "Driver", true, "Pilot"),
                    seat(2, "gunner", "Gunner", false, "")), 1, 2);
            refresh();
            Document.runWithContext(document, () -> {
                document.commitStyleRecalc(); document.commitRenderState();
                assertTrue(document.body.getOffsetWidth() > 0, skin);
                assertTrue(document.body.getOffsetHeight() > 0, skin);
                assertFalse(document.body.getTextContent().contains("MOLANG_ERROR"), skin);
                if (skin.equals("seat_list")) {
                    assertEquals(2, document.querySelectorAll(".crew-row").size());
                    assertEquals("Pilot", document.querySelector(".crew-player").innerText);
                    assertEquals("---", document.querySelectorAll(".crew-player").getLast().innerText);
                } else assertNotNull(document.querySelector("machinemax-vehicle-model"));
            });
            binding.close(); document.remove(); binding = null; document = null;
        }
    }
}
