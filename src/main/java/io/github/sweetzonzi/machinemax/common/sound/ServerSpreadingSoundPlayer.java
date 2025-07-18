package io.github.sweetzonzi.machinemax.common.sound;

import io.github.sweetzonzi.machinemax.network.payload.SpreadingSoundPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public class ServerSpreadingSoundPlayer implements ISpreadingSoundPlayer {

    public void playSpreadingSound(Level level, SoundEvent soundEvent, SoundSource soundType, Vec3 position, Vec3 speed, float range, float pitch, float volume) {
        if (level instanceof ServerLevel serverLevel)
            PacketDistributor.sendToPlayersNear(
                    serverLevel,
                    null,
                    position.x, position.y, position.z,
                    2 * range,
                    new SpreadingSoundPayload(soundEvent, soundType, position, speed, range, pitch, volume)
            );
        else throw new IllegalArgumentException("method was called on a client-side level");
    }

    public void playSpreadingSound(Level level, SoundEvent soundEvent, SoundSource soundType, ISpreadingSoundSource ISpreadingSoundSource, String name) {
        if (level instanceof ServerLevel serverLevel) {
            //TODO: 怎么找到客户端对应的ISpreadingSoundSource？
        } else throw new IllegalArgumentException("method was called on a client-side level");
    }
}
