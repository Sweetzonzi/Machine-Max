package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem;

public enum SubsystemTypes {
    BASIC, //基础子系统，无实际功能，但可用于扩展其他子系统，也可作为独立可破坏部分向零件传导伤害
    ITEM_STORAGE,//物品存储子系统，可存储物品，可指定容量
    ENGINE,//发动机子系统，可指定最大功率，转速等
    MOTOR,//电动机子系统，与发动机类似，可指定最大功率等
    GEARBOX,//变速箱子系统，可指定多级减速比，自动变速
    CAR_CTRL,//车辆控制子系统，用于更好地处理控制输入信号，辅助控制变速箱、轮胎转向等
    MOTORBIKE_CTRL,//摩托车控制子系统，用于摩托车特有的控制逻辑，如倾斜角度限制
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