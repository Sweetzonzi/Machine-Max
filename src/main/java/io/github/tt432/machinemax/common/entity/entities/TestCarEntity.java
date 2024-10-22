package io.github.tt432.machinemax.common.entity.entities;

import com.mojang.logging.LogUtils;
import io.github.tt432.machinemax.common.entity.MMMBasicEntity;
import io.github.tt432.machinemax.common.entity.bodycontrollers.CarController;
import io.github.tt432.machinemax.common.phys.PhysThread;
import io.github.tt432.machinemax.utils.math.DQuaternion;
import io.github.tt432.machinemax.utils.math.DVector3;
import io.github.tt432.machinemax.utils.ode.DBody;
import io.github.tt432.machinemax.utils.ode.DGeom;
import io.github.tt432.machinemax.utils.ode.DMass;
import io.github.tt432.machinemax.utils.ode.OdeHelper;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.slf4j.Logger;

import static io.github.tt432.machinemax.utils.MMMMath.sigmoidSignum;
import static java.lang.Math.*;

public class TestCarEntity extends MMMBasicEntity {
    public static final Logger LOGGER = LogUtils.getLogger();
    public Input input;
    public float MAX_POWER = 80000;//最大功率80kW
    public float ENG_ACC = 0.05F;//引擎加速系数
    public float ENG_DEC = 0.2F;//引擎减速系数
    public float REACT_T = 0.75F;//达到满舵所需时间
    public float MIN_TURNING_R = 5;//最小转弯半径
    public float MAX_TURNING_W = (float) (PI)/20;//最大转弯角速度
    public float mass;//质量
    public float target_power;//目标输出功率
    public float power;//推进功率
    public float turning_input;//转向角输入
    public Vec3 selfDeltaMovement;//自身坐标系下的三轴每tick移动距离
    public boolean brake;
    public float max_ang;//单位时间最大转向角(rad)
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;
    private double lerpXRot;
    private float ZRot;
    //以下为物理引擎相关
    public Quaternionf q;
    public volatile DBody dbody;
    public volatile DMass dmass;
    public volatile DGeom dgeom;

    public TestCarEntity(EntityType<? extends MMMBasicEntity> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
        this.input = new Input();
        mass=1200;
        turning_input=0;
        max_ang=0;
        power=0;
        target_power=0;
        brake=true;
        ZRot=0;
        selfDeltaMovement=new Vec3(0,0,0);
        //以下为物理引擎相关
        controller = new CarController(this);
        dbody = OdeHelper.createBody(PhysThread.world,this);//创建车体
        dmass = OdeHelper.createMass();//创造质量属性
        dmass.setBoxTotal(mass,40D/16,32D/16,72D/16);//设置质量与转动惯量
        dbody.setMass(dmass);//将设置好的质量属性赋予车体
        dgeom = OdeHelper.createBox(40D/16,32D/16,72D/16);//创建一定尺寸的碰撞体
        dgeom.setBody(dbody);//将碰撞体绑定到运动物体
        dgeom.setOffsetPosition(0,8D/16,-12D/16);//对齐碰撞体形状与模型形状
        dbody.setPosition(this.getX(),this.getY(),this.getZ());//将位置同步到物理计算线程
        this.setXRot((float) (random()*10));
        this.setYRot((float) (random()*10));
        this.setZRot((float) (random()*10));
        DQuaternion dq = DQuaternion.fromEulerDegrees(this.getXRot(),this.getYRot(),this.getZRot());
        dbody.setQuaternion(dq);
        if(this.level().isClientSide()){
            PhysThread.renderSpace.geomAddEnQueue(dgeom);//等待将碰撞体加入本地碰撞空间
        }else {
            PhysThread.serverSpace.geomAddEnQueue(dgeom);//等待将碰撞体加入服务器碰撞空间
        }
        q=new Quaternionf();
    }

    @Override
    public void tick() {
        getControlInput();
        engineControl();
        rudderControl();
        if((this.getFirstPassenger() instanceof Player)){
            clampRotation(this.getFirstPassenger());}
        this.setPos(dbody.getPosition().get0(),dbody.getPosition().get1(),dbody.getPosition().get2());
        DQuaternion dq = (DQuaternion) dbody.getQuaternion();
        DVector3 heading = dq.toEulerDegrees();
        setXRot((float) heading.get0());
        setYRot((float) heading.get1());
        setZRot((float) heading.get2());
        q=new Quaternionf(dq.get0(),dq.get1(),dq.get2(),dq.get3());
        //move();
        //MachineMax.LOGGER.info("heading:" + heading);
        //MachineMax.LOGGER.info(" pitch:" + this.getXRot() + " yaw:" + this.getYRot() + " roll:" + this.getZRot());
        //MachineMax.LOGGER.info("pos:" + this.getPosition(0));
        this.level().addParticle(ParticleTypes.SMOKE,getX(),getY(),getZ(),0,0,0);
        super.tick();
    }
    public void move(){
        this.setYRot((this.getYRot() -(float) (max_ang*180/PI)));
        ZRot= (float) (-max_ang*180/PI);
        selfDeltaMovement = this.getDeltaMovement().yRot((float) (getYRot()*PI/180)).add(acceleration().scale(0.05));
        this.setDeltaMovement(selfDeltaMovement.yRot((float) (-getYRot()*PI/180)));
        this.setPos(this.getPosition(1).add(this.getDeltaMovement()));
    }
    public Vec3 acceleration(){//计算受力进而求得加速度
        double fx=-0.8*this.mass*9.8*sigmoidSignum(this.selfDeltaMovement.x)//滑动摩擦阻力
                -0.2*signum(this.selfDeltaMovement.x);//防止微动的补充阻力
        double fy=0;
        double fz=power/(20*abs(selfDeltaMovement.z)+1)//动力
                -(0.1*this.mass*9.8*sigmoidSignum(this.selfDeltaMovement.z*0.5))//滚动摩擦阻力
                -(0.5*0.5*pow(20*this.selfDeltaMovement.z,3))//空气阻力
                -0.2*signum(this.selfDeltaMovement.z);//防止微动的补充阻力
        if (brake){
            fz=fz-(0.8*this.mass*9.8*sigmoidSignum(this.selfDeltaMovement.z*0.5));//刹车时的额外滑动摩擦阻力
        }
        return new Vec3(fx/mass,fy/mass,fz/mass);//受力转换为加速度
    }

