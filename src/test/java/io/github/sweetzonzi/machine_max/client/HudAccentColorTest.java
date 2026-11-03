package io.github.sweetzonzi.machine_max.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HudAccentColorTest {

    @Test
    void blankOrUnparsableValuesFallBackToTheContentPackColor() {
        assertEquals("", MMClientConfig.parseHudAccentColor(null));
        assertEquals("", MMClientConfig.parseHudAccentColor(""));
        assertEquals("", MMClientConfig.parseHudAccentColor("   "));
        assertEquals("", MMClientConfig.parseHudAccentColor("unset"));
        assertEquals("", MMClientConfig.parseHudAccentColor("not-a-color"));
        assertEquals("", MMClientConfig.parseHudAccentColor("#gggggg"));
        assertEquals("", MMClientConfig.parseHudAccentColor("transparent"));
    }

    @Test
    void parsableCssColorsAreKept() {
        assertEquals("#a4ff80", MMClientConfig.parseHudAccentColor("#a4ff80"));
        assertEquals("#fc6", MMClientConfig.parseHudAccentColor(" #fc6 "));
        assertEquals("RED", MMClientConfig.parseHudAccentColor("RED"));
        assertEquals("rgb(164, 255, 128)", MMClientConfig.parseHudAccentColor("rgb(164, 255, 128)"));
        assertEquals("rgba(164, 255, 128, 0.8)", MMClientConfig.parseHudAccentColor("rgba(164, 255, 128, 0.8)"));
    }
}
