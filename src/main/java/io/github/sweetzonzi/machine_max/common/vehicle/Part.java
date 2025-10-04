package io.github.sweetzonzi.machine_max.common.vehicle;

import cn.solarmoon.spark_core.animation.model.origin.OBone;
import cn.solarmoon.spark_core.animation.model.origin.OLocator;
import cn.solarmoon.spark_core.animation.model.origin.OModel;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.util.SparkMathKt;
import cn.solarmoon.spark_core.sound.SpreadingSoundHelper;
import cn.solarmoon.spark_core.util.PPhase;
import com.jme3.bounding.BoundingBox;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.datafixers.util.Pair;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.ConnectorAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.HitBoxAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.SubPartAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.AbstractSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AttachPointConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.SpecialConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.data.PartData;
import io.github.sweetzonzi.machine_max.common.vehicle.signal.ISignalReceiver;
import io.github.sweetzonzi.machine_max.common.vehicle.signal.SignalChannel;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import io.github.sweetzonzi.machine_max.network.payload.PartSyncPayload;
import io.github.sweetzonzi.machine_max.util.data.PosRotVelVel;
import jme3utilities.math.MyMath;
import lombok.Getter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

@Getter
public class Part implements ISignalReceiver {
    //渲染属性 Renderer attributes
    public volatile boolean hurtMarked = false;
    public volatile boolean oHurtMarked = false;
    //常规属性 General attributes
    public volatile VehicleCore vehicle;//所属的VehicleCore
    public String name;
    public final PartType type;
    public final Level level;
    public final String variant;
    public final UUID uuid;
    public volatile boolean destroyed = false;
    public volatile float durability;
    public volatile float integrity;
    private final ConcurrentMap<Vector3f, Float> accumulatedImpact = new ConcurrentHashMap<>(8);
    public final ConcurrentMap<String, SignalChannel> signalChannels = new ConcurrentHashMap<>();//部件内共享的信号

    public record PartDamageData(
            DamageSource source,
            IPhysicsProjectile projectileSource,
            SubPart subPart,
            Vector3f normal,
            Vector3f worldContactSpeed,
            Vector3f worldContactPoint,
            HitBox hitBox
    ) {
    }

    private final ConcurrentLinkedQueue<Pair<Float, PartDamageData>> accumulatedDamage = new ConcurrentLinkedQueue<>();
    public final Map<String, SubPart> subParts = HashMap.newHashMap(1);
    public final SubPart rootSubPart;
    public float totalMass;
    //模块化属性 Modular attributes
    public final Map<Pair<String, String>, AbstractConnector> externalConnectors = HashMap.newHashMap(1);
    public final Map<Pair<String, String>, AbstractConnector> allConnectors = HashMap.newHashMap(1);

    /**
     * <p>创建新部件，使用指定变体</p>
     * <p>仅应在服务端新建部件时使用</p>
     *
     * @param partType 部件类型
     * @param variant  部件变体类型
     * @param level    部件被加入的世界
     */
    public Part(PartType partType, @Nullable String variant, Level level) {
        if (variant == null) variant = "default";
        this.name = partType.getName();
        this.type = partType;
        this.variant = variant;
        this.level = level;
        this.uuid = UUID.randomUUID();
        this.durability = partType.basicDurability;
        this.integrity = partType.basicIntegrity;
        this.rootSubPart = createSubParts(type.getVariants().get(variant).subParts());//创建子部件并指定根子部件
        updateMass();
    }

    /**
     * <p>创建新部件，使用默认变体</p>
     * <p>仅应在服务端新建部件时使用</p>
     *
     * @param partType 部件类型
     * @param level    部件被加入的世界
     */
    public Part(PartType partType, Level level) {
        this(partType, "default", level);
    }


