package io.github.sweetzonzi.machinemax.web.widget;

import io.github.sweetzonzi.machinemax.web.MMWebApp;

/**
 * 该对象为对应flutter_ui/lib/blink.dart中的HudSmartWidget对象的java控制对象
 * 计划用于告知前端：注册hud图形、控制显示状态、大小、位置等操作（可在下面补充
 * <br> 通过 {@link MMWebApp#sendPacket(String, Object...)} 与前端通信
 * <br>TODO 注册hud图形: 通过mc命令注册自定义矢量图形（如.svg文件），若名称被占用则提示失败
 * <br>TODO 控制状态，向前端传入状态名与状态详细配置
 * <br>TODO 写出传入大小、位置等的改变配置的方法
 */

@FlutterWidget.ID(tag = "hud", category = "smart")
public class HudSmartWidget extends FlutterWidget{
    public enum HudStatus {//hud显示状态
        blink,//闪烁
        on,//常亮
        off//熄灭
    }
    public final String widgetName;
    private HudStatus status = HudStatus.off;

    public HudSmartWidget(String widgetName) {
        this.widgetName = widgetName;
    }
    public HudSmartWidget(String widgetName, HudStatus status) {
        this.widgetName = widgetName;
        this.status = status;
    }

    public void setStatus(HudStatus status) {
        this.status = status;
    }

    public void update() { //通知前端更新状态
        super.update(widgetName, status.name());
    }



}
