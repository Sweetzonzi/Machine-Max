package io.github.sweetzonzi.machine_max.common.mech;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/**
 * 内容包对 FMOD Studio 事件的引用。解码仅校验标识符与路径格式，bank 和事件由客户端播放端解析。
 *
 * @param bank SparkCore 内容包中的 bank ID
 * @param event Studio 事件路径，形如 {@code event:/vehicle/engine}
 */
public record FmodSoundAttr(ResourceLocation bank, String event) {
    /** Studio 事件路径的内容包入口校验，各用途的音效配置共用。 */
    public static final Codec<String> EVENT_PATH_CODEC = Codec.STRING.validate(
            path -> path.startsWith("event:/") && path.length() > "event:/".length()
                    ? DataResult.success(path) : DataResult.error(() -> "FMOD 事件路径应为 event:/<名称>"));

    public static final Codec<FmodSoundAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("bank").forGetter(FmodSoundAttr::bank),
            EVENT_PATH_CODEC.fieldOf("event").forGetter(FmodSoundAttr::event)
    ).apply(instance, FmodSoundAttr::new));
}