    /**
     * 从保存或网络传输的数据中重建部件
     *
     * @param data          保存或网络传输的数据
     * @param level         部件所在的世界
     * @param readSavedData 是否从保存的数据中读取部件数据，否则使用默认数据
     */
    public Part(PartData data, Level level, boolean readSavedData) {
        this.name = data.name;
        this.type = getPT(level, data.registryKey);
        this.level = level;
        this.variant = data.variant;
        this.uuid = UUID.fromString(data.uuid);
        this.durability = readSavedData ? Math.min(data.durability, type.basicDurability) : type.basicDurability;
        this.integrity = readSavedData ? Math.min(data.integrity, type.basicIntegrity) : type.basicIntegrity;
        this.rootSubPart = createSubParts(type.getVariants().get(variant).subParts());//重建子部件并指定根子部件
        //遍历保存的子部件位置、旋转、速度数据
        for (Map.Entry<String, PosRotVelVel> entry : data.subPartTransforms.entrySet()) {
            SubPart subPart = subParts.get(entry.getKey());//获取已重建的子部件
            if (subPart != null) {//设定子部件body的位置、旋转、速度
                PosRotVelVel posRotVelVel = entry.getValue();
                subPart.body.setPhysicsLocation(posRotVelVel.position());
                subPart.body.setPhysicsRotation(SparkMathKt.toBQuaternion(posRotVelVel.rotation()));
                subPart.body.setLinearVelocity(posRotVelVel.linearVel());
                subPart.body.setAngularVelocity(posRotVelVel.angularVel());
                PhysicsBodyExtensionKt.stateOf(subPart.body).setTransform(posRotVelVel.toTransform());
                PhysicsBodyExtensionKt.stateOf(subPart.body).setLastTransform(posRotVelVel.toTransform());
            } else
                throw new NullPointerException("部件" + name + "的子部件" + entry.getKey() + "不存在，请检查数据。");
        }
        if (readSavedData) {
            //加载子系统储存的数据
            for (Map.Entry<String, Map<String, CompoundTag>> entry : data.subsystemData.entrySet()) {
                SubPart subPart = subParts.get(entry.getKey());
                for (Map.Entry<String, CompoundTag> subsystemData : entry.getValue().entrySet()) {
                    String subSystemName = subsystemData.getKey();
                    CompoundTag subsystemTagData = subsystemData.getValue();
                    AbstractSubsystem subsystem = subPart.subsystems.get(subSystemName);
                    subsystem.loadData(subsystemTagData);
                }
            }
        }
        updateMass();//更新部件总质量
    }

    public PartType getPT(Level level, ResourceLocation registryKey) {
        PartType pt;
        if (level.isClientSide) pt = MMDynamicRes.PART_TYPES.get(registryKey);
        else pt = MMDynamicRes.SERVER_PART_TYPES.get(registryKey);
        if (pt == null)
            throw new NullPointerException("部件类型" + registryKey + "不存在，请检查数据。可用部件列表: " + (level.isClientSide() ? MMDynamicRes.PART_TYPES.keySet() : MMDynamicRes.SERVER_PART_TYPES.keySet()));
        return pt;
    }

    public void onTick() {
        //处理各线程造成的伤害
        if (!level.isClientSide()) handleAccumulatedDamage();
        //判定摧毁
        if (!destroyed && durability <= 0) onDestroyed();
        if (oHurtMarked) oHurtMarked = false;
        else if (hurtMarked) hurtMarked = false;
    }

