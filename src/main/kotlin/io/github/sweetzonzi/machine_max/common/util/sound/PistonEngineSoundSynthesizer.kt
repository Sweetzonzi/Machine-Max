package io.github.sweetzonzi.machine_max.common.util.sound

import cn.solarmoon.spark_core.sound.SoundData
import cn.solarmoon.spark_core.util.SoundHelper
import cn.solarmoon.spark_core.util.sound.WaveEffects
import cn.solarmoon.spark_core.util.sound.filter.MonoFilter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import kotlin.math.*

class PistonEngineSoundSynthesizer {

    /**
     * 引擎物理参数配置
     */
    data class EngineParams(
        val cylinders: Int = 4,                     // 气缸数
        val fourStroke: Boolean = true,             // 冲程类型(四冲程/二冲程)
        val cylinderVolume: Double = 500.0,         // 气缸容积(cc)
        val maxRPM: Double = 6000.0,                // 最大转速
        val idleRPM: Double = 500.0,                // 怠速转速
        val firingAngles: List<Double> = listOf(0.0, 180.0, 360.0, 540.0),  // 各气缸点火角度(度)
        val exhaustLengths: List<Double> = List(4) { 0.6 },                 // 各气缸排气歧管长度(m)
    )

    /**
     * 运行时状态
     */
    data class EngineState(
        var rpm: Double = 500.0,         // 当前转速
        var load: Double = 1.0,          // 发动机负载(0-1)
    )

    /**
     * 音效合成参数
     */
    data class SoundParams(
        var volume: Double = 1.0,        // 总体音量(0-1)
        var lowFreqBoost: Double = 0.5,  // 低频增强
        var midFreqBoost: Double = 0.2,  // 中频增强
        var highFreqBoost: Double = -0.2, // 高频增强
        var distortion: Double = 0.1,    // 失真/饱和
        var resonance: Double = 0.5,     // 排气共振
        var airNoise: Double = 0.05,     // 空气噪音
        var mechanicalNoise: Double = 0.5, // 机械噪音
        var sampleRate: Int = 44100      // 采样率
    )

    private var engineParams = EngineParams()
    private var engineState = EngineState()
    private var soundParams = SoundParams()

    private val random = java.util.Random()
    private val speedOfSound = 340.0 // 声速，单位：m/s

    /**
     * 合成完整发动机声音
     */
    fun synthesizeEngineSound(duration: Double = 5.0): SoundData {
        val sampleRate = soundParams.sampleRate

        // 1. 生成单个气缸的基础脉冲波形
        val baseCylinderPulse = synthesizeSingleCylinderPulse(duration)

        // 2. 为每个气缸计算延迟并生成延迟后的波形
        val cylinderSignals = mutableListOf<DoubleArray>()

        for (cylinderIndex in 0 until engineParams.cylinders) {
            // 计算该气缸的总延迟（点火延迟 + 传播延迟）
            val totalDelay = calculateTotalDelayForCylinder(cylinderIndex)

            // 应用延迟效果（使用干信号混合为0，湿信号混合为1，因为我们只需要延迟后的信号）
            val delayedSignal = MonoFilter.applyDelay(
                samples = baseCylinderPulse,
                delay = totalDelay,
                dryMix = 0.0,
                wetMix = 1.0,
                sampleRate = sampleRate
            )

            // TODO: 添加一些气缸间的微小差异
            cylinderSignals.add(delayedSignal)
        }

        // 3. 混合所有气缸信号
        val mixedSamples = WaveEffects.mixSamples(cylinderSignals, autoNormalize = true)

        // 4. 应用后处理效果
        val processedSamples = applyPostProcessing(mixedSamples)

        // 5. 转换为16位PCM音频数据
        val byteBuffer = convertToPCM(processedSamples)

        byteBuffer.rewind()
        val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
        return SoundData(byteBuffer, format)
    }

