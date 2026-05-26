package io.github.sweetzonzi.machine_max.common.mech.control;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.*;

/**
 * 控制组，挂载在 AbstractControllableSubsystem 上。<br>
 * 每个座椅/遥控站持有两个级别：一个始终激活的 baseGroup，和一系列可切换的子控制组。<br>
 * 控制组负责将玩家输入（移动/视角/离散按键）翻译为对特定信号频道的输出。
 */
public class ControlGroup {

    /** 控制组唯一标识名，baseGroup 固定为 "base" */
    public final String name;

    /** 控制模式，决定客户端按键语义分派方式 */
    public final ControlMode controlMode;

    /** 移动输入输出映射 {频道名: [目标名称列表]} */
    public final Map<String, List<String>> moveTargets;

    /** 视角输入输出映射 {频道名: [目标名称列表]} */
    public final Map<String, List<String>> viewTargets;

    /** 常规按键输入输出映射 {频道名: [目标名称列表]}，如离合/换挡/灯光等 KeyInputMapping 事件 */
    public final Map<String, List<String>> regularTargets;

    /** 离散按键绑定列表 */
    public final List<ControlBinding> bindings;

    public ControlGroup(String name, ControlMode controlMode,
                        Map<String, List<String>> moveTargets,
                        Map<String, List<String>> viewTargets,
                        Map<String, List<String>> regularTargets,
                        List<ControlBinding> bindings) {
        this.name = name;
        this.controlMode = controlMode != null ? controlMode : ControlMode.INHERIT;
        this.moveTargets = moveTargets != null ? Map.copyOf(moveTargets) : Collections.emptyMap();
        this.viewTargets = viewTargets != null ? Map.copyOf(viewTargets) : Collections.emptyMap();
        this.regularTargets = regularTargets != null ? Map.copyOf(regularTargets) : Collections.emptyMap();
        this.bindings = bindings != null ? List.copyOf(bindings) : Collections.emptyList();
    }

    /**
     * 便捷构造：适用于纯离散按键的控制组（无移动/视角/常规输出）。
     */
    public ControlGroup(String name, ControlMode controlMode, List<ControlBinding> bindings) {
        this(name, controlMode, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), bindings);
    }

    private static final Codec<Map<String, List<String>>> TARGET_MAP_CODEC = Codec.unboundedMap(
            Codec.STRING,
            Codec.STRING.listOf()
    );

    public static final Codec<ControlGroup> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(g -> g.name),
            ControlMode.CODEC.optionalFieldOf("control_mode", ControlMode.INHERIT).forGetter(g -> g.controlMode),
            TARGET_MAP_CODEC.optionalFieldOf("move_targets", Collections.emptyMap()).forGetter(g -> g.moveTargets),
            TARGET_MAP_CODEC.optionalFieldOf("view_targets", Collections.emptyMap()).forGetter(g -> g.viewTargets),
            TARGET_MAP_CODEC.optionalFieldOf("regular_targets", Collections.emptyMap()).forGetter(g -> g.regularTargets),
            ControlBinding.CODEC.listOf().optionalFieldOf("bindings", Collections.emptyList()).forGetter(g -> g.bindings)
    ).apply(instance, ControlGroup::new));

    private static final StreamCodec<ByteBuf, Map<String, List<String>>> TARGET_MAP_STREAM_CODEC = new StreamCodec<>() {
        @Override
        public Map<String, List<String>> decode(ByteBuf buf) {
            int size = ByteBufCodecs.VAR_INT.decode(buf);
            Map<String, List<String>> map = new HashMap<>();
            for (int i = 0; i < size; i++) {
                String key = ByteBufCodecs.STRING_UTF8.decode(buf);
                int listSize = ByteBufCodecs.VAR_INT.decode(buf);
                List<String> list = new ArrayList<>();
                for (int j = 0; j < listSize; j++) {
                    list.add(ByteBufCodecs.STRING_UTF8.decode(buf));
                }
                map.put(key, list);
            }
            return map;
        }

        @Override
        public void encode(ByteBuf buf, Map<String, List<String>> map) {
            ByteBufCodecs.VAR_INT.encode(buf, map.size());
            for (Map.Entry<String, List<String>> entry : map.entrySet()) {
                ByteBufCodecs.STRING_UTF8.encode(buf, entry.getKey());
                ByteBufCodecs.VAR_INT.encode(buf, entry.getValue().size());
                for (String s : entry.getValue()) {
                    ByteBufCodecs.STRING_UTF8.encode(buf, s);
                }
            }
        }
    };

    public static final StreamCodec<ByteBuf, ControlGroup> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, g -> g.name,
            ControlMode.STREAM_CODEC, g -> g.controlMode,
            TARGET_MAP_STREAM_CODEC, g -> g.moveTargets,
            TARGET_MAP_STREAM_CODEC, g -> g.viewTargets,
            TARGET_MAP_STREAM_CODEC, g -> g.regularTargets,
            ControlBinding.STREAM_CODEC.apply(ByteBufCodecs.list()), g -> g.bindings,
            ControlGroup::new
    );
}
