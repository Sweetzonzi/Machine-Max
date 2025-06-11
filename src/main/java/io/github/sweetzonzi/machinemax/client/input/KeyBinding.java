package io.github.sweetzonzi.machinemax.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.sweetzonzi.machinemax.MachineMax;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = MachineMax.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
@OnlyIn(Dist.CLIENT)
public class KeyBinding {

    //本地化用的按键资源路径
    public static final String FREE_CAM_KEY = "key.machine_max.general.free_cam";
    public static final String INTERACT_KEY = "key.machine_max.general.interact";
    public static final String LEAVE_VEHICLE_KEY = "key.machine_max.general.leave_vehicle";

    public static final String GROUND_FORWARD_KEY = "key.machine_max.ground.forward";
    public static final String GROUND_BACKWARD_KEY = "key.machine_max.ground.backward";
    public static final String GROUND_LEFTWARD_KEY = "key.machine_max.ground.leftward";
    public static final String GROUND_RIGHTWARD_KEY = "key.machine_max.ground.rightward";
    public static final String GROUND_CLUTCH_KEY = "key.machine_max.ground.clutch";
    public static final String GROUND_UP_SHIFT_KEY = "key.machine_max.ground.up_shift";
    public static final String GROUND_DOWN_SHIFT_KEY = "key.machine_max.ground.down_shift";
    public static final String GROUND_HAND_BRAKE_KEY = "key.machine_max.ground.hand_brake";
    public static final String GROUND_TOGGLE_HAND_BRAKE_KEY = "key.machine_max.ground.toggle_hand_brake";

    public static final String ASSEMBLY_CYCLE_CONNECTOR_KEY = "key.machine_max.assembly.cycle_connector";
    public static final String ASSEMBLY_CYCLE_VARIANT_KEY = "key.machine_max.assembly.cycle_variant";
    public static final String SCRIPT_HOT_RELOAD_KEY = "key.machine_max.assembly.hot_reload";

    /**
     * 在此注册所有按键
     */
    @SubscribeEvent
    public static void onKeyRegister(RegisterKeyMappingsEvent event) {
        //通用按键
        event.register(KeyBinding.generalFreeCamKey);//自由视角
        event.register(KeyBinding.generalInteractKey);//互动
        event.register(KeyBinding.generalLeaveVehicleKey);//离开载具
        //地面载具
        event.register(KeyBinding.groundForwardKey);//地面载具前进
        event.register(KeyBinding.groundBackWardKey);//地面载具后退
        event.register(KeyBinding.groundLeftwardKey);//地面载具左转
        event.register(KeyBinding.groundRightwardKey);//地面载具右转
        event.register(KeyBinding.groundClutchKey);//地面载具离合
        event.register(KeyBinding.groundUpShiftKey);//地面载具升档
        event.register(KeyBinding.groundDownShiftKey);//地面载具降档
        event.register(KeyBinding.groundHandBrakeKey);//地面载具手刹
        event.register(KeyBinding.groundToggleHandBrakeKey);//地面载具手刹切换
        //船只

        //飞行器

        //机甲

        //部件组装
        event.register(KeyBinding.assemblyCycleConnectorKey);//部件循环选取连接点
        event.register(KeyBinding.assemblyCycleVariantKey);//部件循环选取变体类型
        event.register(KeyBinding.JavascriptHotReloadKey);//脚本热更新


    }

    public static KeyMapping generalFreeCamKey = new KeyMapping(FREE_CAM_KEY,//键位名称
            KeyCategory.GENERAL,//键位冲突类型
            InputConstants.Type.KEYSYM,//默认为键盘
            GLFW.GLFW_KEY_C,//默认按键
            KeyCategory.GENERAL.getCategory()//键位类型
    );
    public static KeyMapping generalInteractKey = new KeyMapping(INTERACT_KEY,//键位名称
            KeyCategory.GENERAL,//键位冲突类型
            InputConstants.Type.KEYSYM,//默认为键盘
            GLFW.GLFW_KEY_F,//默认按键
            KeyCategory.GENERAL.getCategory()//键位类型
    );
    public static KeyMapping generalLeaveVehicleKey = new KeyMapping(LEAVE_VEHICLE_KEY,//键位名称
            KeyCategory.GENERAL,//键位冲突类型
            InputConstants.Type.KEYSYM,//默认为键盘
            GLFW.GLFW_KEY_J,//默认按键
            KeyCategory.GENERAL.getCategory()//键位类型
    );