    /**
     * 计算指定气缸的总延迟时间
     * @param cylinderIndex 气缸索引（0-based）
     * @return 总延迟时间（秒）
     */
    private fun calculateTotalDelayForCylinder(cylinderIndex: Int): Double {
        // 1. 计算点火延迟（基于点火角度）
        val ignitionDelay = calculateIgnitionDelay(cylinderIndex)

        // 2. 计算传播延迟（基于排气歧管长度）
        val propagationDelay = calculatePropagationDelay(cylinderIndex)

        // 总延迟 = 点火延迟 + 传播延迟
        return ignitionDelay + propagationDelay
    }

    /**
     * 计算点火延迟
     * @param cylinderIndex 气缸索引
     * @return 点火延迟时间（秒）
     */
    private fun calculateIgnitionDelay(cylinderIndex: Int): Double {
        // 获取该气缸的点火角度（相对于曲轴位置）
        val firingAngle = engineParams.firingAngles[cylinderIndex]

        // 将角度转换为时间延迟
        // 对于四冲程发动机：720度 = 一个完整循环
        // 对于二冲程发动机：360度 = 一个完整循环
        val cycleAngle = if (engineParams.fourStroke) 720.0 else 360.0

        // 计算曲轴每秒转数
        val rps = engineState.rpm / 60.0

        // 计算每度所需的时间
        val timePerDegree = 1.0 / (rps * 360.0)

        // 计算点火延迟（相对于0度位置）
        return firingAngle * timePerDegree
    }

    /**
     * 计算传播延迟
     * @param cylinderIndex 气缸索引
     * @return 传播延迟时间（秒）
     */
    private fun calculatePropagationDelay(cylinderIndex: Int): Double {
        // 获取该气缸的排气歧管长度
        val exhaustLength = engineParams.exhaustLengths[cylinderIndex]

        // 计算声音传播时间：距离 / 声速
        return exhaustLength / speedOfSound
    }

    /**
     * 合成单个气缸的基础脉冲
     * @param duration 持续时间（秒）
     * @return 单气缸脉冲波形
     */
    private fun synthesizeSingleCylinderPulse(duration: Double): DoubleArray {
        val sampleRate = soundParams.sampleRate
        val samples = (duration * sampleRate).toInt()
        val result = DoubleArray(samples) { 0.0 }

        // 计算单个气缸的点火频率
        val singleCylinderFiringFreq = calculateSingleCylinderFiringFrequency()

        // 生成脉冲序列
        val firingPeriod = 1.0 / singleCylinderFiringFreq // 点火周期（秒）

        // 计算每个点火事件的持续时间
        val pulseDuration = 0.02 // 20ms脉冲
        val pulseSamples = (pulseDuration * sampleRate).toInt()

        // 生成所有点火脉冲
        var currentTime = 0.0
        var sampleIndex = 0

        while (sampleIndex < samples) {
            // 计算脉冲开始位置
            val pulseStartSample = (currentTime * sampleRate).toInt()

            // 生成单个脉冲
            if (pulseStartSample < samples) {
                val pulseEndSample = min(pulseStartSample + pulseSamples, samples)

                for (i in pulseStartSample until pulseEndSample) {
                    val pulseTime = (i - pulseStartSample).toDouble() / sampleRate

                    // 脉冲包络：快速起音，指数衰减
                    val envelope = when {
                        pulseTime < 0.001 -> pulseTime * 1000.0 // 极快速起音
                        pulseTime < 0.01 -> 1.0 - (pulseTime - 0.001) * 111.0 // 快速衰减
                        else -> exp(-(pulseTime - 0.01) * 100.0) // 指数衰减尾部
                    }

                    // 脉冲波形：带随机变化的爆炸声
                    val randomFactor = 0.7 + 0.5 * random.nextDouble()
                    val loadFactor = 0.5 + 0.5 * engineState.load

                    // 基频 + 谐波
                    val baseFreq = 80.0 + engineState.rpm * 0.1
                    val harmonic1 = sin(2.0 * PI * baseFreq * pulseTime) * 0.7
                    val harmonic2 = sin(2.0 * PI * baseFreq * 2 * pulseTime) * 0.3
                    val harmonic3 = sin(2.0 * PI * baseFreq * 3 * pulseTime) * 0.1

                    val pulseShape = (harmonic1 + harmonic2 + harmonic3) / 1.1

                    result[i] = pulseShape * envelope * randomFactor * loadFactor * soundParams.volume
                }
            }

            // 移动到下一个点火时间
            currentTime += firingPeriod
            sampleIndex = (currentTime * sampleRate).toInt()
        }

        return result
    }

