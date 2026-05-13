package io.github.sweetzonzi.machine_max.client.compat.jade;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.Locale;

public enum MMPartEntityStatusProvider implements IEntityComponentProvider {
    INSTANCE;

    // Jade 侧用于识别该 Provider 的唯一键。
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(
            MachineMax.MOD_ID,
            "mm_part_entity_status"
    );

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        // 仅处理本模组的部件实体，避免对其他实体做无意义计算。
        if (!(accessor.getEntity() instanceof MMPartEntity partEntity)) return;
        if (partEntity.subPart == null) return;

        // 第一行展示子部件耐久。
        tooltip.add(Component.translatable(
                "tooltip.machine_max.jade.subpart_durability",
                formatDurabilityStatus(partEntity.subPart.getDurability(), partEntity.subPart.getMaxDurability())
        ));

        // 第二行展示所属整车耐久（若该子部件已挂到载具上）。
        VehicleCore vehicle = partEntity.subPart.part.vehicle;
        if (vehicle != null) {
            tooltip.add(Component.translatable(
                    "tooltip.machine_max.jade.vehicle_durability",
                    formatDurabilityStatus(vehicle.getHp(), vehicle.getMaxHp())
            ));
        }
    }

    private static String formatDurabilityStatus(float current, float max) {
        // 统一格式：当前/上限(百分比)，并处理无上限或异常值。
        float safeCurrent = Math.max(0f, current);
        if (max <= 0f) {
            return String.format(Locale.ROOT, "%.0f/N/A (N/A)", safeCurrent);
        }
        float safeMax = Math.max(0f, max);
        float percent = safeCurrent * 100f / safeMax;
        return String.format(Locale.ROOT, "%.0f/%.0f (%.1f%%)", safeCurrent, safeMax, percent);
    }
}
