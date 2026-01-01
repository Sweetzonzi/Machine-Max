package io.github.sweetzonzi.machine_max.util.data;

import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import lombok.Getter;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;

import static io.github.sweetzonzi.machine_max.util.data.PosRotVelVel.VECTOR3F_CODEC;
@Getter
public class PosRot {
    Vector3f position;
    Quaternionf rotation;

    public static final Codec<PosRot> CODEC = RecordCodecBuilder.create((instance) -> instance.group(
        VECTOR3F_CODEC.fieldOf("position").forGetter(PosRot::getPosition),
        ExtraCodecs.QUATERNIONF.fieldOf("rotation").forGetter(PosRot::getRotation)
    ).apply(instance, PosRot::new));

    public static final StreamCodec<ByteBuf, PosRot> STREAM_CODEC = new StreamCodec<>() {
        public @NotNull PosRot decode(ByteBuf data) {
            Vector3f pos = new Vector3f(data.readFloat(), data.readFloat(), data.readFloat());//解码位置 Decode position
            Quaternionf rot = new Quaternionf(data.readFloat(), data.readFloat(), data.readFloat(), data.readFloat());//解码姿态 Decode rotation
            return new PosRot(pos, rot);
        }

        public void encode(ByteBuf buffer, PosRot data) {
            buffer.writeFloat(data.position.x);//编码位置 Encode position
            buffer.writeFloat(data.position.y);
            buffer.writeFloat(data.position.z);
            buffer.writeFloat(data.rotation.x);//编码姿态 Encode rotation
            buffer.writeFloat(data.rotation.y);
            buffer.writeFloat(data.rotation.z);
            buffer.writeFloat(data.rotation.w);
        }
    };

    public PosRot(Vector3f position, Quaternionf rotation) {
        this.position = position;
        this.rotation = rotation;
    }

    public PosRot(Transform transform) {
        this.position = transform.getTranslation();
        this.rotation = SparkMathKt.toQuaternionf(transform.getRotation());
    }

    public Transform toTransform() {
        return new Transform(position, SparkMathKt.toBQuaternion(rotation));
    }
}
