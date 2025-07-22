package io.github.sweetzonzi.machinemax.client.sound;

import cn.solarmoon.spark_core.sound.ClientSpreadingSoundPlayer;
import cn.solarmoon.spark_core.sound.ISpreadingSoundPlayer;
import cn.solarmoon.spark_core.sound.ISpreadingSoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.function.Supplier;

public class CustomSoundHelper {
    public static final ICustomSoundPlayer INSTANCE = init();

    private static ICustomSoundPlayer init() {
        Supplier<ICustomSoundPlayer> spreadingSoundManager;
        if (FMLEnvironment.dist.isClient()) {
            spreadingSoundManager = ClientCustomSoundPlayer::new;
        } else {
            spreadingSoundManager = null;
        }

        if (spreadingSoundManager != null) {
            return spreadingSoundManager.get();
        } else return null;
    }

    public static void playCustomSound(SoundSource soundType, ResourceLocation path, Vec3 position, Vec3 speed, float range, float pitch, float volume) {
        if (INSTANCE != null) INSTANCE.playCustomSound(soundType, path, position, speed, range, pitch, volume);
    }

    public static void playCustomSound(SoundSource soundType, ResourceLocation path, ISpreadingSoundSource spreadingSoundSource) {
        if (INSTANCE != null) INSTANCE.playCustomSound(soundType, path, spreadingSoundSource);
    }
}