    public void onPrePhysicsTick() {
        if (!level.isClientSide) {
            float impact = 0;
            if (!accumulatedImpact.isEmpty()) {
                Vector3f hitPoint = new Vector3f();
                for (Map.Entry<Vector3f, Float> entry : accumulatedImpact.entrySet()) {
                    impact += entry.getValue();
                    hitPoint.add(entry.getKey().mult(entry.getValue()));
                }
                if (impact > 0) {
                    hitPoint.divideLocal(impact);
                    if (impact >= integrity) {
                        //强冲击，立即击落部件
                        int count = (int) Math.floor(impact / integrity);//计算击落数量
                        if (integrity <= 0) count = externalConnectors.size();
                        for (int i = 0; i < count; i++) {
                            AbstractConnector connectorToBreak = null;
                            float minDistance = Float.MAX_VALUE;
                            //寻找最近的外部接口并设置为要断开的接口
                            for (AbstractConnector connector : externalConnectors.values()) {
                                if (connector.hasPart()) {
                                    Vector3f connectorWorldPos = connector.subPart.getLocatorWorldPos(connector.attr.locatorName());
                                    float distance = connectorWorldPos.subtract(hitPoint).lengthSquared();
                                    if (distance < minDistance) connectorToBreak = connector;
                                }
                            }
                            if (connectorToBreak != null) vehicle.detachConnector(connectorToBreak);
                        }
                    }
                    //削减部件完整性
                    integrity = Math.clamp(integrity - (destroyed ? 0.5f * impact : 0.1f * impact), 0, type.basicIntegrity);
                }
                accumulatedImpact.clear();
            }
            if (integrity <= 0 && destroyed) {
                float finalImpact = (destroyed ? 0.5f * impact : 0.1f * impact);
                level.submitImmediateTask(PPhase.PRE, () -> {
                    vehicle.removePart(this);
                    SoundEvent sound = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part.torn_apart"));
                    SpreadingSoundHelper.playSpreadingSound(level, sound, SoundSource.NEUTRAL, SparkMathKt.toVec3(PhysicsBodyExtensionKt.stateOf(rootSubPart.body).getTransform().getTranslation()), Vec3.ZERO, 64f,
                            (float) ((2 - Math.min(type.basicIntegrity, finalImpact) / type.basicIntegrity) * (1f + 0.2f * (Math.random() - 0.5f))),
                            0.2f + 0.8f * Math.min(type.basicIntegrity, finalImpact) / type.basicIntegrity);
                    return null;
                });
            }
        }
    }

    public void onPostPhysicsTick() {
    }

