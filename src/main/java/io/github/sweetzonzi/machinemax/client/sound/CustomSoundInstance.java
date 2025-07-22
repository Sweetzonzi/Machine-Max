package io.github.sweetzonzi.machinemax.client.sound;

import cn.solarmoon.spark_core.sound.ISpreadingSoundSource;
import cn.solarmoon.spark_core.sound.SpreadingSoundInstance;
import com.mojang.blaze3d.audio.SoundBuffer;
import io.github.sweetzonzi.machinemax.common.registry.MMSounds;
import io.github.sweetzonzi.machinemax.external.manager.SoundAssetsManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent;

import javax.annotation.Nullable;
import javax.sound.sampled.AudioFormat;
/**
 * 参考自TACZ的GunSoundInstance
 */
@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class CustomSoundInstance extends SpreadingSoundInstance {
    /**
     * <p>针对单次声源的构造函数，用于创建单个定点声源的音效实例</p>
     *
     * @param soundType
     * @param position
     * @param speed
     * @param range
     * @param pitch
     * @param volume
     */
    public CustomSoundInstance(SoundSource soundType, ResourceLocation name, Vec3 position, Vec3 speed, float range, float pitch, float volume) {
        super(MMSounds.getCUSTOM_SOUND().get(), soundType, name, position, speed, range, pitch, volume);
    }

    /**
     * <p>针对持续发出声音的声源的构造函数，用于创建位置速度音高等时刻改变声源的音效实例</p>
     *
     * @param soundType
     * @param ISpreadingSoundSource
     * @param name
     */
    public CustomSoundInstance(SoundSource soundType, ResourceLocation name, ISpreadingSoundSource ISpreadingSoundSource) {
        super(MMSounds.getCUSTOM_SOUND().get(), soundType, name, ISpreadingSoundSource);
    }

    @Nullable
    public SoundBuffer getSoundBuffer() {
        SoundAssetsManager.SoundData soundData = SoundAssetsManager.getSound(this.name);
        if (soundData == null) {
            return null;
        }
        AudioFormat rawFormat = soundData.audioFormat();
        if (rawFormat.getChannels() > 1) {
            AudioFormat monoFormat = new AudioFormat(rawFormat.getEncoding(), rawFormat.getSampleRate(), rawFormat.getSampleSizeInBits(), 1, rawFormat.getFrameSize(), rawFormat.getFrameRate(), rawFormat.isBigEndian(), rawFormat.properties());
            return new SoundBuffer(soundData.byteBuffer(), monoFormat);
        }
        return new SoundBuffer(soundData.byteBuffer(), soundData.audioFormat());
    }

    @SubscribeEvent
    public static void onPlaySoundSource(PlaySoundSourceEvent event) {
        if (event.getSound() instanceof CustomSoundInstance instance) {
            SoundBuffer soundBuffer = instance.getSoundBuffer();
            if (soundBuffer != null) {
                event.getChannel().attachStaticBuffer(soundBuffer);
                event.getChannel().play();
            }
        }
    }
}
