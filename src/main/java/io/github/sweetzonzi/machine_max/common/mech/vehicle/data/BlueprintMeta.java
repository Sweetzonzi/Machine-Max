package io.github.sweetzonzi.machine_max.common.mech.vehicle.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 蓝图元信息：作者、创建时间与设计描述。
 *
 * <p>本类型<b>只参与 JSON 持久化</b>（{@link VehicleData#CODEC}），<b>不并入</b>
 * {@link VehicleData#STREAM_CODEC}：服务端从存档恢复载具时不关心元信息，跨网络时由载荷
 * 以独立字段携带（抄录走 {@code VehicleDataSavedPayload}，取出走
 * {@code BlueprintExtractLocalPayload}）。</p>
 *
 * <p>三个子字段全部可选，因此旧存档与旧蓝图文件天然兼容，无需迁移。</p>
 *
 * @param author      入库时的玩家名
 * @param createdAt   ISO-8601 时间戳
 * @param description 玩家填写的设计描述
 */
public record BlueprintMeta(String author, String createdAt, String description) {
    /** 空元信息，作为缺省值使用 */
    public static final BlueprintMeta EMPTY = new BlueprintMeta("", "", "");

    public static final Codec<BlueprintMeta> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("author", "").forGetter(BlueprintMeta::author),
            Codec.STRING.optionalFieldOf("created_at", "").forGetter(BlueprintMeta::createdAt),
            Codec.STRING.optionalFieldOf("description", "").forGetter(BlueprintMeta::description)
    ).apply(instance, BlueprintMeta::new));

    /** 仅供网络载荷独立字段使用，{@link VehicleData#STREAM_CODEC} 不调用它 */
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintMeta> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, BlueprintMeta::author,
            ByteBufCodecs.STRING_UTF8, BlueprintMeta::createdAt,
            ByteBufCodecs.STRING_UTF8, BlueprintMeta::description,
            BlueprintMeta::new
    );

    /** 三个子字段均为空时返回 true */
    public boolean isEmpty() {
        return author.isEmpty() && createdAt.isEmpty() && description.isEmpty();
    }
}
