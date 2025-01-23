package io.github.tt432.machinemax.common.sloarphys.body;

import cn.solarmoon.spark_core.animation.anim.play.KeyAnimData;
import io.github.tt432.machinemax.common.part.AbstractPart;
import io.github.tt432.machinemax.common.part.slot.AbstractBodySlot;
import io.github.tt432.machinemax.common.registry.MMBodyTypes;
import io.github.tt432.machinemax.util.data.PosRot;
import io.github.tt432.machinemax.util.formula.IPartPhysParameters;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.world.phys.Vec3;
import org.ode4j.math.DQuaternion;
import org.ode4j.math.DVector3;
import org.ode4j.ode.OdeHelper;

import java.util.HashMap;

public abstract class AbstractPartBody extends AbstractBody implements IPartPhysParameters {

    /*模块化相关参数*/
    @Getter
    protected final HashMap<String, PosRot> parentBodyAttachPoints = HashMap.newHashMap(2);//可用连接点及其相对零件质心的坐标与旋转
    @Getter
    volatile protected HashMap<String, AbstractBodySlot> parentBodyAttachSlots = HashMap.newHashMap(1);//本零件安装于的槽位
    @Getter
    final protected HashMap<String, AbstractBodySlot> bodySlots = HashMap.newHashMap(2);//本零件的零件安装槽
    @Getter
    final protected AbstractPart part;//零件所属部件
    @Getter
    @Setter
    boolean isRootBody = false;//是否是根部件
    @Getter
    @Setter
    volatile protected AbstractPartBody motherBody;//本零件附着于的零件(唯一，遵循骨骼结构)
    @Getter
    @Setter
    volatile protected String MotherBodySlot;//本零件安装于的槽位名称(唯一，遵循骨骼结构)
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
        //创建零件连接点(可以是多个)
        parentBodyAttachPoints.put("MassCenter", new PosRot(new DVector3(), new DQuaternion().setIdentity()));//质心作为默认的公有的连接点
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
