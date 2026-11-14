package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.FmodSoundAttr;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import java.util.Map;
import java.util.Optional;

/**
 * 自动装弹机子系统静态属性。<br>
 * 描述两段装填流程：可选的<b>人工准备</b>段（{@code human_reload_time}，由乘员执行）与必填的
 * <b>机械运行</b>段（{@code reload_time}），外加并发产能 {@code can_supply_multiple} 与两段各自的进度音效。
 * 库存与再生参数属于弹药架型号。
 * <p>
 * 机械段是一段正时长的流程：{@code reload_time} 必填且必须大于零。长度为 0 的段按"未配置"处理，
 * 而没有机械段的自动装弹机不能表达任何装填路径，因此在解码期就拒绝。人工段相反：0 表示全自动流程，
 * 只有负值或非有限值被拒绝。
 * </p>
 */
@Getter
public class AmmoLoaderSubsystemStaticAttr extends BasicSubsystemStaticAttr {

    /** 装填音的 bank 与事件路径。 */
    public record ReloadFmodSoundAttr(ResourceLocation bank, String reload) {
        public static final Codec<ReloadFmodSoundAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("bank").forGetter(ReloadFmodSoundAttr::bank),
                FmodSoundAttr.EVENT_PATH_CODEC.fieldOf("reload").forGetter(ReloadFmodSoundAttr::reload)
        ).apply(instance, ReloadFmodSoundAttr::new));
    }

    /**
     * 自动装弹机音效属性 — 基础音效加人工段、机械段各自的进度分段音效。
     *
     * @param basicSounds        基础音效（onDestroyed / onActivated / onDeactivated）
     * @param progressSounds     机械段进度阈值到音效的映射，键是 {@code "0.0"}~{@code "1.0"} 的十进制字符串
     * @param humanProgressSounds 人工段进度阈值到音效的映射，键的写法同上；声音定位在发射器上
     * @param fmod               可选的 FMOD 装填事件引用
     */
    public record AmmoLoaderSoundAttr(
            BasicSoundAttr basicSounds,
            Map<String, SoundEvent> progressSounds,
            Map<String, SoundEvent> humanProgressSounds,
            Optional<ReloadFmodSoundAttr> fmod
    ) {
        public static final AmmoLoaderSoundAttr DEFAULT = new AmmoLoaderSoundAttr(
                BasicSoundAttr.DEFAULT,
                Map.of(),
                Map.of(),
                Optional.empty()
        );

        public static final Codec<AmmoLoaderSoundAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BasicSoundAttr.basicSounds(AmmoLoaderSoundAttr::basicSounds),
                Codec.unboundedMap(Codec.STRING, SoundEvent.DIRECT_CODEC)
                        .optionalFieldOf("progress_sounds", Map.of())
                        .forGetter(AmmoLoaderSoundAttr::progressSounds),
                Codec.unboundedMap(Codec.STRING, SoundEvent.DIRECT_CODEC)
                        .optionalFieldOf("human_progress_sounds", Map.of())
                        .forGetter(AmmoLoaderSoundAttr::humanProgressSounds),
                ReloadFmodSoundAttr.CODEC.optionalFieldOf("fmod").forGetter(AmmoLoaderSoundAttr::fmod)
        ).apply(instance, AmmoLoaderSoundAttr::new));
    }

    /** 一整份交付的基准机械秒数（必填，必须大于零） */
    private final float reloadTime;

    /**
     * 人工准备的基准秒数；0 表示本型号没有人工段（全自动）。<br>
     * 人工段在机械段之前执行，实际时长还要乘岗位倍率与乘员个人倍率（见装填计划 §2.2）。配了人工段而没有
     * 座位接线到本机的装填发现频道时，这条流程永远等不到执行者——{@code AmmoLoaderSubsystem} 在发现阶段
     * 会就此告警一次。
     */
    private final float humanReloadTime;

    /**
     * 是否允许多个作业同时占用本机构。<br>
     * {@code false} 时一次只为一个作业分配槽位（独占且满速）；{@code true} 时不限槽位、各作业分别满速。
     */
    private final boolean canSupplyMultiple;

    /** 机械段的音效配置 */
    private final AmmoLoaderSoundAttr ammoLoaderSoundAttr;

    public static final MapCodec<AmmoLoaderSubsystemStaticAttr> CODEC =
            RecordCodecBuilder.<AmmoLoaderSubsystemStaticAttr>mapCodec(instance -> instance.group(
                    BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
                    Codec.FLOAT.fieldOf("reload_time")
                            .forGetter(AmmoLoaderSubsystemStaticAttr::getReloadTime),
                    Codec.FLOAT.optionalFieldOf("human_reload_time", 0f)
                            .forGetter(AmmoLoaderSubsystemStaticAttr::getHumanReloadTime),
                    Codec.BOOL.optionalFieldOf("can_supply_multiple", false)
                            .forGetter(AmmoLoaderSubsystemStaticAttr::isCanSupplyMultiple),
                    AmmoLoaderSoundAttr.CODEC.optionalFieldOf("sounds", AmmoLoaderSoundAttr.DEFAULT)
                            .forGetter(AmmoLoaderSubsystemStaticAttr::getAmmoLoaderSounds)
            ).apply(instance, AmmoLoaderSubsystemStaticAttr::new))
                    .validate(AmmoLoaderSubsystemStaticAttr::validateAttributes);

    public AmmoLoaderSubsystemStaticAttr(
            BasicAttr basicAttr,
            float reloadTime,
            float humanReloadTime,
            boolean canSupplyMultiple,
            AmmoLoaderSoundAttr sounds) {
        super(basicAttr, sounds.basicSounds());
        this.reloadTime = reloadTime;
        this.humanReloadTime = humanReloadTime;
        this.canSupplyMultiple = canSupplyMultiple;
        this.ammoLoaderSoundAttr = sounds;
    }

    /**
     * 边界校验：{@code basic_durability} 与 {@code reload_time} 必须是有限正数，{@code human_reload_time}
     * 只能是有限非负数。
     * 前者为零时机构一碰就毁，{@code reload_time} 为零时没有可执行的机械段——两者都不是合法配置；
     * 人工段按装填计划 §9 第 4 条处理：显式的 0 表示"不配置人工段"，负值或非有限值是解码错误。
     */
    private static DataResult<AmmoLoaderSubsystemStaticAttr> validateAttributes(
            AmmoLoaderSubsystemStaticAttr attr) {
        float durability = attr.basicAttr.basicDurability();
        if (!Float.isFinite(durability) || durability <= 0f) {
            return DataResult.error(() -> "basic_durability 必须为有限正数，实际为 " + durability);
        }
        float reloadTime = attr.reloadTime;
        if (!Float.isFinite(reloadTime)) {
            return DataResult.error(() -> "reload_time 必须是有限数，实际为 " + reloadTime);
        }
        if (reloadTime <= 0f) {
            return DataResult.error(() -> "reload_time 必须大于 0（零长度的机械段按未配置处理），实际为 " + reloadTime);
        }
        float human = attr.humanReloadTime;
        if (!Float.isFinite(human)) {
            return DataResult.error(() -> "human_reload_time 必须是有限数，实际为 " + human);
        }
        if (human < 0f) {
            return DataResult.error(() -> "human_reload_time 不得为负（零长度的人工段按未配置处理），实际为 " + human);
        }
        return DataResult.success(attr);
    }

    /** 机械段的完整音效属性 */
    public AmmoLoaderSoundAttr getAmmoLoaderSounds() {
        return ammoLoaderSoundAttr;
    }

    /** 进度阈值到音效的映射（键是 {@code "0.0"}~{@code "1.0"} 的十进制字符串） */
    public Map<String, SoundEvent> getProgressSounds() {
        return ammoLoaderSoundAttr.progressSounds();
    }

    /**
     * 人工段的进度音效映射；键的写法与 {@link #getProgressSounds()} 相同。
     * <p>人工段的声音定位在发射器上（人把弹交到炮闩跟前），机械段的声音定位在本机。
     */
    public Map<String, SoundEvent> getHumanProgressSounds() {
        return ammoLoaderSoundAttr.humanProgressSounds();
    }

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }

}
