package io.github.sweetzonzi.machine_max.util.mechanic;

import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.AdvancedAeroAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.HydrodynamicAttr;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 此类中集中收纳了本模组与动力学有关的机理公式，方便管理与调用
 *
 * @author 甜粽子
 */
public class DynamicUtil {

    /**
     * 通用滑移率摩擦缩放函数 (C¹ 连续分段曲线)
     * * @param slipRatio     当前滑移率 (通常 0.0 ~ 1.0+)
     * @param peakSlip      峰值滑移率点 (例如 0.15)
     * @param baseScale     初始摩擦系数缩放 (s=0 时的值，通常为 1.0)
     * @param peakScale     峰值摩擦系数缩放 (s=peakSlip 时的值，例如 1.2)
     * @param kineticScale  极限滑移下的摩擦系数缩放 (s=1.0 时的值，例如 0.9)
     * @return 最终的摩擦缩放因子
     */
    public static float calculateSlipScale(
            float slipRatio,
            float peakSlip,
            float baseScale,
            float peakScale,
            float kineticScale
    ) {
        return calculateSlipScale(slipRatio, peakSlip, 1.0f, baseScale, peakScale, kineticScale);
    }

    /**
     * 通用滑移率摩擦缩放函数 (C¹ 连续分段曲线，支持自定义动摩擦终点)
     *
     * @param slipRatio     当前滑移率/角度（非负）
     * @param peakSlip      峰值点
     * @param kineticSlip   动摩擦终点（达到后保持 kineticScale）
     * @param baseScale     初始摩擦系数缩放
     * @param peakScale     峰值摩擦系数缩放
     * @param kineticScale  动摩擦缩放
     * @return 最终摩擦缩放因子
     */
    public static float calculateSlipScale(
            float slipRatio,
            float peakSlip,
            float kineticSlip,
            float baseScale,
            float peakScale,
            float kineticScale
    ) {
        float s = Math.max(0f, slipRatio);
        float peak = Math.max(1e-5f, peakSlip);
        float kinetic = Math.max(peak + 1e-5f, kineticSlip);

        if (s < peak) {
            // --- 上升段：从 baseScale 到 peakScale ---
            // 归一化插值因子 [0, 1]
            float t = s / peak;
            // Smoothstep 插值: 3t^2 - 2t^3
            float smooth = t * t * (3f - 2f * t);
            return baseScale + (peakScale - baseScale) * smooth;

        } else {
            // --- 下降段：从 peakScale 到 kineticScale ---
            // 归一化插值因子 [0, 1]，超过 kineticSlip 的部分会被 clamp
            float t = (s - peak) / Math.max(1e-5f, kinetic - peak);
            t = Math.min(t, 1f);
            // Smoothstep 插值
            float smooth = t * t * (3f - 2f * t);
            return peakScale + (kineticScale - peakScale) * smooth;
        }
    }

