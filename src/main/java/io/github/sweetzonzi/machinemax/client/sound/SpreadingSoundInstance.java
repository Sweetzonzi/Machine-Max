package io.github.sweetzonzi.machinemax.client.sound;

import io.github.sweetzonzi.machinemax.common.sound.ISpreadingSoundSource;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 声音传播范围的实现参考自https://github.com/MCModderAnchor/TACZ
 */
@OnlyIn(Dist.CLIENT)
public class SpreadingSoundInstance extends AbstractTickableSoundInstance {
    @Nullable
    public final ISpreadingSoundSource ISpreadingSoundSource;
    @Getter
    private final String name;
    public final LinkedHashMap<Vec3, Float> spreadDistances = new LinkedHashMap<>(20);
    public final LinkedHashMap<Vec3, Vec3> speeds = new LinkedHashMap<>(20);
    private final LinkedHashMap<Vec3, Float> ranges = new LinkedHashMap<>(20);
    private final LinkedHashMap<Vec3, Float> pitches = new LinkedHashMap<>(20);
    private final LinkedHashMap<Vec3, Float> volumes = new LinkedHashMap<>(20);
    public boolean isPlaying = false;

    /**
     * <p>针对单次声源的构造函数，用于创建单个定点声源的音效实例</p>
     *
     * @param soundEvent
     * @param soundType
     * @param position
     * @param speed
     * @param pitch
     * @param volume
     */
    public SpreadingSoundInstance(SoundEvent soundEvent, SoundSource soundType, Vec3 position, Vec3 speed, float range, float pitch, float volume) {
        super(soundEvent, soundType, SoundInstance.createUnseededRandom());
        this.ISpreadingSoundSource = null;
        this.attenuation = Attenuation.NONE;
        this.name = "IndividualSound";
        this.x = position.x;
        this.y = position.y;
        this.z = position.z;
        this.pitch = pitch;
        this.volume = volume;
        this.spreadDistances.put(position, 0F);
        this.speeds.put(position, speed);
        this.ranges.put(position, range);
        this.pitches.put(position, pitch);
        this.volumes.put(position, volume);
    }

    /**
     * <p>针对持续发出声音的声源的构造函数，用于创建位置速度音高等时刻改变声源的音效实例</p>
     *
     * @param soundEvent
     * @param soundType
     * @param ISpreadingSoundSource
     */
    public SpreadingSoundInstance(SoundEvent soundEvent, SoundSource soundType, ISpreadingSoundSource ISpreadingSoundSource, String name) {
        super(soundEvent, soundType, SoundInstance.createUnseededRandom());
        this.ISpreadingSoundSource = ISpreadingSoundSource;
        this.attenuation = Attenuation.NONE;
        this.name = name;
        this.x = ISpreadingSoundSource.getPosition(this).x;
        this.y = ISpreadingSoundSource.getPosition(this).y;
        this.z = ISpreadingSoundSource.getPosition(this).z;
        this.pitch = ISpreadingSoundSource.getPitch(this);
        this.volume = ISpreadingSoundSource.getVolume(this);
        this.spreadDistances.put(ISpreadingSoundSource.getPosition(this), 0F);
        this.speeds.put(ISpreadingSoundSource.getPosition(this), ISpreadingSoundSource.getSpeed(this));
        this.ranges.put(ISpreadingSoundSource.getPosition(this), ISpreadingSoundSource.getRange(this));
        this.pitches.put(ISpreadingSoundSource.getPosition(this), this.pitch);
        this.volumes.put(ISpreadingSoundSource.getPosition(this), this.volume);
    }

    @Override
    public void tick() {
        Iterator<Map.Entry<Vec3, Float>> iterator = this.spreadDistances.entrySet().iterator();
        if (!this.isPlaying) {
            //更新已有声源的传播距离
            while (iterator.hasNext()) {
                Map.Entry<Vec3, Float> entry = iterator.next();
                Vec3 position = entry.getKey();
                float distance = entry.getValue();
                if (distance > this.ranges.get(position)) {
                    //移除已达到传播范围上限的声源
                    iterator.remove();
                }
                float speed = getSoundSpeed(
                        position.scale(0.5).add(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().scale(0.5)),
                        Minecraft.getInstance().level);
                this.spreadDistances.put(position, distance + speed * 0.05f);
            }
        } else {
            if (iterator.hasNext()) {
                Vec3 position = iterator.next().getKey();
                float pitch = this.pitches.getOrDefault(position, 1.0f);
                float volume = this.volumes.getOrDefault(position, 1.0f);
                this.x = position.x;
                this.y = position.y;
                this.z = position.z;
                this.pitch = pitch;
                this.volume = volume;
                iterator.remove();
            }
        }
        if (ISpreadingSoundSource != null) {
            //记录最新的声源位置速度
            this.spreadDistances.put(ISpreadingSoundSource.getPosition(this), 0F);
            this.speeds.put(ISpreadingSoundSource.getPosition(this), ISpreadingSoundSource.getSpeed(this));
            this.pitches.put(ISpreadingSoundSource.getPosition(this), ISpreadingSoundSource.getPitch(this));
            this.volumes.put(ISpreadingSoundSource.getPosition(this), ISpreadingSoundSource.getVolume(this));
        }
    }

    public static float getSoundSpeed(Vec3 position, ClientLevel level) {
        //TODO:高度挂钩，维度设定挂钩
        return 100f;
    }

    public Vec3 getSpeed(Vec3 position) {
        return this.speeds.get(position);
    }

    public float getRange(Vec3 position) {
        return this.ranges.get(position);
    }

    public float getPitch(Vec3 position) {
        return this.pitches.get(position);
    }

    public float getVolume(Vec3 position) {
        return this.volumes.get(position);
    }

    public void setPitch(float pitch){
        this.pitch = pitch;
    }

    public void setVolume(float volume){
        this.volume = volume;
    }

}
