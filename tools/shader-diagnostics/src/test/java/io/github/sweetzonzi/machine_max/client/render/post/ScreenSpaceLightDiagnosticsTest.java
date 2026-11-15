package io.github.sweetzonzi.machine_max.client.render.post;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;

class ScreenSpaceLightDiagnosticsTest {
    @TempDir Path directory;

    @Test void comparisonCountsRgbDifferencesAndPreservesInputFiles() throws Exception {
        Path left = image("left image.png", 0xFF102030, 0xFF405060);
        Path right = image("right image.png", 0x00102030, 0xFF405063);
        byte[] before = Files.readAllBytes(left);
        Path output = directory.resolve("report with spaces");
        ScreenSpaceLightDiagnostics.main(new String[]{"--compare-left", left.toString(), "--compare-right", right.toString(), "--output", output.toString()});
        JsonObject result = JsonParser.parseString(Files.readString(output.resolve("report.json"))).getAsJsonObject()
                .getAsJsonArray("results").get(0).getAsJsonObject().getAsJsonObject("comparison");
        assertEquals(1, result.get("changed_pixels").getAsInt());
        assertEquals(1, result.get("nonidentical_pixels").getAsInt());
        assertEquals(3, result.get("max_channel_difference").getAsInt());
        assertEquals(.5, result.get("mean_absolute_channel_difference").getAsDouble());
        assertArrayEquals(before, Files.readAllBytes(left));
        assertTrue(Files.exists(output.resolve("overview.png")));
        assertTrue(Files.readString(output.resolve("index.html")).contains("comparison.png"));
        assertFalse(Files.readString(output.resolve("index.html")).contains("{{"));
    }

    @Test void thresholdDoesNotTurnAnApproximateMatchIntoAnExactMatch() throws Exception {
        Path left = image("left.png", 0xFF000000, 0xFF000000);
        Path right = image("right.png", 0xFF000002, 0xFF000000);
        Path output = directory.resolve("threshold");
        ScreenSpaceLightDiagnostics.main(new String[]{"--compare-left", left.toString(), "--compare-right", right.toString(), "--output", output.toString()});
        JsonObject result = JsonParser.parseString(Files.readString(output.resolve("report.json"))).getAsJsonObject()
                .getAsJsonArray("results").get(0).getAsJsonObject().getAsJsonObject("comparison");
        assertEquals(0, result.get("changed_pixels").getAsInt());
        assertEquals(1, result.get("nonidentical_pixels").getAsInt());
        assertFalse(result.get("exact_rgb_match").getAsBoolean());
    }

    @Test void refusesToOverwriteOutput() throws Exception {
        Path left = image("a.png", 0, 0), output = Files.createDirectory(directory.resolve("existing"));
        Path marker = output.resolve("marker.txt"); Files.writeString(marker, "preserve");
        assertThrows(FileAlreadyExistsException.class, () -> ScreenSpaceLightDiagnostics.main(new String[]{
                "--compare-left", left.toString(), "--compare-right", left.toString(), "--output", output.toString()}));
        assertEquals("preserve", Files.readString(marker));
    }

    @Test void rejectsTruncatedDepthBeforeCreatingGraphicsContext() throws Exception {
        Path capture = capture(); Files.write(capture.resolve("depth.f32be"), new byte[3]);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> ScreenSpaceLightDiagnostics.main(new String[]{"--capture", capture.toString()}))
                .getMessage().contains("depth.f32be size"));
    }

    @Test void rejectsUnknownSchemaAndModifiedSnapshot() throws Exception {
        Path capture = capture(); JsonObject metadata = metadata(capture);
        metadata.addProperty("capture_format_version", 99); Files.writeString(capture.resolve("frame.json"), metadata.toString());
        assertTrue(assertThrows(IllegalArgumentException.class, () -> ScreenSpaceLightDiagnostics.main(new String[]{"--capture", capture.toString()}))
                .getMessage().contains("capture_format_version"));
        metadata.remove("capture_format_version"); metadata.addProperty("fragment_sha256", "wrong"); Files.writeString(capture.resolve("frame.json"), metadata.toString());
        assertTrue(assertThrows(IllegalArgumentException.class, () -> ScreenSpaceLightDiagnostics.main(new String[]{"--capture", capture.toString()}))
                .getMessage().contains("hash mismatch"));
    }

    @Test void rejectsUnsafeVariantNamesAndOutputInsideCapture() throws Exception {
        Path capture = capture(), variants = directory.resolve("variants.json");
        Files.writeString(variants, "[{\"name\":\"../escape\",\"uniforms\":{}}]");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> ScreenSpaceLightDiagnostics.main(new String[]{
                "--capture", capture.toString(), "--variants", variants.toString()})).getMessage().contains("variant name"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> ScreenSpaceLightDiagnostics.main(new String[]{
                "--capture", capture.toString(), "--output", capture.resolve("output").toString()})).getMessage().contains("read-only capture"));
    }

    private Path image(String name, int a, int b) throws Exception {
        BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB); image.setRGB(0, 0, a); image.setRGB(1, 0, b);
        Path path = directory.resolve(name); ImageIO.write(image, "PNG", path.toFile()); return path;
    }
    private Path capture() throws Exception {
        Path capture = Files.createDirectory(directory.resolve("capture"));
        Files.copy(image("color.png", 0, 0), capture.resolve("00_without_added_light.png"));
        Files.write(capture.resolve("depth.f32be"), new byte[8]);
        Files.writeString(capture.resolve("fragment.fsh"), "#version 150\nvoid main() {}\n");
        JsonObject metadata = new JsonObject(); metadata.addProperty("width", 2); metadata.addProperty("height", 1); metadata.addProperty("light_count", 0);
        metadata.addProperty("focal_px", 1); metadata.addProperty("depth_a", -1); metadata.addProperty("depth_b", -.05);
        metadata.addProperty("normal_weight", .5); metadata.addProperty("OcclusionTolRel", .002);
        String[] names = {"LightView", "LightColor", "LightTol", "LightOcclusion", "LightMinDist", "LightGlow", "LightGlare"};
        int[] lengths = {64, 64, 16, 16, 16, 32, 16};
        for (int i = 0; i < names.length; i++) { JsonArray array = new JsonArray(); for (int j = 0; j < lengths[i]; j++) array.add(0); metadata.add(names[i], array); }
        Files.writeString(capture.resolve("frame.json"), metadata.toString()); return capture;
    }
    private JsonObject metadata(Path capture) throws Exception { return JsonParser.parseString(Files.readString(capture.resolve("frame.json"))).getAsJsonObject(); }
}
