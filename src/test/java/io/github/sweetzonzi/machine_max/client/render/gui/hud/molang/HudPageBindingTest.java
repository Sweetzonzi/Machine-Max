package io.github.sweetzonzi.machine_max.client.render.gui.hud.molang;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.parser.HTML;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HudPageBindingTest {
    private Document document;
    private HudPageBinding binding;
    private WeaponHudElement runtime;

    @AfterEach void close() {
        if (binding != null) binding.close();
        if (document != null) document.remove();
    }

    private void create(String body) {
        String page = "test://hud-binding/" + UUID.randomUUID() + ".html";
        HTML.putTemple(page, "<html><head></head><body>" + body + "</body></html>");
        document = ApricityUI.createDocument(page);
        assertNotNull(document);
        runtime = new WeaponHudElement(null, HudMolangContextTest.element());
        binding = new HudPageBinding(document, runtime, "test-style");
    }

    private void publish(HudWeaponSnapshot.Tube... tubes) {
        runtime.publish(HudPartSnapshot.EMPTY, HudMolangContextTest.snapshot(List.of(tubes)), true);
    }

    private void refresh() { Document.runWithContext(document, binding::refresh); }

    @Test void rowsReuseIdentityThroughReorderAndSameCountReplacement() {
        create("<div class='list'><div class='row' data-mm-repeat='weapon.tubes' data-mm-index='row'>"
                + "<span data-mm-text='tube.fill'></span><b data-mm-text='variable.row'></b>"
                + "<i data-mm-num='tube.fill' data-mm-var='--fill' data-mm-unit='%'></i>"
                + "</div></div>");
        var a = HudMolangContextTest.tube(1, 25); var b = HudMolangContextTest.tube(2, 75);
        publish(a, b); refresh();
        var rows = document.querySelectorAll(".row");
        assertEquals(2, rows.size());
        var first = rows.getFirst(); var second = rows.getLast();
        assertEquals("25", document.querySelectorAll(".row span").getFirst().innerText);
        assertEquals("75%", document.querySelectorAll(".row i").getLast().getInlineStylePropertyValue("--fill"));
        publish(b, a); refresh();
        assertSame(second, document.querySelectorAll(".row").getFirst());
        assertSame(first, document.querySelectorAll(".row").getLast());
        assertEquals("0", document.querySelectorAll(".row b").getFirst().innerText);
        publish(b, HudMolangContextTest.tube(3, 50)); refresh();
        assertSame(second, document.querySelectorAll(".row").getFirst());
        assertFalse(first.isConnected());
        assertEquals("50", document.querySelectorAll(".row span").getLast().innerText);
        publish(); refresh();
        assertTrue(document.querySelectorAll(".row").isEmpty());
    }

    @Test void invalidSlotsAndRegionsDoNotInterruptOtherValues() {
        create("<span class='bad' data-mm-text='tube.fill'></span>"
                + "<span class='good' data-mm-text='weapon.ammo_text'></span>"
                + "<span class='badnum' data-mm-num='0/0' data-mm-var='--x' data-mm-unit='oops'></span>"
                + "<div data-mm-repeat='weapon.tubes'><span id='duplicate'></span></div>"
                + "<div data-mm-repeat='weapon.belts'><span data-mm-repeat='weapon.tubes'></span></div>"
                + "<div class='ok-row' data-mm-repeat='weapon.tubes' data-mm-class='tube.state'></div>");
        publish(HudMolangContextTest.tube(1, 25));
        assertDoesNotThrow(this::refresh);
        assertEquals("MOLANG_ERROR", document.querySelector(".bad").innerText);
        assertEquals("12/30", document.querySelector(".good").innerText);
        assertEquals(1, document.querySelectorAll(".ok-row").size());
        assertTrue(document.querySelector(".ok-row").getClassList().contains("is-reloading"));
        assertEquals(3, document.querySelectorAll("[data-mm-repeat]").size(), "invalid templates and their nested originals stay hidden");
        assertEquals("none", document.querySelector("[data-mm-repeat]").getInlineStylePropertyValue("display"));
    }

    @Test void parserInstantiatesCanvasAttributesImagesAndInlineHandlers() {
        com.sighs.apricityui.init.Element.register("canvas", (owner, tag) -> new com.sighs.apricityui.element.Canvas(owner));
        create("<div class='row' data-mm-repeat='weapon.tubes'>"
                + "<canvas width='96' height='48'></canvas><img src='test.png'>"
                + "<button onclick='return 1;'>launch</button><span data-mm-text='tube.fill'></span></div>");
        publish(HudMolangContextTest.tube(1, 25)); refresh();
        var canvas = document.querySelector("canvas");
        assertInstanceOf(com.sighs.apricityui.element.Canvas.class, canvas);
        assertEquals(96, ((com.sighs.apricityui.element.Canvas) canvas).getWidth());
        assertEquals(48, ((com.sighs.apricityui.element.Canvas) canvas).getHeight());
        assertEquals("test.png", document.querySelector("img").getAttribute("src"));
        assertEquals("return 1;", document.querySelector("button").getAttribute("onclick"));
        assertTrue(document.querySelector("button").EventListener.stream()
                .anyMatch(listener -> listener.type().equals("click") && !listener.internal()));
        assertEquals("25", document.querySelector("span").innerText);
    }

    @Test void externalNodesAndMarkerChangesAreBoundAndReleased() {
        create("<div class='target'></div>");
        publish(HudMolangContextTest.tube(1, 25)); refresh();
        var target = document.querySelector(".target");
        Document.runWithContext(document, () -> target.appendChild(document.createHTML("<span data-mm-text='weapon.ammo_text'></span>")));
        refresh();
        var added = document.querySelector("span");
        assertEquals("12/30", added.innerText);
        Document.runWithContext(document, () -> added.setAttribute("data-mm-text", "weapon.name"));
        refresh();
        assertEquals("gun", added.innerText);
        Document.runWithContext(document, () -> target.removeChild(added));
        publish(); refresh();
        assertFalse(added.isConnected());
        assertEquals("gun", added.innerText);
    }

    @Test void textBindingReplacesTemplatePlaceholderBeforeLayout() {
        create("<span data-mm-text='weapon.ammo_text'>Preview placeholder</span>");
        publish(HudMolangContextTest.tube(1, 25)); refresh();
        var node = document.querySelector("span");
        assertEquals("12/30", node.getTextContent());
        assertEquals("12/30", node.innerText);
        assertEquals("12/30", node.getInnerText());
    }

    @Test void documentRefreshRebindsNewTreeBeforeValuesAreConsumed() {
        create("<div class='row' data-mm-repeat='weapon.tubes'><span data-mm-text='tube.fill'></span></div>");
        publish(HudMolangContextTest.tube(1, 25)); refresh();
        var old = document.querySelector(".row");
        document.refresh();
        publish(HudMolangContextTest.tube(2, 75)); refresh();
        assertNotSame(old, document.querySelector(".row"));
        assertEquals("75", document.querySelector("span").innerText);
    }
}
