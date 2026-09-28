package io.github.sweetzonzi.machine_max.common.mech.projectile.component.guidance;

import com.jme3.math.Vector3f;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 纯追踪制导律（注册名 {@code machine_max:pure_pursuit}）。
 * <p>
 * 指令为"把速度矢量朝视线方向转"：
 * <pre>
 * θ      = acos(clamp(v̂·r̂, −1, 1))            // 航向误差
 * n̂      = normalize(v̂ × r̂)                   // 转弯轴
 * ω_cmd  = turn_gain · θ                       // 一阶航向控制（rad/s）
 * a_cmd  = (n̂ · ω_cmd) × v                     // 幅值 ω_cmd·|v|，方向垂直于 v
 * </pre>
 * 增益越大越贴视线。本律只给无约束指令，过载/转率限幅与诱导阻力由
 * {@link AuthorityLimits} 施加（见 {@link GuidanceLaw#computeAcceleration}）。
 * <p>
 * 设计出处：《武器系统-组件化投射物与类型体系设计》§4.2。
 *
 * @param turnGain 航向增益（1/s），默认 3.0
 * @param limits   可用过载包线等限幅参数，缺省 {@link AuthorityLimits#DEFAULT}
 */
public record PurePursuitGuidance(float turnGain, AuthorityLimits limits) implements GuidanceLaw {

    /** 几何退化阈值：视线长度、转弯轴幅值（即 sinθ）低于此值即不给指令 */
    private static final float GEOMETRY_EPS = 1e-5f;

    public static final MapCodec<PurePursuitGuidance> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        Codec.FLOAT.optionalFieldOf("turn_gain", 3.0f)
            .forGetter(PurePursuitGuidance::turnGain),
        AuthorityLimits.CODEC.optionalFieldOf("limits", AuthorityLimits.DEFAULT)
            .forGetter(PurePursuitGuidance::limits)
    ).apply(instance, PurePursuitGuidance::new));

    @Override
    public MapCodec<? extends GuidanceLaw> codec() {
        return CODEC;
    }

    /**
     * 计算无约束指令加速度。
     * <p>
     * 守卫（任一命中即本步不给指令，交回弹道）：
     * <ul>
     *   <li>{@code |r| < ε} — 目标与弹重合，视为到达</li>
     *   <li>{@code |v| < MIN_SPEED} — 低速下 a/v 发散</li>
     *   <li>{@code |v̂ × r̂| < ε} — 已对准或完全反向，转弯轴退化</li>
     * </ul>
     */
    @Override
    public boolean computeCommandAcceleration(GuidanceContext ctx, Vector3f out) {
        out.set(0f, 0f, 0f);

        float rx = ctx.targetX() - ctx.posX();
        float ry = ctx.targetY() - ctx.posY();
        float rz = ctx.targetZ() - ctx.posZ();
        float distance = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (distance < GEOMETRY_EPS) return false;

        float vx = ctx.velX(), vy = ctx.velY(), vz = ctx.velZ();
        float speed = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (speed < MIN_SPEED) return false;

        float invDistance = 1f / distance;
        float invSpeed = 1f / speed;
        float rhatX = rx * invDistance, rhatY = ry * invDistance, rhatZ = rz * invDistance;
        float vhatX = vx * invSpeed, vhatY = vy * invSpeed, vhatZ = vz * invSpeed;

        // 转弯轴 = v̂ × r̂，其幅值即 sinθ
        float axisX = vhatY * rhatZ - vhatZ * rhatY;
        float axisY = vhatZ * rhatX - vhatX * rhatZ;
        float axisZ = vhatX * rhatY - vhatY * rhatX;
        float sinTheta = (float) Math.sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ);
        if (sinTheta < GEOMETRY_EPS) return false;

        float cosTheta = Math.clamp(vhatX * rhatX + vhatY * rhatY + vhatZ * rhatZ, -1.0f, 1.0f);
        float theta = (float) Math.acos(cosTheta);
        float omega = turnGain * theta;

        float invSin = 1f / sinTheta;
        float normX = axisX * invSin, normY = axisY * invSin, normZ = axisZ * invSin;

        // (n̂ · ω) × v
        out.set(
            (normY * vz - normZ * vy) * omega,
            (normZ * vx - normX * vz) * omega,
            (normX * vy - normY * vx) * omega
        );
        return out.lengthSquared() > 1e-12f;
    }
}
