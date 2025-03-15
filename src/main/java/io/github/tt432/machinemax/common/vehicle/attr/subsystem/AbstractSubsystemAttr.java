package io.github.tt432.machinemax.common.vehicle.attr.subsystem;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import io.github.tt432.machinemax.common.registry.MMDataRegistries;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Getter
abstract public class AbstractSubsystemAttr {

    protected AbstractSubsystemAttr() {
    }

    public abstract MapCodec<? extends AbstractSubsystemAttr> codec();

    public enum SubsystemType {
        RESOURCE_STORAGE,//资源存储子系统，可指定储存类型、容量等
        ENGINE,//发动机子系统，可指定最大功率，转速等
        GEARBOX,//变速箱子系统，可指定多级减速比，自动变速
        CAR_CTRL,//车辆控制子系统，用于更好地处理控制输入信号，辅助控制变速箱、轮胎转向等
        TURRET_CTRL,//炮塔控制子系统，用于控制炮塔的位置、角度、开火等
        TRANSMISSION,//传动子系统，将转速、转矩分发给各个轴
        MOTOR,//驱动机构子系统
        SEAT,//座椅子系统，可指定乘坐位置、信号输出等
        SIGNAL_CONVERT,//信号转换器子系统，可将指定名称的输入信号转换为其他名称，此外可将收到的信号进行延迟处理
    }

    public abstract SubsystemType getType();

    public static final Codec<Pair<String, List<String>>> TARGET_NAMES_CODEC = Codec.pair(
            Codec.STRING.fieldOf("key").codec(),
            Codec.STRING.listOf().fieldOf("targets").codec());

    public static final Codec<AbstractSubsystemAttr> CODEC = MMDataRegistries.getSUBSYSTEM_ATTR_CODEC().byNameCodec()
            .dispatch(
                    AbstractSubsystemAttr::codec,
                    Function.identity()
            );

    public static final Codec<Map<String, AbstractSubsystemAttr>> MAP_CODEC = Codec.unboundedMap(
            Codec.STRING,//子系统名称
            CODEC//子系统属性
    );
}
