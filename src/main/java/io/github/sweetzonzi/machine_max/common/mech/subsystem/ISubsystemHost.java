package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.common.mech.energy.EnergyGrid;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

public interface ISubsystemHost {
    SubPart getSubPart();

    /**
     * 宿主直接持有的子系统，迭代序 = 内容包声明序。
     * <p>只交值不交键：按名查本宿主用 {@link #findOwnSubsystem(String)}，“放到部件全域找”属于
     * Part 级职责（{@link SubPart#findSubsystem(String)}），不从这里开第二个入口。</p>
     */
    Collection<AbstractSubsystem> ownSubsystems();

    /**
     * 只在本宿主内按名查子系统，供网络包与存档按 {@code (宿主, 名字)} 定位。
     *
     * @param name 子系统名
     * @return 本宿主上的该子系统；不存在时返回 {@code null}
     */
    @Nullable
    AbstractSubsystem findOwnSubsystem(String name);

    Level getLevel();

    @NotNull
    SubsystemController getSubsystemController();

    @Nullable
    default EnergyGrid getEnergyGrid() { return null; }

    /**
     * 获取子系统额外质量映射表（子系统名称 → 额外质量值，单位 kg）。
     * 由 {@link #updateExtraMass(AbstractSubsystem, float)} 自动管理。
     */
    @NotNull
    Map<String, Float> getExtraMass();

    /**
     * 质量变化回调。当子系统额外质量之和发生变化时触发。
     * 实现者应在此将基态质量 + totalExtraMass 应用到物理刚体。
     *
     * @param totalExtraMass 所有子系统的额外质量之和（kg），不含基态质量
     */
    void onMassChange(float totalExtraMass);

    /**
     * 由子系统调用，更新其额外质量贡献并触发 {@link #onMassChange}。
     * <p>子系统在内部状态变化时调用 {@code owner.updateExtraMass(this, newExtraMass)}，
     * 此 default 方法负责写入映射表、求和、回调。</p>
     *
     * @param subsystem 上报额外质量的子系统
     * @param extraMass 该子系统的额外质量（kg）
     */
    default void updateExtraMass(AbstractSubsystem subsystem, float extraMass) {
        Map<String, Float> extraMassMap = getExtraMass();
        if (extraMass > 0.001f) {
            extraMassMap.put(subsystem.getName(), extraMass);
        } else {
            extraMassMap.remove(subsystem.getName());
        }

        // 求和所有子系统的额外质量
        float totalExtraMass = 0f;
        for (float value : extraMassMap.values()) {
            totalExtraMass += value;
        }
        onMassChange(totalExtraMass);
    }
}
