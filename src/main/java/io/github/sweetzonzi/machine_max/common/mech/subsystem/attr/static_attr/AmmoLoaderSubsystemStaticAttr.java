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
 * 两段都可以是零时长，含义各不相同：机械段写 0 表示本机构没有机械动作（弹链或弹匣直接接在枪上，
 * 交付瞬时完成，节奏由上游弹药架的整批回充决定），人工段写 0 表示本型号走全自动流程。负值与非有限值
 * 在解码期拒绝。
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
     * @param progress           机械段进度阈值到音效的映射，键是 {@code "0.0"}~{@code "1.0"} 的十进制字符串
     * @param humanProgress      人工段进度阈值到音效的映射，键的写法同上；声音定位在发射器上
     * @param fmod               可选的 FMOD 装填事件引用
     */
    public record AmmoLoaderSoundAttr(
            BasicSoundAttr basicSounds,
            Map<String, SoundEvent> progress,
            Map<String, SoundEvent> humanProgress,
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
                        .optionalFieldOf("progress", Map.of())
                        .forGetter(AmmoLoaderSoundAttr::progress),
                Codec.unboundedMap(Codec.STRING, SoundEvent.DIRECT_CODEC)
                        .optionalFieldOf("human_progress", Map.of())
                        .forGetter(AmmoLoaderSoundAttr::humanProgress),
                ReloadFmodSoundAttr.CODEC.optionalFieldOf("fmod").forGetter(AmmoLoaderSoundAttr::fmod)
        ).apply(instance, AmmoLoaderSoundAttr::new));
    }

    /** 一整份交付的基准机械秒数（必填，非负；0 表示没有机械段，交付瞬时完成） */
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
     * 边界校验：{@code basic_durability} 必须是有限正数，{@code reload_time} 与 {@code human_reload_time}
     * 只能是有限非负数。
     * 耐久为零时机构一碰就毁，不是合法配置；两个时长字段的 0 各有含义（没有机械段／没有人工段），
     * 只有负值与非有限值是解码错误。
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
        if (reloadTime < 0f) {
            return DataResult.error(() -> "reload_time 不得为负（0 表示没有机械段），实际为 " + reloadTime);
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
        return ammoLoaderSoundAttr.progress();
    }

    /**
     * 人工段的进度音效映射；键的写法与 {@link #getProgressSounds()} 相同。
     * <p>人工段的声音定位在发射器上（人把弹交到炮闩跟前），机械段的声音定位在本机。
     */
    public Map<String, SoundEvent> getHumanProgressSounds() {
        return ammoLoaderSoundAttr.humanProgress();
    }

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }

}