    public boolean onHurt(DamageSource source,
                          float damage,
                          IPhysicsProjectile projectileSource,
                          SubPart subPart,
                          Vector3f normal,
                          Vector3f worldContactSpeed,
                          Vector3f worldContactPoint,
                          HitBox hitBox) {
        Vec3 sourcePos = source.getSourcePosition();
        if (sourcePos == null)
            sourcePos = SparkMathKt.toVec3(PhysicsBodyExtensionKt.stateOf(rootSubPart.body).getTransform().getTranslation());
        Vec3 finalSourcePos = sourcePos;
        float armor = hitBox.getRHA(subPart);
        float armorPenetration = 0;
        //击退处理与特殊逻辑
        if (projectileSource == null && !level.isClientSide) {//原版伤害处理
            //冲击效果
            float knockBack = (float) (Math.log10(Math.max(1.01, 10 * Math.sqrt(damage / this.type.basicDurability))) * 150f);//伤害转化为动量
            if (source.getDirectEntity() != null && source.getWeaponItem() != null) {//应用附魔等效果调整击退力度
                knockBack *= EnchantmentHelper.modifyKnockback((ServerLevel) level, source.getWeaponItem(), source.getDirectEntity(), source, 1.0f);
            }
            if (source.is(DamageTypes.EXPLOSION) || source.is(DamageTypes.PLAYER_EXPLOSION))
                knockBack *= 15.0f;
            float finalKnockBack = knockBack;
            level.getPhysicsLevel().submitImmediateTask(PPhase.PRE, () -> {//施加动量
                vehicle.activate();
                subPart.body.applyImpulse(worldContactSpeed.normalize().mult(finalKnockBack), worldContactPoint.subtract(subPart.body.getPhysicsLocation(null)));
//                vehicle.poseSyncCountDown = 0;//发生击退时立刻重新同步位置姿态速度
                return null;
            });
            //换算穿深
            armorPenetration = damage / 2f;
        } else if (projectileSource != null) {//甲弹对抗处理
            //获取穿深
            try {
                armorPenetration = damage;
                //TODO:研究一下Key是怎么用的
//                armorPenetration = (float) source.getExtraData().getBlackBoard().getStorage().getOrDefault(new Key<>("armor_pierce", Float.class), 0f);
            } catch (Exception e) {
                armorPenetration = damage / 2f;
                MachineMax.LOGGER.warn("{}受到的伤害不包含穿甲值信息", subPart.part.name);
            }
        }
        //线性减伤处理
        float impactDamage = damage - hitBox.getDamageReduction();
        //累积冲击效果用于削减结构完整性
        accumulatedImpact.put(worldContactPoint, impactDamage);
        //甲弹对抗相关处理
        if (hitBox.hasAngleEffect()) armorPenetration *= -normal.dot(worldContactSpeed.normalize());//按照设置考虑入射角影响
        //击穿判定
        if (armorPenetration > armor || hitBox.hasUnPenetrateDamage()) {
            if (armorPenetration < armor)//未击穿且有未击穿伤害时按照设置造成部分伤害
                impactDamage *= (float) Math.pow(armorPenetration / armor, hitBox.getUnPenetrateDamageFactor());
            impactDamage *= hitBox.getDamageMultiplier();
            //对部件造成伤害
            Part.PartDamageData data = new Part.PartDamageData(source, projectileSource, subPart, normal, worldContactSpeed, worldContactPoint, hitBox);
            accumulateDamage(impactDamage, data);
            return true;
        } else {
            if (!level.isClientSide) {
                level.getPhysicsLevel().submitImmediateTask(PPhase.PRE, () -> {
                    //播放命中音效
                    SoundEvent sound = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part.no_pen"));
                    SpreadingSoundHelper.playSpreadingSound(level, sound, SoundSource.NEUTRAL, finalSourcePos, Vec3.ZERO, 64f,
                            (float) ((2 - Math.min(7f, damage) / 7f) * (1f + 0.2f * (Math.random() - 0.5f))),
                            0.1f + 0.4f * Math.min(7f, damage) / 7f);
                    return null;
                });
            }
            return false;
        }
    }

    /**
     * <p>线程安全地对部件造成伤害，伤害会被在主线程统一处理，参见 {@link #handleAccumulatedDamage()}</p>
     * <p>Accumulates damage to the part thread safely, which will be handled in the main thread, see {@link #handleAccumulatedDamage()}</p>
     *
     * @param damage 伤害值 damage value
     * @param data   伤害源、命中点、判定区等 damage source, hit point, hit box, etc.
     */
    private void accumulateDamage(float damage, PartDamageData data) {
        if (damage > 0) {
            hurtMarked = true;
            accumulatedDamage.add(Pair.of(damage, data));
        }
    }

    /**
     * <p>处理各线程造成的伤害并相应对子系统造成伤害，在主线程中统一处理，参见 {@link #onTick()}</p>
     * <p>Handles the damage caused by each thread and applies it to the subsystem, which will be handled in the main thread, see {@link #onTick()}</p>
     */
    private void handleAccumulatedDamage() {
        if (!level.isClientSide() && !accumulatedDamage.isEmpty()) {
            float totalDamage = 0;
            Vec3 soundPos = Vec3.ZERO;
            while (!accumulatedDamage.isEmpty()) {
                Pair<Float, PartDamageData> pair = accumulatedDamage.poll();
                float damage = pair.getFirst();
                PartDamageData data = pair.getSecond();
                //对子系统造成伤害
                if (data.hitBox.getSubsystem() != null)
                    data.hitBox.getSubsystem().onHurt(damage, data);
                totalDamage += damage;
                soundPos = SparkMathKt.toVec3(data.worldContactPoint);
            }
            this.durability = Math.clamp(durability - totalDamage, 0, type.basicDurability);
            //TODO:对载具造成伤害
            //发包同步部件与子系统状态
            syncStatus();
            //播放音效
            SoundEvent sound = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part.penetrate"));
            SpreadingSoundHelper.playSpreadingSound(level, sound, SoundSource.NEUTRAL, soundPos, Vec3.ZERO, 64f,
                    (float) ((2 - 2 * Math.min(0.5f * type.basicDurability, totalDamage) / type.basicDurability) * (1f + 0.2f * (Math.random() - 0.5f))),
                    0.2f + 0.8f * 2 * Math.min(0.5f * type.basicDurability, totalDamage) / type.basicDurability);
        }
    }

