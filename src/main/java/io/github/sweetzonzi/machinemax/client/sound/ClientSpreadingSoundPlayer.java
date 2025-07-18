package io.github.sweetzonzi.machinemax.client.sound;

import io.github.sweetzonzi.machinemax.common.sound.ISpreadingSoundPlayer;
import io.github.sweetzonzi.machinemax.common.sound.ISpreadingSoundSource;
import io.github.sweetzonzi.machinemax.mixin_interface.ISoundManagerMixin;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ClientSpreadingSoundPlayer implements ISpreadingSoundPlayer {
    public void playSpreadingSound(SpreadingSoundInstance soundInstance) {
        ISoundManagerMixin soundManager = (ISoundManagerMixin) Minecraft.getInstance().getSoundManager();
        soundManager.machine_Max$playSpreading(soundInstance);
    }

    public void playSpreadingSound(Level level, SoundEvent soundEvent, SoundSource soundType, Vec3 position, Vec3 speed, float range, float pitch, float volume) {
        playSpreadingSound(new SpreadingSoundInstance(soundEvent, soundType, position, speed, range, pitch, volume));
    }

    public void playSpreadingSound(Level level, SoundEvent soundEvent, SoundSource soundType, ISpreadingSoundSource ISpreadingSoundSource, String name) {
        playSpreadingSound(new SpreadingSoundInstance(soundEvent, soundType, ISpreadingSoundSource, name));
    }
}
