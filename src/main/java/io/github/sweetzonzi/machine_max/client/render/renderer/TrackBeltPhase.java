package io.github.sweetzonzi.machine_max.client.render.renderer;

/**
 * 主线程按实际轮位姿展开角度；渲染线程用同一时刻的实际位姿修正帧间插值。
 * 角速度只预测跨圈分支。断带期间冻结输送角，修复从冻结值继续。
 */
final class TrackBeltPhase {
    private double wheel, previousWheel, transport, previousTransport, entry, previousEntry;
    private double initialRelative;
    private boolean initialized, broken;

    TrackBeltPhase() { }

    TrackBeltPhase(TrackBeltPhase source) {
        wheel = source.wheel; previousWheel = source.previousWheel;
        transport = source.transport; previousTransport = source.previousTransport;
        entry = source.entry; previousEntry = source.previousEntry;
        initialRelative = source.initialRelative;
        initialized = source.initialized; broken = source.broken;
    }

    void advance(double rawWheel, double rawEntry, double predictedDelta, boolean broken) {
        if (!initialized) {
            wheel = previousWheel = transport = previousTransport = rawWheel;
            entry = previousEntry = rawEntry;
            initialRelative = entry - wheel;
            initialized = true;
        } else {
            previousWheel = wheel;
            previousTransport = transport;
            previousEntry = entry;
            wheel = near(rawWheel, wheel + predictedDelta);
            entry = near(rawEntry, entry);
            if (!broken && !this.broken) transport += wheel - previousWheel;
        }
        this.broken = broken;
    }

    double label(double rawWheel, double rawEntry, float partialTick, double radiusOverPitch, double sign) {
        double expectedWheel = previousWheel + (wheel - previousWheel) * partialTick;
        double actualWheel = near(rawWheel, expectedWheel);
        double actualEntry = near(rawEntry, previousEntry + (entry - previousEntry) * partialTick);
        double actualTransport = previousTransport + (transport - previousTransport) * partialTick;
        if (!broken) actualTransport += actualWheel - expectedWheel;
        return sign * radiusOverPitch * (actualEntry - actualTransport - initialRelative);
    }

    static double near(double angle, double reference) {
        return angle + Math.rint((reference - angle) / (2 * Math.PI)) * (2 * Math.PI);
    }
}
