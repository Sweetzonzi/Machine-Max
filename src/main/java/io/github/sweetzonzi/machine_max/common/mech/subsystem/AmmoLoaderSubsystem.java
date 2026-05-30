package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.AmmoLoaderSubsystemAttr;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 装弹机子系统。<br>
 * 负责管理弹药的供给、装填时序和弹序循环，与 {@link LauncherSubsystem} 通过信号通信。<br>
 * <p>
 * 两种装填模式由静态属性 {@code round_by_round} 布尔控制：
 * <ul>
 *   <li>{@code false} — 整体换弹匣（机炮），耗时 reloadTimeTicks</li>
 *   <li>{@code true} — 逐发压入（火炮、霰弹枪），每发耗时 reloadTimeTicks</li>
 * </ul>
 * <p>
 * TODO: 完整实现装填状态机、弹药消耗、弹序循环、与 ItemStorageSubsystem 的联动
 */
public class AmmoLoaderSubsystem extends BasicSubsystem {

    public final AmmoLoaderSubsystemAttr attr;

    public AmmoLoaderSubsystem(ISubsystemHost owner, String name, AmmoLoaderSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
    }

    // TODO: 装填状态机进度 (tick)
    // TODO: 当前弹药计数
    // TODO: 弹序索引（支持 AP→HE→AP-I-T 交替弹序）

    @Override
    public Map<String, List<String>> getTargetNames() {
        Map<String, List<String>> result = new HashMap<>(4);
        // TODO: 注册信号输出目标
        return result;
    }

    @Override
    public List<String> getAcceptedChannels() {
        return List.of(
                attr.staticAttribute.getAmmoRequestChannel(),
                attr.staticAttribute.getAmmoConsumedChannel(),
                attr.staticAttribute.getReloadChannel()
        );
    }
}
