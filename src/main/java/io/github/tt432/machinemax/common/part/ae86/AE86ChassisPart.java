package io.github.tt432.machinemax.common.part.ae86;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.entity.part.MMPartEntity;
import io.github.tt432.machinemax.common.part.AbstractPart;
import net.minecraft.resources.ResourceLocation;

public class AE86ChassisPart extends AbstractPart {
    //模型资源参数
    public static final String PART_NAME = "ae86_chassis";
    public static final ResourceLocation PART_TEXTURE = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "textures/part/ae86/ae86_1.png");
    public static final ResourceLocation PART_MODEL = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part/ae86/ae86_chassis");
    public static final ResourceLocation PART_ANIMATION = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part/ae86/ae86_chassis.animation");
    public static final ResourceLocation PART_ANI_CONTROLLER = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part/ae86/ae86_chassis.animation_controllers");

    public AE86ChassisPart(MMPartEntity attachedEntity) {
        super(attachedEntity);
    }

    @Override
    public String getName() {
        return PART_NAME;
    }

    @Override
    public ResourceLocation getModel() {
        return PART_MODEL;
    }

    @Override
    public ResourceLocation getTexture() {
        return PART_TEXTURE;
    }

    @Override
    public ResourceLocation getAnimation() {
        return PART_ANIMATION;
    }

    @Override
    public ResourceLocation getAniController() {
        return PART_ANI_CONTROLLER;
    }
}
