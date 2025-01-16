package io.github.tt432.machinemax.common.part.ae86;

import cn.solarmoon.spark_core.animation.anim.play.ModelIndex;
import cn.solarmoon.spark_core.animation.model.origin.OModel;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.part.AbstractPart;
import io.github.tt432.machinemax.common.part.PartType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class AE86ChassisPart extends AbstractPart {
    //模型资源参数
    public static final String PART_NAME = "ae86_chassis";
    public static final ResourceLocation PART_TEXTURE = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "textures/part/ae86_1.png");
    public static final ResourceLocation PART_MODEL = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part/ae86_chassis");
    public static final ResourceLocation PART_ANIMATION = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part/ae86_chassis.animation");
    
    public AE86ChassisPart(Level level) {
        super(PartType.AE86_CHASSIS_PART.get(),level);
    }

    public AE86ChassisPart(PartType partType, Level level) {
        super(partType, level);
    }

    @Override
    public String getName() {
        return PART_NAME;
    }

    @NotNull
    @Override
    public ModelIndex getModelIndex() {
        return new ModelIndex(PART_MODEL,PART_ANIMATION,PART_TEXTURE);
    }
}
