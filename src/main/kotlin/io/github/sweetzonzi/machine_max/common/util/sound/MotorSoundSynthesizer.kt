package io.github.sweetzonzi.machine_max.common.util.sound

import cn.solarmoon.spark_core.sound.SoundData
import cn.solarmoon.spark_core.util.sound.WaveGenerators
import cn.solarmoon.spark_core.util.sound.WaveEffects
import cn.solarmoon.spark_core.util.sound.filter.MonoFilter
import cn.solarmoon.spark_core.util.toSoundData

/**
 * 无刷电机音效合成器
 * 基于物理参数合成逼真的无刷电机音效
 */
object MotorSoundSynthesizer {

    /**
     * 无刷电机参数配置
     * @param polePairs 极对数
     * @param pwmFrequency PWM载波频率(Hz)，默认8kHz
     * @param maxRPM 最大转速(RPM)，用于归一化
     * @param mechanicalResonanceFreq 机械共振频率(Hz)
     */
    data class MotorConfig(
        val polePairs: Int = 4,
        val pwmFrequency: Double = 8000.0,
        val maxRPM: Double = 20000.0,
        val mechanicalResonanceFreq: Double = 1200.0
    )

    // 随机源用于相位生成
    private val random = java.util.Random()

    /**
     * 合成完整的无刷电机音效 - 改进版
     */
    @JvmStatic
    @JvmOverloads
    fun synthesizeBrushlessMotor(
        duration: Double,
        rpm: Double,
        load: Double = 0.5,
        config: MotorConfig = MotorConfig(),
        sampleRate: Int = 44100
    ): SoundData {
        // 分层合成
        val electromagneticNoise = synthesizeElectromagneticNoise(duration, rpm, load, config, sampleRate)
        val mechanicalNoise = synthesizeMechanicalNoise(duration, rpm, load, config, sampleRate)
        val pwmWhine = synthesizePWMWhine(duration, rpm, load, config, sampleRate)

        // 混合所有层并应用动态滤波器
        val mixed = WaveEffects.mixSamples(
            listOf(
                electromagneticNoise,
                mechanicalNoise,
                pwmWhine
            )
        )

        // 应用随速变化的低通滤波器
        return applySpeedDependentFilter(mixed, load, config).toSoundData()
    }

    /**
     * 改进的电磁噪音层（基于6n阶次径向力波模型）
     */
    private fun synthesizeElectromagneticNoise(
        duration: Double,
        rpm: Double,
        load: Double,
        config: MotorConfig,
        sampleRate: Int
    ): DoubleArray {
        val baseFrequency = calculateBaseFrequency(rpm, config.polePairs)
        val nyquistFrequency = sampleRate / 2.0

        // 使用6n阶次径向力波模型：6, 12, 18阶为主
        val forceWaveComponents = listOf(
            ForceWaveComponent(6, 1.0, 0.20),   // 6阶主频，脉冲波，最大振幅
            ForceWaveComponent(12, 0.5, 0.20),  // 12阶，脉冲波
            ForceWaveComponent(18, 0.3, 0.20),  // 18阶，脉冲波
            ForceWaveComponent(1, 0.1, 0.05, false)  // 1阶基频，正弦波，能量很低
        )

        val waves = mutableListOf<DoubleArray>()

        // 为每个分量生成随机初始相位
        val initialPhases = forceWaveComponents.associate {
            it.order to random.nextDouble() * 2 * Math.PI
        }

        for (component in forceWaveComponents) {
            val harmonicFreq = baseFrequency * component.order
            // 抗混叠检查
            if (harmonicFreq >= nyquistFrequency * 0.85) {
                continue
            }

            val amplitude = calculateForceWaveAmplitude(component.baseAmplitude, rpm, load, config)
            val phaseOffset = initialPhases[component.order] ?: 0.0

            val wave = if (component.usePulseWave) {
                // 使用脉冲波模拟电磁力脉冲
                WaveGenerators.sawtoothWave(
                    duration = duration,
                    frequency = harmonicFreq,
                    amplitude = amplitude,
//                    pulseWidth = component.pulseWidth,
                    phaseOffset = phaseOffset,
                    sampleRate = sampleRate
                )
            } else {
                // 低阶次使用正弦波
                WaveGenerators.squareWave(
                    duration = duration,
                    frequency = harmonicFreq,
                    amplitude = amplitude,
                    phaseOffset = phaseOffset,
                    sampleRate = sampleRate
                )
            }
            waves.add(wave)
        }

        val mixedWaves = WaveEffects.mixSamples(waves)

        // 应用负载相关的振幅调制，模拟转矩脉动
        return applyLoadModulation(mixedWaves, load, baseFrequency)
    }

    /**
     * 应用负载调制 - 模拟转矩脉动
     */
    private fun applyLoadModulation(
        soundData: DoubleArray,
        load: Double,
        baseFrequency: Double
    ): DoubleArray {
        if (load < 0.1) return soundData // 轻载时不调制

        // 负载越大，调制深度越深
        val modDepth = load * 0.3
        // 调制频率与基频相关
        val modFrequency = baseFrequency * 2.0

        return MonoFilter.amplitudeModulation(
            samples = soundData,
            modFrequency = modFrequency,
            modDepth = modDepth
        )
    }

