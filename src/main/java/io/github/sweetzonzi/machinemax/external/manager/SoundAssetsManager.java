package io.github.sweetzonzi.machinemax.external.manager;

import com.google.common.collect.Maps;
import io.github.sweetzonzi.machinemax.MachineMax;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.FiniteAudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.jetbrains.annotations.NotNull;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;

/**
 * 参考自TACZ的SoundAssetsManager
 */
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class SoundAssetsManager extends SimplePreparableReloadListener<Map<ResourceLocation, SoundAssetsManager.SoundData>> {
    public record SoundData(ByteBuffer byteBuffer, AudioFormat audioFormat) {
    }
    private static final Marker MARKER = MarkerManager.getMarker("SoundsLoader");

    private static final Map<ResourceLocation, SoundData> sounds = Maps.newHashMap();
    private final FileToIdConverter filetoidconverter = new FileToIdConverter("sound", ".ogg");

    @Override
    @NotNull
    protected Map<ResourceLocation, SoundData> prepare(@NotNull ResourceManager pResourceManager, @NotNull ProfilerFiller pProfiler) {
        Map<ResourceLocation, SoundData> output = Maps.newHashMap();
        for(Map.Entry<ResourceLocation, Resource> entry : filetoidconverter.listMatchingResources(pResourceManager).entrySet()) {
            ResourceLocation resourcelocation = entry.getKey();
//            ResourceLocation resourcelocation1 = filetoidconverter.fileToId(resourcelocation);
            try (InputStream stream = entry.getValue().open(); FiniteAudioStream audioStream = new JOrbisAudioStream(stream)) {
                ByteBuffer bytebuffer = audioStream.readAll();
                output.put(resourcelocation, new SoundData(bytebuffer, audioStream.getFormat()));
            } catch (IOException exception) {
                MachineMax.LOGGER.warn((org.slf4j.Marker) MARKER, "Failed to read sound file: {}", resourcelocation);
                exception.printStackTrace();
            }
        }
        return output;
    }

    @Override
    protected void apply(@NotNull Map<ResourceLocation, SoundData> pObject, @NotNull ResourceManager pResourceManager, @NotNull ProfilerFiller pProfiler) {
        sounds.clear();
        sounds.putAll(pObject);
    }

    public static SoundData getSound(ResourceLocation id) {
        if (!sounds.containsKey(id)) MachineMax.LOGGER.warn((org.slf4j.Marker) MARKER, "Sound not found: {}", id);
        return sounds.get(id);
    }

    @SubscribeEvent
    public static void registerClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new SoundAssetsManager());
    }
}
