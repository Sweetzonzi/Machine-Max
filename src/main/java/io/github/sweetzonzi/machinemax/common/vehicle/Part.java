package io.github.sweetzonzi.machinemax.common.vehicle;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.anim.origin.OAnimation;
import cn.solarmoon.spark_core.animation.anim.play.*;
import cn.solarmoon.spark_core.animation.model.origin.OBone;
import cn.solarmoon.spark_core.animation.model.origin.OLocator;
import cn.solarmoon.spark_core.molang.core.storage.IForeignVariableStorage;
import cn.solarmoon.spark_core.molang.core.storage.IScopedVariableStorage;
import cn.solarmoon.spark_core.molang.core.storage.ITempVariableStorage;
import cn.solarmoon.spark_core.molang.core.storage.VariableStorage;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.SparkMathKt;
import cn.solarmoon.spark_core.sound.SpreadingSoundHelper;
import cn.solarmoon.spark_core.sync.SyncData;
import cn.solarmoon.spark_core.sync.SyncerType;
import cn.solarmoon.spark_core.util.PPhase;
import cn.solarmoon.spark_core.util.TaskSubmitOffice;
import com.jme3.bounding.BoundingBox;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.datafixers.util.Pair;
import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.common.entity.MMPartEntity;
import io.github.sweetzonzi.machinemax.common.vehicle.attr.ConnectorAttr;
import io.github.sweetzonzi.machinemax.common.vehicle.attr.HitBoxAttr;
import io.github.sweetzonzi.machinemax.common.vehicle.attr.SubPartAttr;
import io.github.sweetzonzi.machinemax.common.vehicle.attr.subsystem.AbstractSubsystemAttr;
import io.github.sweetzonzi.machinemax.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machinemax.common.vehicle.connector.AttachPointConnector;
import io.github.sweetzonzi.machinemax.common.vehicle.connector.SpecialConnector;
import io.github.sweetzonzi.machinemax.common.vehicle.data.PartData;
import io.github.sweetzonzi.machinemax.common.vehicle.signal.ISignalReceiver;
import io.github.sweetzonzi.machinemax.common.vehicle.signal.SignalChannel;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machinemax.external.MMDynamicRes;
import io.github.sweetzonzi.machinemax.network.payload.PartSyncPayload;
import io.github.sweetzonzi.machinemax.network.payload.assembly.PartPaintPayload;
import io.github.sweetzonzi.machinemax.util.data.PosRotVelVel;
import jme3utilities.math.MyMath;
import kotlin.Unit;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

@Getter
public class Part implements IAnimatable<Part>, ISubsystemHost, ISignalReceiver {
    //渲染属性 Renderer attributes
    public ModelIndex modelIndex;//用于储存部件的模型索引(模型贴图动画路径等)
    public volatile boolean hurtMarked = false;
    public volatile boolean oHurtMarked = false;
    @Setter
    private BoneGroup bones;//用于储存部件的骨骼组
    public int textureIndex;//当前使用的纹理的索引(用于切换纹理)
    //常规属性 General attributes
    public volatile VehicleCore vehicle;//所属的VehicleCore
    @Nullable
    public MMPartEntity entity;//用于渲染模型以及和原版内容进行交互的的实体对象
    public String name;
    public final PartType type;
    public final Level level;
    public final String variant;
    public final UUID uuid;
    public volatile boolean destroyed = false;
    public volatile float durability;
    public volatile float integrity;
    private final ConcurrentMap<Vector3f, Float> accumulatedImpact = new ConcurrentHashMap<>(8);

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
    private final Map<String, SubPart> locatorSubPart = HashMap.newHashMap(1);
    public final SubPart rootSubPart;
    public final AnimController animController = new AnimController(this);
    public float totalMass;
    //Molang变量存储 Molang variable storage
    public ITempVariableStorage tempStorage = new VariableStorage();
    public IScopedVariableStorage scopedStorage = new VariableStorage();
    public IForeignVariableStorage foreignStorage = new VariableStorage();
    //模块化属性 Modular attributes
    public final Map<String, AbstractConnector> externalConnectors = HashMap.newHashMap(1);
    public final Map<String, AbstractConnector> allConnectors = HashMap.newHashMap(1);
    public final Map<String, AbstractSubsystem> subsystems = HashMap.newHashMap(1);
    public final ConcurrentHashMap<String, HitBox> hitBoxes = new ConcurrentHashMap<>();
    public final ConcurrentMap<String, SignalChannel> signalChannels = new ConcurrentHashMap<>();//部件内共享的信号

