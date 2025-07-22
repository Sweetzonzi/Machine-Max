package io.github.sweetzonzi.machinemax.client.sound;

import cn.solarmoon.spark_core.mixin_interface.ISoundManagerMixin;
import cn.solarmoon.spark_core.sound.ISpreadingSoundSource;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ClientCustomSoundPlayer implements ICustomSoundPlayer{

    @Override
    public void playCustomSound(SoundSource soundType, ResourceLocation path, Vec3 position, Vec3 speed, float range, float pitch, float volume) {
        var soundManager = (ISoundManagerMixin) Minecraft.getInstance().getSoundManager();
        soundManager.machine_Max$playSpreading(new CustomSoundInstance(soundType, path, position, speed, range, pitch, volume));
    }

    @Override
    public void playCustomSound(SoundSource soundType, ResourceLocation path, ISpreadingSoundSource spreadingSoundSource) {
        var soundManager = (ISoundManagerMixin) Minecraft.getInstance().getSoundManager();
        soundManager.machine_Max$playSpreading(new CustomSoundInstance(soundType, path, spreadingSoundSource));
    }
}
