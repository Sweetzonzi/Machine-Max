package io.github.tt432.machinemax.common.part;

import cn.solarmoon.spark_core.animation.anim.play.ModelIndex;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.registry.PartType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class TestCubePart extends AbstractPart {
    //模型资源参数
    public static final String PART_NAME = "test_cube";
    public static final ResourceLocation PART_TEXTURE = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "textures/part/test_cube.png");
    public static final ResourceLocation PART_MODEL = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part/test_cube");
    public static final ResourceLocation PART_ANIMATION = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part/test_cube.animation");

    public TestCubePart(Level level) {
        super(PartType.TEST_CUBE_PART.get(), level);
    }

    public TestCubePart(PartType partType, Level level) {
        super(partType, level);
    }

    @Override
    public String getName() {
        return PART_NAME;
    }

    @NotNull
    @Override
    public ModelIndex getModelIndex() {
        return new ModelIndex(PART_MODEL, PART_ANIMATION, PART_TEXTURE);
    }
}