    /**
     * 计算单个气缸的点火频率
     * @return 单个气缸每秒点火次数
     */
    private fun calculateSingleCylinderFiringFrequency(): Double {
        // 对于四冲程发动机：每两转点火一次
        // 对于二冲程发动机：每转点火一次
        val strokesPerFiring = if (engineParams.fourStroke) 2.0 else 1.0

        // 每秒转数
        val rps = engineState.rpm / 60.0

        // 单个气缸点火频率 = 每秒转数 / 每次点火所需转数
        return rps / strokesPerFiring
    }

    /**
     * 应用后处理效果
     */
    private fun applyPostProcessing(samples: DoubleArray): DoubleArray {
        var processed = samples

        // 1. 应用共振效果（模拟排气系统共振）
        if (soundParams.resonance > 0.01) {
//            processed = ConvolutionFilter(ConvolutionFilter.loadImpulseFromWavResource("/impulses/test.wav")).apply(processed)
//            processed = MonoFilter.applyFeedbackDelay(
//                samples = processed,
//                delayTime = 0.03,
//                feedback = 0.8
//            )
        }

        // 2. 应用低通滤波模拟机械结构阻碍
        processed = MonoFilter.lowPassFilter(
            samples = processed,
            cutoff = calculateSingleCylinderFiringFrequency() * 6,
            sampleRate = soundParams.sampleRate.toDouble()
        )

        // 2. 应用频率增强
        processed = applyFrequencyEnhancement(processed)

        // 3. 应用轻微失真（模拟排气系统饱和）
        if (soundParams.distortion > 0.01) {
            processed = applySoftClipping(processed, soundParams.distortion)
        }

        return processed
    }

    /**
     * 计算排气系统共振频率
     */
    private fun calculateResonantFrequency(): Double {
        // 基于排气歧管长度计算共振频率
        val avgExhaustLength = engineParams.exhaustLengths.average()

        // 基本共振频率公式：f = v / (4L) 对于一端封闭的管道
        // 这里使用简化公式
        return speedOfSound / (4.0 * avgExhaustLength)
    }

    /**
     * 应用频率增强
     */
    private fun applyFrequencyEnhancement(samples: DoubleArray): DoubleArray {
        var result = samples.copyOf()

        // 分离处理不同频段
        val lowCutoff = 100.0
        val midCutoffLow = 100.0
        val midCutoffHigh = 2000.0
        val highCutoff = 2000.0

        // 低频增强
        val lowPassed = MonoFilter.lowPassFilter(
            samples = samples,
            cutoff = lowCutoff,
            resonance = 0.3,
            sampleRate = soundParams.sampleRate.toDouble()
        )

        // 中频增强
        val bandPassed = MonoFilter.bandPassFilter(
            samples = samples,
            centerFreq = 800.0,
            bandwidth = 1500.0,
            sampleRate = soundParams.sampleRate.toDouble()
        )

        // 高频增强
        val highPassed = MonoFilter.highPassFilter(
            samples = samples,
            cutoff = highCutoff,
            sampleRate = soundParams.sampleRate.toDouble()
        )

        // 混合各频段
        for (i in result.indices) {
            val low = if (i < lowPassed.size) lowPassed[i] else 0.0
            val mid = if (i < bandPassed.size) bandPassed[i] else 0.0
            val high = if (i < highPassed.size) highPassed[i] else 0.0

            result[i] = (low * soundParams.lowFreqBoost +
                    mid * soundParams.midFreqBoost +
                    high * soundParams.highFreqBoost)
        }

        result = WaveEffects.mixSamples(listOf(MonoFilter.dcFilter(result)), autoNormalize = true)

        return result
    }

