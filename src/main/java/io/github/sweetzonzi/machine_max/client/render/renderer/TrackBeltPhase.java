package io.github.sweetzonzi.machine_max.client.render.renderer;

/**
 * 按实际轮位姿展开角度；接触入口按渲染需求采样，帧内用实际位姿修正插值。
 * 角速度只预测跨圈分支。断带期间冻结输送角，修复从冻结值继续。
 */
final class TrackBeltPhase {
    private double wheel, previousWheel, transport, previousTransport, entry, previousEntry;
    private double initialRelative;
    private boolean wheelInitialized, entryInitialized, broken;

    TrackBeltPhase() { }

    TrackBeltPhase(TrackBeltPhase source) {
        wheel = source.wheel; previousWheel = source.previousWheel;
        transport = source.transport; previousTransport = source.previousTransport;
        entry = source.entry; previousEntry = source.previousEntry;
        initialRelative = source.initialRelative;
        wheelInitialized = source.wheelInitialized; entryInitialized = source.entryInitialized;
        broken = source.broken;
    }

    void advance(double rawWheel, double rawEntry, double predictedDelta, boolean broken) {
        advanceWheel(rawWheel, predictedDelta, broken);
        if (entryInitialized) {
            entry = near(rawEntry, entry);
        } else {
            anchorEntry(rawWheel, rawEntry, 1f);
        }
    }

    /** 客户端 tick：不依赖包络，离屏时仍可跟踪跨圈及冻结状态。 */
    void advanceWheel(double rawWheel, double predictedDelta, boolean broken) {
        previousEntry = entry;
        if (!wheelInitialized) {
            wheel = previousWheel = transport = previousTransport = rawWheel;
            wheelInitialized = true;
        } else {
            previousWheel = wheel;
            previousTransport = transport;
            wheel = near(rawWheel, wheel + predictedDelta);
            if (!broken && !this.broken) transport += wheel - previousWheel;
        }
        this.broken = broken;
    }

    boolean hasEntry() { return entryInitialized; }

    /** 用首次有效接触的实际时刻建立节序零点，不覆盖已累计的输送角。 */
    void anchorEntry(double rawWheel, double rawEntry, float partialTick) {
        entry = previousEntry = rawEntry;
        initialRelative = rawEntry - transportAt(rawWheel, partialTick);
        entryInitialized = true;
    }

    private double transportAt(double rawWheel, float partialTick) {
        double expectedWheel = previousWheel + (wheel - previousWheel) * partialTick;
        double actualTransport = previousTransport + (transport - previousTransport) * partialTick;
        if (!broken) actualTransport += near(rawWheel, expectedWheel) - expectedWheel;
        return actualTransport;
    }

    double label(double rawWheel, double rawEntry, float partialTick, double radiusOverPitch, double sign) {
        double actualEntry = near(rawEntry, previousEntry + (entry - previousEntry) * partialTick);
        return sign * radiusOverPitch * (actualEntry - transportAt(rawWheel, partialTick) - initialRelative);
    }

    static double near(double angle, double reference) {
        return angle + Math.rint((reference - angle) / (2 * Math.PI)) * (2 * Math.PI);
    }
}