    /**
     * <p>获取部件所有零件持有的子系统</p>
     * <p>Gets all subsystems held by all parts</p>
     * @return
     */
    public Set<AbstractSubsystem> getAllSubsystems() {
        HashSet<AbstractSubsystem> subsystems = new HashSet<>();
        for (SubPart subPart : subParts.values()) {
            subsystems.addAll(subPart.subsystems.values());
        }
        return subsystems;
    }

    /**
     * <p>立刻发包同步部件与子系统耐久度等数据</p>
     * <p>Sends a packet to synchronize the durability and other data of the part and its subsystem immediately</p>
     */
    public void syncStatus() {
        if (!level.isClientSide) {
            Map<String, Map<String, Float>> allSubsystemDurability = getStatusSyncData();
            PacketDistributor.sendToPlayersInDimension((ServerLevel) level,
                    new PartSyncPayload(vehicle.uuid, uuid, durability, integrity, allSubsystemDurability));
        }
    }

    /**
     * <p>获取部件与子系统耐久度等数据，用于同步</p>
     * <p>Gets the durability and other data of the part and its subsystem for synchronization</p>
     * @return 部件与子系统耐久度等数据
     */
    public @NotNull Map<String, Map<String, Float>> getStatusSyncData() {
        Map<String,Map<String,Float>> allSubsystemDurability = new HashMap<>();
        for (Map.Entry<String, SubPart> entry : this.subParts.entrySet()){
            String subPartName = entry.getKey();
            SubPart subPart = entry.getValue();
            Map<String, Float> subsystemDurability = new HashMap<>();
            for (Map.Entry<String, AbstractSubsystem> entry2 : subPart.getSubsystems().entrySet()) {
                subsystemDurability.put(entry2.getKey(), entry2.getValue().getDurability());
            }
            allSubsystemDurability.put(subPartName, subsystemDurability);
        }
        return allSubsystemDurability;
    }

    protected void onDestroyed() {
        destroyed = true;
        for (SubPart subPart : subParts.values()) {
            for (AbstractSubsystem subsystem : subPart.subsystems.values()) {
                subsystem.setActive(false);
            }
            for (AbstractConnector connector : allConnectors.values()) {
                //TODO:随机锁定/解锁某个关节的自由度？
                if (connector.attr.breakable()) {

                }
            }
        }
        if (level.isClientSide) {
            SoundEvent sound = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part.destroyed"));
            SpreadingSoundHelper.playSpreadingSound(level, sound, SoundSource.NEUTRAL, SparkMathKt.toVec3(rootSubPart.getWorldPositionMatrix(1).getTranslation(new org.joml.Vector3f())), Vec3.ZERO, 64f,
                    (float) (1f + 0.2f * (Math.random() - 0.5f)),
                    1f);
        }
    }

    private void createSubsystems(
            SubPart subPart,
            Map<String, AbstractSubsystemAttr> subSystemAttrMap
    ) {
        for (Map.Entry<String, AbstractSubsystemAttr> entry : subSystemAttrMap.entrySet()) {
            String name = entry.getKey();
            AbstractSubsystemAttr attr = entry.getValue();
            AbstractSubsystem subsystem = attr.createSubsystem(subPart, name);
            subPart.subsystems.put(name, subsystem);//部件内的子系统
        }
    }

