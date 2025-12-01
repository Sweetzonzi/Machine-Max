package io.github.sweetzonzi.machine_max.common.util.sound

import cn.solarmoon.spark_core.sound.SoundData
import cn.solarmoon.spark_core.util.SoundPlayer
import cn.solarmoon.spark_core.util.SoundSynthesizers
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import kotlin.math.*

/**
 * 基于物理原理的引擎音效合成器
 *
 * 根据发动机物理参数实时合成引擎音效：
 * - 转速(RPM) -> 音高和节奏
 * - 负载/油门 -> 音量和音色
 * - 气缸数和排列 -> 谐波和节奏模式
 * - 冲程数 -> 点火频率
 * - 气缸容积 -> 低频共振
 */
class EngineSoundSynthesizer {

    /**
     * 引擎物理参数配置
     */
    data class EngineParams(
        val cylinders: Int = 4,          // 气缸数
        val stroke: Int = 4,             // 冲程数（2或4）
        val cylinderVolume: Double = 500.0, // 气缸容积(cc)
        val maxRPM: Double = 6000.0,     // 最大转速
        val idleRPM: Double = 500.0,     // 怠速转速
        val bankAngle: Double = 0.0,     // 气缸组夹角（V型发动机）
        val exhaustLength: Double = 2.0, // 排气管长度(m)
    )

    /**
     * 运行时状态
     */
    data class EngineState(
        var rpm: Double = 500.0,         // 当前转速
        var throttle: Double = 1.0,      // 油门开度(0-1)
        var load: Double = 1.0,          // 发动机负载(0-1)
    )

    /**
     * 音效合成参数
     */
    data class SoundParams(
        var volume: Double = 1.0,        // 总体音量(0-1)
        var lowFreqBoost: Double = 0.3,  // 低频增强
        var midFreqBoost: Double = 0.5,  // 中频增强
        var highFreqBoost: Double = 0.2, // 高频增强
        var distortion: Double = 0.1,    // 失真/饱和
        var resonance: Double = 0.5,     // 排气共振
        var airNoise: Double = 0.05,     // 空气噪音
        var mechanicalNoise: Double = 0.5, // 机械噪音
        var sampleRate: Int = 44100      // 采样率
    )

    private var engineParams = EngineParams()
    private var engineState = EngineState()
    private var soundParams = SoundParams()

    private var phaseAccumulator = 0.0
    private var mechanicalPhaseAccumulator = 0.0
    private val random = java.util.Random()

    /**
     * 根据物理参数合成引擎音效
     */
    fun synthesizeEngineSound(duration: Double = 1.0): SoundData {
        val samples = (duration * soundParams.sampleRate).toInt()
        val byteBuffer = ByteBuffer.allocateDirect(samples * 2).order(ByteOrder.LITTLE_ENDIAN)

        // 计算基础频率和参数
        val firingFrequency = calculateFiringFrequency()
        val cylinderFreq = calculateCylinderBaseFrequency()
        val harmonicRatios = calculateHarmonicRatios()

        // 生成各声音层
        val mixedSamples = DoubleArray(samples) { 0.0 }

        // 1. 基础点火脉冲层（主音高）
        val firingPulses = synthesizeFiringPulses(samples, firingFrequency)

        // 2. 气缸谐振层
        val cylinderHarmonics = synthesizeCylinderHarmonics(samples, cylinderFreq, harmonicRatios)

        // 3. 排气声浪层
        val exhaustRumble = synthesizeExhaustRumble(samples)

        // 4. 机械噪音层
        val mechanicalNoiseLayer = synthesizeMechanicalNoise(samples, cylinderFreq)

        // 5. 进气噪音层
        val intakeNoiseLayer = synthesizeIntakeNoise(samples)

        // 混合所有层
        for (i in 0 until samples) {
            var sample = 0.0

            // 混合比例基于油门和负载
            val throttleFactor = engineState.throttle
            val loadFactor = engineState.load

            // 基础点火脉冲（始终存在）
            sample += firingPulses[i] * (0.5 + 0.5 * throttleFactor)

            // 气缸谐振（随负载增加）
            sample += cylinderHarmonics[i] * (0.3 + 0.7 * loadFactor)

            // 排气声浪（随RPM增加）
            val rpmFactor = engineState.rpm / engineParams.maxRPM
            sample += exhaustRumble[i] * (0.2 + 0.8 * rpmFactor)

            // 机械噪音（随RPM增加）
            sample += mechanicalNoiseLayer[i] * (0.1 + 0.3 * rpmFactor)

            // 进气噪音（随油门增加）
            sample += intakeNoiseLayer[i] * (0.05 + 0.15 * throttleFactor)

            // 应用失真和动态范围压缩
            sample = applyDistortion(sample)
            sample = applyDynamicCompression(sample, i, samples)

            // 混合到主缓冲区
            mixedSamples[i] = sample
        }

        // 应用总体滤波
        applyFinalFiltering(mixedSamples)

        // 转换为16位PCM
        for (i in 0 until samples) {
            val amplitude = mixedSamples[i] * soundParams.volume
            val sampleValue = (amplitude * Short.MAX_VALUE).toInt().coerceIn(
                Short.MIN_VALUE.toInt(),
                Short.MAX_VALUE.toInt()
            )
            byteBuffer.putShort(i * 2, sampleValue.toShort())
        }

        byteBuffer.rewind()
        val format = AudioFormat(soundParams.sampleRate.toFloat(), 16, 1, true, false)
        return SoundData(byteBuffer, format)
    }

