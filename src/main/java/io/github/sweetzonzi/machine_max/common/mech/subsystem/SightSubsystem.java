package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.SightSubsystemAttr;

/**
 * 瞄准镜子系统：继承 CameraSubsystem，提供炮镜视角与瞄准点输出功能。<p>
 * 目前完全复用 CameraSubsystem 的字段与方法，作为独立子系统类型注册以支持未来扩展：
 * <ul>
 *   <li>标尺调节（reticle adjustment）</li>
 *   <li>归零距离（zero range）</li>
 *   <li>分划板样式切换</li>
 * </ul>
 */
public class SightSubsystem extends CameraSubsystem {
    public final SightSubsystemAttr sightAttr;

    public SightSubsystem(ISubsystemHost owner, String name, SightSubsystemAttr attr) {
        super(owner, name, attr);
        this.sightAttr = attr;
    }
}