    /**
     * <p>创建新部件，使用指定变体</p>
     * <p>仅应在服务端新建部件时使用</p>
     *
     * @param partType 部件类型
     * @param variant  部件变体类型
     * @param level    部件被加入的世界
     */
    public Part(PartType partType, String variant, Level level) {
        if (variant == null) variant = "default";
        this.modelIndex = new ModelIndex(
                partType.variants.getOrDefault(variant, partType.variants.get("default")),//获取部件模型路径
                partType.animation,//获取部件动画路径
                partType.textures.getFirst());//获取部件第一个可用纹理作为默认纹理
        this.textureIndex = 0;
        this.name = partType.getName();
        this.type = partType;
        this.variant = variant;
        this.level = level;
        this.uuid = UUID.randomUUID();
        this.durability = partType.basicDurability;
        this.integrity = partType.basicIntegrity;
        this.rootSubPart = createSubPart(type.subParts);//创建子部件并指定根子部件
        updateMass();
    }

    /**
     * <p>从注册名创建新部件，使用指定变体</p>
     * <p>仅应在服务端新建部件时使用</p>
     *
     * @param registryKey 部件注册名
     * @param variant     部件变体类型
     * @param level       部件被加入的世界
     */
    public Part(ResourceLocation registryKey, String variant, Level level) {
        this(Objects.requireNonNull(level.registryAccess().registry(PartType.PART_REGISTRY_KEY).get().get(registryKey)), variant, level);
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
     * <p>创建新部件，使用默认变体</p>
     * <p>仅应在服务端新建部件时使用</p>
     *
     * @param registryKey 部件注册名
     * @param level       部件被加入的世界
     */
    public Part(ResourceLocation registryKey, Level level) {
        this(registryKey, "default", level);
    }

    /**
     * 从保存或网络传输的数据中重建部件
     *
     * @param data  保存或网络传输的数据
     * @param level 部件所在的世界
     */
    public Part(PartData data, Level level) {
        this.name = data.name;
        this.type = getPT(level, data.registryKey);
        this.level = level;
        this.variant = data.variant;
        this.textureIndex = data.textureIndex;
        this.modelIndex = new ModelIndex(
                type.variants.getOrDefault(variant, type.variants.get("default")),//获取部件模型路径
                type.animation,//获取部件动画路径
                type.textures.get(textureIndex % type.textures.size()));//获取部件第一个可用纹理作为默认纹理
        this.uuid = UUID.fromString(data.uuid);
        this.durability = Math.min(data.durability, type.basicDurability);
        this.integrity = Math.min(data.integrity, type.basicIntegrity);
        this.rootSubPart = createSubPart(type.subParts);//重建子部件并指定根子部件
        updateMass();//更新部件总质量
        for (Map.Entry<String, PosRotVelVel> entry : data.subPartTransforms.entrySet()) {//遍历保存的子部件位置、旋转、速度数据
            SubPart subPart = subParts.get(entry.getKey());//获取已重建的子部件
            if (subPart != null) {//设定子部件body的位置、旋转、速度
                PosRotVelVel posRotVelVel = entry.getValue();
                subPart.body.setPhysicsLocation(posRotVelVel.position());
                subPart.body.setPhysicsRotation(SparkMathKt.toBQuaternion(posRotVelVel.rotation()));
                subPart.body.setLinearVelocity(posRotVelVel.linearVel());
                subPart.body.setAngularVelocity(posRotVelVel.angularVel());
            } else
                throw new NullPointerException("部件" + name + "的子部件" + entry.getKey() + "不存在，请检查数据。");
        }
    }

    public PartType getPT(Level level, ResourceLocation registryKey) {
        PartType pt;
        if (level.isClientSide) pt = MMDynamicRes.PART_TYPES.get(registryKey);
        else pt = MMDynamicRes.SERVER_PART_TYPES.get(registryKey);
        if (pt == null)
            throw new NullPointerException("部件类型" + registryKey + "不存在，请检查数据。部件列表: " + (level.isClientSide() ? MMDynamicRes.PART_TYPES.keySet() : MMDynamicRes.SERVER_PART_TYPES.keySet()));
        return pt;
    }

    public void onTick() {
        if (this.entity == null || this.entity.isRemoved()) {
            if (!level.isClientSide()) refreshPartEntity();
        }
        //处理各线程造成的伤害
        if (!level.isClientSide()) handleAccumulatedDamage();
        //判定摧毁
        if (!destroyed && durability <= 0) onDestroyed();
        if (oHurtMarked) oHurtMarked = false;
        else if (hurtMarked) hurtMarked = false;
        if (this.entity != null && !this.entity.isRemoved()) {
            getAnimController().tick();
            var animSet = modelIndex.getAnimationSet().getAnimations();
            if (!animSet.isEmpty() && animController.getMainAnim() == null) {
                for (Map.Entry<String, OAnimation> entry : animSet.entrySet()) {
                    String name = entry.getKey();
                    var anim = entry.getValue();
                    var animInstance = AnimInstance.create(this, name, anim, a -> Unit.INSTANCE);
                    getAnimController().getBlendSpace().putIfAbsent(name, new BlendAnimation(animInstance, 1, List.of()));
                    getAnimController().setAnimation(name, 0, a -> Unit.INSTANCE);
                }
            }
        }
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
                ((TaskSubmitOffice) level).submitImmediateTask(PPhase.PRE, () -> {
                    vehicle.removePart(this);
                    SoundEvent sound = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part.torn_apart"));
                    SpreadingSoundHelper.playSpreadingSound(level, sound, SoundSource.NEUTRAL, getWorldPosition(1), Vec3.ZERO, 64f,
                            (float) ((2 - Math.min(type.basicIntegrity, finalImpact) / type.basicIntegrity) * (1f + 0.2f * (Math.random() - 0.5f))),
                            0.2f + 0.8f * Math.min(type.basicIntegrity, finalImpact) / type.basicIntegrity);
                    return null;
                });
            }
        }
    }

    public void onPostPhysicsTick() {
        if (entity != null && !entity.isRemoved()) {//更新实体包围盒
            List<BoundingBox> boxes = new ArrayList<>();
            for (SubPart subPart : subParts.values()) boxes.add(subPart.body.cachedBoundingBox);
            entity.boundingBoxes.set(boxes);
            getAnimController().physTick();
        }
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
        if (sourcePos == null) sourcePos = SparkMathKt.toVec3(rootSubPart.body.tickTransform.getTranslation());
        Vec3 finalSourcePos = sourcePos;
        float armor = hitBox.getRHA(this);
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
                vehicle.poseSyncCountDown = 0;//发生击退时立刻重新同步位置姿态速度
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
     * @param damage 伤害值 damage value
     * @param data 伤害源、命中点、判定区等 damage source, hit point, hit box, etc.
     */
    private void accumulateDamage(float damage, PartDamageData data) {
        if (damage > 0) {
            hurtMarked = true;
            accumulatedDamage.add(Pair.of(damage, data));
        }
    }

    /**
     * <p>处理各线程造成的伤害并相应对子系统造成伤害，在主线程中统一处理，参见 {@link #onTick()}</p>
     * <p>Handles the damage caused by each thread and applies it to the subsystems, which will be handled in the main thread, see {@link #onTick()}</p>
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
                data.hitBox.getSubsystems().values().forEach(subsystem -> subsystem.onHurt(damage, data));
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
     * <p>立刻发包同步部件与子系统耐久度等数据</p>
     * <p>Sends a packet to synchronize the durability and other data of the part and its subsystems immediately</p>
     */
    public void syncStatus() {
        if (!level.isClientSide) {
            Map<String, Float> subsystemDurability = new HashMap<>();
            for (Map.Entry<String, AbstractSubsystem> entry : this.getSubsystems().entrySet()) {
                subsystemDurability.put(entry.getKey(), entry.getValue().getDurability());
            }
            PacketDistributor.sendToPlayersInDimension((ServerLevel) level,
                    new PartSyncPayload(vehicle.uuid, uuid, durability, integrity, subsystemDurability));
        }
    }

    protected void onDestroyed() {
        destroyed = true;
        for (AbstractSubsystem subsystem : subsystems.values()) {
            subsystem.setActive(false);
        }
        if (level.isClientSide) {
            SoundEvent sound = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part.destroyed"));
            SpreadingSoundHelper.playSpreadingSound(level, sound, SoundSource.NEUTRAL, getWorldPosition(1), Vec3.ZERO, 64f,
                    (float) (1f + 0.2f * (Math.random() - 0.5f)),
                    1f);
        }
    }

    public void refreshPartEntity() {
        this.entity = new MMPartEntity(level, this);
        level.addFreshEntity(this.entity);
    }

    private void createSubsystems(
            Map<String, AbstractSubsystemAttr> subSystemAttrMap
    ) {
        for (Map.Entry<String, AbstractSubsystemAttr> entry : subSystemAttrMap.entrySet()) {
            String name = entry.getKey();
            AbstractSubsystemAttr attr = entry.getValue();
            AbstractSubsystem subsystem = attr.createSubsystem(this, name);
            subsystems.put(name, subsystem);//部件内的子系统
            subsystem.hitBox = hitBoxes.get(attr.hitBox);//设置子系统的判定区
        }
    }

    private void createConnectors(
            SubPart subPart,
            SubPartAttr subPartAttr,
            LinkedHashMap<String, OLocator> locators
    ) {
        for (Map.Entry<String, ConnectorAttr> connectorEntry : subPartAttr.connectors.entrySet()) {
            if (locators.get(connectorEntry.getValue().locatorName()) instanceof OLocator locator) {//若找到了对应的零件对接口Locator
                org.joml.Vector3f rotation = locator.getRotation().toVector3f();
                Transform posRot = new Transform(//对接口的位置与姿态
                        PhysicsHelperKt.toBVector3f(locator.getOffset()).subtract(subPart.massCenterTransform.getTranslation()),
                        SparkMathKt.toBQuaternion(new Quaternionf().rotationZYX(rotation.x, rotation.y, rotation.z)).mult(subPart.massCenterTransform.getRotation().inverse())
                );
                AbstractConnector connector = switch (connectorEntry.getValue().type()) {
                    case "AttachPoint" ->//连接点接口
                            new AttachPointConnector(
                                    connectorEntry.getKey(),
                                    connectorEntry.getValue(),
                                    subPart,
                                    posRot
                            );
                    case "Special" ->//6自由度自定义关节接口
                            new SpecialConnector(
                                    connectorEntry.getKey(),
                                    connectorEntry.getValue(),
                                    subPart,
                                    posRot
                            );
                    default ->
                            throw new NullPointerException(Component.translatable("error.machine_max.part.invalid_connector_type", type.name, connectorEntry.getKey(), connectorEntry.getValue().type()).getString());
                };
                subPart.connectors.put(connectorEntry.getKey(), connector);
                this.allConnectors.put(connectorEntry.getKey(), connector);
                if (!connector.internal) this.externalConnectors.put(connectorEntry.getKey(), connector);
            } else
                throw new NullPointerException(Component.translatable("error.machine_max.part.connector_locator_not_found", type.name, connectorEntry.getKey(), connectorEntry.getValue().locatorName()).getString());
        }
    }

    private SubPart createSubPart(Map<String, SubPartAttr> subPartAttrMap) {
        SubPart rootSubPart = null;
        ModelIndex data = this.getModelIndex();
        HashMap<SubPart, String> subPartMap = new HashMap<>();//用于记录子部件的父子关系
        LinkedHashMap<String, OBone> bones = data.getModel().getBones();//从模型获取所有骨骼
        LinkedHashMap<String, OLocator> locators = LinkedHashMap.newLinkedHashMap(0);
        for (OBone bone : bones.values()) locators.putAll(bone.getLocators());//从模型获取所有定位器
        //创建零件
        for (Map.Entry<String, SubPartAttr> subPartEntry : subPartAttrMap.entrySet()) {//遍历部件的零件属性
            SubPart subPart = new SubPart(subPartEntry.getKey(), this, subPartEntry.getValue());//创建零件
            subParts.put(subPartEntry.getKey(), subPart);//将零件放入部件的零件表
            if (subPartEntry.getValue().parent.isEmpty()) {//检测是否为根零件
                if (rootSubPart == null) rootSubPart = subPart;//记录第一个根零件
                else MachineMax.LOGGER.error("仅允许存在一个根零件，请检查模型文件{}。", type);
            } else subPartMap.put(subPart, subPartEntry.getValue().parent);//记录子部件的父子关系
            subPart.body.setMass(subPartEntry.getValue().mass > 0 ? subPartEntry.getValue().mass : 20);//设置质量
            subPart.body.setCcdSweptSphereRadius(subPart.collisionShape.maxRadius());//设置CCD半径
            //计算/设置零件三轴投影面积，用于阻力计算
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
            subPart.projectedArea = new Vec3(xArea, yArea, zArea);
            //创建零件对接口
            createConnectors(subPart, subPartEntry.getValue(), locators);
            //用于从部件查找定位器对应的零件
            subPart.attr.locatorTransforms.computeIfAbsent(variant, v1 -> new ConcurrentHashMap<>()).keySet().forEach(name -> this.locatorSubPart.put(name, subPart));
            //创建命中碰撞区属性
            for (HitBoxAttr hitBoxAttr : subPart.attr.hitBoxes.values()) {
                hitBoxes.put(hitBoxAttr.hitBoxName(), new HitBox(subPart, hitBoxAttr));
            }
        }
        //创建部件内子系统
        createSubsystems(type.subsystems);//创建子系统，赋予部件实际功能
        for (Map.Entry<String, AbstractSubsystem> entry : subsystems.entrySet()) {
            String name = entry.getKey();
            AbstractSubsystem subsystem = entry.getValue();
            HitBox hitBox = hitBoxes.get(subsystem.attr.hitBox);
            if (hitBox != null) {
                hitBox.getSubsystems().put(name, subsystem);//碰撞箱与子系统绑定
            } else if (!subsystem.attr.hitBox.isEmpty())
                MachineMax.LOGGER.warn(Component.translatable("error.machine_max.part.subsystem_hitbox_not_found", type.name, name, subsystem.attr.hitBox).getString());
        }
        //设置零件的父子关系，连接内部关节
        for (Map.Entry<SubPart, String> entry : subPartMap.entrySet()) {
            SubPart subPart = entry.getKey();
            String parentName = entry.getValue();
            subPart.parent = subParts.get(parentName);//设置子部件的父部件
            for (AbstractConnector connector : subPart.connectors.values()) {
                if (connector.internal && connector.attachedConnector == null) {
                    for (Map.Entry<SubPart, String> entry2 : subPartMap.entrySet()) {
                        if (entry2.getValue().equals(parentName)) continue;
                        if (entry2.getKey().connectors.containsKey(connector.attr.ConnectedTo())) {
                            AbstractConnector targetConnector = entry2.getKey().connectors.get(connector.attr.ConnectedTo());
                            if (targetConnector.internal && targetConnector.attachedConnector == null && targetConnector instanceof AttachPointConnector) {
                                targetConnector.subPart.body.setPhysicsTransform(targetConnector.subPart.massCenterTransform);
                                connector.attach((AttachPointConnector) targetConnector, true);
                            } else if (targetConnector.internal && targetConnector.attachedConnector == null && connector instanceof AttachPointConnector) {
                                connector.subPart.body.setPhysicsTransform(connector.subPart.massCenterTransform);
                                targetConnector.attach((AttachPointConnector) connector, true);
                            } else
                                throw new IllegalArgumentException(Component.translatable("error.machine_max.part.invalid_internal_connector_connection", type.name, connector.name, targetConnector.name).getString());
                        }
                    }
                }
            }
        }
        //设置默认根零件
        if (!subParts.values().isEmpty() && rootSubPart == null)//若模型中没有根零件
            rootSubPart = subParts.values().iterator().next();//将表中第一个零件作为部件的根零件
        return rootSubPart;
    }

    /**
     * 按给定的纹理索引切换部件纹理
     * 可用于为拥有多个纹理的部件选择外观
     *
     * @param index 纹理索引
     */
    public void switchTexture(int index) {
        if (type.getTextures().size() == 1) return;
        this.textureIndex = index % type.getTextures().size();
        this.setModelIndex(new ModelIndex(
                modelIndex.getModelPath(),
                modelIndex.getAnimPath(),
                type.getTextures().get(index % type.getTextures().size())
        ));
        //同步客户端
        if (!level.isClientSide()) PacketDistributor.sendToPlayersInDimension((ServerLevel) level,
                new PartPaintPayload(vehicle.uuid, this.uuid, this.textureIndex));
    }

    public void updateMass() {
        float totalMass = 0;
        for (SubPartAttr subPart : type.subParts.values()) {
            totalMass += subPart.mass;
        }
        this.totalMass = totalMass;
    }

    public Transform getLocatorLocalTransform(String locatorName) {
        SubPart subPart = locatorSubPart.get(locatorName);
        if (subPart != null) {
            return subPart.getLocatorLocalTransform(locatorName);
        } else throw new NullPointerException("error.machine_max.subpart.locator_not_found");
    }

    public Transform getLerpedLocatorWorldTransform(String locatorName, float partialTick) {
        SubPart subPart = locatorSubPart.get(locatorName);
        if (subPart != null) {
            return subPart.getLerpedLocatorWorldTransform(locatorName, partialTick);
        } else throw new NullPointerException("error.machine_max.subpart.locator_not_found");
    }

    public Transform getLerpedLocatorWorldTransform(String locatorName, Transform offset, float partialTick) {
        SubPart subPart = locatorSubPart.get(locatorName);
        if (subPart != null) {
            return subPart.getLerpedLocatorWorldTransform(locatorName, offset, partialTick);
        } else throw new NullPointerException("error.machine_max.subpart.locator_not_found");
    }

    public Transform getLocatorWorldTransform(String locatorName) {
        SubPart subPart = locatorSubPart.get(locatorName);
        if (subPart != null) {
            return subPart.getLocatorWorldTransform(locatorName);
        } else throw new NullPointerException("error.machine_max.subpart.locator_not_found");
    }

    public Vector3f getLocatorLocalPos(String locatorName) {
        SubPart subPart = locatorSubPart.get(locatorName);
        if (subPart != null) {
            return subPart.getLocatorLocalPos(locatorName);
        } else throw new NullPointerException("error.machine_max.subpart.locator_not_found");
    }

    public Vector3f getLocatorWorldPos(String locatorName) {
        SubPart subPart = locatorSubPart.get(locatorName);
        if (subPart != null) {
            return subPart.getLocatorWorldPos(locatorName);
        } else throw new NullPointerException("error.machine_max.subpart.locator_not_found");
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
        subsystems.forEach((name, subsystem) -> subsystem.onDetach());
        subsystems.clear();
        if (this.entity != null) {
            this.entity.part = null;
            this.entity.remove(Entity.RemovalReason.DISCARDED);
            this.entity = null;
        }
    }

    public void setModelIndex(@NotNull ModelIndex modelIndex) {
        this.modelIndex = modelIndex;
        this.setBones(new BoneGroup(this));
    }

    public @NotNull BoneGroup getBones() {
        if (this.bones == null) bones = new BoneGroup(this);
        return this.bones;
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
    public Part getAnimatable() {
        return this;
    }

    @NotNull
    @Override
    public AnimController getAnimController() {
        return animController;
    }

    @NotNull
    @Override
    public Vec3 getWorldPosition(float v) {
        if (rootSubPart != null) {
            return SparkMathKt.toVec3(SparkMathKt.lerp(rootSubPart.body.lastTickTransform, rootSubPart.body.tickTransform, v).getTranslation());
        } else return Vec3.ZERO;
    }

    @Override
    public float getRootYRot(float v) {
        return 0;
    }

    @Override
    public Matrix4f getWorldPositionMatrix(float partialTick) {
        if (rootSubPart != null) {
            return SparkMathKt.toMatrix4f(
                    SparkMathKt.lerp(rootSubPart.body.lastTickTransform, rootSubPart.body.tickTransform, partialTick).toTransformMatrix()
            );
        } else return new Matrix4f().identity();
    }

    @NotNull
    @Override
    public SyncerType getSyncerType() {
        return null;
    }

    @NotNull
    @Override
    public SyncData getSyncData() {
        return null;
    }

    @NotNull
    @Override
    public ITempVariableStorage getTempStorage() {
        return tempStorage;
    }

    @NotNull
    @Override
    public IScopedVariableStorage getScopedStorage() {
        return scopedStorage;
    }

    @NotNull
    @Override
    public IForeignVariableStorage getForeignStorage() {
        return foreignStorage;
    }

    @Override
    public Part getPart() {
        return this;
    }

    @Override
    public ConcurrentMap<String, SignalChannel> getSignalInputChannels() {
        return signalChannels;
    }

    @Override
    public @NotNull Level getAnimLevel() {
        return level;
    }
}
