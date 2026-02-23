package io.github.sweetzonzi.machine_max.util.mechanic;

import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.HydrodynamicAttr;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 此类中集中收纳了本模组与动力学有关的机理公式，方便管理与调用
 *
 * @author 甜粽子
 */
public class DynamicUtil {
    /**
     * 根据给定部件的运动状态计算其受到的流体动力
     *
     * @param density 流体密度，仅用于阻力二阶项和升力计算
     * @param viscosity 流体动力粘度，用于一阶阻力项
     * @param projectedArea 投影面积
     * @param attr 要计算受力的零部件的流体动力属性
     * @param localVel 相对部件自身坐标的部件速度
     * @return 相对部件自身坐标的部件受力向量
     */
    public static Vector3f aeroDynamicForce(float density, float viscosity, Vec3 projectedArea, HydrodynamicAttr attr, Vector3f localVel) {
        if (density <= 0) return new Vector3f();//真空中没有气动力
        Vector3f result = new Vector3f();
        double xzVel = Math.sqrt(localVel.x * localVel.x + localVel.z * localVel.z);
        double xyVel = Math.sqrt(localVel.x * localVel.x + localVel.y * localVel.y);
        double yzVel = Math.sqrt(localVel.y * localVel.y + localVel.z * localVel.z);
        double vel = Math.sqrt(localVel.x * localVel.x + localVel.y * localVel.y + localVel.z * localVel.z);
        float mach = (float) (vel / 340.29);
        float transSonicAmplifier = calculateTransSonicAmplifier(attr.transSonicAmplifier(), mach);
        
        //计算湿表面积（投影面积之和*2）
        float wettedArea = (float) (projectedArea.x + projectedArea.y + projectedArea.z) * 2f;
        
        //阻力项
        List<Float> xCoeff, yCoeff, zCoeff;
        if (localVel.x > 0) xCoeff = attr.leftward();
        else xCoeff = attr.rightward();
        for (int x = 0; x < xCoeff.size(); x++) {
            float dragCoeff = xCoeff.get(x);
            float velocityPower = (float) Math.pow(Math.abs(localVel.x), x + 1);
            //一阶阻力项乘以粘度并使用湿表面积，二阶阻力项乘以密度并使用投影面积
            float dragForce;
            if (x == 0) {
                dragForce = viscosity * dragCoeff * velocityPower * wettedArea;
            } else {
                dragForce = density * dragCoeff * velocityPower * (float) projectedArea.x * 0.5f;
            }
            result.x += (float) (-Math.signum(localVel.x) * dragForce);
        }
        if (localVel.y > 0) yCoeff = attr.upward();
        else yCoeff = attr.downward();
        for (int y = 0; y < yCoeff.size(); y++) {
            float dragCoeff = yCoeff.get(y);
            float velocityPower = (float) Math.pow(Math.abs(localVel.y), y + 1);
            //一阶阻力项乘以粘度并使用湿表面积，二阶阻力项乘以密度并使用投影面积
            float dragForce;
            if (y == 0) {
                dragForce = viscosity * dragCoeff * velocityPower * wettedArea;
            } else {
                dragForce = density * dragCoeff * velocityPower * (float) projectedArea.y * 0.5f;
            }
            result.y += (float) (-Math.signum(localVel.y) * dragForce);
        }
        if (localVel.z > 0) zCoeff = attr.backward();
        else zCoeff = attr.forward();
        for (int z = 0; z < zCoeff.size(); z++) {
            float dragCoeff = zCoeff.get(z);
            float velocityPower = (float) Math.pow(Math.abs(localVel.z), z + 1);
            //一阶阻力项乘以粘度并使用湿表面积，二阶阻力项乘以密度并使用投影面积
            float dragForce;
            if (z == 0) {
                dragForce = viscosity * dragCoeff * velocityPower * wettedArea;
            } else {
                dragForce = density * dragCoeff * velocityPower * (float) projectedArea.z * 0.5f;
            }
            result.z += (float) (-Math.signum(localVel.z) * dragForce);
        }
        result = result.mult(transSonicAmplifier);//阻力项乘以超声增益系数
        
        //升力项（使用密度计算）
        float xLift = attr.xLift() * (float) yzVel * density * (float) projectedArea.x * 0.5f;
        float yLift = attr.yLift() * (float) xzVel * density * (float) projectedArea.y * 0.5f;
        float zLift = attr.zLift() * (float) xyVel * density * (float) projectedArea.z * 0.5f;
        
        //计算结果
        result.x += xLift;
        result.y += yLift;
        result.z += zLift;
        result.multLocal(attr.scale());//应用缩放系数
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
