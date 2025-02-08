package io.github.tt432.machinemax.common.phys.body;

import cn.solarmoon.spark_core.animation.anim.play.KeyAnimData;
import io.github.tt432.machinemax.common.vehicle.AbstractPart;
import io.github.tt432.machinemax.common.vehicle.port.AbstractPortPort;
import io.github.tt432.machinemax.common.registry.MMBodyTypes;
import io.github.tt432.machinemax.util.formula.IPartPhysParameters;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.world.phys.Vec3;
import org.ode4j.math.DVector3;
import org.ode4j.ode.OdeHelper;

import java.util.HashMap;

public abstract class AbstractPartBody extends AbstractBody implements IPartPhysParameters {

    /*模块化相关参数*/
    @Getter
    final protected HashMap<String, AbstractPortPort> partPorts = HashMap.newHashMap(2);//本零件的零件安装槽(包含内部零件之间的安装槽)
    @Getter
    final protected AbstractPart part;//零件所属部件
    @Getter
    @Setter
    boolean isRootBody = false;//是否是根部件
    /*物理运算相关参数*/
    //流体动力相关系数
    public DVector3 airDragCentre = new DVector3();//空气阻力/升力作用点(相对重心位置)
    public DVector3 waterDragCentre = new DVector3();//水阻力/升力作用点(相对重心位置)

    public AbstractPartBody(String name, AbstractPart part) {
        super(name, part.getLevel());
        body = OdeHelper.createBody(MMBodyTypes.getPART().get(), this, name, false, getPhysLevel().getWorld());
        body.disable();
        body.onTick(this::onTick);
        body.onPhysTick(this::onPhysTick);
        this.part = part;
    }

    @Override
    protected void onTick() {
        if (isRootBody) {
            //TODO:检查为什么没生效
            part.getBone("root").update(new KeyAnimData(
                    new Vec3(0, 0, 0),//pos
                    new Vec3(0, 0, 0),//rot
                    new Vec3(1, 2, 1)//scale
            ));
        }
    }
}
