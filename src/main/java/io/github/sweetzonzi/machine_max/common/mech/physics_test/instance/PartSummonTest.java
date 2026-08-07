package io.github.sweetzonzi.machine_max.common.mech.physics_test.instance;

import cn.solarmoon.spark_core.api.SpreadingSoundHelper;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.datafixers.util.Pair;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.physics_test.BaseJoinPositionPhysicsTest;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.PartType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.util.MMMath;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.function.Consumer;

/**
 * 一个示范测试用例，通过召唤者与入点召唤指定刚体
 * */
public class PartSummonTest extends BaseJoinPositionPhysicsTest<VehicleCore> {

    /**复位冷却时长（物理刻）：触发一次复位后，在该时间内不再重复触发，避免物理引擎连续碰撞回调导致反复复位*/
    private static final int RESET_COOLDOWN_TICKS = 155;

    /**复位冷却倒计时，仅物理线程访问*/
    private int resetCooldown = 0;

    public PartSummonTest(ServerPlayer player) {
        super(player);
    }

    @Override
    public void run(VehicleCore physicsTestingObject) {
        ObjectManager.addVehicle(physicsTestingObject);
    }

    public Consumer<Boolean> getPlayingEvent() {
        Consumer<Boolean> playingEvent = super.getPlayingEvent();
        if (playingEvent == null) {
            playingEvent = playing -> {
                {
                    VehicleCore vehicleCore = getPhysicsTestingObject();
                    if (playing) {
                        vehicleCore.freezeAllPhysics(this);
                    } else {
                        vehicleCore.unfreezeAllPhysics(this);
                    }
                }
            };
        }
        return playingEvent;
    }

    @Override
    protected VehicleCore setPhysicsTestingObject() {
        ResourceLocation partId = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "wine_fox_gt_spoiler");
        PartType partType = PartType.get(level, partId);
        Part part = new Part(partType, level);
        setState(part);
        return new VehicleCore(level, part);
    }

    @Override
    public Pair<Vec3, Transform> setJoinPosition() {
        // 若没有可用的连接点，则尝试直接放置零件
        Quaternionf rotation = new Quaternionf()
                .rotationY((float) Math.toRadians(0 - player.getYRot()))
                .rotateX((float) (Math.PI));

        Transform transform = new Transform(
                PhysicsHelperKt.toBVector3f(level.clip(new ClipContext(
                        player.getEyePosition(),
                        player.getEyePosition().add(player.getViewVector(1).scale(player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE))),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation()),
                SparkMathKt.toBQuaternion(rotation)
        );
        return Pair.of(player.getLookAngle(), transform);
    }

    private void setState(Part part) {
        part.setTransform(getJoinPosition().getSecond());

        double strength = 150.0; //初速度常数
        var lookAt = getJoinPosition().getFirst();
        var velocity = new Vec3(lookAt.x * strength, lookAt.y * strength, lookAt.z * strength);
        Vector3f v = new Vector3f((float) velocity.x, (float) velocity.y, (float) velocity.z);
        part.rootSubPart.setLinearVelocity(v);
        part.rootSubPart.body.setLinearVelocity(v);

        var hg = 0.2; //角速度常数

        Vector3f v2 = new Vector3f(0f, (float) (lookAt.x * hg), (float) (lookAt.z * hg));
        part.rootSubPart.setAngularVelocity(v2);
        part.rootSubPart.body.setAngularVelocity(v2);

    }


    @Override
    public void vehicle_core$prePhysicsTick(VehicleCore core) {
        //冷却倒计时：复位后经过 RESET_COOLDOWN_TICKS 物理刻才允许下次落地触发
        if (resetCooldown > 0) resetCooldown--;


        if (core.tickCount % 3 == 0) {
            for (Part part : core.getPartMap().values()) {
                part.rootSubPart.body.applyCentralForce(
                        MMMath.localVectorToWorldVector(new Vector3f(0, 0, -35.3f), part.rootSubPart.body));
                //每物理刻对部件正前方施加单位推力（正前方即部件局部 -Z 轴，随部件旋转）
            }
//            SoundEvent sound = SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "item.part.placed"), 64f);
//            SpreadingSoundHelper.playSpreadingSound(core.level, sound, SoundSource.NEUTRAL, core.getPosition(), core.getVelocity(),
//                    (float) (1f + 0.1f * (Math.random() - 0.5f)),
//                    5f);
        }

    }

    @Override
    public void subpart$onTerrainCollision(SubPart subPart) {
//        if (subPart.level.isClientSide) return;
        //冷却中不重复触发，保证幂等；冷却结束后的下一次落地仍会正常触发
        if (resetCooldown > 0) return;
        Transform transform = getJoinPosition().getSecond();
        VehicleCore vehicle = getPhysicsTestingObject();
        if (vehicle.getPartMap().containsKey(subPart.getPart().uuid)) {
            subPart.body.setLinearVelocity(Vector3f.ZERO);
            subPart.body.setAngularVelocity(Vector3f.ZERO);
            subPart.body.clearForces();
            subPart.body.setPhysicsTransform(transform);
            setState(subPart.getPart());
            resetCooldown = RESET_COOLDOWN_TICKS;
        }
    }

    @Override
    public void subpart$onDestroyBlock(SubPart subPart) {

    }

    @Override
    public boolean isDisabled() {
        return getPhysicsTestingObject().isDestroyed() || getPhysicsTestingObject().isRemoved();
    }

}
