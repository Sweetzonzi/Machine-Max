package io.github.tt432.machinemax.util.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public record PosRotVelVel(
        Vec3 position,
        Quaternionf rotation,
        Vec3 linearVel,
        Vec3 angularVel
) {
    public static final Codec<PosRotVelVel> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Vec3.CODEC.fieldOf("position").forGetter(PosRotVelVel::position),
            ExtraCodecs.QUATERNIONF.fieldOf("rotation").forGetter(PosRotVelVel::rotation),
            Vec3.CODEC.fieldOf("linearVel").forGetter(PosRotVelVel::linearVel),
            Vec3.CODEC.fieldOf("angularVel").forGetter(PosRotVelVel::angularVel)
    ).apply(instance, PosRotVelVel::new));

    public static final StreamCodec<ByteBuf, PosRotVelVel> DATA_CODEC = new StreamCodec<>() {
        public PosRotVelVel decode(ByteBuf data) {
            Vec3 pos = new Vec3(data.readDouble(), data.readDouble(), data.readDouble());//解码位置 Decode position
            Quaternionf rot = new Quaternionf(data.readFloat(), data.readFloat(), data.readFloat(), data.readFloat());//解码姿态 Decode rotation
            Vec3 lVel = new Vec3(data.readDouble(), data.readDouble(), data.readDouble());//解码线速度 Decode liner velocity
            Vec3 aVel = new Vec3(data.readDouble(), data.readDouble(), data.readDouble());//解码角速度 Decode angular velocity
            PosRotVelVel result = new PosRotVelVel(pos, rot, lVel, aVel);
            return result;
        }

        public void encode(ByteBuf buffer, PosRotVelVel data) {
            buffer.writeDouble(data.position.x);//编码位置 Encode position
            buffer.writeDouble(data.position.y);
            buffer.writeDouble(data.position.z);
            buffer.writeFloat(data.rotation.x);//编码姿态 Encode rotation
            buffer.writeFloat(data.rotation.y);
            buffer.writeFloat(data.rotation.z);
            buffer.writeFloat(data.rotation.w);
            buffer.writeDouble(data.linearVel.x);//编码线速度 Encode liner velocity
            buffer.writeDouble(data.linearVel.y);
            buffer.writeDouble(data.linearVel.z);
            buffer.writeDouble(data.angularVel.x);//编码角速度 Encode Angular velocity
            buffer.writeDouble(data.angularVel.y);
            buffer.writeDouble(data.angularVel.z);
        }
    };
}