    private void engineControl(){
        if (selfDeltaMovement.z>=-0.1&&input.forwardImpulse==1){//未在后退时按w则前进
            target_power=MAX_POWER;
            brake=false;
        }else if (selfDeltaMovement.z>0.1&&input.forwardImpulse==-1) {//前进时按s则刹车
            target_power=0;
            brake=true;
        }else if (selfDeltaMovement.z<=0.05&&input.forwardImpulse==-1) {//未在前进时按s则后退
            target_power=-MAX_POWER;
            brake=false;
        }else if (selfDeltaMovement.z<-0.1&&input.forwardImpulse==1) {//后退时按w则刹车
            target_power=0;
            brake=true;
        }else if(abs(selfDeltaMovement.z)<=0.05&&!input.up&&!input.down){//无输入且低速时自动刹车
            target_power=0;
            brake=true;
        }else{//溜车
            target_power=0;
            brake=false;
        }
        if((target_power>=0&&target_power>=power)||(target_power<=0&&target_power<=power)){//增加引擎输出功率
            power= (1-ENG_ACC)*power+ENG_ACC*target_power;
        }
        else{//降低引擎输出功率
            power= (1-ENG_DEC)*power+ENG_DEC*target_power;
        }
    }
    private void rudderControl(){//转向控制
        if(input.leftImpulse>0||(input.leftImpulse==0&&turning_input<-0.05)){
            turning_input = clamp(turning_input+0.05F,-REACT_T,REACT_T);
        } else if (input.leftImpulse<0||(turning_input>0.05)) {
            turning_input = clamp(turning_input-0.05F,-REACT_T,REACT_T);
        } else {
            turning_input = 0F;
        }
        //求最大单位时间转角
        max_ang= (float) (turning_input/REACT_T*(selfDeltaMovement.z+0.1*selfDeltaMovement.x)/MIN_TURNING_R);
    }
    private void getControlInput(){//读取驾驶员玩家的输入
        if (this.getFirstPassenger() instanceof LocalPlayer pLocalPlayer){
            this.input=pLocalPlayer.input;
        }
        else{
            input.up=false;
            input.down=false;
            input.jumping=false;
            input.shiftKeyDown=false;
            input.left=false;
            input.right=false;
            input.forwardImpulse=0;
            input.leftImpulse=0;
        }
    }

    @Override
    public @NotNull InteractionResult interact(Player pPlayer, InteractionHand pHand) {
        InteractionResult interactionresult = super.interact(pPlayer, pHand);
        if (interactionresult != InteractionResult.PASS) {
            return interactionresult;
        } else if (pPlayer.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        } else {
            if (!this.level().isClientSide) {
                return pPlayer.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
            } else {
                return InteractionResult.SUCCESS;
            }
        }
    }
    @Override
    public boolean isPickable() {//不是是否可拾取而是是否可被选中
        return !this.isRemoved();
    }
    @Override
    public boolean hurt(DamageSource pSource, float pAmount) {
        this.destroy(Items.AIR);
        return true;
    }
    @Override
    public void remove(RemovalReason reason) {
        dbody.destroy();
        dgeom.destroy();
        super.remove(reason);
    }
    /**
     * Applies this boat's yaw to the given entity. Used to update the orientation of its passenger.
     */
    protected void clampRotation(Entity pEntityToUpdate) {
        pEntityToUpdate.setYBodyRot(this.getYRot());
        float f = Mth.wrapDegrees(pEntityToUpdate.getYRot() - this.getYRot());
        float f1 = Mth.clamp(f, -105.0F, 105.0F);
        pEntityToUpdate.yRotO += f1 - f;
        pEntityToUpdate.setYRot(pEntityToUpdate.getYRot() + f1 - f);
        pEntityToUpdate.setYHeadRot(pEntityToUpdate.getYRot());
    }
    /**
     * Applies this entity's orientation (pitch/yaw) to another entity. Used to update passenger orientation.
     */
    @Override
    public void onPassengerTurned(Entity pEntityToUpdate) {
        this.clampRotation(pEntityToUpdate);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag pCompound) {

    }

    @Override
    protected void addAdditionalSaveData(CompoundTag pCompound) {

    }

    @Override
    public Item getDropItem() {
        return Items.AIR;
    }

    @Override
    public boolean canCollideWith(Entity pEntity) {
        return canVehicleCollide(this, pEntity);
    }

    public static boolean canVehicleCollide(Entity pVehicle, Entity pEntity) {
        return (pEntity.canBeCollidedWith() || pEntity.isPushable()) && !pVehicle.isPassengerOfSameVehicle(pEntity);
    }
}