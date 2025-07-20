package io.github.sweetzonzi.machinemax.client.sound;

import cn.solarmoon.spark_core.sound.ISpreadingSoundSource;
import cn.solarmoon.spark_core.sound.SpreadingSoundInstance;
import io.github.sweetzonzi.machinemax.common.registry.MMSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

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
}
