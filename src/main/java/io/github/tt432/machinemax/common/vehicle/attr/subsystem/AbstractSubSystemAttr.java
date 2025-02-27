package io.github.tt432.machinemax.common.vehicle.attr.subsystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import io.github.tt432.machinemax.common.registry.MMDataRegistries;
import io.github.tt432.machinemax.common.vehicle.attr.PortAttr;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Getter
abstract public class AbstractSubSystemAttr {

    protected final Map<String,PortAttr> portAttrs = new HashMap<>();

    protected AbstractSubSystemAttr() {}

    public abstract MapCodec<? extends AbstractSubSystemAttr> codec();

    public enum SubsystemType {
        RESOURCE_STORAGE,//资源存储子系统，可指定储存类型、容量等
        ENGINE,//发动机子系统，可指定最大功率，转速等
        GEARBOX,//变速箱子系统，可指定多级减速比，自动变速
        TRANSMISSION,//传输子系统，可将能量或资源转移给其他子系统
        BRAKE,//刹车子系统，允许指定轴的旋转减速停止
        CAR_STEERING,//车辆阿克曼转向子系统，允许平稳转向
        SEAT,//座椅子系统，可指定乘坐位置
        CONTROL_INPUT
    }

    public abstract SubsystemType getType();

    public static final Codec<AbstractSubSystemAttr> CODEC = MMDataRegistries.getSUBSYSTEM_DATA_CODEC().byNameCodec()
            .dispatch(
                 AbstractSubSystemAttr::codec,
                 Function.identity()
            );

    public static final Codec<Map<String, AbstractSubSystemAttr>> MAP_CODEC = Codec.unboundedMap(
            Codec.STRING,//子系统名称
            CODEC//子系统属性
    );
}
