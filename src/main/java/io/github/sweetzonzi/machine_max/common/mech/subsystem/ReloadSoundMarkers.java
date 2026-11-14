package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import net.minecraft.sounds.SoundEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 进度音效表的纯计算：阈值解析、一段进度里跨过的标记、某个固定阈值上的标记。<br>
 * 音效表是"进度比例（{@code "0.0"}~{@code "1.0"} 的十进制字符串）→ 音效"的映射，两种装填需求方共用同一套
 * 键写法：自动装弹机（人工段与机械段各有表）与声明了人工段的发射器（人力直取路径）。已播游标归各自的持有者，
 * 这里只回答"这一段进度里该响哪些声音"，因此不持任何状态。
 */
final class ReloadSoundMarkers {

    /** 极小量：进度是浮点累计值，判定"正好落在阈值上"需要容差 */
    private static final float EPSILON = 1e-4f;

    private ReloadSoundMarkers() {
    }

    /**
     * 解析阈值键；不是数值的键按"不是进度标记"处理。
     *
     * @param key 音效表的键
     * @return 阈值；无法解析时返回 {@link Float#NaN}
     */
    static float thresholdOf(String key) {
        try {
            return Float.parseFloat(key);
        } catch (NumberFormatException ignored) {
            return Float.NaN;
        }
    }

    /**
     * 区间 {@code (from, to]} 里跨过的标记，按阈值升序。<br>
     * 阈值 0 属于"段开始"音，不参与这个区间——它只在该段第一个 tick 播放，不因进度回退重播。
     * 一次推进越过多个阈值时全部返回，调用方按顺序播完，不会只播最大的那个。
     *
     * @param sounds 音效表
     * @param from   已播到的位置（不含）
     * @param to     本 tick 的进度（含）
     * @return 该播放的音效，升序
     */
    static List<SoundEvent> crossed(Map<String, SoundEvent> sounds, float from, float to) {
        if (sounds.isEmpty()) return List.of();
        List<Map.Entry<Float, SoundEvent>> crossed = new ArrayList<>(sounds.size());
        for (Map.Entry<String, SoundEvent> entry : sounds.entrySet()) {
            float threshold = thresholdOf(entry.getKey());
            if (!Float.isFinite(threshold) || threshold <= 0f) continue;
            if (threshold > from && threshold <= to) crossed.add(Map.entry(threshold, entry.getValue()));
        }
        crossed.sort(Map.Entry.comparingByKey());
        List<SoundEvent> ordered = new ArrayList<>(crossed.size());
        for (Map.Entry<Float, SoundEvent> entry : crossed) ordered.add(entry.getValue());
        return ordered;
    }

    /**
     * 落在给定阈值上的标记（{@code 0} = 段开始音）。
     *
     * @param sounds    音效表
     * @param threshold 目标阈值
     * @return 该阈值上的音效；表里没有这一档时返回 null
     */
    @Nullable
    static SoundEvent at(Map<String, SoundEvent> sounds, float threshold) {
        for (Map.Entry<String, SoundEvent> entry : sounds.entrySet()) {
            float value = thresholdOf(entry.getKey());
            if (Float.isFinite(value) && Math.abs(value - threshold) < EPSILON) return entry.getValue();
        }
        return null;
    }
}
