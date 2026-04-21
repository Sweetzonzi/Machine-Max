package io.github.sweetzonzi.machine_max.common.vehicle.molang;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.anim.AnimInstance;
import cn.solarmoon.spark_core.js.molang.IMolangContext;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import kotlin.jvm.JvmField;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VehicleBinding implements IMolangContext {

    private AnimInstance anim;

    @Override
    public void update(@NotNull String molang, @NotNull AnimInstance anim, @NotNull Context context, @NotNull Value bindings) {
        this.anim = anim;
        durability = getDurability(getAnimatable());
        max_durability = getMaxDurability(getAnimatable());
    }

    @HostAccess.Export
    @JvmField
    private Double durability;

    @HostAccess.Export
    @JvmField
    private Double max_durability;

    @HostAccess.Export
    @Nullable
    public Object get(String key) {
        var holder = getAnimatable().getAnimatable();
        if (holder instanceof LivingEntity) {
            if (((IEntityMixin) holder).machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat) {
                return seat.getOwner().getSubPart().getPart().getVehicle().getSubSystemController().signalStorage.get(key);
            } else return null;
        } else if (holder instanceof SubPart subPart) {
            return subPart.part.getVehicle().getSubSystemController().signalStorage.get(key);
        }
        else return null;
    }

    @Nullable
    private static Double getDurability(IAnimatable<?> ctx) {
        if (ctx.getAnimatable() instanceof LivingEntity) {
            if (((IEntityMixin) ctx.getAnimatable()).machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat) {
                return (double) seat.getOwner().getSubPart().getPart().getVehicle().getHp();
            } else return 0.0;
        } else if (ctx.getAnimatable() instanceof SubPart subPart)
            return (double) subPart.part.getVehicle().getHp();
        else return 0.0;
    }

    @Nullable
    private static Double getMaxDurability(IAnimatable<?> ctx) {
        if (ctx.getAnimatable() instanceof LivingEntity) {
            if (((IEntityMixin) ctx.getAnimatable()).machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat) {
                return (double) seat.getOwner().getSubPart().getPart().getVehicle().getMaxHp();
            } else return 0.0;
        } else if (ctx.getAnimatable() instanceof SubPart subPart)
            return (double) subPart.part.getVehicle().getMaxHp();
        else return 0.0;
    }

    private IAnimatable<?> getAnimatable() {
        return anim.getHolder();
    }

    private Level getLevel() {
        return getAnimatable().getAnimLevel();
    }
}