    /**
     * 应用随速变化的低通滤波器
     */
    private fun applySpeedDependentFilter(
        soundData: DoubleArray,
        rpm: Double,
        config: MotorConfig
    ): DoubleArray {
        // 转速越高，截止频率越高
        val speedRatio = rpm / config.maxRPM
        val minCutoff = 500.0
        val maxCutoff = 8000.0
        val cutoff = minCutoff + (maxCutoff - minCutoff) * speedRatio.coerceIn(0.0, 1.0)

        return MonoFilter.lowPassFilter(
            samples = soundData,
            cutoff = cutoff,
            resonance = 0.3
        )
    }

    /**
     * 合成机械噪音层 - 改进版
     */
    private fun synthesizeMechanicalNoise(
        duration: Double,
        rpm: Double,
        load: Double,
        config: MotorConfig,
        sampleRate: Int
    ): DoubleArray {
        // 基础机械噪音（轴承、齿轮等）- 使用高斯噪声更自然
        val baseNoise = WaveGenerators.gaussianWhiteNoise(
            duration = duration,
            amplitude = load * (0.3 + 0.7 * rpm / config.maxRPM),
            sampleRate = sampleRate
        )

        // 应用带通滤波突出机械共振频率范围
        val centerFreq = config.mechanicalResonanceFreq * (0.8 + 0.4 * rpm / config.maxRPM)
        val filteredNoise = MonoFilter.bandPassFilter(
            samples = baseNoise,
            centerFreq = centerFreq,
            bandwidth = 300.0
        )

        // 添加与转速相关的振幅调制，模拟旋转机械的周期性
        val modulationFreq = calculateBaseFrequency(rpm, config.polePairs) * 0.5

        return MonoFilter.amplitudeModulation(
            samples = filteredNoise,
            modFrequency = modulationFreq,
            modDepth = 0.1 + load * 0.2
        )
    }

    /**
     * 合成PWM载波啸叫层 - 改进版
     */
    private fun synthesizePWMWhine(
        duration: Double,
        rpm: Double,
        load: Double,
        config: MotorConfig,
        sampleRate: Int
    ): DoubleArray {
        // PWM啸叫在轻载和中速时更明显
        val pwmAmplitude = 0.1 * (1.0 - load * 0.5) *
                (1.0 - Math.abs(rpm / config.maxRPM - 0.5) * 1.5).coerceAtLeast(0.0)

        if (pwmAmplitude < 0.01) {
            // 振幅太小，返回静音
            return WaveGenerators.sineWave(duration, 1.0, 0.0, sampleRate = sampleRate)
        }

        val pwmFundamental = WaveGenerators.squareWave(
            duration = duration,
            frequency = config.pwmFrequency,
            amplitude = pwmAmplitude,
            dutyCycle = 0.3, // 非对称方波听起来更自然
            sampleRate = sampleRate
        )

        val pwmSecondHarmonic = WaveGenerators.squareWave(
            duration = duration,
            frequency = 2 * config.pwmFrequency,
            amplitude = pwmAmplitude * 0.3,
            dutyCycle = 0.3,
            sampleRate = sampleRate
        )

        // 对PWM高频成分进行轻微低通滤波，使其不那么刺耳
        val filteredPwm = MonoFilter.lowPassFilter(
            samples = WaveEffects.mixSamples(listOf(pwmFundamental, pwmSecondHarmonic)),
            cutoff = 12000.0,
            resonance = 0.1
        )

        return filteredPwm
    }

    /**
     * 计算力波振幅 - 考虑负载和转速的非线性影响
     */
    private fun calculateForceWaveAmplitude(
        baseAmplitude: Double,
        rpm: Double,
        load: Double,
        config: MotorConfig
    ): Double {
        // 负载越大，电磁噪音越强
        val loadFactor = 0.4 + load * 0.6
        // 转速越高，噪音振幅越大，但在极高转速时可能饱和
        val speedFactor = Math.pow((rpm / config.maxRPM), 3.0).coerceIn(0.01, 1.0)

        return baseAmplitude * loadFactor * speedFactor
    }

    /**
     * 计算电磁基频
     * 公式：基频 = (RPM / 60) * 极对数
     */
    private fun calculateBaseFrequency(rpm: Double, polePairs: Int): Double {
        return (rpm / 60.0) * polePairs
    }

    /**
     * 力波分量数据类
     * @param order 谐波阶次
     * @param baseAmplitude 基础振幅
     * @param pulseWidth 脉冲宽度（仅对脉冲波有效）
     * @param usePulseWave 是否使用脉冲波（true）或正弦波（false）
     */
    private data class ForceWaveComponent(
        val order: Int,
        val baseAmplitude: Double,
        val pulseWidth: Double = 0.5,
        val usePulseWave: Boolean = true
    )

}