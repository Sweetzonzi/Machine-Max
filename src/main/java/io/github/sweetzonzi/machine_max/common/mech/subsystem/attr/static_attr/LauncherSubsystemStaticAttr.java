package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.module.BarrelModuleAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.module.BreechModuleAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.module.CapacitorModuleAttr;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import java.util.List;
import java.util.Optional;

/**
 * 发射器子系统静态属性。<br>
 * 一个子系统代表一个发射管/挂架/炮闩，包含炮管、炮闩、电容三个模块。
 */
@Getter
public class LauncherSubsystemStaticAttr extends BasicSubsystemStaticAttr {

    /**
     * 发射器音效属性 — 包含基础音效和发射器特有音效。
     *
     * @param basicSounds  基础音效（onDestroyed / onActivated / onDeactivated）
     * @param dryFireSound 空膛击发音效（扣扳机但无弹药）
     */
    public record LauncherSoundAttr(
        BasicSoundAttr basicSounds,
        SoundEvent dryFireSound
    ) {
        public static final LauncherSoundAttr DEFAULT = new LauncherSoundAttr(
            BasicSoundAttr.DEFAULT,
            SoundEvent.createFixedRangeEvent(
                ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "subsystem.launcher.dry_fire"), 16)
        );

        public static final Codec<LauncherSoundAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BasicSoundAttr.basicSounds(LauncherSoundAttr::basicSounds),
            SoundEvent.DIRECT_CODEC.optionalFieldOf("dry_fire", DEFAULT.dryFireSound)
                .forGetter(LauncherSoundAttr::dryFireSound)
        ).apply(instance, LauncherSoundAttr::new));
    }

    // ====== 子系统级字段 ======

    /** 开火信号输入频道列表，优先级从高到低 */
    private final List<String> controlInputs;
    /** 弹药供给发现频道列表（空列表 = 万能接收模式） */
    private final List<String> ammoInputs;
    /** 发射器专属音效属性 */
    private final LauncherSoundAttr launcherSoundAttr;

    // ====== 模块字段 ======

    /** 炮管模块（必填，有默认值） */
    private final BarrelModuleAttr barrel;
    /** 炮闩模块（必填，有默认值） */
    private final BreechModuleAttr breech;
    /** 电容模块（可空，null = 无电容/非电磁炮） */
    private final @javax.annotation.Nullable CapacitorModuleAttr capacitor;

    public static final MapCodec<LauncherSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
            Codec.STRING.listOf().optionalFieldOf("control_inputs", List.of("weapon_control"))
                    .forGetter(LauncherSubsystemStaticAttr::getControlInputs),
            Codec.STRING.listOf().optionalFieldOf("ammo_inputs", List.of())
                    .forGetter(LauncherSubsystemStaticAttr::getAmmoInputs),
            LauncherSoundAttr.CODEC.optionalFieldOf("sounds", LauncherSoundAttr.DEFAULT)
                    .forGetter(LauncherSubsystemStaticAttr::getLauncherSounds),
            BarrelModuleAttr.CODEC.codec().optionalFieldOf("barrel", BarrelModuleAttr.DEFAULT)
                    .forGetter(LauncherSubsystemStaticAttr::getBarrel),
            BreechModuleAttr.CODEC.codec().optionalFieldOf("breech", BreechModuleAttr.DEFAULT)
                    .forGetter(LauncherSubsystemStaticAttr::getBreech),
            CapacitorModuleAttr.CODEC.codec().optionalFieldOf("capacitor")
                    .forGetter(attr -> Optional.ofNullable(attr.getCapacitor()))
    ).apply(instance, LauncherSubsystemStaticAttr::new));

    public LauncherSubsystemStaticAttr(
            BasicAttr basicAttr,
            List<String> controlInputs,
            List<String> ammoInputs,
            LauncherSoundAttr sounds,
            BarrelModuleAttr barrel,
            BreechModuleAttr breech,
            Optional<CapacitorModuleAttr> capacitor) {
        super(basicAttr, sounds.basicSounds());
        this.controlInputs = controlInputs;
        this.ammoInputs = ammoInputs;
        this.launcherSoundAttr = sounds;
        this.barrel = barrel;
        this.breech = breech;
        this.capacitor = capacitor.orElse(null);
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

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.LAUNCHER;
    }
}
