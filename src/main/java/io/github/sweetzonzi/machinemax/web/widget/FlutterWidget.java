package io.github.sweetzonzi.machinemax.web.widget;

import io.github.sweetzonzi.machinemax.web.MMWebApp;

import java.lang.annotation.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@FlutterWidget.ID(tag = "origin", category = "widget")
public class FlutterWidget{

    public final String tag;
    public final String category;

    /**
     * 用于标记FlutterWidget子类的注解，配置其对应的前端交互标识（tag）和分类（category）
     */
    @Target(ElementType.TYPE) // 作用于类/接口/枚举
    @Retention(RetentionPolicy.RUNTIME) // 运行时保留，可通过反射获取
    public @interface ID {
        /**
         * 前端交互的唯一标识（通常对应前端的组件名或消息类型）
         * @return tag字符串
         */
        String tag();

        /**
         * 分类标识（用于前端对组件的分组管理）
         * @return category字符串
         */
        String category();
    }
    public FlutterWidget () {
        FlutterWidget.ID annotation = this.getClass().getAnnotation(FlutterWidget.ID.class);
        if (annotation == null) {
            throw new IllegalStateException("HudSmartWidget未标注@FlutterWidget.ID注解");
        }
        tag = annotation.tag();
        category = annotation.category();
    }

    public void update(Object... args) {
        List<Object> cache = new ArrayList<>();
        cache.add(category);
        Collections.addAll(cache, args);
        MMWebApp.sendPacket(tag, cache.toArray());
    }

}