    public static KeyMapping groundForwardKey = new KeyMapping(GROUND_FORWARD_KEY,//键位名称
            KeyCategory.GROUND,//键位冲突类型
            InputConstants.Type.KEYSYM,//默认为键盘
            GLFW.GLFW_KEY_W,//默认按键
            KeyCategory.GROUND.getCategory()//键位类型
    );
    public static KeyMapping groundBackWardKey = new KeyMapping(GROUND_BACKWARD_KEY,//键位名称
            KeyCategory.GROUND,//键位冲突类型
            InputConstants.Type.KEYSYM,//默认为键盘
            GLFW.GLFW_KEY_S,//默认按键
            KeyCategory.GROUND.getCategory()//键位类型
    );
    public static KeyMapping groundLeftwardKey = new KeyMapping(GROUND_LEFTWARD_KEY,//键位名称
            KeyCategory.GROUND,//键位冲突类型
            InputConstants.Type.KEYSYM,//默认为键盘
            GLFW.GLFW_KEY_A,//默认按键
            KeyCategory.GROUND.getCategory()//键位类型
    );
    public static KeyMapping groundRightwardKey = new KeyMapping(GROUND_RIGHTWARD_KEY,//键位名称
            KeyCategory.GROUND,//键位冲突类型
            InputConstants.Type.KEYSYM,//默认为键盘
            GLFW.GLFW_KEY_D,//默认按键
            KeyCategory.GROUND.getCategory()//键位类型
    );
    public static KeyMapping groundClutchKey = new KeyMapping(GROUND_CLUTCH_KEY,//键位名称
            KeyCategory.GROUND,//键位冲突类型
            InputConstants.UNKNOWN,//默认按键无
            KeyCategory.GROUND.getCategory()//键位类型
    );
    public static KeyMapping groundUpShiftKey = new KeyMapping(GROUND_UP_SHIFT_KEY,//键位名称
            KeyCategory.GROUND,//键位冲突类型
            InputConstants.UNKNOWN,//默认按键无
            KeyCategory.GROUND.getCategory()//键位类型
    );
    public static KeyMapping groundDownShiftKey = new KeyMapping(GROUND_DOWN_SHIFT_KEY,//键位名称
            KeyCategory.GROUND,//键位冲突类型
            InputConstants.UNKNOWN,//默认按键无
            KeyCategory.GROUND.getCategory()//键位类型
    );
    public static KeyMapping groundHandBrakeKey = new KeyMapping(GROUND_HAND_BRAKE_KEY,//键位名称
            KeyCategory.GROUND,//键位冲突类型
            InputConstants.UNKNOWN,//默认按键无
            KeyCategory.GROUND.getCategory()//键位类型
    );
    public static KeyMapping groundToggleHandBrakeKey = new KeyMapping(GROUND_TOGGLE_HAND_BRAKE_KEY,//键位名称
            KeyCategory.GROUND,//键位冲突类型
            InputConstants.UNKNOWN,//默认按键无
            KeyCategory.GROUND.getCategory()//键位类型
    );

    public static KeyMapping assemblyCycleConnectorKey = new KeyMapping(ASSEMBLY_CYCLE_CONNECTOR_KEY,//键位名称
            KeyCategory.ASSEMBLY,//键位冲突类型
            InputConstants.Type.KEYSYM,//默认为键盘
            GLFW.GLFW_KEY_C,//默认按键
            KeyCategory.ASSEMBLY.getCategory()//键位类型
    );

    public static KeyMapping assemblyCycleVariantKey = new KeyMapping(ASSEMBLY_CYCLE_VARIANT_KEY,//键位名称
            KeyCategory.ASSEMBLY,//键位冲突类型
            InputConstants.Type.KEYSYM,//默认为键盘
            GLFW.GLFW_KEY_V,//默认按键
            KeyCategory.ASSEMBLY.getCategory()//键位类型
    );

    public static KeyMapping JavascriptHotReloadKey = new KeyMapping(SCRIPT_HOT_RELOAD_KEY,//键位名称
            KeyCategory.ASSEMBLY,//键位冲突类型
            InputConstants.Type.KEYSYM,//默认为键盘
            GLFW.GLFW_KEY_BACKSLASH,//默认按键是反斜杠
            KeyCategory.ASSEMBLY.getCategory()//键位类型
    );
}