package io.github.tt432.machinemax.util.formula;

import com.jme3.math.Vector3f;
import io.github.tt432.machinemax.common.vehicle.Part;

/**
 * 此类中集中收纳了本模组与动力学有关的机理公式，方便管理与调用
 * @author 甜粽子
 */
public class Dynamic {
    /**
     * 根据给定部件的运动状态计算其受到的气动力
     * @param part 要计算受力的零部件
     * @return 相对部件自身坐标的部件受力向量
     */
    public static Vector3f aerodynamicForce(Part part){
        return new Vector3f();
    }
    /**
     * 根据给定部件的运动状态计算其受到的水动力
     * @param part 要计算受力的零部件
     * @return 相对部件自身坐标的部件受力向量
     */
    public static Vector3f hydrodynamicForce(Part part){
         return new Vector3f();
    }
}
