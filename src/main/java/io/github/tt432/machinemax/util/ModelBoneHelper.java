package io.github.tt432.machinemax.util;

import cn.solarmoon.spark_core.animation.model.origin.OLocator;
import net.minecraft.world.phys.Vec3;
import org.ode4j.math.DVector3;

public class ModelBoneHelper {
    /**
     * 根据locator名称获取供机体结构构建使用的locator的属性值
     * @param locatorName
     * @param locator
     * @return
     */
    public static Object getLocatorValue(String locatorName, OLocator locator) {
        Object value;
        Object temp;
        if (locatorName.startsWith("offset_")) {//未使用
            temp = locator.getOffset();
            value = new DVector3(((Vec3) temp).x, ((Vec3) temp).y, ((Vec3) temp).z);
        } else if (locatorName.startsWith("rotation_")) {//旋转
            temp = locator.getRotation();
            value = new DVector3(((Vec3) temp).x, ((Vec3) temp).y, ((Vec3) temp).z);
        }  else if (locatorName.startsWith("mass_")) {//质量(kg)
            temp = locatorName.replaceFirst("mass_", "");
            value = Double.parseDouble((String) temp);
        } else if (locatorName.startsWith("radius_")) {//半径(m)
            temp = locatorName.replaceFirst("radius_", "");
            value = Double.parseDouble((String) temp);
        } else if (locatorName.startsWith("length_")) {//长度(m)
            temp = locatorName.replaceFirst("length_", "");
            value = Double.parseDouble((String) temp);
        } else if (locatorName.startsWith("direction_")) {//方向(1=x,2=y,3=z)
            temp = locatorName.replaceFirst("direction_", "");
            value = switch ((String) temp) {
                case "x", "X" -> 1;
                case "y", "Y" -> 2;
                case "z", "Z" -> 3;
                default -> 1;
            };
        } else if (locatorName.startsWith("material_")) {//材料名称，控制碰撞体的材料类型，决定其装甲抗性、导热系数、相对摩擦系数等
            temp = locatorName.replaceFirst("material_", "");
            value = temp;//TODO:材料名称映射到record或注册表
        } else if (locatorName.startsWith("thickness_")) {//材料厚度，直接决定碰撞体的护甲水平
            temp = locatorName.replaceFirst("thickness_", "");
            value = Double.parseDouble((String) temp);
        } else {
            value = null;
        }
        return value;
    }
}
