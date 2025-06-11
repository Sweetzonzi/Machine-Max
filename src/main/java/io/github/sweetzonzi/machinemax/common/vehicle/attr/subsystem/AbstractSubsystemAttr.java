package io.github.sweetzonzi.machinemax.common.vehicle.attr.subsystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import io.github.sweetzonzi.machinemax.common.registry.MMDataRegistries;
import io.github.sweetzonzi.machinemax.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.AbstractSubsystem;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Getter
abstract public class AbstractSubsystemAttr {

    public final float basicDurability;
    public final String hitBox;

    protected AbstractSubsystemAttr(float basicDurability, String hitBox) {
        this.basicDurability = basicDurability;
        this.hitBox = hitBox;
    }

    public abstract MapCodec<? extends AbstractSubsystemAttr> codec();

    public enum SubsystemType {
        RESOURCE_STORAGE,//资源存储子系统，可指定储存类型、容量等
        ENGINE,//发动机子系统，可指定最大功率，转速等
        MOTOR,//电动机子系统，与发动机类似，可指定最大功率等
        GEARBOX,//变速箱子系统，可指定多级减速比，自动变速
        CAR_CTRL,//车辆控制子系统，用于更好地处理控制输入信号，辅助控制变速箱、轮胎转向等
        TURRET_CTRL,//炮塔控制子系统，用于控制炮塔的位置、角度、开火等
        TRANSMISSION,//传动子系统，将转速、转矩分发给各个轴
        JOINT,//驱动机构子系统，可指定关节各轴驱动和伺服
        WHEEL,//轮胎子系统，与MOTOR类似，但仅限x轴旋转驱动和y轴旋转伺服
        TURRET,//炮塔子系统，与MOTOR类似，但仅限x轴和y轴旋转伺服
        SEAT,//座椅子系统，可指定乘坐位置、信号输出等
        SIGNAL_CONVERT,//信号转换器子系统，可将指定名称的输入信号转换为其他名称，此外可将收到的信号进行延迟处理
        CAMERA,//摄像头子系统，可提供视角
        JAVASCRIPT,//自定义脚本子系统
    }

    public abstract SubsystemType getType();

    public abstract AbstractSubsystem createSubsystem(ISubsystemHost owner, String name);

    public static final Codec<Map<String, List<String>>> SIGNAL_TARGETS_CODEC = Codec.unboundedMap(
            Codec.STRING,
            Codec.STRING.listOf()
    );

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
