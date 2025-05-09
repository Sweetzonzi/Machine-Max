package io.github.tt432.machinemax.datagen;

import io.github.tt432.machinemax.external.MMDynamicRes;
import net.neoforged.neoforge.common.data.LanguageProvider;

public class OtherLanguage {
    public interface CustomLanguageGetter {
        void doTrans(LanguageProvider provider);
    }
    public static void injection() {
        MMDynamicRes.CUSTOM_LANGUAGE_PROVIDERS.put( "zh_cn", ( provider -> {
            //按键类别
            provider.add("resourceType.category.machine_max.general", "Machine Max:通用");
            provider.add("resourceType.category.machine_max.ground", "Machine Max:地面载具");
            provider.add("resourceType.category.machine_max.ship", "Machine Max:舰艇");
            provider.add("resourceType.category.machine_max.plane", "Machine Max:飞行器");
            provider.add("resourceType.category.machine_max.mech", "Machine Max:机甲");
            provider.add("resourceType.category.machine_max.assembly", "Machine Max:组装");
            //按键名称-通用
            provider.add("resourceType.machine_max.general.free_cam", "自由摄像");
            provider.add("resourceType.machine_max.general.interact", "交互");
            provider.add("resourceType.machine_max.general.leave_vehicle", "离开载具");
            //按键名称-地面载具
            provider.add("resourceType.machine_max.ground.forward", "前进");
            provider.add("resourceType.machine_max.ground.backward", "后退");
            provider.add("resourceType.machine_max.ground.leftward", "左转");
            provider.add("resourceType.machine_max.ground.rightward", "右转");
            provider.add("resourceType.machine_max.ground.clutch", "离合");
            provider.add("resourceType.machine_max.ground.up_shift", "升档");
            provider.add("resourceType.machine_max.ground.down_shift", "降档");
            //按键名称-组装
            provider.add("resourceType.machine_max.assembly.cycle_connector", "循环部件连接口");
            provider.add("resourceType.machine_max.assembly.cycle_variant", "循环部件变体");
            //提示信息
            provider.add("message.machine_max.leaving_vehicle", "长按%1$s键%2$s/0.50秒以离开载具");
            provider.add("tooltip.machinemax.crossbar.interact", "互动以拆除：");
            provider.add("tooltip.machinemax.spray_can.interact", "互动以喷涂：");
        }));


        MMDynamicRes.CUSTOM_LANGUAGE_PROVIDERS.put( "en_us", ( provider -> {
            //按键类别
            provider.add("resourceType.category.machine_max.general", "Machine Max:General");
            provider.add("resourceType.category.machine_max.ground", "Machine Max:Ground");
            provider.add("resourceType.category.machine_max.ship", "Machine Max:Ship");
            provider.add("resourceType.category.machine_max.plane", "Machine Max:Plane");
            provider.add("resourceType.category.machine_max.mech", "Machine Max:Mech");
            provider.add("resourceType.category.machine_max.assembly", "Machine Max:Assembly");
            //按键名称-通用
            provider.add("resourceType.machine_max.general.free_cam", "Free Camera");
            provider.add("resourceType.machine_max.general.interact", "Interact with Vehicle");
            provider.add("resourceType.machine_max.general.leave_vehicle", "Leave Vehicle");
            //按键名称-地面载具
            provider.add("resourceType.machine_max.ground.forward", "Forward");
            provider.add("resourceType.machine_max.ground.backward", "Backward");
            provider.add("resourceType.machine_max.ground.leftward", "Leftward");
            provider.add("resourceType.machine_max.ground.rightward", "Rightward");
            provider.add("resourceType.machine_max.ground.clutch", "Clutch");
            provider.add("resourceType.machine_max.ground.up_shift", "Shift Up");
            provider.add("resourceType.machine_max.ground.down_shift", "Shift Down");
            //按键名称-组装
            provider.add("resourceType.machine_max.assembly.cycle_connector", "Cycle Part Connector");
            provider.add("resourceType.machine_max.assembly.cycle_variant", "Cycle Part Variant");

            //提示信息
            provider.add("message.machine_max.leaving_vehicle", "Hold %1$s %2$s/0.50s to leave the vehicle.");
            provider.add("tooltip.machinemax.crossbar.interact", "Interact to disassemble:");
            provider.add("tooltip.machinemax.spray_can.interact", "Interact to paint:");
        }));
    }
}
