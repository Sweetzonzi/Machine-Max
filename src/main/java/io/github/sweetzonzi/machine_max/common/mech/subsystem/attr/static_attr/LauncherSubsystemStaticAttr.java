package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.FmodSoundAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.module.BarrelModuleAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.module.BreechModuleAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.module.CapacitorModuleAttr;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 发射器子系统静态属性。<br>
 * 一个子系统代表一个发射管/挂架/炮闩，包含炮管、炮闩、电容三个模块。
 */
@Getter
public class LauncherSubsystemStaticAttr extends BasicSubsystemStaticAttr {
    /** 发射器开火音的 bank 与事件路径。 */
    public record LauncherFmodSoundAttr(ResourceLocation bank, String fire) {
        public static final Codec<LauncherFmodSoundAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("bank").forGetter(LauncherFmodSoundAttr::bank),
                FmodSoundAttr.EVENT_PATH_CODEC.fieldOf("fire").forGetter(LauncherFmodSoundAttr::fire)
        ).apply(instance, LauncherFmodSoundAttr::new));
    }

    /**
     * 发射器音效属性 — 包含基础音效和发射器特有音效。
     *
     * @param basicSounds  基础音效（onDestroyed / onActivated / onDeactivated）
     * @param dryFireSound 空膛击发音效（扣扳机但无弹药）
     * @param humanProgressSounds 人工段进度阈值到音效的映射，键是 {@code "0.0"}~{@code "1.0"} 的十进制字符串
     * @param fmod 实际开火的可选 FMOD 事件引用
     */
    public record LauncherSoundAttr(
        BasicSoundAttr basicSounds,
        SoundEvent dryFireSound,
        Map<String, SoundEvent> humanProgressSounds,
        Optional<LauncherFmodSoundAttr> fmod
    ) {
        public static final LauncherSoundAttr DEFAULT = new LauncherSoundAttr(
            BasicSoundAttr.DEFAULT,
            SoundEvent.createFixedRangeEvent(
                ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "subsystem.launcher.dry_fire"), 16),
            Map.of(),
            Optional.empty()
        );

        public static final Codec<LauncherSoundAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BasicSoundAttr.basicSounds(LauncherSoundAttr::basicSounds),
            SoundEvent.DIRECT_CODEC.optionalFieldOf("dry_fire", DEFAULT.dryFireSound)
                .forGetter(LauncherSoundAttr::dryFireSound),
            Codec.unboundedMap(Codec.STRING, SoundEvent.DIRECT_CODEC)
                .optionalFieldOf("human_progress_sounds", Map.of())
                .forGetter(LauncherSoundAttr::humanProgressSounds),
            LauncherFmodSoundAttr.CODEC.optionalFieldOf("fmod").forGetter(LauncherSoundAttr::fmod)
        ).apply(instance, LauncherSoundAttr::new));
    }

    // ====== 子系统级字段 ======

    /** 开火信号输入频道列表，优先级从高到低 */
    private final List<String> controlInputs;
    /**
     * 一次拉取装多少发：{@code true} 只把一发推进膛室，{@code false} 把整份 Belt 装进已装数据。<br>
     * 它只描述武器实际怎么装填，与口径、射速和供给者类型无关；第一方定义显式填写。
     */
    private final boolean singleRoundLoading;
    /**
     * 完整人力装填流程的基准秒数：本型号直接从弹药架取弹、由乘员全程装填时使用；0 表示不支持该路径。<br>
     * 只有交付方本身没有人工段（库存持有者）时，本字段才生效——机构声明了人工段时由机构负责用人。
     * 实际时长还要乘岗位倍率与乘员个人倍率（见装填计划 §2.2），换算由发射器在受理时做。
     */
    private final float humanReloadTime;
    /** 发射器专属音效属性 */
    private final LauncherSoundAttr launcherSoundAttr;

    // ====== 模块字段 ======

    /** 炮管模块（必填，有默认值） */
    private final BarrelModuleAttr barrel;
    /** 炮闩模块（必填，有默认值） */
    private final BreechModuleAttr breech;
    /** 电容模块（可空，null = 无电容/非电磁炮） */
    private final @javax.annotation.Nullable CapacitorModuleAttr capacitor;

    public static final MapCodec<LauncherSubsystemStaticAttr> CODEC =
            RecordCodecBuilder.<LauncherSubsystemStaticAttr>mapCodec(instance -> instance.group(
            BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
            Codec.STRING.listOf().optionalFieldOf("control_inputs", List.of("weapon_control"))
                    .forGetter(LauncherSubsystemStaticAttr::getControlInputs),
            Codec.BOOL.optionalFieldOf("single_round_loading", false)
                    .forGetter(LauncherSubsystemStaticAttr::isSingleRoundLoading),
            Codec.FLOAT.optionalFieldOf("human_reload_time", 0f)
                    .forGetter(LauncherSubsystemStaticAttr::getHumanReloadTime),
            LauncherSoundAttr.CODEC.optionalFieldOf("sounds", LauncherSoundAttr.DEFAULT)
                    .forGetter(LauncherSubsystemStaticAttr::getLauncherSounds),
            BarrelModuleAttr.CODEC.codec().optionalFieldOf("barrel", BarrelModuleAttr.DEFAULT)
                    .forGetter(LauncherSubsystemStaticAttr::getBarrel),
            BreechModuleAttr.CODEC.codec().optionalFieldOf("breech", BreechModuleAttr.DEFAULT)
                    .forGetter(LauncherSubsystemStaticAttr::getBreech),
            CapacitorModuleAttr.CODEC.codec().optionalFieldOf("capacitor")
                    .forGetter(attr -> Optional.ofNullable(attr.getCapacitor()))
    ).apply(instance, LauncherSubsystemStaticAttr::new))
            .validate(LauncherSubsystemStaticAttr::validateHumanReloadTime);

    public LauncherSubsystemStaticAttr(
            BasicAttr basicAttr,
            List<String> controlInputs,
            boolean singleRoundLoading,
            float humanReloadTime,
            LauncherSoundAttr sounds,
            BarrelModuleAttr barrel,
            BreechModuleAttr breech,
            Optional<CapacitorModuleAttr> capacitor) {
        super(basicAttr, sounds.basicSounds());
        this.controlInputs = controlInputs;
        this.singleRoundLoading = singleRoundLoading;
        this.humanReloadTime = humanReloadTime;
        this.launcherSoundAttr = sounds;
        this.barrel = barrel;
        this.breech = breech;
        this.capacitor = capacitor.orElse(null);
    }

    /**
     * 边界校验：人工段只能是有限非负数。显式的 0 表示本型号不支持人力直取路径，负值与非有限值是解码错误。
     */
    private static DataResult<LauncherSubsystemStaticAttr> validateHumanReloadTime(
            LauncherSubsystemStaticAttr attr) {
        float human = attr.humanReloadTime;
        if (!Float.isFinite(human)) {
            return DataResult.error(() -> "human_reload_time 必须是有限数，实际为 " + human);
        }
        if (human < 0f) {
            return DataResult.error(() -> "human_reload_time 不得为负（零长度的人工段按未配置处理），实际为 " + human);
        }
        return DataResult.success(attr);
    }

    // ====== 便捷访问器 ======

    /** 获取空膛击发音效 */
    public SoundEvent getDryFireSound() {
        return launcherSoundAttr.dryFireSound();
    }

    /** 获取发射器完整音效属性 */
    public LauncherSoundAttr getLauncherSounds() {
        return launcherSoundAttr;
    }

    /**
     * 人工段的进度音效映射；键的写法与装弹机型号的机械段音效相同（{@code "0.0"}~{@code "1.0"}）。<br>
     * 人力直取路径的两段声音都定位在本管上——人把弹取到炮闩跟前，机构不存在。
     */
    public Map<String, SoundEvent> getHumanProgressSounds() {
        return launcherSoundAttr.humanProgressSounds();
    }

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }

}