    /**
     * 计算点火频率（基于RPM、气缸数、冲程数）
     */
    private fun calculateFiringFrequency(): Double {
        // 对于四冲程：每转两圈点火一次
        // 对于二冲程：每转一圈点火一次
        val strokesPerRevolution = if (engineParams.stroke == 2) 1.0 else 0.5

        // 每秒点火次数 = (RPM/60) * 气缸数 * 每转点火次数
        val firingPerSecond = (engineState.rpm / 60.0) *
                engineParams.cylinders *
                strokesPerRevolution

        return firingPerSecond
    }

    /**
     * 计算基础气缸频率
     */
    private fun calculateCylinderBaseFrequency(): Double {
        // 基础频率与RPM和气缸排列相关
        val baseFreq = engineState.rpm / 60.0 // 转/秒

        // V型发动机有相位差
        val phaseShift = if (engineParams.bankAngle > 0) {
            Math.sin(Math.toRadians(engineParams.bankAngle / 2))
        } else 1.0

        return baseFreq * phaseShift
    }

    /**
     * 计算谐波比率（基于气缸数、点火顺序、气缸容积）
     */
    private fun calculateHarmonicRatios(): Map<Int, Double> {
        val harmonics = mutableMapOf<Int, Double>()

        // 容积因子：大容积倾向于更强的低频谐波
        val volumeFactor = engineParams.cylinderVolume / 500.0

        // 基础谐波（点火频率倍数）
        harmonics[1] = 1.0

        // 二阶谐波（气缸平衡产生的）
        harmonics[2] = 0.7 * (engineParams.cylinders / 4.0)

        // 奇次谐波（点火脉冲特性）- 受容积影响
        for (i in 3..9 step 2) {
            // 大容积气缸：低频谐波更强
            val volumeWeight = if (i <= 3) {
                1.0 + (volumeFactor - 1.0) * 0.9
            } else {
                1.0 - (volumeFactor - 1.0) * 0.1
            }
            harmonics[i] = (1.0 / i * (1.0 - 0.1 * (i - 3))) * volumeWeight
        }

        // 偶次谐波（排气共振）- 受负荷影响
        for (i in 2..8 step 2) {
            harmonics[i] = 0.5 / i * sqrt(engineState.load)
        }

        return harmonics
    }

    /**
     * 合成点火脉冲层
     */
    private fun synthesizeFiringPulses(samples: Int, firingFrequency: Double): DoubleArray {
        val result = DoubleArray(samples) { 0.0 }
        val sampleRate = soundParams.sampleRate.toDouble()

        // 计算每个点火事件的时间点
        val firingPeriod = 1.0 / firingFrequency

        for (i in 0 until samples) {
            val time = i.toDouble() / sampleRate

            // 计算最近的点火时间
            val firingIndex = (time / firingPeriod).toInt()
            val timeSinceFiring = time - firingIndex * firingPeriod

            // 生成点火脉冲（短促的爆炸声）
            if (timeSinceFiring < 0.02) { // 20ms脉冲
                // 脉冲形状：快速起音，指数衰减
                val pulseTime = timeSinceFiring / 0.02

                // 脉冲振幅 = 基础振幅 * 负载系数 * 随机变化
                val baseAmplitude = 0.8
                val loadFactor = 0.5 + 0.5 * engineState.load
                val randomVariation = 0.9 + 0.2 * random.nextDouble()

                val pulseAmplitude = baseAmplitude * loadFactor * randomVariation

                // ADSR包络
                val envelope = when {
                    pulseTime < 0.1 -> pulseTime * 10 // 起音
                    pulseTime < 0.3 -> 1.0 - (pulseTime - 0.1) * 2.5 // 衰减
                    else -> max(0.0, 0.5 - (pulseTime - 0.3) * 5) // 持续+释音
                }

                // 脉冲波形：短促的方波，带一点正弦软化
                val pulseShape = if (pulseTime < 0.05) {
                    1.0
                } else {
                    cos((pulseTime - 0.05) * Math.PI * 10) * 0.5 + 0.5
                }

                result[i] = pulseAmplitude * envelope * pulseShape

                // 添加高频成分（火花塞放电）
                val sparkFreq = 5000.0 + 5000.0 * engineState.throttle
                val sparkComponent = sin(2 * Math.PI * sparkFreq * time) *
                        exp(-pulseTime * 50) * 0.1
                result[i] += sparkComponent
            }
        }

        return result
    }

