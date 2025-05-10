package io.github.tt432.machinemax.common.vehicle.data;

import cn.solarmoon.spark_core.physics.SparkMathKt;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.tt432.machinemax.common.vehicle.Part;
import io.github.tt432.machinemax.common.vehicle.SubPart;
import io.github.tt432.machinemax.util.data.PosRotVelVel;
import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;

/**
 * 用于保存部件动态数据(位置，耐久度等)
 */
@Getter
public class PartData {
    public final ResourceLocation registryKey;
    public final String name;
    public final String variant;
    public final int textureIndex;
    public final String uuid;
    public final float durability;
    public final Map<String, PosRotVelVel> subPartTransforms;

    public static final Codec<PartData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("registryKey").forGetter(PartData::getRegistryKey),
            Codec.STRING.fieldOf("subpart").forGetter(PartData::getName),
            Codec.STRING.fieldOf("variant").forGetter(PartData::getVariant),
            Codec.INT.fieldOf("textureIndex").forGetter(PartData::getTextureIndex),
            Codec.STRING.fieldOf("uuid").forGetter(PartData::getUuid),
            Codec.FLOAT.fieldOf("durability").forGetter(PartData::getDurability),
            PosRotVelVel.MAP_CODEC.fieldOf("subPartTransforms").forGetter(PartData::getSubPartTransforms)
    ).apply(instance, PartData::new));

    public static final Codec<Map<String, PartData>> MAP_CODEC = CODEC.listOf().xmap(
            list -> {
                Map<String, PartData> map = new java.util.HashMap<>();
                for (PartData data : list) {
                    map.put(data.uuid, data);
                }
                return map;
            },
            map -> map.values().stream().toList()
    );

    public static final StreamCodec<FriendlyByteBuf, PartData> STREAM_CODEC = new StreamCodec<>() {

        @Override
        public @NotNull PartData decode(FriendlyByteBuf buffer) {
            ResourceLocation registryKey = buffer.readResourceLocation();
            String name = buffer.readUtf();
            String variant = buffer.readUtf();
            int textureIndex = buffer.readInt();
            String uuid = buffer.readUtf();
            float durability = buffer.readFloat();
            Map<String, PosRotVelVel> subPartTransforms = buffer.readJsonWithCodec(PosRotVelVel.MAP_CODEC);
            return new PartData(registryKey, name, variant, textureIndex, uuid, durability, subPartTransforms);
        }

        @Override
        public void encode(FriendlyByteBuf buffer, @NotNull PartData value) {
            buffer.writeResourceLocation(value.registryKey);
            buffer.writeUtf(value.name);
            buffer.writeUtf(value.variant);
            buffer.writeInt(value.textureIndex);
            buffer.writeUtf(value.uuid);
            buffer.writeFloat(value.durability);
            buffer.writeJsonWithCodec(PosRotVelVel.MAP_CODEC, value.subPartTransforms);
        }

    };

    public PartData(ResourceLocation registryKey, String name, String variant, int textureIndex, String uuid, float durability, Map<String, PosRotVelVel> subPartTransforms) {
        this.registryKey = registryKey;
        this.name = name;
        this.variant = variant;
        this.textureIndex = textureIndex;
        this.uuid = uuid;
        this.durability = durability;
        this.subPartTransforms = subPartTransforms;
        //校验数据
        for (Map.Entry<String, PosRotVelVel> entry : subPartTransforms.entrySet()) {
            String subPartName = entry.getKey();
            PosRotVelVel transform = entry.getValue();
            Vector3f position = transform.position();
            Quaternionf rotation = transform.rotation();
            Vector3f linearVel = transform.linearVel();
            Vector3f angularVel = transform.angularVel();
            if (!Vector3f.isValidVector(position) ||
                    !rotation.isFinite() ||
                    !Vector3f.isValidVector(linearVel) ||
                    !Vector3f.isValidVector(angularVel) ||
                    position.length() > 3e8 ||
                    linearVel.length() > 4e8 ||
                    angularVel.length() > 4e8) {
                throw new IllegalArgumentException("Invalid transform data for sub-part " + subPartName + " " + transform);
            }
        }
    }

    /**
     * 将部件数据打包为方便传输与读取的PartData
     *
     * @param part 部件实例
     */
    public PartData(Part part) {
        this.registryKey = part.type.registryKey;
        this.name = part.name;
        this.variant = part.variant;
        this.textureIndex = part.textureIndex;
        this.uuid = part.getUuid().toString();
        this.durability = part.durability;
        this.subPartTransforms = HashMap.newHashMap(1);
        Vector3f pos = new Vector3f();
        Quaternion rot = new Quaternion();
        Vector3f vel = new Vector3f();
        Vector3f angVel = new Vector3f();
        for (Map.Entry<String, SubPart> entry : part.subParts.entrySet()) {
            this.subPartTransforms.put(entry.getKey(), new PosRotVelVel(
                    entry.getValue().body.getPhysicsLocation(pos),
                    SparkMathKt.toQuaternionf(entry.getValue().body.getPhysicsRotation(rot)),
                    entry.getValue().body.getLinearVelocity(vel),
                    entry.getValue().body.getAngularVelocity(angVel)
            ));
        }
    }
}
