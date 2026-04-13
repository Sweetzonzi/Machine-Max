package io.github.sweetzonzi.machine_max.client.compat.jade;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.vehicle.VehicleCore;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.Locale;

public enum MMPartEntityStatusProvider implements IEntityComponentProvider {
    INSTANCE;

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
        if (!(accessor.getEntity() instanceof MMPartEntity partEntity)) return;
        if (partEntity.subPart == null) return;
        tooltip.add(Component.translatable(
                "tooltip.machine_max.jade.subpart_durability",
                formatDurabilityStatus(partEntity.subPart.getDurability(), partEntity.subPart.getMaxDurability())
        ));

        VehicleCore vehicle = partEntity.subPart.part.vehicle;
        if (vehicle != null) {
            tooltip.add(Component.translatable(
                    "tooltip.machine_max.jade.vehicle_durability",
                    formatDurabilityStatus(vehicle.getHp(), vehicle.getMaxHp())
            ));
        }
    }

    private static String formatDurabilityStatus(float current, float max) {
        float safeCurrent = Math.max(0f, current);
        if (max <= 0f) {
            return String.format(Locale.ROOT, "%.0f/N/A (N/A)", safeCurrent);
        }
        float safeMax = Math.max(0f, max);
        float percent = safeCurrent * 100f / safeMax;
        return String.format(Locale.ROOT, "%.0f/%.0f (%.1f%%)", safeCurrent, safeMax, percent);
    }
}
