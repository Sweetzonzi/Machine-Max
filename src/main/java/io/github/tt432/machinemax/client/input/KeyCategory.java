package io.github.tt432.machinemax.client.input;

import io.github.tt432.machinemax.common.entity.MMPartEntity;
import io.github.tt432.machinemax.common.vehicle.Part;
import io.github.tt432.machinemax.common.vehicle.VehicleCore;
import io.github.tt432.machinemax.common.vehicle.subsystem.SeatSubsystem;
import io.github.tt432.machinemax.mixin_interface.IEntityMixin;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

import static net.neoforged.neoforge.client.settings.KeyConflictContext.GUI;

public enum KeyCategory implements IKeyConflictContext, IKeyCategory {

    GENERAL {
        @Override
        public String getCategory() {
            return "resourceType.category.machine_max.general";
        }

        @Override
        public boolean isActive() {
            return !GUI.isActive();
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            if (other == this) return true;
            else return (other instanceof KeyCategory);
        }
    },
    GROUND {
        @Override
        public String getCategory() {
            return "resourceType.category.machine_max.ground";
        }

        @Override
        public boolean isActive() {
            if (GUI.isActive()) return false;
            Minecraft client = Minecraft.getInstance();
            if (client.player != null && ((IEntityMixin) client.player).machine_Max$getRidingSubsystem() instanceof SeatSubsystem subSystem) {
                return subSystem.getPart().vehicle.getMode() == VehicleCore.ControlMode.GROUND;
            } else return false;
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == this ||
                    other == GENERAL ||
                    other == KeyConflictContext.IN_GAME; //二者为同一类时，或另一类为通用或原版时，冲突
        }
    },
    SHIP {
        @Override
        public String getCategory() {
            return "resourceType.category.machine_max.ship";
        }

        public boolean isActive() {
            if (GUI.isActive()) return false;
            Minecraft client = Minecraft.getInstance();
            if (client.player != null && ((IEntityMixin) client.player).machine_Max$getRidingSubsystem() instanceof SeatSubsystem subSystem) {
                return subSystem.getPart().vehicle.getMode() == VehicleCore.ControlMode.SHIP;
            } else return false;
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == this ||
                    other == GENERAL ||
                    other == KeyConflictContext.IN_GAME; //二者为同一类时，或另一类为通用或原版时，冲突
        }
    },
    PLANE {
        @Override
        public String getCategory() {
            return "resourceType.category.machine_max.plane";
        }

        public boolean isActive() {
            if (GUI.isActive()) return false;
            Minecraft client = Minecraft.getInstance();
            if (client.player != null && ((IEntityMixin) client.player).machine_Max$getRidingSubsystem() instanceof SeatSubsystem subSystem) {
                return subSystem.getPart().vehicle.getMode() == VehicleCore.ControlMode.PLANE;
            } else return false;
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == this ||
                    other == GENERAL ||
                    other == KeyConflictContext.IN_GAME; //二者为同一类时，或另一类为通用或原版时，冲突
        }
    },
    MECH {
        @Override
        public String getCategory() {
            return "resourceType.category.machine_max.mech";
        }

        public boolean isActive() {
            if (GUI.isActive()) return false;
            Minecraft client = Minecraft.getInstance();
            if (client.player != null && ((IEntityMixin) client.player).machine_Max$getRidingSubsystem() instanceof SeatSubsystem subSystem) {
                return subSystem.getPart().vehicle.getMode() == VehicleCore.ControlMode.MECH;
            } else return false;
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == this ||
                    other == GENERAL ||
                    other == KeyConflictContext.IN_GAME; //二者为同一类时，或另一类为通用或原版时，冲突
        }
    },
    ASSEMBLY {
        @Override
        public String getCategory() {
            return "resourceType.category.machine_max.assembly";
        }

        public boolean isActive() {
            if (GUI.isActive()) return false;
            return Minecraft.getInstance().player == null || Minecraft.getInstance().player.getVehicle() == null;
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            if (other == this || other == GENERAL) return true;
            else return (!(other instanceof KeyCategory));
        }
    }
}