    /**
     * 合成气缸谐振层
     */
    private fun synthesizeCylinderHarmonics(
        samples: Int,
        baseFreq: Double,
        harmonicRatios: Map<Int, Double>
    ): DoubleArray {
        val result = DoubleArray(samples) { 0.0 }
        val sampleRate = soundParams.sampleRate.toDouble()
        // 生成每个谐波
        for ((harmonic, ratio) in harmonicRatios) {
            val freq = baseFreq * harmonic
            val amplitude = ratio * soundParams.volume *
                    (0.3 + 0.7 * engineState.throttle)

            // 正弦波叠加，带有轻微相位调制
            for (i in 0 until samples) {
                val time = i.toDouble() / sampleRate

                // 基础正弦波
                val baseWave = sin(2 * Math.PI * freq * time + phaseAccumulator)

                // 轻微相位调制（模拟机械振动）
                val phaseMod = sin(2 * Math.PI * freq * 0.1 * time) * 0.1

                // 振幅调制（模拟气缸压力变化）
                val ampMod = 1.0 + 0.2 * sin(2 * Math.PI * baseFreq * time)

                result[i] += baseWave * amplitude * ampMod * (1.0 + phaseMod)
            }
        }

        // 更新相位累加器
        phaseAccumulator += 2 * Math.PI * baseFreq * (samples.toDouble() / sampleRate)
        if (phaseAccumulator > 2 * Math.PI) {
            phaseAccumulator -= 2 * Math.PI
        }

        return result
    }

    /**
     * 合成排气声浪层
     */
    private fun synthesizeExhaustRumble(samples: Int): DoubleArray {
        val result = DoubleArray(samples) { 0.0 }
        val sampleRate = soundParams.sampleRate.toDouble()

        // 排气共振频率与排气管长度相关
        val speedOfSound = 343.0 // m/s
        val exhaustFreq = speedOfSound / (4 * engineParams.exhaustLength)

        // 基础排气频率
        val baseFreq = exhaustFreq * 0.5

        // 生成多个共振模式
        for (mode in 1..5) {
            val freq = baseFreq * mode
            val amplitude = soundParams.resonance *
                    (1.0 / mode) *
                    (0.5 + 0.5 * engineState.throttle)

            for (i in 0 until samples) {
                val time = i.toDouble() / sampleRate

                // 排气声浪：正弦波带轻微失真
                val wave = sin(2 * Math.PI * freq * time)

                // 添加谐波失真（模拟排气管非线性）
                val distorted = wave + 0.1 * sin(2 * Math.PI * freq * 2 * time) +
                        0.05 * sin(2 * Math.PI * freq * 3 * time)

                result[i] += distorted * amplitude
            }
        }

        // 添加随机湍流噪音
        for (i in 0 until samples) {
            val turbulence = (random.nextDouble() * 2 - 1) *
                    soundParams.airNoise *
                    (0.1 + 0.9 * engineState.throttle)
            result[i] += turbulence
        }

        return result
    }

