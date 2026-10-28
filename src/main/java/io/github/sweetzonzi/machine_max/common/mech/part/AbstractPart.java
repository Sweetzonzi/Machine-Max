package io.github.sweetzonzi.machine_max.common.mech.part;

import io.github.sweetzonzi.machine_max.common.mech.vehicle.*;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.anim.AnimController;
import cn.solarmoon.spark_core.animation.anim.AnimGroups;
import cn.solarmoon.spark_core.animation.anim.AnimLayer;
import cn.solarmoon.spark_core.animation.anim.AnimInstance;
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex;
import cn.solarmoon.spark_core.animation.anim.origin.Loop;
import cn.solarmoon.spark_core.animation.anim.origin.OAnimation;
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet;
import cn.solarmoon.spark_core.animation.model.ModelController;
import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.animation.model.origin.OBone;
import cn.solarmoon.spark_core.animation.model.origin.OLocator;
import cn.solarmoon.spark_core.animation.model.origin.OModel;
import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.molang.SparkMolangContext;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.util.PPhase;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.bounding.BoundingBox;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.datafixers.util.Pair;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.datagen.MMLanguageRegistry;
import io.github.sweetzonzi.machine_max.common.mech.molang.MechMolangContext;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalReceiver;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalChannel;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.connector.ConnectorAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.HitBoxAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.SubPartAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.VariantAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.AbstractSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AdvancedConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.ConnectorAlignmentHelper;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.SimpleConnector;

