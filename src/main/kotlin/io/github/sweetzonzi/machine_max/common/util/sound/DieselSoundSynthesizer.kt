package io.github.sweetzonzi.machine_max.common.util.sound

import cn.solarmoon.spark_core.sound.SoundData
import cn.solarmoon.spark_core.util.SoundPlayer
import cn.solarmoon.spark_core.util.SoundSynthesizers

/**
 * 内燃机音效合成器
 * 基于物理模型，利用 SoundSynthesizers 合成引擎声音
 */
object DieselSoundSynthesizer {

    /**
     * 合成单个气缸的点火（爆炸）声音
     * * @param cylinderVolume 单个气缸的容积（单位：升）。例如 2.0L 4缸引擎，此值为 0.5
     * @param load 引擎负载（油门开度），范围 0.0 (怠速) 到 1.0 (满载)
     * @return 合成的单个点火音效 (SoundData)
     */
    @JvmStatic
    @JvmOverloads
    fun createSingleCylinderFire(
        cylinderVolume: Double = 0.5,
        load: Double = 0.5
    ): SoundData {
        // 总时长，300ms足以捕获一个点火事件的完整尾音
        val duration = 0.5

        // --- 1. 基于物理参数计算合成参数 ---

        val loadFactor = load.coerceIn(0.0, 1.0)

        // [气缸容积] -> [共振基频]
        // 容积越大，频率越低。我们映射 0.1L -> 180Hz, 1.5L -> 40Hz
        val baseFreq = (40.0 + (1.5 - cylinderVolume.coerceIn(0.1, 1.5)) * 100.0)

        // [负载] -> [点火瞬态 "Crack"]
        // 负载越高，"Crack"越响亮、越明亮、衰减越快（更"清脆"）
        val noiseAmp = (0.2 + loadFactor * 0.6)       // 幅度 0.2 (怠速) -> 0.8 (满载)
        val noiseDecay = (0.07 - loadFactor * 0.05).coerceAtLeast(0.01) // 衰减 70ms -> 20ms
        val noiseCutoff = 3000.0 + loadFactor * 6000.0 // 截止频率 3kHz -> 9kHz (更亮)

        // [负载] -> [燃烧共振 "Thump"]
        // 负载越高，"Thump"越响亮、谐波越丰富（更"粗糙"）、衰减越快（更"紧实"）
        val tonalAmp = (0.3 + loadFactor * 0.5)        // 幅度 0.3 -> 0.8
        val tonalDecay = (0.15 - loadFactor * 0.05).coerceAtLeast(0.05) // 衰减 150ms -> 100ms
        // 负载高时，保留更多高次谐波，声音更粗糙
        val tonalCutoff = (baseFreq * 3.0 + loadFactor * (baseFreq * 5.0)).coerceAtMost(5000.0)

        // [负载] -> [排气脉冲 "Puff"]
        // 负载越高，排气脉冲越明显
        val exhaustAmp = (0.1 + loadFactor * 0.2)
        val exhaustDecay = 0.25 // 排气尾音较长

        // --- 2. 合成三个核心组件 ---

        // 组件1: "Crack" (点火瞬态)
        // 使用高斯白噪声
        val noiseWave = SoundSynthesizers.gaussianWhiteNoise(duration, noiseAmp)
        // ADSR: 极快起音 (2ms), 快速衰减 (由负载决定), 无持续
        val noiseEnv = SoundSynthesizers.ADSREnvelope(attack = 0.002, decay = noiseDecay, sustain = 0.0, release = 0.0)
        var ignition = SoundSynthesizers.applyADSREnvelope(noiseWave, noiseEnv)
        // 应用低通滤波器，截止频率由负载决定
        ignition = SoundSynthesizers.lowPassFilter(ignition, noiseCutoff, 0.1)

        // 组件2: "Thump" (燃烧共振)
        // 使用锯齿波，声音比正弦波更"粗糙"，更像爆炸
        val tonalWave = SoundSynthesizers.sawtoothWave(duration, baseFreq, tonalAmp)
        // ADSR: 快速起音 (5ms), 较慢衰减
        val tonalEnv = SoundSynthesizers.ADSREnvelope(attack = 0.005, decay = tonalDecay, sustain = 0.0, release = 0.0)
        var combustion = SoundSynthesizers.applyADSREnvelope(tonalWave, tonalEnv)
        // 应用低通滤波器，保留基频和少量谐波
        combustion = SoundSynthesizers.lowPassFilter(combustion, tonalCutoff, 0.3)

        // 组件3: "Puff" (排气脉冲)
        // 使用白噪声
        val exhaustWave = SoundSynthesizers.gaussianWhiteNoise(duration, exhaustAmp)
        // ADSR: 稍慢的起音 (10ms)，较长的衰减
        val exhaustEnv = SoundSynthesizers.ADSREnvelope(attack = 0.01, decay = exhaustDecay, sustain = 0.0, release = 0.0)
        var exhaustPuff = SoundSynthesizers.applyADSREnvelope(exhaustWave, exhaustEnv)
        // 强力低通，只保留非常低沉的"噗"声
        exhaustPuff = SoundSynthesizers.lowPassFilter(exhaustPuff, 250.0, 0.1)

        // --- 3. 混合三个组件 ---

        // 使用 mixSounds 将三个图层混合在一起
        // autoNormalize = true 可以防止三个声音叠加时产生削波（破音）
        return SoundSynthesizers.mixSounds(
            listOf(ignition, combustion, exhaustPuff),
            autoNormalize = true
        )
    }

    // ... (未来可以在这里添加循环合成函数)

    @JvmStatic
    fun main(args: Array<String>){
        val sound = createSingleCylinderFire(1.5, 1.0)
        SoundPlayer.playSound(sound)
    }
}

