package io.github.tt432.machinemax.util.data;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * 存储一个运动体的所有位姿速度信息。
 * <p>
 * Storage all posture and velocity info of a body.
 *
 * @param pos  位置坐标 Position
 * @param rot  四元数形式的旋转姿态 Rotation in quaternion
 * @param lVel 线速度 Liner velocity
 * @param aVel 角速度 Angular velocity
 */
public record PosRotVel(Vector3f pos, Quaternion rot, Vector3f lVel, Vector3f aVel) {
    /**
     * 用于网络发包传输运动体位姿速度信息的编解码器
     * <p>
     * Stream codec for body phys sync payload delivering.
     */
    public static final StreamCodec<ByteBuf, PosRotVel> DATA_CODEC = new StreamCodec<>() {
        public PosRotVel decode(ByteBuf data) {
            Vector3f pos = new Vector3f(data.readFloat(), data.readFloat(), data.readFloat());//解码位置 Decode position
            Quaternion rot = new Quaternion(data.readFloat(), data.readFloat(), data.readFloat(), data.readFloat());//解码姿态 Decode rotation
            Vector3f lVel = new Vector3f(data.readFloat(), data.readFloat(), data.readFloat());//解码线速度 Decode liner velocity
            Vector3f aVel = new Vector3f(data.readFloat(), data.readFloat(), data.readFloat());//解码角速度 Decode angular velocity
            return new PosRotVel(pos, rot, lVel, aVel);
        }

        public void encode(ByteBuf buffer, PosRotVel data) {
            buffer.writeFloat(data.pos.x);//编码位置 Encode position
            buffer.writeFloat(data.pos.y);
            buffer.writeFloat(data.pos.z);
            buffer.writeFloat(data.rot.getX());//编码姿态 Encode rotation
            buffer.writeFloat(data.rot.getY());
            buffer.writeFloat(data.rot.getZ());
            buffer.writeFloat(data.rot.getW());
            buffer.writeFloat(data.lVel.x);//编码线速度 Encode liner velocity
            buffer.writeFloat(data.lVel.y);
            buffer.writeFloat(data.lVel.z);
            buffer.writeFloat(data.aVel.x);//编码角速度 Encode Angular velocity
            buffer.writeFloat(data.aVel.y);
            buffer.writeFloat(data.aVel.z);
        }
    };
}