    /**
     * 根据给定部件的运动状态计算其受到的流体动力
     *
     * @param density       流体密度，仅用于阻力二阶项和升力计算
     * @param viscosity     流体动力粘度，用于一阶阻力项
     * @param projectedArea 三轴参考面积，通常为投影面积
     * @param attr          要计算受力的零部件的流体动力属性
     * @param localVel      流体动力计算点本地坐标系下的速度
     * @return 流体动力计算点本地坐标系下的受力向量
     */
    public static Vector3f aeroDynamicForce(
            float density,
            float viscosity,
            Vec3 projectedArea,
            HydrodynamicAttr attr,
            Vector3f localVel) {
        if (density <= 0)
            return Vector3f.ZERO;

        // 速度量
        float vx = localVel.x;
        float vy = localVel.y;
        float vz = localVel.z;

        float vel2 = vx * vx + vy * vy + vz * vz;
        if (vel2 < 1e-6f)
            return Vector3f.ZERO;

        float vel = (float) Math.sqrt(vel2);

        // 马赫数与跨音增益
        float mach = vel / 340.29f;
        float transSonicAmplifier = calculateTransSonicAmplifier(attr.transSonicAmplifier(), mach);

        // 湿表面积（投影面积之和 * 2）
        float wettedArea = (float) (projectedArea.x + projectedArea.y + projectedArea.z) * 2f;

        Vector3f result = new Vector3f();

        // 是否尝试使用高级气动
        boolean useAdvanced = attr.advanced()
                && vz < 0.0f // 来流方向正确（z-）
                && vel > 1e-3f;

        if (useAdvanced) {
            // —— 高级气动框架 ——
            // 1. 先计算并叠加“一阶粘性阻力”（所有方向都保留）
            applyLinearDrag(result, viscosity, wettedArea, attr, localVel);

            // 2. 高级升阻力
            Vector3f advancedForce = advancedAeroForce(
                    density,
                    projectedArea,
                    attr.advancedAero(),
                    localVel);

            result.addLocal(advancedForce);

            // x 方向侧滑阻力仍使用简单模型（二阶）
            applySimpleQuadraticDragX(
                    result, density, projectedArea, attr, vx);
        } else {
            // —— 完全简单模型 ——
            applySimpleDragAllDirections(
                    result,
                    density,
                    viscosity,
                    projectedArea,
                    wettedArea,
                    attr,
                    localVel);
        }

        // 超声增益
        result.multLocal(transSonicAmplifier);

        // 简单升力（仅在非高级模式下）
        if (!useAdvanced) {
            float xzVel2 = vx * vx + vz * vz;
            float xyVel2 = vx * vx + vy * vy;
            float yzVel2 = vy * vy + vz * vz;

            result.x += attr.xLift() * yzVel2 * density * (float) projectedArea.x * 0.5f;
            result.y += attr.yLift() * xzVel2 * density * (float) projectedArea.y * 0.5f;
            result.z += attr.zLift() * xyVel2 * density * (float) projectedArea.z * 0.5f;
        }

        // 全局缩放
        result.multLocal(attr.scale());
        return result;
    }

    /*
     * =========================
     * 简单模型工具函数
     * =========================
     */

    private static void applySimpleDragAllDirections(
            Vector3f result,
            float density,
            float viscosity,
            Vec3 projectedArea,
            float wettedArea,
            HydrodynamicAttr attr,
            Vector3f v) {
        applyAxisDrag(
                result, v.x,
                v.x > 0 ? attr.leftward() : attr.rightward(),
                viscosity, density,
                wettedArea, (float) projectedArea.x,
                Axis.X);
        applyAxisDrag(
                result, v.y,
                v.y > 0 ? attr.upward() : attr.downward(),
                viscosity, density,
                wettedArea, (float) projectedArea.y,
                Axis.Y);
        applyAxisDrag(
                result, v.z,
                v.z > 0 ? attr.backward() : attr.forward(),
                viscosity, density,
                wettedArea, (float) projectedArea.z,
                Axis.Z);
    }

    private static void applyLinearDrag(
            Vector3f result,
            float viscosity,
            float wettedArea,
            HydrodynamicAttr attr,
            Vector3f v) {
        applyLinearAxis(result, v.x,
                v.x > 0 ? attr.leftward() : attr.rightward(),
                viscosity, wettedArea, Axis.X);
        applyLinearAxis(result, v.y,
                v.y > 0 ? attr.upward() : attr.downward(),
                viscosity, wettedArea, Axis.Y);
        applyLinearAxis(result, v.z,
                v.z > 0 ? attr.backward() : attr.forward(),
                viscosity, wettedArea, Axis.Z);
    }

    private static void applySimpleQuadraticDragX(
            Vector3f result,
            float density,
            Vec3 projectedArea,
            HydrodynamicAttr attr,
            float vx) {
        List<Float> coeff = vx > 0 ? attr.leftward() : attr.rightward();
        if (coeff.size() < 2)
            return;

        float force = 0.5f * density
                * coeff.get(1)
                * vx * vx
                * (float) projectedArea.x;

        result.x += -Math.signum(vx) * force;
    }

