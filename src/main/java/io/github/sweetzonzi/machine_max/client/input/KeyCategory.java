package io.github.sweetzonzi.machine_max.client.input;

import io.github.sweetzonzi.machine_max.common.mech.control.ControlMode;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractControllableSubsystem;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

import static net.neoforged.neoforge.client.settings.KeyConflictContext.GUI;
@OnlyIn(Dist.CLIENT)
public enum KeyCategory implements IKeyConflictContext, IKeyCategory {

    GENERAL {
        @Override
        public String getCategory() {
            return "key.category.machine_max.general";
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
            return "key.category.machine_max.ground";
        }

        @Override
        public boolean isActive() {
            return isControlModeActive(ControlMode.GROUND);
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
            return "key.category.machine_max.ship";
        }

        public boolean isActive() {
            return isControlModeActive(ControlMode.SHIP);
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
            return "key.category.machine_max.plane";
        }

        public boolean isActive() {
            return isControlModeActive(ControlMode.PLANE);
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
            return "key.category.machine_max.mech";
        }

        public boolean isActive() {
            return isControlModeActive(ControlMode.MECH);
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == this ||
                    other == GENERAL ||
                    other == KeyConflictContext.IN_GAME;
        }
    },

    ASSEMBLY {
        @Override
        public String getCategory() {
            return "key.category.machine_max.assembly";
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
    };

    /**
     * 判断玩家当前控制的子系统的控制组模式是否与指定模式匹配。
     */
    private static boolean isControlModeActive(ControlMode targetMode) {
        if (GUI.isActive()) return false;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return false;
        var subsystem = ((IEntityMixin) client.player).machine_Max$getControllingSubsystem();
        if (!(subsystem instanceof AbstractControllableSubsystem controllable)) return false;
        return controllable.getControlGroupSet().getEffectiveControlMode() == targetMode;
    }
}