    /**
     * 合成机械噪音层（气门、活塞、齿轮等）
     */
    /**
     * 合成机械噪音层（改进：与转速周期性挂钩）
     * 机械噪音主要与转速相关，受负荷影响较小
     */
    private fun synthesizeMechanicalNoise(
        samples: Int,
        cylinderFreq: Double
    ): DoubleArray {
        // 机械噪音主要基于转速，与气缸基础频率挂钩
        val sampleRate = soundParams.sampleRate.toDouble()
        val result = DoubleArray(samples) { 0.0 }

        // 机械噪音的基础频率：与气缸频率相关
        val mechanicalBaseFreq = cylinderFreq

        // 生成多个机械噪音成分
        for (component in 1..3) {
            val componentFreq = mechanicalBaseFreq * component
            val componentAmplitude = soundParams.mechanicalNoise * (1.0 / component)

            for (i in 0 until samples) {
                val time = i.toDouble() / sampleRate

                // 基础机械振动（正弦波，代表平衡轴等）
                val baseVibration = sin(2 * Math.PI * componentFreq * time + mechanicalPhaseAccumulator)

                // 添加不规则性（随机相位调制）
                val randomPhase = (random.nextDouble() * 2 - 1) * 0.1
                val irregularVibration = sin(2 * Math.PI * componentFreq * time + mechanicalPhaseAccumulator + randomPhase)

                // 振幅调制：与转速同步但受随机因素影响
                val modFreq = cylinderFreq * 0.25
                val amplitudeMod = 0.7 + 0.3 * sin(2 * Math.PI * modFreq * time)

                result[i] += (baseVibration * 0.7 + irregularVibration * 0.3) *
                        componentAmplitude * amplitudeMod
            }
        }

        // 更新机械相位累加器
        mechanicalPhaseAccumulator += 2 * Math.PI * mechanicalBaseFreq * (samples.toDouble() / sampleRate)
        if (mechanicalPhaseAccumulator > 2 * Math.PI) {
            mechanicalPhaseAccumulator -= 2 * Math.PI
        }

        // 添加宽带噪音成分（代表摩擦、齿轮啮合等）
        val broadbandNoise = SoundSynthesizers.whiteNoise(
            duration = samples.toDouble() / soundParams.sampleRate,
            amplitude = soundParams.mechanicalNoise,
            sampleRate = soundParams.sampleRate
        )

        val filtered = SoundSynthesizers.lowPassFilter(broadbandNoise, mechanicalBaseFreq * 50)

        val buffer = filtered.byteBuffer()
        for (i in 0 until samples) {
            if (i * 2 < buffer.capacity()) {
                val noiseSample = buffer.getShort(i * 2).toDouble() / Short.MAX_VALUE

                // 宽带噪音也受转速调制
                val noiseMod = 0.8 + 0.2 * sin(2 * Math.PI * mechanicalBaseFreq * i / sampleRate)
                result[i] += noiseSample * noiseMod
            }
        }

        return result
    }

    /**
     * 合成进气噪音层
     */
    private fun synthesizeIntakeNoise(samples: Int): DoubleArray {
        val result = DoubleArray(samples) { 0.0 }
        val sampleRate = soundParams.sampleRate.toDouble()

        // 进气噪音：中高频白噪音，带气流特征
        val intakeFreq = 800.0 + 2000.0 * engineState.throttle

        for (i in 0 until samples) {
            val time = i.toDouble() / sampleRate

            // 基础气流噪音
            val noise = (random.nextDouble() * 2 - 1) *
                    soundParams.airNoise *
                    engineState.throttle

            // 共振频率（进气歧管）
            val resonance = sin(2 * Math.PI * intakeFreq * time) * 0.2

            // 脉冲成分（气门开闭）
            val intakePeriod = 1.0 / (engineState.rpm / 60.0 * engineParams.cylinders / 2)
            val intakePulse = if ((time % intakePeriod) < 0.01) {
                0.5 * exp(-(time % intakePeriod) * 100)
            } else 0.0

            result[i] = (noise + resonance + intakePulse) * 0.3
        }

        return result
    }

    /**
     * 应用失真效果
     */
    private fun applyDistortion(sample: Double): Double {
        val distortionAmount = soundParams.distortion

        // 软削波失真
        return when {
            distortionAmount < 0.01 -> sample
            else -> {
                val threshold = 0.8
                val gain = 1.0 + distortionAmount * 3
                val distorted = sample * gain

                // 双曲正切软削波
                tanh(distorted * threshold) / threshold
            }
        }
    }

    /**
     * 应用动态范围压缩
     */
    private fun applyDynamicCompression(sample: Double, index: Int, totalSamples: Int): Double {
        // 简单的自动增益控制
        val targetLevel = 0.7
        val compressionRatio = 4.0

        // 计算当前电平
        val currentLevel = abs(sample)

        // 压缩器计算（简化版）
        return if (currentLevel > targetLevel) {
            val excess = currentLevel - targetLevel
            val gainReduction = excess / compressionRatio
            sample * (1.0 - gainReduction / currentLevel)
        } else {
            sample
        }
    }

