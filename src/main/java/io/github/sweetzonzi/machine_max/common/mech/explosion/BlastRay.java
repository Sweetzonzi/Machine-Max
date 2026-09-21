package io.github.sweetzonzi.machine_max.common.mech.explosion;

import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.util.mechanic.VoxelCursor;

import java.util.HashSet;
import java.util.Set;

/**
 * 跨 tick 持久存在的传播射线。
 *
 * <p>射线维护的是<b>剩余能量比例</b> {@code E_r}（初始 1.0，本征量），只被穿透折减；
 * 强度比 {@code I} 是派生量，由 {@link BlastField} 现算。射线方向在构造后终生不变。</p>
 *
 * <p><b>纯主线程</b>，无并发访问。</p>
 */
public final class BlastRay {

    /** E_r，剩余能量比例，初始 1.0；只被穿透折减 */
    private float energy = 1.0f;

    // ===== 推进线索 =====
    /** 当前波前位置（世界坐标） */
    private float px, py, pz;
    /** 单位方向（构造后不变） */
    private final float dx, dy, dz;
    /** d_r，已飞行距离 */
    private float distance;

    /** DDA 游标：跨 tick 复用，保存"起始体素是否已判定"等遍历状态 */
    private final VoxelCursor cursor = new VoxelCursor();

    // ===== 交互 =====
    /** false = E_r 低于 interactionCutoff，只推进、不再判定 */
    private boolean interacting = true;
    /** false = 越界或已被拦下，彻底结束 */
    private boolean alive = true;

    /**
     * 生命周期级去重集合（不清空）：键为 {@code PenetrationKey} / {@code BlockPos} / {@code Entity} 三类之一。
     * 同一去重键在射线整个生命周期内只判定一次。
     */
    private final Set<Object> struck = new HashSet<>();

    /**
     * @param origin 起爆点
     * @param dir    单位方向（构造后被复制，此后不变）
     */
    BlastRay(Vector3f origin, Vector3f dir) {
        this.px = origin.x;
        this.py = origin.y;
        this.pz = origin.z;
        this.dx = dir.x;
        this.dy = dir.y;
        this.dz = dir.z;
        this.distance = 0f;
    }

    // ===== 只读访问 =====

    public float energy() {
        return energy;
    }

    public float distance() {
        return distance;
    }

    public boolean isAlive() {
        return alive;
    }

    public boolean isInteracting() {
        return interacting;
    }

    public float x() {
        return px;
    }

    public float y() {
        return py;
    }

    public float z() {
        return pz;
    }

    public float dirX() {
        return dx;
    }

    public float dirY() {
        return dy;
    }

    public float dirZ() {
        return dz;
    }

    VoxelCursor cursor() {
        return cursor;
    }

    // ===== 状态变更（仅由 ExplosionInstance 在推进/结算中调用）=====

    /** 推进波前位置并累加已飞行距离。 */
    void moveTo(float x, float y, float z, float step) {
        this.px = x;
        this.py = y;
        this.pz = z;
        this.distance += step;
    }

    /** 终止本射线（被拦下或越界）。 */
    public void kill() {
        alive = false;
        interacting = false;
    }

    /**
     * 按传输效率 η 折减剩余能量（{@link BlastField} 之外唯一允许改动 E_r 的地方）。
     * 仅在判定为 PENETRATED 时调用；BLOCKED / RICOCHET 走 {@link #kill()}。
     */
    public void absorb(float eta) {
        energy = Math.max(0f, energy * eta);
    }

    /** 能量低于阈值时停止交互（纯性能旋钮，不参与平衡）。 */
    void stopInteracting() {
        interacting = false;
    }

    /**
     * 生命周期级去重：首次登记该键返回 true，重复返回 false。
     *
     * @param key {@code PenetrationKey} / {@code BlockPos} / {@code Entity} 三类之一
     * @return true 表示这是本射线第一次遇到该键，应当继续判定
     */
    public boolean markStruck(Object key) {
        return struck.add(key);
    }
}