    private void createConnectors(
            SubPart subPart,
            SubPartAttr subPartAttr,
            LinkedHashMap<String, OLocator> locators
    ) {
        for (Map.Entry<String, ConnectorAttr> connectorEntry : subPartAttr.connectors.entrySet()) {
            String connectorName = connectorEntry.getKey();
            ConnectorAttr connectorAttr = connectorEntry.getValue();
            if (locators.get(connectorAttr.locatorName()) instanceof OLocator locator) {//若找到了对应的零件对接口Locator
                org.joml.Vector3f rotation = locator.getRotation().toVector3f();
                Transform posRot = new Transform(//对接口的位置与姿态
                        PhysicsHelperKt.toBVector3f(locator.getOffset()).subtract(subPart.massCenterTransform.getTranslation()),
                        SparkMathKt.toBQuaternion(new Quaternionf().rotationZYX(rotation.x, rotation.y, rotation.z)).mult(subPart.massCenterTransform.getRotation().inverse())
                );
                AbstractConnector connector = switch (connectorAttr.type()) {
                    case "AttachPoint" ->//连接点接口
                            new AttachPointConnector(
                                    connectorName,
                                    connectorAttr,
                                    subPart,
                                    posRot
                            );
                    case "Special" ->//6自由度自定义关节接口
                            new SpecialConnector(
                                    connectorName,
                                    connectorAttr,
                                    subPart,
                                    posRot
                            );
                    default ->
                            throw new NullPointerException(Component.translatable("error.machine_max.part.invalid_connector_type", type.name, connectorName, connectorAttr.type()).getString());
                };
                subPart.connectors.put(connectorName, connector);
                this.allConnectors.put(Pair.of(subPart.name, connectorName), connector);
                if (!connector.internal) this.externalConnectors.put(Pair.of(subPart.name, connectorName), connector);
            } else
                throw new NullPointerException(Component.translatable("error.machine_max.part.connector_locator_not_found", type.name, connectorName, connectorAttr.locatorName()).getString());
        }
    }

    /**
     * <p>计算零件三轴投影面积，用于阻力计算以及RCS计算</p>
     * <p>Calculates the projection area of the part in three axes, which is used for force calculation.</p>
     * <p>首选零件属性指定的投影面积，否则使用碰撞体积估算</p>
     * <p>First, the projected area specified in the part attribute is used, otherwise, the estimated volume of the collision shape is used.</p>
     *
     * @param subPart 零件
     * @return 投影面积 projection area (m^2)
     */
    public Vec3 calculateProjectedArea(SubPart subPart) {
        //计算零件三轴投影面积，用于阻力计算
        BoundingBox boundingBox = subPart.collisionShape.boundingBox(new Vector3f(), Quaternion.IDENTITY, null);
        double xArea, yArea, zArea;
        if (subPart.attr.projectedArea.x <= 0)
            xArea = 4 * boundingBox.getYExtent() * boundingBox.getZExtent();//半长相乘，还需乘4才能获得真正的面积
        else xArea = subPart.attr.projectedArea.x;
        if (subPart.attr.projectedArea.y <= 0)
            yArea = 4 * boundingBox.getXExtent() * boundingBox.getZExtent();
        else yArea = subPart.attr.projectedArea.y;
        if (subPart.attr.projectedArea.z <= 0)
            zArea = 4 * boundingBox.getXExtent() * boundingBox.getYExtent();
        else zArea = subPart.attr.projectedArea.z;
        return new Vec3(xArea, yArea, zArea);
    }

