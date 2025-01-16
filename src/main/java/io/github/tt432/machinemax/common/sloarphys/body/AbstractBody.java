package io.github.tt432.machinemax.common.sloarphys.body;

import cn.solarmoon.spark_core.phys.attached_body.AttachedBody;
import cn.solarmoon.spark_core.phys.thread.PhysLevel;
import cn.solarmoon.spark_core.phys.thread.ThreadHelperKt;
import io.github.tt432.machinemax.MachineMax;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.ode4j.ode.*;

import java.util.ArrayList;

//TODO:设为抽象类，并创建一些变体，如：
// 1. 直接指定形状、碰撞箱等参数的零件
// 2. 特殊碰撞功能的零件，如刀刃、引信
public abstract class AbstractBody implements AttachedBody {
    @Getter
    ArrayList<DGeom> geoms = new ArrayList<>();
    @Getter
    volatile DMass mass;
    @Getter
    DBody body;
    @Getter
    final String name;
    @Getter
    final Level level;

    public AbstractBody(String name, Level level) {
        this.name = name;
        this.level = level;
        mass = OdeHelper.createMass();
        body = OdeHelper.createBody(name, this, false, getPhysLevel().getPhysWorld().getWorld());
        body.disable();
        body.onTick(this::onTick);
        body.onPhysTick(this::onPhysTick);

    }

    protected abstract void onTick();


    protected abstract void onCollide(DGeom dGeom, DContactBuffer dContacts);

    /**
     * 运动体在每次物理线程迭代时执行的方法
     */
    protected abstract void onPhysTick();

    @Override
    public void enable() {
        getBody().enable();
    }

    @Override
    public void disable() {
        getBody().disable();
    }

    @NotNull
    @Override
    public PhysLevel getPhysLevel() {
        return ThreadHelperKt.getPhysLevelById(level, ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "main"));
    }
}