    /**
     * 应用软削波失真
     */
    private fun applySoftClipping(samples: DoubleArray, amount: Double): DoubleArray {
        return DoubleArray(samples.size) { i ->
            val x = samples[i]
            val threshold = 0.5 + amount * 0.5

            when {
                x > threshold -> threshold + (x - threshold) / (1.0 + amount * (x - threshold))
                x < -threshold -> -threshold + (x + threshold) / (1.0 + amount * (-x - threshold))
                else -> x
            }
        }
    }

    /**
     * 转换为16位PCM格式
     */
    private fun convertToPCM(samples: DoubleArray): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)

        for (i in samples.indices) {
            val amplitude = samples[i].coerceIn(-1.0, 1.0)
            val sampleValue = (amplitude * Short.MAX_VALUE).toInt().coerceIn(
                Short.MIN_VALUE.toInt(),
                Short.MAX_VALUE.toInt()
            )
            byteBuffer.putShort(i * 2, sampleValue.toShort())
        }

        return byteBuffer
    }

    /**
     * 设置引擎参数
     */
    fun setEngineParams(params: EngineParams) {
        this.engineParams = params
        // 验证参数
        require(params.cylinders == params.firingAngles.size) {
            "气缸数必须与点火角度数组长度一致"
        }
        require(params.cylinders == params.exhaustLengths.size) {
            "气缸数必须与排气歧管长度数组长度一致"
        }
    }

    /**
     * 更新引擎状态
     */
    fun updateEngineState(rpm: Double, load: Double) {
        engineState.rpm = rpm.coerceIn(0.0, engineParams.maxRPM * 1.2)
        engineState.load = load.coerceIn(0.0, 1.0)
    }

    /**
     * 设置音效参数
     */
    fun setSoundParams(params: SoundParams) {
        this.soundParams = params
    }

    companion object {
        /**
         * 测试主函数
         */
        @JvmStatic
        fun main(args: Array<String>) {
            val synthesizer = PistonEngineSoundSynthesizer()
            val engine = EngineParams(
                cylinders = 4,
                fourStroke = false,
                firingAngles = listOf(0.0, 180.0, 360.0, 540.0),
                exhaustLengths = List(4) { 0.6 }
            )
            synthesizer.engineParams = engine
            // 设置音效参数
            synthesizer.setSoundParams(
                SoundParams(
                    volume = 0.8,
                    lowFreqBoost = 0.4,
                    midFreqBoost = 0.6,
                    highFreqBoost = 0.3,
                    distortion = 0.15,
                    resonance = 0.6,
                    airNoise = 0.1,
                    mechanicalNoise = 0.4,
                    sampleRate = 44100
                )
            )

            // 测试不同转速
            println("合成怠速声音...")
            synthesizer.updateEngineState(rpm = 1000.0, load = 0.7)
            val idleSound = synthesizer.synthesizeEngineSound(2.0)
            println("合成中速声音...")
            synthesizer.updateEngineState(rpm = 3000.0, load = 0.7)
            val midSound = synthesizer.synthesizeEngineSound(2.0)
            println("合成高速声音...")
            synthesizer.updateEngineState(rpm = 6500.0, load = 0.8)
            val highSound = synthesizer.synthesizeEngineSound(2.0)
            println("播放怠速声音...")
            SoundHelper.playSound(idleSound)
            println("播放中速声音...")
            SoundHelper.playSound(midSound)
            println("播放高速声音...")
            SoundHelper.playSound(highSound)
        }
    }
}