    private SubPart createSubParts(Map<String, SubPartAttr> subPartAttrMap) {
        //创建零件
        for (Map.Entry<String, SubPartAttr> subPartEntry : subPartAttrMap.entrySet()) {//遍历部件的零件属性
            String name = subPartEntry.getKey();
            SubPartAttr subPartAttr = subPartEntry.getValue();
            SubPart subPart = new SubPart(name, this, subPartAttr);//创建零件
            //获取模型用于构建碰撞
            OModel model = subPart.getModelController().getOriginModel();
            LinkedHashMap<String, OBone> bones = model.getBones();//从模型获取所有骨骼
            LinkedHashMap<String, OLocator> locators = LinkedHashMap.newLinkedHashMap(0);
            for (OBone bone : bones.values()) locators.putAll(bone.getLocators());//从模型获取所有定位器

            subParts.put(name, subPart);//将零件放入部件的零件表

            subPart.body.setMass(subPartAttr.mass > 0 ? subPartAttr.mass : 20);//设置质量
            subPart.body.setCcdSweptSphereRadius(subPart.collisionShape.maxRadius());//设置CCD半径
            subPart.projectedArea = calculateProjectedArea(subPart);
            //创建零件对接口
            createConnectors(subPart, subPartAttr, locators);
            //创建部件内子系统
            createSubsystems(subPart, subPartAttr.subsystems);//创建子系统，赋予部件实际功能
            //创建命中判定区属性并匹配对应子系统(内部实现)
            for (HitBoxAttr hitBoxAttr : subPart.attr.hitBoxes.values()) {
                subPart.hitBoxes.put(hitBoxAttr.hitBoxName(), new HitBox(subPart, hitBoxAttr));
            }
        }
        //设置默认根零件，取质量最大的
        float maxMass = -100;
        SubPart rootSubPart = null;
        for (SubPart subPart : subParts.values()) {
            if (subPart.body.getMass() > maxMass) {
                maxMass = subPart.body.getMass();
                rootSubPart = subPart;
            }
        }
        return rootSubPart;
    }

    public void updateMass() {
        float totalMass = 0;
        for (SubPartAttr subPart : type.getVariants().get(variant).subParts().values()) {
            totalMass += subPart.mass;
        }
        this.totalMass = totalMass;
    }

    /**
     * <p>获取此部件为载具提供的最大耐久度</p>
     * <p>Gets the maximum durability of this part as a vehicle</p>
     * @return 最大耐久度 max durability
     */
    public float getDurabilityForVehicle() {
        return type.vehicleDurabilityRate * type.basicDurability;
    }

    /**
     * 主线程 Main thread
     * <p>将部件的所有零件添加到物理世界，开始物理运算</p>
     * <p>Adds all sub-parts of the part to the physical world and starts the physical calculation.</p>
     */
    public void addToLevel() {
        for (SubPart subPart : subParts.values()) subPart.addToLevel();
    }

    /**
     * 主线程 Main thread
     * <p>部件实例被销毁时调用，清除所有子零件、子系统、实体、碰撞箱等</p>
     * <p>Called when the part instance is destroyed, clears all sub-parts, sub-systems, entities, hit boxes, etc.</p>
     */
    public void destroy() {
        for (SubPart subPart : subParts.values()) subPart.destroy();
    }

    public void setTransform(Transform transform) {
        if (vehicle == null || !vehicle.inLevel) {
            setTransformRaw(transform);
        } else level.getPhysicsLevel().submitImmediateTask(PPhase.PRE, () -> {
            setTransformRaw(transform);
            return null;
        });
    }

    private void setTransformRaw(Transform transform) {
        Transform rootTransform = rootSubPart.body.getTransform(null).invert();
        rootSubPart.body.setPhysicsTransform(transform);
        Transform subPartTransform = new Transform();
        for (SubPart subPart : subParts.values()) {
            if (subPart == rootSubPart) continue;
            subPart.body.getTransform(subPartTransform);
            MyMath.combine(subPartTransform, rootTransform, subPartTransform);
            MyMath.combine(subPartTransform, transform, subPartTransform);
            subPart.body.setPhysicsTransform(subPartTransform);
        }
    }

    @Override
    public ConcurrentMap<String, SignalChannel> getSignalInputChannels() {
        return signalChannels;
    }
}
