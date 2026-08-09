package io.github.sweetzonzi.machine_max.common.mech.physics_test.instance;

import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.datafixers.util.Pair;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.physics_test.BaseJoinPositionPhysicsTest;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.VehicleData;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import io.github.sweetzonzi.machine_max.util.MMMath;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * 一个示范测试用例，通过模板 ResourceLocation 直接召唤载具到入点
 * */
public class BlueprintVehicleSummonTest extends BaseJoinPositionPhysicsTest<VehicleCore> {

    /**复位冷却时长（物理刻）：触发一次复位后，在该时间内不再重复触发，避免物理引擎连续碰撞回调导致反复复位*/
    private static final int RESET_COOLDOWN_TICKS = 155;

    /**复位冷却倒计时，仅物理线程访问*/
    private int resetCooldown = 0;

    public BlueprintVehicleSummonTest(ServerPlayer player) {
        super(player);
    }

    @Override
    public void run(VehicleCore physicsTestingObject) {
        if (physicsTestingObject == null) return;
        ObjectManager.addVehicle(physicsTestingObject);
        player.sendSystemMessage(Component.literal("§a已召唤测试载具，共 " + physicsTestingObject.getPartMap().size() + " 个部件"));
    }

    public Consumer<Boolean> getPlayingEvent() {
        Consumer<Boolean> playingEvent = super.getPlayingEvent();
        if (!isDisabled() && playingEvent == null) {
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
        // 直接通过模板 ResourceLocation 获取载具数据，不依赖任何物品
        ResourceLocation TEMPLATE_ID = ResourceLocation.fromNamespaceAndPath("machine_max", "wine_fox_gt");
        VehicleData vehicleData = MMDynamicRes.TEMPLATES.get(TEMPLATE_ID);
        if (vehicleData == null) {
            player.sendSystemMessage(Component.literal("§c测试失败：模板 §e" + TEMPLATE_ID + "§c 未注册，可用模板：" + MMDynamicRes.TEMPLATES.keySet()));
            return null;
        }
        // readAdditionalData=true：读取模板保存的组装进度/纹理/耐久等附加数据，否则部件会以未组装（线框无材质）状态出现
        VehicleCore vehicle = new VehicleCore(level, vehicleData.withNewUUID(UUID.randomUUID()), true);
        setVehicleState(vehicle);
        return vehicle;
    }

    @Override
    public Pair<Vec3, Transform> setJoinPosition() {
        // 若没有可用的连接点，则尝试直接放置零件
        Quaternionf rotation = new Quaternionf()
                .rotationY((float) Math.toRadians(180 - player.getYRot()));

        Transform transform = new Transform(
                PhysicsHelperKt.toBVector3f(level.clip(new ClipContext(
                        player.getEyePosition(),
                        player.getEyePosition().add(player.getViewVector(1).scale(player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE))),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation()),
                SparkMathKt.toBQuaternion(rotation)
        );
        return Pair.of(player.getLookAngle(), transform);
    }

    /**
     * 设置整车初始状态：入点变换作用于整车（所有部件保持相对姿态），再给予整车初速度与角速度
     */
    private void setVehicleState(VehicleCore vehicle) {
        vehicle.setTransform(getJoinPosition().getSecond());

        double strength = 70.0; //初速度常数
        var lookAt = getJoinPosition().getFirst();
        var velocity = new Vec3(lookAt.x * strength, lookAt.y * strength, lookAt.z * strength);
        Vector3f v = new Vector3f((float) velocity.x, (float) velocity.y, (float) velocity.z);

        Vector3f v2 = new Vector3f(0f, 0f, 0f);

        for (Part part : vehicle.getPartMap().values()) {
            PhysicsRigidBody partBody = part.rootSubPart.body;
            part.rootSubPart.setLinearVelocity(v);
            partBody.setLinearVelocity(v);
            part.rootSubPart.setAngularVelocity(v2);
            partBody.setAngularVelocity(v2);
        }
    }


    @Override
    public void vehicle_core$prePhysicsTick(VehicleCore core) {
        if (isDisabled() || getPhysicsTestingObject() != core) return;
        //冷却倒计时：复位后经过 RESET_COOLDOWN_TICKS 物理刻才允许下次落地触发
        if (resetCooldown > 0) resetCooldown--;


        // todo在下面实现你的受力

//        int index = 0;
//        for (Part part : core.getPartMap().values()) {
//            if (index == 16) {
//                part.rootSubPart.body.applyCentralForce(
//                        MMMath.localVectorToWorldVector(new Vector3f(0,0, -12122).divideLocal(part.rootSubPart.body.getMass()), part.rootSubPart.body));
//            }
//
//            index++;
//        }
//            SoundEvent sound = SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "item.part.placed"), 64f);
//            SpreadingSoundHelper.playSpreadingSound(core.level, sound, SoundSource.NEUTRAL, core.getPosition(), core.getVelocity(),
//                    (float) (1f + 0.1f * (Math.random() - 0.5f)),
//                    5f);
//        if (core.tickCount % 3 == 0) {
//
//        }

    }

    @Override
    public void subpart$onTerrainCollision(SubPart subPart) {
//        if (subPart.level.isClientSide) return;
        //冷却中不重复触发，保证幂等；冷却结束后的下一次落地仍会正常触发

        if (resetCooldown > 0) return;
        VehicleCore vehicle = getPhysicsTestingObject();
        if (vehicle != null && vehicle.getPartMap().containsKey(subPart.getPart().uuid)) {
            //整车复位：清零所有部件的速度与受力，再整体回到入点姿态
            for (Part part : vehicle.getPartMap().values()) {
                part.rootSubPart.setLinearVelocity(Vector3f.ZERO);
                part.rootSubPart.body.setLinearVelocity(Vector3f.ZERO);
                part.rootSubPart.setAngularVelocity(Vector3f.ZERO);
                part.rootSubPart.body.setAngularVelocity(Vector3f.ZERO);
                part.rootSubPart.body.clearForces();
            }
            setVehicleState(vehicle);
            resetCooldown = RESET_COOLDOWN_TICKS;
        }
    }

    @Override
    public void subpart$onDestroyBlock(SubPart subPart) {

    }

    @Override
    public boolean isDisabled() {
        return getP() == null || getPhysicsTestingObject().isDestroyed() || getPhysicsTestingObject().isRemoved();
    }

}