    private static void applyAxisDrag(
            Vector3f result,
            float v,
            List<Float> coeff,
            float viscosity,
            float density,
            float wettedArea,
            float projectedArea,
            Axis axis) {
        float sign = Math.signum(v);
        float absV = Math.abs(v);

        for (int i = 0; i < coeff.size(); i++) {
            float c = coeff.get(i);
            float force;
            if (i == 0) {
                force = viscosity * c * absV * wettedArea;
            } else {
                force = 0.5f * density * c * absV * absV * projectedArea;
            }
            axis.add(result, -sign * force);
        }
    }

    private static void applyLinearAxis(
            Vector3f result,
            float v,
            List<Float> coeff,
            float viscosity,
            float wettedArea,
            Axis axis) {
        if (coeff.isEmpty())
            return;
        float force = viscosity * coeff.get(0) * Math.abs(v) * wettedArea;
        axis.add(result, -Math.signum(v) * force);
    }

    /*
     * =========================
     * 高级气动（占位）
     * =========================
     */

    private static Vector3f advancedAeroForce(
            float density,
            Vec3 projectedArea,
            AdvancedAeroAttr aero,
            Vector3f v) {
        // 局部速度分量
        float vy = v.y;
        float vz = v.z;

        // 来流速度模长（只考虑 y-z 平面）
        float v2 = vy * vy + vz * vz;
        if (v2 < 1e-6f)
            return Vector3f.ZERO;

        float vMag = (float) Math.sqrt(v2);

        // === 1. 攻角 α ===
        // z- 为来流方向
        float alpha = (float) Math.atan2(vy, -vz);

        // === 2. 升力系数 Cl（带对称失速裁剪） ===
        float cl = aero.liftSlope() * (alpha - aero.alpha0());

        float clMax = aero.liftSlope() * aero.alphaStall();
        if (cl > clMax)
            cl = clMax;
        else if (cl < -clMax)
            cl = -clMax;

        // === 3. 阻力系数 Cd ===
        float cd = aero.cd0() + aero.kInduced() * cl * cl;

        // === 4. 动压 q ===
        float q = 0.5f * density * v2;

        // 机翼面积：使用 y 方向投影面积
        float area = (float) projectedArea.y;

        // === 5. 升力与阻力大小 ===
        float lift = q * area * cl;
        float drag = q * area * cd;

        // === 6. 力方向分解 ===
        // 来流单位向量（反向速度）
        float invV = 1.0f / vMag;
        float flowY = -vy * invV;
        float flowZ = -vz * invV;

        // 升力方向：来流在 y-z 平面的法向（右手系）
        float liftY = -flowZ;
        float liftZ = flowY;

        Vector3f result = new Vector3f();

        // 阻力（反向来流）
        result.y += drag * flowY;
        result.z += drag * flowZ;

        // 升力
        result.y += lift * liftY;
        result.z += lift * liftZ;

        return result;
    }

    private static float calculateTransSonicAmplifier(float baseAmplifier, float mach) {
        if (mach <= 0.8f)
            return 1.0f;
        else if (mach <= 1.2f) {
            float t = (mach - 0.8f) / 0.4f;
            t = (float) (3 * t * t - 2 * t * t * t);
            return 1.0f + (baseAmplifier - 1.0f) * t;
        } else
            return baseAmplifier;
    }

    private enum Axis {
        X {
            void add(Vector3f v, float f) {
                v.x += f;
            }
        },
        Y {
            void add(Vector3f v, float f) {
                v.y += f;
            }
        },
        Z {
            void add(Vector3f v, float f) {
                v.z += f;
            }
        };

        abstract void add(Vector3f v, float f);
    }
}