    /**
     * 应用最终滤波（均衡）
     */
    private fun applyFinalFiltering(samples: DoubleArray) {
        // 简单的IIR滤波器实现均衡
        var lowPrev = 0.0
        var midPrev = 0.0
        var highPrev = 0.0

        val dt = 1.0 / soundParams.sampleRate

        // 低频增强
        val lowCutoff = 100.0
        val lowAlpha = dt / (1.0 / (2 * Math.PI * lowCutoff) + dt)

        // 高频滚降
        val highCutoff = 8000.0
        val highAlpha = 1.0 / (2 * Math.PI * highCutoff * dt + 1)

        for (i in samples.indices) {
            val original = samples[i]

            // 低频增强
            val lowPass = lowPrev + lowAlpha * (original - lowPrev)
            lowPrev = lowPass

            // 中频带通（通过原始减去低通和高通得到）
            val highPass = original - lowPass
            val highPassFiltered = highPrev + highAlpha * (highPass - highPrev)
            highPrev = highPassFiltered

            val bandPass = lowPass - highPassFiltered

            // 混合均衡
            val lowBoosted = lowPass * (1.0 + soundParams.lowFreqBoost)
            val midBoosted = bandPass * (1.0 + soundParams.midFreqBoost)
            val highBoosted = highPassFiltered * (1.0 + soundParams.highFreqBoost)

            samples[i] = (lowBoosted + midBoosted + highBoosted) / 3.0
        }
    }

    /**
     * 双曲正切函数（用于软削波）
     */
    private fun tanh(x: Double): Double {
        return (exp(x) - exp(-x)) / (exp(x) + exp(-x))
    }

    /**
     * 设置引擎参数
     */
    fun setEngineParams(params: EngineParams) {
        this.engineParams = params
    }

    /**
     * 更新引擎状态
     */
    fun updateEngineState(rpm: Double, throttle: Double) {
        engineState.rpm = rpm.coerceIn(0.0, engineParams.maxRPM * 1.5)
        engineState.throttle = throttle.coerceIn(0.0, 1.0)
        engineState.load = throttle.coerceIn(0.0, 1.0)
    }

    /**
     * 设置音效参数
     */
    fun setSoundParams(params: SoundParams) {
        this.soundParams = params
    }

    /**
     * 获取实时音效数据（用于实时播放）
     */
    fun getRealTimeSound(bufferSize: Int = 4096): SoundData {
        // 计算需要的时间长度
        val duration = bufferSize.toDouble() / soundParams.sampleRate
        return synthesizeEngineSound(duration)
    }

    companion object {
        /**
         * 根据车辆配置创建引擎参数
         */
        @JvmStatic
        @JvmOverloads
        fun createEngineParamsForVehicle(
            cylinders: Int,
            displacement: Double, // 排量(cc)
            bankAngle: Double = 0.0
        ): EngineParams {
            // 计算单缸容积
            val cylinderVolume = displacement / cylinders

            // 根据排量估算最大RPM
            val maxRPM = when {
                displacement < 1000 -> 8000.0
                displacement < 2000 -> 6500.0
                displacement < 4000 -> 5500.0
                else -> 4500.0
            }

            // 怠速RPM
            val idleRPM = maxRPM * 0.06

            // 根据气缸数确定点火顺序
            val firingOrder = when (cylinders) {
                1 -> intArrayOf(1)
                2 -> intArrayOf(1, 2)
                3 -> intArrayOf(1, 3, 2)
                4 -> intArrayOf(1, 3, 4, 2)
                5 -> intArrayOf(1, 2, 4, 5, 3)
                6 -> intArrayOf(1, 5, 3, 6, 2, 4)
                8 -> intArrayOf(1, 8, 4, 3, 6, 5, 7, 2)
                10 -> intArrayOf(1, 6, 5, 10, 2, 7, 3, 8, 4, 9)
                12 -> intArrayOf(1, 7, 5, 11, 3, 9, 6, 12, 2, 8, 4, 10)
                else -> IntArray(cylinders) { it + 1 } // 默认顺序
            }

            return EngineParams(
                cylinders = cylinders,
                stroke = 4, // 默认四冲程
                cylinderVolume = cylinderVolume,
                maxRPM = maxRPM,
                idleRPM = idleRPM,
                bankAngle = bankAngle,
            )
        }

        /**
         * 测试主函数
         */
        @JvmStatic
        fun main(args: Array<String>) {
            val engineSoundSynthesizer = EngineSoundSynthesizer()
            engineSoundSynthesizer.setEngineParams(createEngineParamsForVehicle(6, 3.0))
            engineSoundSynthesizer.updateEngineState(8500.0, 1.0)
            val soundData = engineSoundSynthesizer.synthesizeEngineSound(5.0)
            SoundPlayer.playSound(soundData)
        }
    }
}


