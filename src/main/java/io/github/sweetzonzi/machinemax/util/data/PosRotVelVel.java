package io.github.sweetzonzi.machinemax.util.data;

import com.jme3.math.Vector3f;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.Util;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import org.joml.Quaternionf;

import java.util.List;
import java.util.Map;

public record PosRotVelVel(
        Vector3f position,
        Quaternionf rotation,
        Vector3f linearVel,
        Vector3f angularVel
) {
    public static final Codec<Vector3f> VECTOR3F_CODEC = Codec.FLOAT
            .listOf()
            .comapFlatMap(
                    a -> Util.fixedSize(a, 3).map(s -> new Vector3f(s.getFirst(), s.get(1), s.get(2))),
                    v -> List.of(v.get(0), v.get(1), v.get(2))
            );

    public static final Codec<PosRotVelVel> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            VECTOR3F_CODEC.fieldOf("position").forGetter(PosRotVelVel::position),
            ExtraCodecs.QUATERNIONF.fieldOf("rotation").forGetter(PosRotVelVel::rotation),
            VECTOR3F_CODEC.fieldOf("linearVel").forGetter(PosRotVelVel::linearVel),
            VECTOR3F_CODEC.fieldOf("angularVel").forGetter(PosRotVelVel::angularVel)
    ).apply(instance, PosRotVelVel::new));

    public static final Codec<Map<String, PosRotVelVel>> MAP_CODEC = Codec.unboundedMap(
            Codec.STRING,// 键：子部件名称，字符串
            PosRotVelVel.CODEC// 值：子部件位置、旋转、线速度和角速度
    );

    public static final StreamCodec<ByteBuf, PosRotVelVel> STREAM_CODEC = new StreamCodec<>() {
        public PosRotVelVel decode(ByteBuf data) {
            Vector3f pos = new Vector3f(data.readFloat(), data.readFloat(), data.readFloat());//解码位置 Decode position
            Quaternionf rot = new Quaternionf(data.readFloat(), data.readFloat(), data.readFloat(), data.readFloat());//解码姿态 Decode rotation
            Vector3f lVel = new Vector3f(data.readFloat(), data.readFloat(), data.readFloat());//解码线速度 Decode liner velocity
            Vector3f aVel = new Vector3f(data.readFloat(), data.readFloat(), data.readFloat());//解码角速度 Decode angular velocity
            return new PosRotVelVel(pos, rot, lVel, aVel);
        }

        public void encode(ByteBuf buffer, PosRotVelVel data) {
            buffer.writeFloat(data.position.x);//编码位置 Encode position
            buffer.writeFloat(data.position.y);
            buffer.writeFloat(data.position.z);
            buffer.writeFloat(data.rotation.x);//编码姿态 Encode rotation
            buffer.writeFloat(data.rotation.y);
            buffer.writeFloat(data.rotation.z);
            buffer.writeFloat(data.rotation.w);
            buffer.writeFloat(data.linearVel.x);//编码线速度 Encode liner velocity
            buffer.writeFloat(data.linearVel.y);
            buffer.writeFloat(data.linearVel.z);
            buffer.writeFloat(data.angularVel.x);//编码角速度 Encode Angular velocity
            buffer.writeFloat(data.angularVel.y);
            buffer.writeFloat(data.angularVel.z);
        }
    };
}
