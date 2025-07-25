package io.github.sweetzonzi.machinemax.util.mechanic;

import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machinemax.common.vehicle.attr.HydrodynamicAttr;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 此类中集中收纳了本模组与动力学有关的机理公式，方便管理与调用
 *
 * @author 甜粽子
 */
public class DynamicUtil {
    /**
     * 根据给定部件的运动状态计算其受到的气动力
     *
     * @param attr     要计算受力的零部件的气动属性
     * @param localVel 相对部件自身坐标的部件速度
     * @return 相对部件自身坐标的部件受力向量
     */
    public static Vector3f aeroDynamicForce(float density, Vec3 projectedArea, HydrodynamicAttr attr, Vector3f localVel) {
        if (density <= 0) return new Vector3f();//真空中没有气动力
        Vector3f result = new Vector3f();
        double xzVel = Math.sqrt(localVel.x * localVel.x + localVel.z * localVel.z);
        double xyVel = Math.sqrt(localVel.x * localVel.x + localVel.y * localVel.y);
        double yzVel = Math.sqrt(localVel.y * localVel.y + localVel.z * localVel.z);
        double vel = Math.sqrt(localVel.x * localVel.x + localVel.y * localVel.y + localVel.z * localVel.z);
        float mach = (float) (vel / 340.29);
        float transSonicAmplifier = calculateTransSonicAmplifier(attr.transSonicAmplifier(), mach);
        //阻力项
        List<Float> xCoeff, yCoeff, zCoeff;
        if (localVel.x > 0) xCoeff = attr.leftward();
        else xCoeff = attr.rightward();
        for (int x = 0; x < xCoeff.size(); x++)
            result.x += (float) (-Math.signum(localVel.x) * (xCoeff.get(x) * Math.pow(Math.abs(localVel.x), x + 1)));
        if (localVel.y > 0) yCoeff = attr.upward();
        else yCoeff = attr.downward();
        for (int y = 0; y < yCoeff.size(); y++)
            result.y += (float) (-Math.signum(localVel.y) * (yCoeff.get(y) * Math.pow(Math.abs(localVel.y), y + 1)));
        if (localVel.z > 0) zCoeff = attr.backward();
        else zCoeff = attr.forward();
        for (int z = 0; z < zCoeff.size(); z++)
            result.z += (float) (-Math.signum(localVel.z) * (zCoeff.get(z) * Math.pow(Math.abs(localVel.z), z + 1)));
        result = result.mult(transSonicAmplifier);//阻力项乘以超声增益系数
        //升力项
        float xLift = 0, yLift = 0, zLift = 0;
        for (int x = 0; x < attr.xLift().size(); x++)
            xLift += (float) (attr.xLift().get(x) * Math.pow(yzVel, x));
        for (int y = 0; y < attr.yLift().size(); y++)
            yLift += (float) (attr.yLift().get(y) * Math.pow(xzVel, y));
        for (int z = 0; z < attr.zLift().size(); z++)
            zLift += (float) (attr.zLift().get(z) * Math.pow(xyVel, z));
        //计算结果
        result.x += xLift;
        result.y += yLift;
        result.z += zLift;
        result.x *= (float) projectedArea.x;//根据投影面积缩放受力
        result.y *= (float) projectedArea.y;
        result.z *= (float) projectedArea.z;
        result.multLocal(0.5f * density * attr.scale());//应用空气密度和阻力公式前的1/2，并应用缩放
        return result;
    }

    private static float calculateTransSonicAmplifier(float baseAmplifier, float mach){
        if (mach <= 0.8) return 1.0f;
        else if (mach <= 1.2) {
            float temp = (mach - 0.8f)/0.4f;
            temp = (float) (3 * Math.pow(temp, 2) - 2 * Math.pow(temp, 3));
            return 1.0f + (baseAmplifier - 1.0f) * temp;
        }
        else return baseAmplifier;
    }
}
