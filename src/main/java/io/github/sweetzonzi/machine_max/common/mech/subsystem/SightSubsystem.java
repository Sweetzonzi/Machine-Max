package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.SightSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.control.aim.AimGeometrySnapshot;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

/**
 * 随挂载子部件运动的固定炮镜，完整实际位姿由公共视点的 locator 采样提供。<br>
 * 炮镜提供观察、光学、瞄准保持模式及主 WC 机构采样的发布；水平输入对应偏航，垂直输入对应俯仰。
 * 世界点与相对角通过操作者控制组交付，WC 分发给各自负责求角和限位的驱动器。
 */
public class SightSubsystem extends AbstractViewpointSubsystem {

    public final SightSubsystemAttr sightAttr;
    /** 主线程按操作者当前主武器选择，机构本身由 WC 的 control_outputs 握手发现。 */
    @Nullable
    private volatile WeaponControllerSubsystem aimController;
    /** 物理线程发布的完整采样；主线程只读取这一份几何及实际角。 */
    @Getter
    @Nullable
    private volatile AimGeometrySnapshot aimGeometry;

    public SightSubsystem(ISubsystemHost owner, String name, SightSubsystemAttr attr) {
        super(owner, name, attr);
        sightAttr = attr;
    }

    /** 主线程切换观察所用的主 WC；等待新机构采样期间不得使用另一套机构的角度。 */
    public void setAimController(@Nullable WeaponControllerSubsystem controller) {
        if (aimController != controller) {
            aimController = controller;
            aimGeometry = null;
        }
    }

    @Override
    public void onPostPhysicsTick() {
        super.onPostPhysicsTick();
        WeaponControllerSubsystem controller = aimController;
        AimGeometrySnapshot sample = controller == null || !controller.isActive() || controller.isDestroyed()
                || !isActive() || isDestroyed()
                ? null : controller.sampleAimGeometry(this);
        if (controller == aimController) {
            aimGeometry = sample;
        }
    }
}