import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.SubPartData;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.HitBox;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.util.TextUtil;
import io.github.sweetzonzi.machine_max.util.data.PosRotVelVel;
import jme3utilities.math.MyMath;
import lombok.Getter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 部件实例的公共机械与动画基础。
 *
 * <p>一个部件拥有一组 {@link SubPart}、连接器、子系统以及一套模型、动画和 MoLang 上下文。
 * 材料进度、工作许可、维修上限及伤害折算由具体种类实现；装配体决定是否接受该种类以及是否创建
 * MM 子零件代理实体。公共基础不假设实例一定属于载具。</p>
 * <p><b>构造顺序：</b>基类构造器只保存公共字段；子类先初始化自身状态，按需绑定查询上下文，
 * 再调用一次 {@link #initializeStructure()}，恢复路径随后调用 {@link #restoreState(AbstractPartData, PartRestoreMode)}。
 * 结构构造会查询虚方法提供的耐久和工作策略，因此不能提前到子类字段初始化之前。</p>
 * <p><b>线程约定：</b>入世前的独占构造阶段可以直接设置刚体；入世后的刚体变更须在物理线程执行。
 * 宿主在主线程驱动 {@link #onTick()} 发布姿态，在物理线程驱动 {@link #onPrePhysicsTick()} 混合动画，
 * 每个相位只驱动一次。并发映射只保护其自身操作，不使整个部件状态成为线程安全事务。</p>
 *
 * @param <T> 本种类的静态定义类型
 */
@Getter
public abstract class AbstractPart<T extends AbstractPartType> implements IAnimatable<AbstractPart<?>>, ISignalReceiver {
    // 身份、归属与公共运行状态
    /**
     * 所属的装配体（VehicleCore、MechUnit 等）
     */
    public volatile IPartAssembly assembly;
    public String name;
    public final T type;
    public final Level level;
    public final String variantName;
    public final VariantAttr variant;
    public final UUID uuid;
    public SubPart rootSubPart;
    /**
     * 构造路径授予的撬棍拆卸权限，整个实例生命周期固定；不从定义或存档读取。
     * <p>权限只约束撬棍交互，不阻止伤害销毁或宿主主动拆离。</p>
     */
    public final boolean crowbarRemovable;
    public float totalMass;
    public boolean destroyed = false;
    // 部件模型、动画、纹理与 MoLang 上下文
    public ModelController modelController;
    public AnimController animController;
    /** AbstractPart 级 Molang 求值上下文（同时供动画、HitBox 条件与 HUD 使用） */
    private MechMolangContext molangContext;
    /** 当前使用的纹理索引（用于切换纹理） */
    public String textureName;
    /** 部件级信号存储，同时作为 Molang 变量表（{@link #getVariables()}） */
    public final ConcurrentMap<String, Object> signalStorage = new ConcurrentHashMap<>();
    public final ConcurrentMap<String, SignalChannel> signalInputChannels = new ConcurrentHashMap<>();
    // 子零件、连接器与子系统索引
    public final Map<String, SubPart> subParts = HashMap.newHashMap(1);
    public final Map<Pair<String, String>, AbstractConnector> externalConnectors = HashMap.newHashMap(1);
    public final Map<Pair<String, String>, AbstractConnector> allConnectors = HashMap.newHashMap(1);
    /** AbstractPart 级按名索引：连接点名 → 连接点（用于 Molang 按名寻址） */
    public final Map<String, AbstractConnector> connectorsByName = HashMap.newHashMap(1);
    /** AbstractPart 级按名索引：子系统名 → 子系统（用于 Molang 按名寻址） */
    public final Map<String, AbstractSubsystem> subsystemsByName = HashMap.newHashMap(1);

    /**
     * 绑定当前定义、变体、身份和实例权限，建立默认 MoLang 上下文。
     *
     * <p>此时尚未创建模型控制器、子零件或连接器；子类必须在自身字段初始化后显式构造结构。</p>
     *
     * @param type 当前逻辑端已发布的定义，过期引用会被拒绝
     * @param variantName 变体名；{@code null} 使用 {@code default}
     * @param level 所在世界
     * @param uuid 本实例身份；新建路径随机生成，恢复路径使用存档 UUID
     * @param name 本实例显示名称
     * @param context 构造路径授予的权限
     */
    protected AbstractPart(T type, String variantName, Level level, UUID uuid, String name,
                           PartCreationContext context) {
        this.type = PartDefinitions.requireCurrent(level.isClientSide(), Objects.requireNonNull(type));
        this.variantName = variantName == null ? "default" : variantName;
        this.variant = Objects.requireNonNull(type.getVariant(this.variantName), "Unknown variant: " + this.variantName);
        this.level = level;
        this.uuid = uuid;
        this.name = name;
        this.crowbarRemovable = context.crowbarRemovable();
        this.molangContext = new MechMolangContext(this);
        this.textureName = variant.getTextures().keySet().iterator().next();
    }

    /**
     * 在子类字段就绪后构造一次模型、动画、子零件及内部连接。
     *
     * <p>只能在尚未入世的独占构造阶段调用。子零件创建期间可能读取种类提供的虚方法，
     * 因此本方法不能由基类构造器提前执行。创建过程中发生运行时异常或错误时，会清理已记录的子零件后重抛。</p>
     *
     * @throws IllegalStateException 模型控制器已经初始化时抛出
     */
    protected final void initializeStructure() {
        if (modelController != null) throw new IllegalStateException("Structure already initialized");
        try {
            modelController = new ModelController(this);
            animController = new AnimController(this);
            modelController.setModel(new ModelIndex("part", variant.getModel()));
            modelController.setTextureLocation(variant.getTexture(textureName));
            rootSubPart = createSubParts(variant.getSubParts());
            updateMass();
        } catch (RuntimeException | Error failure) {
            abortConstruction();
            throw failure;
        }
    }

    /**
     * 在结构构造前绑定扩展查询上下文。
     *
     * <p>命中框等表达式在结构构造时取得上下文，子类必须先完成所需字段初始化，再绑定上下文，
     * 最后调用 {@link #initializeStructure()}。本方法不负责把基类默认上下文中的值迁移到新上下文。</p>
     *
     * @param context 面向当前部件的非空查询上下文
     * @throws IllegalStateException 子零件结构已经开始建立时抛出
     */
    protected final void bindMolangContext(MechMolangContext context) {
        if (rootSubPart != null || !subParts.isEmpty()) throw new IllegalStateException("Structure already initialized");
        molangContext = Objects.requireNonNull(context);
    }

    /**
     * 向已经构造的结构应用公共存档状态；仅在刚体尚未入世时调用。
     *
     * <p>两种模式均恢复子零件位姿、速度及客户端同步 ID，缺少存档的子零件会执行销毁流程。
     * 完整模式还恢复有效涂装、耐久、连接点 NBT 与子系统 NBT；骨架模式保留新建时的默认运行状态。
     * 普通部件的制造与材料进度由子类构造器单独按模式处理。</p>
     * <p>NBT 先复制后加载，不改变输入 DTO。独立耐久逐子零件恢复；共享耐久在全部加载后统一按比例写入，
     * 避免逐个子零件覆盖同一耐久池。调用失败后的清理由具体构造器负责。</p>
     *
     * @param data 已通过工厂预检的存档
     * @param mode 显式恢复策略；调用者必须保证非空
     */
    protected final void restoreState(AbstractPartData data, PartRestoreMode mode) {
        if (mode == PartRestoreMode.FULL_STATE && variant.getTextures().containsKey(data.textureName)) {
            textureName = data.textureName;
            modelController.setTextureLocation(variant.getTexture(textureName));
        }
        //遍历零件，录入基本数据
        for (Map.Entry<String, SubPart> entry : subParts.entrySet()) {
            String subPartName = entry.getKey();
            SubPart subPart = entry.getValue();
            if (data.subParts.containsKey(subPartName)) {
                SubPartData subPartData = data.subParts.get(subPartName);
                if (level.isClientSide()) subPart.setId(subPartData.id);//仅客户端接收应用服务端发送的id
                PosRotVelVel posRotVelVel = subPartData.posRotVelVel;
                subPart.setPosition(posRotVelVel.position());
                subPart.setRotation(SparkMathKt.toBQuaternion(posRotVelVel.rotation()));
                subPart.setLinearVelocity(posRotVelVel.linearVel());
                subPart.setAngularVelocity(posRotVelVel.angularVel());
                subPart.transform = posRotVelVel.toTransform();
                subPart.oldTransform = posRotVelVel.toTransform();
                subPart.body.setPhysicsLocation(posRotVelVel.position());
                subPart.body.setPhysicsRotation(SparkMathKt.toBQuaternion(posRotVelVel.rotation()));
                subPart.body.setLinearVelocity(posRotVelVel.linearVel());
                subPart.body.setAngularVelocity(posRotVelVel.angularVel());
                PhysicsBodyExtensionKt.stateOf(subPart.body).setTransform(posRotVelVel.toTransform());
                PhysicsBodyExtensionKt.stateOf(subPart.body).setLastTransform(posRotVelVel.toTransform());
            } else {
                subPart.destroy();
            }
        }
        if (mode == PartRestoreMode.FULL_STATE) {
            //加载子系统储存的数据
            for (Map.Entry<String, SubPartData> entry : data.subParts.entrySet()) {
                SubPart subPart = subParts.get(entry.getKey());
                if (subPart == null) continue;
                // 共享耐久的部件在循环结束后按 AbstractPart 级比例统一恢复，避免逐个写入相互覆盖
                if (!type.shareDurability) {
                    subPart.setDurability(entry.getValue().durabilityRatio * subPart.getMaxDurability());
                }
                for (Map.Entry<String, CompoundTag> connectorData : entry.getValue().connectorData.entrySet()) {
                    String connectorName = connectorData.getKey();
                    CompoundTag connectorTagData = connectorData.getValue();
                    AbstractConnector connector = subPart.connectors.get(connectorName);
                    if (connector != null)
                        connector.loadData(connectorTagData.copy());
                }
                for (Map.Entry<String, CompoundTag> subsystemData : entry.getValue().subsystemData.entrySet()) {
                    String subSystemName = subsystemData.getKey();
                    CompoundTag subsystemTagData = subsystemData.getValue();
                    AbstractSubsystem subsystem = subPart.subsystems.get(subSystemName);
                    if (subsystem != null)
                        subsystem.loadData(subsystemTagData.copy());
                }
            }
            // 共享耐久：按 AbstractPart 级比例一次性写入全部 SubPart
            if (type.shareDurability) {
                setSharedDurability(data.sharedDurabilityRatio * getSharedMaxDurability());
            }
        }
    }

    /**
     * 导出本种类的动态存档，返回值必须匹配注册的存档类和 Codec。
     * <p>调用者负责安排一致的存档阶段；实现不得把构造权限当作可从存档恢复的状态。</p>
     */
    public abstract AbstractPartData save();
    /**
     * 判断当前实例是否允许子系统工作。
     * <p>由子系统工作判据读取，适合表达组装进度、身体状态等种类规则，不应在查询中改变状态。</p>
     */
    public abstract boolean permitsSubsystemWork();
    /**
     * 返回写入耐久时相对于设计上限的许可比例。
     * <p>子零件原始耐久和共享耐久写入均使用此值钳制上限；具体种类应返回非负、有限的比例。</p>
     */
    public abstract float durabilityLimitRatio();
    /**
     * 返回维修目标相对于设计耐久上限的最大比例。
     * <p>它决定本次维修最多能恢复到哪里，与所有耐久写入均受约束的 {@link #durabilityLimitRatio()} 分开。
     * 材料进度可以限制维修，但不必成为其他种类的公共状态。</p>
     */
    public abstract float repairLimitRatio();
    /**
     * 判断 MM 子零件维修入口能否作用于本部件；拒绝时也跳过子系统维修和连接点加固。
     */
    public abstract boolean permitsRepair();
    /**
     * 把子零件本次结算伤害折算为上报装配体的数值，不在此直接扣除装配体 HP。
     *
     * @param amount 本次经过零件伤害处理后的有效伤害量
     * @param remaining 本次扣除前的剩余耐久，用于区分可吸收部分与溢出部分
     * @param destroyed 结算时子零件是否已处于摧毁状态
     * @return 向装配体上报的非负伤害量；返回零表示本次不传递
     */
    public abstract float assemblyDamage(float amount, float remaining, boolean destroyed);
    /**
     * 判断子零件的延迟重力初始化是否恢复世界正常重力；默认允许。
     * <p>该查询本身不修改刚体，具体种类需自行安排后续状态变化引起的重力更新。</p>
     */
    public boolean usesFullGravity() {
        return true;
    }

    /**
     * 清理已创建子零件的物理与连接资源，跳过子系统正常拆离通知。
     * <p>仅用于构造失败；跳过拆离通知可避免将尚未正式投入使用的库存作为掉落返还。</p>
     */
    protected final void abortConstruction() {
        subParts.values().forEach(SubPart::abortConstruction);
    }

    /**
     * 在批量恢复中清理尚未加入物理世界的部件。
     *
     * <p>调用者须先解除装配体引用并处理自己的注册项。本方法只释放构造资源，不走正常拆卸或掉落流程。</p>
     *
     * @throws IllegalStateException 任一子零件刚体已入世时抛出，防止误用构造期清理路径
     */
    public final void discardUnattached() {
        if (subParts.values().stream().anyMatch(child -> child.body.isInWorld())) {
            throw new IllegalStateException("Cannot discard an attached part");
        }
        abortConstruction();
    }

    /**
     * 子零件伤害或维修结算后的种类回调，默认无操作。
     * <p>由主线程结算路径调用；普通部件在这里重算制造进度，其他种类可更新自身派生状态。</p>
     */
    public void onDurabilityChanged() {
    }

    /**
     * 服务端进入允许的维修分支时调用，即使实际维修量为零也可能触发；默认无操作。
     */
    public void onRepairAttempt() {
    }

    /**
     * 判断本部件是否仍处于组装形态（骨架）；公共基础默认不是，具体种类提供自身状态位。
     * <p>渲染器据此决定是否画线框，重力策略据此决定是否使用折减重力。</p>
     */
    public boolean isAssembling() {
        return false;
    }

    /**
     * 主线程每 tick 调用（由 VehicleCore 在区块加载时驱动）。
     * <p>发布共享动画姿态（唯一发布者），并在客户端自动播放循环动画。</p>
     */
    public void onTick() {
        boolean shouldDestroy = true;
        for (SubPart subPart : subParts.values()) {
            if (subPart.getDestroyTime() > 0) shouldDestroy = false;
            break;
        }
        if (shouldDestroy) this.destroyed = true;
        // 发布共享 pose（唯一 setChanged 调用点）
        animController.tick();
        if (level.isClientSide()) autoPlayLoopAnimations();
    }

    /**
     * 物理线程每物理刻调用（由 VehicleCore 在区块加载时驱动）。
     * <p>推进动画时间并将骨骼混合到共享 pose。</p>
     */
    public void onPrePhysicsTick() {
        animController.physTick();
    }

    /**
     * 客户端自动播放持续动画（loop:true）；一次性事件动画由 {@link #playAnim(String)} 按名触发。
     */
    private void autoPlayLoopAnimations() {
        ModelIndex index = new ModelIndex("part", variant.getAnimations());
        OAnimationSet animSet = OAnimationSet.getORIGINS().get(index);
        if (animController.isPlayingAnim() || animSet == null || animSet.getAnimations().isEmpty()) return;
        for (Map.Entry<String, OAnimation> entry : animSet.getAnimations().entrySet()) {
            if (entry.getValue().getLoop() != Loop.TRUE) continue;
            AnimInstance instance = new AnimInstance(this, new AnimIndex(index, entry.getKey()));
            instance.enter();
        }
    }

    /**
     * 播放指定名称的动画（仅客户端）。
     * <p>动画取自变体自身的动画集，播放于 {@link AnimGroups#ACTION} 动作覆盖层。</p>
     * <p><b>重触发粒度：</b>仅退出 ACTION 层中<b>同名</b>的旧实例后再进入，
     * 而非清空整层。这样同一零件上的多个事件动画（如一门机炮 + 一挺机枪共用同一 AbstractPart）
     * 只要动画名不同即可同时播放、互不打断；同名则从 0 重启。</p>
     * <p>循环行为由动画自身的 {@code loop} 设置决定：缺省 ONCE 播完自停；
     * 作者写 {@code loop:true} 则持续循环，需自行负责停止。</p>
     *
     * @param animName 动画名（对应 variant 动画集中的键）
     */
    public void playAnim(String animName) {
        if (!level.isClientSide()) return;
        ModelIndex index = new ModelIndex("part", variant.getAnimations());
        OAnimationSet set = OAnimationSet.getOrEmpty(index);
        OAnimation target = set.getAnimation(animName);
        if (target == null) {
            MachineMax.LOGGER.warn("[AbstractPart {}-{}] 未找到动画 {}", name, uuid, animName);
            return;
        }
        // 按名精确退出：只打断同一动画的旧实例，保留同层其他事件动画
        AnimLayer layer = animController.getLayers().get(AnimGroups.ACTION);
        if (layer != null) {
            for (AnimInstance old : new ArrayList<>(layer.getAnimations())) {
                if (old.getOrigin() == target) old.exit();
            }
        }
        AnimInstance instance = new AnimInstance(this, new AnimIndex(index, animName));
        instance.setGroup(AnimGroups.ACTION);
        // 事件动画（开火/后座）需要即时响应：AnimInstance 默认 inTransitionTime = 0.15s，
        // 权重从 0 线性爬升会导致后坐位移缓慢淡入，高射速重触发时永远爬不满权重。
        // 置 0 使实例进入后第一个物理子步即达满权重。
        instance.setInTransitionTime(0f);
        instance.enter();
    }

    /**
     * 判断本零件的动画集中是否存在指定名称的动画（不产生日志）。
     * <p>供高频事件触发方在进入前校验，避免动画缺失时按射速刷屏告警。</p>
     *
     * @param animName 动画名
     * @return true 表示动画存在
     */
    public boolean hasAnim(String animName) {
        ModelIndex index = new ModelIndex("part", variant.getAnimations());
        return OAnimationSet.getOrEmpty(index).hasAnimation(animName);
    }

    /**
     * 应用指定纹理（纯本地状态变更，不广播；广播由调用方负责）。
     *
     * @param name 纹理名
     */
    public void applyTexture(String name) {
        if (variant.getTextures().size() == 1) return;
        this.textureName = name;
        this.getModelController().setTextureLocation(variant.getTexture(name));
    }

    /**
     * 物理步结束后的扩展钩子；公共基础默认无操作，由装配体按物理生命周期调用。
     */
    public void onPostPhysicsTick() {
    }

    /**
     * 汇总所有子零件当前持有的子系统，返回独立的去重集合。
     *
     * @return 集合结构与内部索引分离；其中的子系统对象仍为运行时实例
     */
    public Set<AbstractSubsystem> getAllSubsystems() {
        HashSet<AbstractSubsystem> subsystems = new HashSet<>();
        for (SubPart subPart : subParts.values()) {
            subsystems.addAll(subPart.subsystems.values());
        }
        return subsystems;
    }

    /**
     * 汇总各子零件对共享耐久上限的贡献；未启用共享耐久或没有子零件时返回零。
     */
    public float getSharedMaxDurability() {
        float result = 0;
        if (type.shareDurability && !subParts.isEmpty()) {
            for (SubPart subPart : subParts.values()) {
                result += subPart.getSharedMaxDurability();
            }
        }
        return result;
    }

    /**
     * <p>获取共享耐久值。</p>
     * <p>共享耐久语义：同一部件下所有 SubPart 共用同一个耐久值，上限为各 SubPart 耐久上限之和；
     * 该值以任意一个 SubPart 的原始耐久值为权威来源，其余 SubPart 由
     * {@link #setSharedDurability(float)} 同步写入。</p>
     *
     * @return 共享耐久值；非共享部件返回 0
     */
    public float getSharedDurability() {
        if (!type.shareDurability || subParts.isEmpty()) return 0f;
        return subParts.values().iterator().next().getRawDurability();
    }

    /**
     * <p>设置共享耐久值：写入本部件全部 SubPart 的原始耐久值。</p>
     * <p>统一钳制到 [0, 共享上限 × 类型提供的耐久许可比例]，与
     * {@link SubPart#setRawDurability(float)} 的钳制口径保持一致。</p>
     *
     * @param durability 目标共享耐久值
     */
    public void setSharedDurability(float durability) {
        if (!type.shareDurability || subParts.isEmpty()) return;
        float cap = getSharedMaxDurability() * durabilityLimitRatio();
        float value = Math.clamp(durability, 0f, cap);
        for (SubPart subPart : subParts.values()) {
            subPart.setRawDurability(value);
        }
    }


    /**
     * 构造指定子零件的子系统，并同时建立部件级按名查询索引。
     * <p>仅在结构构造期间执行；同名索引按写入顺序覆盖，调用方应避免跨子零件重名。</p>
     */
    private void createSubsystems(
            SubPart subPart,
            Map<String, AbstractSubsystemAttr> subSystemAttrMap
    ) {
        for (Map.Entry<String, AbstractSubsystemAttr> entry : subSystemAttrMap.entrySet()) {
            String name = entry.getKey();
            AbstractSubsystemAttr attr = entry.getValue();
            AbstractSubsystem subsystem = attr.createSubsystem(subPart, name);
            subPart.subsystems.put(name, subsystem);//部件内的子系统
            subsystemsByName.put(name, subsystem);//AbstractPart 级按名索引
        }
    }

    /**
     * 按定位器构造连接器，填充子零件表、部件全量表、外部表及按名索引。
     * <p>缺少配置要求的定位器时立即抛出异常，由结构构造入口清理已分配资源。</p>
     */
    private void createConnectors(
            SubPart subPart,
            SubPartAttr subPartAttr
    ) {
        for (Map.Entry<String, ConnectorAttr> connectorEntry : subPartAttr.connectors.entrySet()) {
            String connectorName = connectorEntry.getKey();
            ConnectorAttr connectorAttr = connectorEntry.getValue();
            if (subPartAttr.getLocatorTransforms().containsKey(connectorAttr.locatorName)) {//若找到了对应的零件连接点Locator
                AbstractConnector connector;
                if (connectorAttr.isSimpleConnector()) {
                    connector = new SimpleConnector(
                            connectorName,
                            connectorAttr,
                            subPart,
                            subPartAttr.getLocatorTransforms().get(connectorAttr.locatorName)
                    );
                } else {
                    connector = new AdvancedConnector(
                            connectorName,
                            connectorAttr,
                            subPart,
                            subPartAttr.getLocatorTransforms().get(connectorAttr.locatorName)
                    );
                }
                subPart.connectors.put(connectorName, connector);
                this.connectorsByName.put(connectorName, connector);//AbstractPart 级按名索引
                this.allConnectors.put(Pair.of(subPart.name, connectorName), connector);
                if (!connector.internal) this.externalConnectors.put(Pair.of(subPart.name, connectorName), connector);
            } else
                throw new IllegalArgumentException(
                        MMLanguageRegistry.ERROR_PART_CONNECTOR_LOCATOR_NOT_FOUND
                                .component(variant.getModel().toString(),
                                        TextUtil.getTranslation(connectorName),
                                        TextUtil.getTranslation(connectorAttr.locatorName))
                                .getString());
        }
    }

    /**
     * 计算子零件沿三轴的投影面积，供阻力等计算使用。
     *
     * <p>逐轴优先使用配置的正数面积；缺省轴由碰撞形状的轴对齐包围盒估算。</p>
     *
     * @param subPart 要测量的子零件
     * @return X、Y、Z 三个方向的投影面积，单位为平方米
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

    /**
     * 生成包含子零件名和连接点名的诊断键，供内部连接失败日志定位。
     */
    private static String toConnectorKey(AbstractConnector connector) {
        return connector.subPart.name + "." + connector.name;
    }

    /**
     * 在尚未入世的结构中匹配并连接内部连接点。
     * <p>候选必须是一对高级与简单连接器，来自不同子零件，且双向条件和几何对齐均通过。
     * 一个连接点命中多个候选时跳过所有涉及它的配对，并记录冲突；无匹配与连接失败也记日志。</p>
     */
    private void autoAttachInternalConnectors() {
        List<AbstractConnector> internalConnectors = new ArrayList<>();
        for (SubPart subPart : subParts.values()) {
            for (AbstractConnector connector : subPart.connectors.values()) {
                if (connector.internal && !connector.hasPart()) {
                    internalConnectors.add(connector);
                }
            }
        }
        if (internalConnectors.isEmpty()) return;

        Map<AbstractConnector, Integer> matchCount = new HashMap<>();
        for (AbstractConnector connector : internalConnectors) {
            matchCount.put(connector, 0);
        }
        List<Pair<AbstractConnector, SimpleConnector>> feasiblePairs = new ArrayList<>();

        for (int i = 0; i < internalConnectors.size(); i++) {
            AbstractConnector connector1 = internalConnectors.get(i);
            for (int j = i + 1; j < internalConnectors.size(); j++) {
                AbstractConnector connector2 = internalConnectors.get(j);
                AbstractConnector advancedConnector;
                SimpleConnector simpleConnector;
                if (connector1 instanceof AdvancedConnector && connector2 instanceof SimpleConnector) {
                    advancedConnector = connector1;
                    simpleConnector = (SimpleConnector) connector2;
                } else if (connector2 instanceof AdvancedConnector && connector1 instanceof SimpleConnector) {
                    advancedConnector = connector2;
                    simpleConnector = (SimpleConnector) connector1;
                } else {
                    continue;
                }
                // 跳过条件不符或位于同一个子零件内的连接点
                if (!advancedConnector.conditionCheck(simpleConnector.subPart.part)
                        || !simpleConnector.conditionCheck(advancedConnector.subPart.part)
                        || simpleConnector.subPart == advancedConnector.subPart) {
                    continue;
                }
                // 跳过几何位置或方向不匹配的连接点
                if (!ConnectorAlignmentHelper.isAlignedForAttach(
                        advancedConnector,
                        simpleConnector,
                        ConnectorAlignmentHelper.DEFAULT_MAX_POS_ERROR,
                        ConnectorAlignmentHelper.DEFAULT_MAX_DIRECTION_ERROR
                )) {
                    continue;
                }

                feasiblePairs.add(Pair.of(advancedConnector, simpleConnector));
                matchCount.put(advancedConnector, matchCount.get(advancedConnector) + 1);
                matchCount.put(simpleConnector, matchCount.get(simpleConnector) + 1);
            }
        }

        Set<AbstractConnector> conflictedConnectors = new HashSet<>();
        List<String> conflictNames = new ArrayList<>();
        List<String> unmatchedNames = new ArrayList<>();
        for (AbstractConnector connector : internalConnectors) {
            int count = matchCount.getOrDefault(connector, 0);
            if (count > 1) {
                conflictedConnectors.add(connector);
                conflictNames.add(toConnectorKey(connector) + "(" + count + ")");
            } else if (count == 0) {
                unmatchedNames.add(toConnectorKey(connector));
            }
        }

        if (!conflictNames.isEmpty()) {
            MachineMax.LOGGER.warn(
                    "部件{}({})内部连接点存在多个可行候选，已跳过冲突连接点: {}",
                    this.name,
                    this.uuid,
                    String.join(", ", conflictNames)
            );
        }
        if (!unmatchedNames.isEmpty()) {
            MachineMax.LOGGER.warn(
                    "部件{}({})内部连接点未找到可行候选: {}",
                    this.name,
                    this.uuid,
                    String.join(", ", unmatchedNames)
            );
        }

        for (Pair<AbstractConnector, SimpleConnector> pair : feasiblePairs) {
            AbstractConnector advancedConnector = pair.getFirst();
            SimpleConnector simpleConnector = pair.getSecond();
            if (conflictedConnectors.contains(advancedConnector) || conflictedConnectors.contains(simpleConnector)) {
                continue;
            }
            if (advancedConnector.hasPart() || simpleConnector.hasPart()) {
                continue;
            }
            advancedConnector.alignActualTransformForJoint(simpleConnector);
            boolean attached = advancedConnector.attach(simpleConnector);
            if (!attached) {
                MachineMax.LOGGER.warn(
                        "部件{}({})内部连接点连接失败: {} <-> {}",
                        this.name,
                        this.uuid,
                        toConnectorKey(advancedConnector),
                        toConnectorKey(simpleConnector)
                );
            }
        }
    }

    /**
     * 构造子零件、连接器、子系统和命中框，再布放并连接内部关节。
     * <p>依赖有效且非空的变体定义。先选根子零件作为相对布放锚点，初始化根耐久，再设置其他子零件耐久；
     * 这期间会调用具体种类的耐久策略，子类字段必须已经就绪。</p>
     * @return 建成结构的根子零件
     */
    private SubPart createSubParts(Map<String, SubPartAttr> subPartAttrMap) {
        //创建零件
        for (Map.Entry<String, SubPartAttr> subPartEntry : subPartAttrMap.entrySet()) {//遍历部件的零件属性
            String name = subPartEntry.getKey();
            SubPartAttr subPartAttr = subPartEntry.getValue();
            SubPart subPart = new SubPart(name, this, subPartAttr);//创建零件
            Map<String, OBone> bones = subPartAttr.getBones(variant);//获取属于该零件的骨骼
            LinkedHashMap<String, OLocator> locators = LinkedHashMap.newLinkedHashMap(0);
            for (OBone bone : bones.values()) locators.putAll(bone.getLocators());//从模型获取所有定位器

            subParts.put(name, subPart);//将零件放入部件的零件表

            subPart.body.setMass(subPartAttr.mass > 0 ? subPartAttr.mass : 20);//设置质量

            subPart.body.setCcdSweptSphereRadius(subPart.collisionShape.maxRadius());//设置CCD半径
            subPart.projectedArea = calculateProjectedArea(subPart);
            //创建零件连接点
            createConnectors(subPart, subPartAttr);
            //创建部件内子系统
            createSubsystems(subPart, subPartAttr.subsystems);//创建子系统，赋予部件实际功能
            //创建命中判定区属性并匹配对应子系统(内部实现)
            Vector3f wheelParam = null;
            for (Map.Entry<String, HitBoxAttr> entry : subPart.attr.hitBoxes.entrySet()) {
                String boneName = entry.getKey();
                HitBoxAttr hitBoxAttr = entry.getValue();
                subPart.hitBoxes.put(boneName, new HitBox(subPart, hitBoxAttr));
                if (hitBoxAttr.shapeType.equals("wheel") && bones.containsKey(boneName)) {
                    wheelParam = PhysicsHelperKt.toBVector3f(bones.get(boneName).getCubes().getFirst().getSize().toVector3f());
                }
            }
            if (wheelParam != null) { // 若此刚体包含轮胎类型的碰撞体积，则根据其数据修正转动惯量，确保行驶平稳
                float mass = subPart.body.getMass();
                float radius = wheelParam.y / 2;
                float width = wheelParam.x;
                float Ix = 0.5f * mass * radius * radius;
                float Iyz = (1.0f / 12.0f) * mass * (3 * radius * radius + width * width);
                subPart.body.setInverseInertiaLocal(new Vector3f(1.0f / Ix, 1.0f / Iyz, 1.0f / Iyz));
            }
        }

        // 提前选出 rootSubPart（质量最大者），作为其余 SubPart 布放的基准
        SubPart rootSubPart = selectRootSubPart();
        rootSubPart.setDurability(rootSubPart.getMaxDurability());

        // 将非 rootSubPart 按模型中的相对质心位置偏移到正确位置，再连接内部关节
        OModel model = OModel.getORIGINS().get(new ModelIndex("part", variant.getModel()));
        positionSubPartsForInternalAttach(model, rootSubPart);
        autoAttachInternalConnectors();

        // 设置其余 SubPart 的初始耐久度
        for (SubPart subPart : subParts.values()) {
            if (subPart != rootSubPart) {
                subPart.setDurability(subPart.getMaxDurability());
            }
        }
        return rootSubPart;
    }

    /**
     * <p>选出根子部件（质量最大者）。</p>
     * <p>与预览动画体 {@link io.github.sweetzonzi.machine_max.common.visual.PartAnimatable} 共用
     * {@link VariantAttr#getRootSubPartName()} 的判定结果，确保布放/预览锚点一致。</p>
     */
    private SubPart selectRootSubPart() {
        SubPart root = subParts.get(variant.getRootSubPartName());
        // 理论上不会为 null；兜底取第一个，避免空指针
        return root != null ? root : subParts.values().iterator().next();
    }

    /**
     * <p>以 rootSubPart 的全局模型空间质心为基准，将其余 SubPart 的刚体移动到正确的相对位置。</p>
     * <p>布放完成后各 SubPart 在世界空间中的相对位置与模型中一致，内部连接点的世界空间位姿自然对齐。</p>
     *
     * @param model       部件模型
     * @param rootSubPart 根子部件（保持在原位不动）
     */
    private void positionSubPartsForInternalAttach(OModel model, SubPart rootSubPart) {
        Vector3f rootGlobalMc = computeSubPartGlobalMassCenter(rootSubPart, model);

        for (SubPart subPart : subParts.values()) {
            if (subPart == rootSubPart) continue;
            Vector3f globalMc = computeSubPartGlobalMassCenter(subPart, model);
            Vector3f offset = globalMc.subtract(rootGlobalMc);
            // 移动刚体（构造期尚未加入物理世界，直接操作安全）
            subPart.body.setPhysicsLocation(subPart.body.getPhysicsLocation(null).add(offset));
            // 同步 Transform 缓存
            Transform transform = subPart.body.getTransform(null);
            subPart.transform = transform.clone();
            subPart.oldTransform = transform.clone();
        }
    }

    /**
     * <p>计算 SubPart 质心在全局模型空间中的位置。</p>
     * <p>优先使用 mass_center locator 的全局位姿；若 locator 不存在则回退到
     * massCenterTransform（startBone 空间）+ startBone 全局变换。</p>
     *
     * @param subPart 子部件
     * @param model   部件模型
     * @return 质心在全局模型空间中的位置
     */
    private Vector3f computeSubPartGlobalMassCenter(SubPart subPart, OModel model) {
        // 统一走 SubPartAttr 的实现，保证与预览动画体（SubPartAnimatable）的布放算法一致
        return subPart.attr.computeGlobalMassCenter(model);
    }


    /**
     * 按所选变体的子零件配置汇总部件设计质量。
     *
     * <p>结果写入 {@link #totalMass}；本方法不修改刚体质量，也不按制造进度缩放质量。
     * 装配体总质量需要由宿主自己的汇总流程更新。</p>
     */
    public void updateMass() {
        float totalMass = 0;
        for (SubPartAttr subPart : type.getVariants().get(variantName).getSubParts().values()) {
            totalMass += subPart.mass;
        }
        this.totalMass = totalMass;
    }

    /**
     * 将各子零件加入世界，开始参与物理运算。
     *
     * <p>由主线程的宿主入世流程调用；要求结构已初始化并已设置装配体归属，具体物理任务由子零件入世流程安排。</p>
     */
    public void addToLevel() {
        for (SubPart subPart : subParts.values()) subPart.addToLevel();
    }

    /**
     * 执行全部子零件的正常销毁流程，处理子系统拆离与关联资源。
     *
     * <p>由主线程生命周期调用。构造失败应使用 {@link #discardUnattached()}，避免触发正常拆离的库存掉落等副作用。
     * 装配体注册表及部件拓扑的移除由宿主管理。</p>
     */
    public void destroy() {
        for (SubPart subPart : subParts.values()) subPart.destroy();
    }

    /**
     * 以目标根位姿整体移动部件，保留各子零件相对根的布局。
     * <p>尚未归属装配体或装配体未入世时直接执行；入世后提交到 PRE 物理阶段。
     * 该方法更新物理位姿及插值缓存，不负责改变线速度或角速度。</p>
     */
    public void setTransform(Transform transform) {
        if (assembly == null || !assembly.isInLevel()) {
            setTransformRaw(transform);
        } else SparkLevel.getPhysicsLevel(level).submitImmediateTask(PPhase.PRE, () -> {
            setTransformRaw(transform);
            return null;
        });
    }

    /**
     * 在独占构造期或物理线程应用根位姿，按根的相对变换更新其他子零件。
     * <p>物理变换强制单位缩放，避免复合碰撞形状拒绝浮点运算产生的非单位比例。</p>
     */
    private void setTransformRaw(Transform transform) {
        Transform rootTransform = rootSubPart.body.getTransform(null).invert();
        rootSubPart.setPosition(transform.getTranslation());
        rootSubPart.setRotation(transform.getRotation());
        // 碰撞形状（CompoundCollisionShape）不支持缩放，矩阵运算产生的浮点误差（如 0.9999998）会导致 setPhysicsTransform 抛异常，故物理变换强制 scale=1
        Transform rootPhysics = transform.clone();
        rootPhysics.setScale(Vector3f.UNIT_XYZ);
        rootSubPart.transform = rootPhysics.clone();
        rootSubPart.oldTransform = rootPhysics.clone();
        rootSubPart.body.setPhysicsTransform(rootPhysics);
        PhysicsBodyExtensionKt.stateOf(rootSubPart.body).setTransform(rootPhysics);
        PhysicsBodyExtensionKt.stateOf(rootSubPart.body).setLastTransform(rootPhysics);
        Transform subPartTransform = new Transform();
        for (SubPart subPart : subParts.values()) {
            if (subPart == rootSubPart) continue;
            subPart.body.getTransform(subPartTransform);
            MyMath.combine(subPartTransform, rootTransform, subPartTransform);
            MyMath.combine(subPartTransform, transform, subPartTransform);
            subPartTransform.setScale(Vector3f.UNIT_XYZ);
            subPart.setPosition(subPartTransform.getTranslation());
            subPart.setRotation(subPartTransform.getRotation());
            subPart.transform = subPartTransform.clone();
            subPart.oldTransform = subPartTransform.clone();
            subPart.body.setPhysicsTransform(subPartTransform);
            PhysicsBodyExtensionKt.stateOf(subPart.body).setTransform(subPartTransform);
            PhysicsBodyExtensionKt.stateOf(subPart.body).setLastTransform(subPartTransform);
        }
    }

    // ======================== IAnimatable<AbstractPart<?>> 实现 ========================

    /**
     * 返回当前部件自身，作为模型与动画控制器共同持有的动画对象。
     */
    @Override
    public AbstractPart<?> getAnimatable() {
        return this;
    }

    /**
     * 返回动画与物理实例共用的所属世界。
     */
    @Override
    public Level getAnimLevel() {
        return level;
    }

    /**
     * 按当前变体模型资源名构造动画引擎使用的模型索引，固定属于 part 模型类型。
     */
    @Override
    public final ModelIndex getDefaultModelIndex() {
        return new ModelIndex("part", variant.getModel());
    }

    /**
     * 获取本部件整模型的全部骨骼（供后续关节采样等使用）。
     * <p>注意：渲染所需的骨骼子树过滤仍由 {@link SubPart#getBones()} 提供。</p>
     */
    public Map<String, OBone> getBones() {
        return OModel.getOrEmpty(new ModelIndex("part", variant.getModel())).getBones();
    }

    /**
     * 获取 AbstractPart 级 Molang 上下文（不做 reset，供编译器获取原型）。
     */
    @Override
    public @NotNull MechMolangContext getMolangContext() {
        return molangContext;
    }

    /**
     * 获取已 reset 到当前 AbstractPart 状态的 Molang 上下文，用于表达式求值。
     */
    public SparkMolangContext<IAnimatable<AbstractPart<?>>> getSparkMolangContext() {
        molangContext.reset(this, 0);
        return molangContext;
    }

    /**
     * 返回部件级信号存储作为动画变量表；与信号接收入口共用同一个并发映射。
     */
    @Override
    public @NotNull Map<@NotNull String, @NotNull Object> getVariables() {
        return signalStorage;
    }

    /**
     * 以根子零件的插值位姿作为整个部件的世界矩阵；要求结构已初始化。
     */
    @Override
    public Matrix4f getWorldPositionMatrix(@NotNull Number partialTicks) {
        return rootSubPart.getWorldPositionMatrix(partialTicks);
    }

    /**
     * 返回根子零件在给定插值时刻的渲染位置；要求结构已初始化。
     */
    @Override
    public Vec3 getRenderPosition(Number partialTicks) {
        return rootSubPart.getRenderPosition(partialTicks);
    }

    // ======================== ISignalReceiver 实现 ========================

    /** AbstractPart 在信号路由中的保留地址：{@code "local"} 表示当前动画体（AbstractPart） */
    @Override
    public String getSignalAddress() {
        return "local";
    }

    /**
     * 返回部件级信号值映射，与动画变量表共享存储。
     */
    @Override
    public ConcurrentMap<String, Object> getSignalStorage() {
        return signalStorage;
    }

}
