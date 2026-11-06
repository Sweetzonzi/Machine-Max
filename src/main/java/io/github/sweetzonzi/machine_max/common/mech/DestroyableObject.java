package io.github.sweetzonzi.machine_max.common.mech;

import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.datafixers.util.Pair;
import io.github.sweetzonzi.machine_max.common.MMServerConfig;
import io.github.sweetzonzi.machine_max.network.payload.SubPartSyncPayload;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageContext;
import io.github.sweetzonzi.ballistics_framework.api.BFHurtTarget;
import jme3utilities.math.MyQuaternion;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SyncedDataHolder;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
public abstract class DestroyableObject implements SyncedDataHolder, BFHurtTarget {
    protected static final AtomicInteger ENTITY_COUNTER = new AtomicInteger();
    //渲染属性 Renderer attributes
    public int hurtTime = 0;
    public int hurtDuration = 3;
    //物理属性 Physical attributes
    public volatile Transform transform = new Transform();
    public volatile Transform oldTransform = new Transform();//用于渲染插值
    public volatile int lastSync;//记录上次同步时刻，用于外推
    /**
     * 客户端呈现位姿的抖动缓冲；服务端为 {@code null}。
     * <p>
     * 服务端按 20 Hz 发位姿、客户端按 20 Hz 走 tick，两者相位不同步，某些 tick 里一个包都没有、
     * 另一些 tick 里挤进两个。把 {@code oldTransform}/{@code transform} 这一对按「包到没到」重建，
     * 就会出现「冻结一帧 + 下一帧补两倍」的拍频；本缓冲让这一对准按<b>客户端 tick</b>前进，
     * 与到达事件无关。取舍与三档余量的含义见 {@link PoseDelayBuffer}。
     */
    private final PoseDelayBuffer presentationPose;
    /**
     * 呈现样对是否已由 {@link #advancePresentationPose()} 写过至少一次。
     * <p>
     * 服务端恒为 {@code false}（那里没有缓冲、不推进）；客户端在首个同步包落进缓冲并被推进之后置 {@code true}。
     * {@link #getPresentationPosition()} 与 {@link #getPresentationRotation()} 靠它区分「呈现位姿」与
     * 「还没有呈现位姿」，而不是靠 {@code transform} 里有没有值：那个字段的初值是单位变换，代表世界原点，
     * 不是这个对象的位姿。
     */
    private volatile boolean presentationReady;
    /** 稳态余量（样本数）；{@code 1} 即呈现落后最新样本 1～2 tick */
    public static final int PRESENTATION_SPARE = 1;
    protected static final EntityDataAccessor<org.joml.Vector3f> DATA_POS_ID = SynchedEntityData.defineId(DestroyableObject.class, EntityDataSerializers.VECTOR3);
    protected static final EntityDataAccessor<Quaternionf> DATA_ROT_ID = SynchedEntityData.defineId(DestroyableObject.class, EntityDataSerializers.QUATERNION);
    protected static final EntityDataAccessor<org.joml.Vector3f> DATA_VEL_ID = SynchedEntityData.defineId(DestroyableObject.class, EntityDataSerializers.VECTOR3);
    protected static final EntityDataAccessor<org.joml.Vector3f> DATA_ANG_VEL_ID = SynchedEntityData.defineId(DestroyableObject.class, EntityDataSerializers.VECTOR3);
    //常规属性 General attributes
    public final Level level;
    @Setter
    private int id = ENTITY_COUNTER.incrementAndGet();//客户端的ID应当根据收到的创建包更新
    public static final EntityDataAccessor<Float> DATA_DURABILITY_ID = SynchedEntityData.defineId(DestroyableObject.class, EntityDataSerializers.FLOAT);
    protected static final EntityDataAccessor<Boolean> DATA_DESTROYED_ID = SynchedEntityData.defineId(DestroyableObject.class, EntityDataSerializers.BOOLEAN);
    protected static final EntityDataAccessor<Integer> DESTROY_TIME_ID = SynchedEntityData.defineId(DestroyableObject.class, EntityDataSerializers.INT);
    protected ConcurrentLinkedQueue<Pair<Float, BFDamageContext>> accumulatedDamage = new ConcurrentLinkedQueue<>();
    protected final SynchedEntityData syncedData;
    //运行中
    public int tickCount = 0;
    public int physicsTickCount = 0;
    public volatile boolean isRemoved = false;

    protected DestroyableObject(Level level) {
        this.level = level;
        this.presentationPose = level.isClientSide() && usesPresentationDelay()
                ? new PoseDelayBuffer(PRESENTATION_SPARE, PoseDelayBuffer.DEFAULT_SNAP_DISTANCE)
                : null;
        SynchedEntityData.Builder syncheddata$builder = new SynchedEntityData.Builder(this);
        syncheddata$builder.define(DATA_POS_ID, new org.joml.Vector3f());
        syncheddata$builder.define(DATA_ROT_ID, new Quaternionf());
        syncheddata$builder.define(DATA_VEL_ID, new org.joml.Vector3f());
        syncheddata$builder.define(DATA_ANG_VEL_ID, new org.joml.Vector3f());
        syncheddata$builder.define(DATA_DURABILITY_ID, 20.0F);
        syncheddata$builder.define(DATA_DESTROYED_ID, false);
        syncheddata$builder.define(DESTROY_TIME_ID, 0);
        this.defineSyncedData(syncheddata$builder);
        this.syncedData = syncheddata$builder.build();
    }

    public void preTick() {
        if (isRemoved) return;
        tickCount++;
        if (hurtTime > 0) hurtTime--;
        if (level.isClientSide()) {
            //客户端处理同步位姿数据
            clientSyncPose();
        }
    }

    public void postTick() {
        if (!level.isClientSide()) {
            settleAccumulatedDamage(); // 结算各线程造成的伤害
            //判定摧毁：紧跟结算，否则本 tick 打到 0 的耐久要等下一 tick 才被标记
            if (checkDestroyed()) setDestroyed();
            if (removesOnCountdownExpiry() && isDestroyed()) { //物体已被摧毁，倒计时结束后移除
                tickDestroyTimer(1);
            }
            syncToClient(); // 同步数据至客户端；晚于结算，使本次耐久变化在同一 tick 内可见
        }
        if (removesOnCountdownExpiry() && isDestroyed() && getDestroyTime() <= 0) this.destroy();
    }

    /**
     * 判断倒计时归零时是否由本对象自行移除。
     *
     * <p>子零件返回 {@code false}：它的倒计时是所属部件的网络镜像，移除时机由部件整体决定。
     * 其他对象保持默认的自行移除行为。</p>
     */
    protected boolean removesOnCountdownExpiry() {
        return true;
    }

    /***
     * 销毁倒计时前进指定tick数
     * @param tick 步进的tick数
     */
    protected void tickDestroyTimer(int tick) {
        int destroyTime = getDestroyTime();
        if (destroyTime > 0) {
            setDestroyTime(destroyTime - tick);
        }
    }

    public void prePhysicsTick() {
        if (isRemoved) return;
        physicsTickCount++;
    }

    public void postPhysicsTick() {
    }

    public void accumulateDamage(float damage, BFDamageContext ctx) {
        if (damage > 0) {
            hurtTime = hurtDuration;
            accumulatedDamage.add(Pair.of(damage, ctx));
        }
    }

    public boolean isDestroyed() {
        return getSyncedData().get(DATA_DESTROYED_ID);
    }

    protected boolean checkDestroyed() {
        return !getSyncedData().get(DATA_DESTROYED_ID) && getDurability() <= 0;
    }

    protected int getInitialDestroyTimeTicks() {
        int ticksByDurability = Math.round(getMaxDurability() * MMServerConfig.getSubPartDestroyTicksPerDurability());
        return Math.max(ticksByDurability, MMServerConfig.getSubPartDestroyMinTicks());
    }

    /**
     * 标记为已摧毁；服务端首次标记时同时写入本对象自己的倒计时初值。
     *
     * <p>子零件覆写本方法：它的倒计时由所属部件统一写入，因此只置标记、不写自身初值。</p>
     */
    protected void setDestroyed() {
        if (!level.isClientSide() && !isDestroyed()) {
            setDestroyTime(getInitialDestroyTimeTicks());
        }
        markDestroyed();
    }

    /**
     * 只置摧毁标记，不改动倒计时。
     */
    protected final void markDestroyed() {
        getSyncedData().set(DATA_DESTROYED_ID, true);
    }

    /**
     * <p>结算各线程造成的伤害并相应对子系统造成伤害，在主线程中统一处理，参见 {@link DestroyableObject#postTick()}</p>
     * <p>Settles the damage caused by each thread and applies it to the subsystem, which will be handled in the main thread, see {@link DestroyableObject#postTick()}</p>
     */
    abstract protected void settleAccumulatedDamage();

    public void addToLevel() {
        ObjectManager.addDestroyableObject(this);
    }

    public void destroy() {
        isRemoved = true;
        ObjectManager.removeDestroyableObject(this);
    }

    /**
     * 把本 tick 需要同步的数据发给客户端。
     * <p>
     * 采用呈现缓冲的对象（{@link #usesPresentationDelay()}）<b>每 tick 都必带位姿字段</b>，缺哪个补哪个。
     * 客户端的缓冲按「每 tick 一个样本」播放，而样本只认「包里出现过位姿键」，不认「值有没有变」
     * （见 {@link #onSyncedDataUpdated(List)}）。于是判断必须落在<b>键</b>上：
     * <ul>
     *   <li>按「脏列表是否为空」判断不够——服务端 {@link #postTick()} 每 tick 都写 {@code DATA_VEL} 与
     *       {@code DATA_ANG_VEL}，于是一个只有速度在变的 tick 会发出包、却<strong>不含位姿</strong>；</li>
     *   <li>那种 tick 客户端不入队样本，缓冲挨饿；挨饿会把已经积累的呈现滞后永久吃掉一格
     *       （见 {@link PoseDelayBuffer#advance()}），此后不再补回，同一辆车上各对象的呈现滞后就逐渐分叉。</li>
     * </ul>
     * 代价是静止时每 tick 多一个只带位姿字段的小包；不采用呈现缓冲的对象（本地模拟的投射物）仍然
     * 只在有脏字段时发。
     */
    public void syncToClient() {
        if (level.isClientSide()) return;
        SynchedEntityData data = this.getSyncedData();
        List<SynchedEntityData.DataValue<?>> dirty = data.packDirty();
        List<SynchedEntityData.DataValue<?>> payload = dirty == null ? new ArrayList<>(2) : new ArrayList<>(dirty);
        if (usesPresentationDelay()) appendMissingPose(data, payload);
        if (!payload.isEmpty()) {
            PacketDistributor.sendToPlayersInDimension((ServerLevel) level, new SubPartSyncPayload(getId(), payload));
        }
    }

    /**
     * 把 {@code payload} 里缺的位姿键补上；两个都在就什么都不做。
     * <p>
     * 只补缺的那个：客户端每收到一个含位姿键的批次就入队一个样本，样本的位置与朝向都从<b>当时的同步值</b>
     * 读取，因此补一个就够，补两个只是多几个字节。
     */
    private static void appendMissingPose(SynchedEntityData data, List<SynchedEntityData.DataValue<?>> payload) {
        boolean hasPosition = false;
        boolean hasRotation = false;
        int positionId = DATA_POS_ID.id();
        int rotationId = DATA_ROT_ID.id();
        for (SynchedEntityData.DataValue<?> value : payload) {
            int id = value.id();
            if (id == positionId) hasPosition = true;
            else if (id == rotationId) hasRotation = true;
        }
        if (!hasPosition) payload.add(SynchedEntityData.DataValue.create(DATA_POS_ID, data.get(DATA_POS_ID)));
        if (!hasRotation) payload.add(SynchedEntityData.DataValue.create(DATA_ROT_ID, data.get(DATA_ROT_ID)));
    }

    protected void clientSyncPose() {
        lastSync = tickCount;
        // 客户端有抖动缓冲时样对由 advancePresentationPose 按 tick 推进，这里不再重建——
        // 否则就会退化成「按到达事件重建」，也就是本缓冲要消除的那个拍频
        if (presentationPose != null) return;
        oldTransform = transform.clone();
        transform = new Transform(getPosition(), getRotation());
    }

    /**
     * 本对象是否使用呈现位姿的抖动缓冲（见 {@link PoseDelayBuffer}）。
     * <p>
     * 默认 {@code true}：所有可毁对象都走「服务端 20 Hz 发包 + 客户端按 tick 播放」这条路，
     * 因此都吃到同一条时间轴上的延迟，相互之间的相对位置不受影响。
     * <p>
     * 唯一的例外是本地模拟的对象——它们的 {@code oldTransform}/{@code transform} 由别的代码每 tick
     * 整对重写，缓冲写进去的值既会被覆盖，又会把「上一对的一半」换成延迟样本。
     * 见 {@link io.github.sweetzonzi.machine_max.common.mech.projectile.BallisticProjectile#usesPresentationDelay()}。
     * <p>
     * <b>只在构造期读一次</b>（决定缓冲是否分配），运行期改返回值无效。
     */
    protected boolean usesPresentationDelay() {
        return true;
    }

    /**
     * 把一个客户端 tick 的时间推进到呈现位姿上，重建 {@code oldTransform}/{@code transform} 这一对。
     * <p>
     * <b>调用线程：客户端主线程，每 tick 恰好一次</b>，且在实体 tick 之前
     * （{@code net.neoforged.neoforge.client.event.ClientTickEvent.Pre}）。两个约束各有理由：
     * 每 tick 一次，插值量 {@code partialTick} 才有意义；早于实体 tick，
     * {@link net.minecraft.world.entity.Entity} 的 {@code xo/x} 这一对才会与模型用同一条时间轴
     * （相机、乘客座位、实体调试框都读实体位置）。
     * <p>
     * 服务端、以及缓冲区未建立的时刻都是 no-op（此时 {@link #getPresentationPosition()} 停在该回退上）。
     */
    public void advancePresentationPose() {
        PoseDelayBuffer buffer = this.presentationPose;
        if (buffer == null || isRemoved) return;
        if (!buffer.hasSample()) {
            // 还没有任何样本（刚入世）：直接按网络真值摆放。留在单位变换上会让模型与实体
            // 出现在世界原点，直到第一个包到达
            transform = new Transform(getPosition(), getRotation());
            oldTransform = transform.clone();
            presentationReady = true;
            onPresentationPoseAdvanced();
            return;
        }
        buffer.advance();
        Transform left = buffer.left();
        Transform right = buffer.right();
        // 两端各复制一份：调用方（渲染、遥测、相机）会跨 tick 持有引用，不能把缓冲槽交出去
        oldTransform = left.clone();
        transform = right.clone();
        presentationReady = true;
        onPresentationPoseAdvanced();
    }

    /**
     * 呈现位姿推进完成的回调，供子类把同一份位姿落到自己的镜像刚体上。
     * <p>
     * 见 {@link DestroyableRigidObject#onPresentationPoseAdvanced()}：客户端刚体只是呈现位的载体，
     * 必须与模型同一条时间轴，否则刚体调试框与实体调试框会各走各的。
     */
    protected void onPresentationPoseAdvanced() {
    }

    /**
     * 呈现位姿的平移，世界坐标，即呈现区间的右端（下一个 tick 会变成 {@code oldTransform}）。
     *
     * <p>返回 {@link Vec3}：不可变，调用方可以安全地跨 tick 持有引用，也不可能就地把模型位姿改坏。
     * 同一个值在本类里还有一份 jme 表示（{@code transform} 字段），选型按调用方所在的层分工：
     * 本方法面向实体摆放、载具形心、相机这类 Minecraft 侧代码；需要喂 jme 物理接口的写点
     * （{@code setPhysicsLocation} / {@code setPhysicsRotation} / {@code lerp(old, new, partial)}）
     * 读 {@code transform} 字段，那一路零拷贝零转换。
     *
     * <p>服务端、以及首个同步包被应用并推进之前退回 {@link #getPosition()}（网络真值）：
     * 服务端没有缓冲，{@code transform} 不按 tick 刷新；客户端在推进之前 {@code transform} 还停在
     * 单位变换上，取它只会得到世界原点。两侧因此都拿到有意义的位姿。
     *
     * <p><b>调用线程：</b>主线程；写入点是 {@link #advancePresentationPose()}（客户端 tick 前相位）。
     */
    public Vec3 getPresentationPosition() {
        if (!this.presentationReady) return SparkMathKt.toVec3(getPosition());
        Vector3f translation = this.transform.getTranslation();
        return new Vec3(translation.x, translation.y, translation.z);
    }

    /**
     * 呈现位姿的旋转，即呈现区间的右端。
     *
     * <p>返回 {@link Quaternionf}：消费者在 Minecraft 侧，渲染期的姿态是
     * {@code PoseStack#mulPose(Quaternionf)}，同步数据里的朝向是
     * {@code EntityDataSerializers.QUATERNION}（同样是 {@link Quaternionf}），所以这是这一侧的原生类型。
     * <b>它是可变类型</b>，因此这里交出的是一份拷贝；jme 侧同理读 {@code transform} 字段。
     *
     * <p>回退条件与 {@link #getPresentationPosition()} 相同：还没有呈现位姿时取 {@link #getRotation()}。
     */
    public Quaternionf getPresentationRotation() {
        return this.presentationReady
                ? SparkMathKt.toQuaternionf(this.transform.getRotation())
                : SparkMathKt.toQuaternionf(getRotation());
    }

    /**
     * 呈现缓冲累计停住的 tick 数：余量耗尽、显示停在最新样本上、这一 tick 没有前进。
     * <p>
     * 诊断用（录制进 CSV 的附加列）。它与 {@link #presentationReserve()} 一起回答「停帧是供给不足
     * 还是播放太快」：停住数持续增长而余量始终为 0，就是供给的样本比播放的 tick 少。
     *
     * @return 累计次数；服务端或该对象不启用呈现缓冲时为 0
     */
    public long presentationStarvedTicks() {
        PoseDelayBuffer buffer = this.presentationPose;
        return buffer == null ? 0L : buffer.starvedTicks();
    }

    /**
     * 呈现缓冲累计丢掉的样本数：积压超过 {@code spare + 2} 时向前跳、把延迟限回稳态值的累计量。
     *
     * @return 累计样本数；服务端或该对象不启用呈现缓冲时为 0
     */
    public long presentationSkippedSamples() {
        PoseDelayBuffer buffer = this.presentationPose;
        return buffer == null ? 0L : buffer.skippedSamples();
    }

    /**
     * 当前余量（样本数）：除了呈现区间右端之外还留着的样本数，{@code 0} 表示下一 tick 就会停住。
     *
     * @return 余量；服务端或该对象不启用呈现缓冲时为 {@code -1}
     */
    public long presentationReserve() {
        PoseDelayBuffer buffer = this.presentationPose;
        return buffer == null ? -1L : buffer.reserve();
    }

    /**
     * 整批同步数据应用完毕后回调一次，<b>恰好等价于「收到一个包」</b>。
     * <p>
     * 位姿样本从这里入队：一个包一个样本，因此样本序号就是服务端 tick 序号的镜像，
     * 缓冲才能按「每 tick 一个样本」的节奏播放。选整批回调而不是单字段回调，是因为
     * 位置与旋转在同一次应用里各自触发一次单字段回调，按后者入队会多出一个
     * 「新位置 + 旧旋转」的幻影样本。
     * <p>
     * 包内既没有位置也没有旋转时不入队：那样的包（耐久、休眠标记等）不带位姿，
     * 入队只会制造一个与上一个完全相同的样本，把呈现速度拉慢一帧。
     */
    @Override
    public void onSyncedDataUpdated(@NotNull List<SynchedEntityData.DataValue<?>> dataValues) {
        PoseDelayBuffer buffer = this.presentationPose;
        if (buffer == null) return;
        int positionId = DATA_POS_ID.id();
        int rotationId = DATA_ROT_ID.id();
        for (SynchedEntityData.DataValue<?> value : dataValues) {
            int id = value.id();
            if (id == positionId || id == rotationId) {
                buffer.push(getPosition(), getRotation());
                return;
            }
        }
    }

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> key) {
        if (!level.isClientSide()) return;//服务器在需同步数据变化时不做特殊处理
        if (key.equals(DATA_DURABILITY_ID)) {
            hurtTime = hurtDuration;
        } else if (key.equals(DATA_VEL_ID)) {
            Vector3f linearVelocity = PhysicsHelperKt.toBVector3f(getSyncedData().get(DATA_VEL_ID));
            this.setLinearVelocity(linearVelocity);//应用到刚体(若有)
        } else if (key.equals(DATA_ANG_VEL_ID)) {
            Vector3f angularVelocity = PhysicsHelperKt.toBVector3f(getSyncedData().get(DATA_ANG_VEL_ID));
            this.setAngularVelocity(angularVelocity);//应用到刚体(若有)
        } else if (key.equals(DATA_DESTROYED_ID)) {
            this.setDestroyed();
        }
    }

    protected abstract void defineSyncedData(SynchedEntityData.Builder builder);

    public Vector3f getPosition() {
        return PhysicsHelperKt.toBVector3f(getSyncedData().get(DATA_POS_ID));
    }

    public Quaternion getRotation() {
        return SparkMathKt.toBQuaternion(getSyncedData().get(DATA_ROT_ID));
    }

    public Quaternionf getQuaternionf() {
        return getSyncedData().get(DATA_ROT_ID);
    }

    public Vector3f getLinearVelocity() {
        return PhysicsHelperKt.toBVector3f(getSyncedData().get(DATA_VEL_ID));
    }

    public Vector3f getAngularVelocity() {
        return PhysicsHelperKt.toBVector3f(getSyncedData().get(DATA_ANG_VEL_ID));
    }

    public void setPosition(Vector3f position) {
        getSyncedData().set(DATA_POS_ID, SparkMathKt.toVector3f(position));
    }

    public void setRotation(Quaternion rotation) {
        getSyncedData().set(DATA_ROT_ID, SparkMathKt.toQuaternionf(rotation));
    }

    public void setLinearVelocity(Vector3f linearVelocity) {
        getSyncedData().set(DATA_VEL_ID, SparkMathKt.toVector3f(linearVelocity));
    }

    public void setAngularVelocity(Vector3f angularVelocity) {
        getSyncedData().set(DATA_ANG_VEL_ID, SparkMathKt.toVector3f(angularVelocity));
    }

    public Vector3f getLinearVelocityLocal() {
        Vector3f result = getLinearVelocity();//获取物体质心在世界坐标系下的线速度
        Quaternion worldToLocal = getRotation(); //获取物体相对世界坐标的四元数
        MyQuaternion.rotateInverse(worldToLocal, result, result);//旋转世界坐标系向量到刚体自身坐标系
        return result;
    }

    public Vector3f getAngularVelocityLocal() {
        Vector3f result = getAngularVelocity();//获取物体在世界坐标系下的角速度
        Quaternion worldToLocal = getRotation(); //获取物体相对世界坐标的四元数
        MyQuaternion.rotateInverse(worldToLocal, result, result);//旋转世界坐标系向量到刚体自身坐标系
        return result;
    }

    public Vector3f getFrontVector() {
        return PhysicsHelperKt.toBVector3f(getQuaternionf().transform(new org.joml.Vector3f(0, 0, -1)));
    }

    public Vector3f getUpVector() {
        return PhysicsHelperKt.toBVector3f(getQuaternionf().transform(new org.joml.Vector3f(0, 1, 0)));
    }

    public Vector3f getRightVector() {
        return PhysicsHelperKt.toBVector3f(getQuaternionf().transform(new org.joml.Vector3f(1, 0, 0)));
    }

    /**
     * <p>基于前向量计算pitch角（俯仰角）</p>
     * <p>pitch角表示物体前后倾斜的角度，范围[-90°, 90°]</p>
     * <p>基于前向量在y轴上的投影计算，避免旋转顺序问题</p>
     *
     * @return pitch角（弧度）
     */
    public float getPitch() {
        Vector3f frontVector = getFrontVector();
        // pitch = arcsin(前向量的y分量)
        return (float) Math.asin(frontVector.y);
    }

    /**
     * <p>基于前向量计算yaw角（偏航角）</p>
     * <p>yaw角表示物体左右旋转的角度，范围[-180°, 180°]</p>
     * <p>基于前向量在xz平面上的投影计算，避免旋转顺序问题</p>
     *
     * @return yaw角（弧度）
     */
    public float getYaw() {
        Vector3f frontVector = getFrontVector();
        // yaw = atan2(前向量的x分量, 前向量的z分量)
        return (float) Math.atan2(frontVector.x, -frontVector.z);
    }

    /**
     * <p>基于右向量和上向量计算roll角（滚转角）</p>
     * <p>roll角表示物体绕前向轴旋转的角度，范围[-180°, 180°]</p>
     * <p>基于右向量在世界坐标系上向量上的投影计算，避免旋转顺序问题</p>
     *
     * @return roll角（弧度）
     */
    public float getRoll() {
        Vector3f rightVector = getRightVector();
        // 计算右向量在世界坐标系上向量(0,1,0)上的投影
        // roll = atan2(右向量的y分量, 右向量在xz平面上的长度)
        float rightVectorLengthXZ = (float) Math.sqrt(rightVector.x * rightVector.x + rightVector.z * rightVector.z);
        return (float) Math.atan2(rightVector.y, rightVectorLengthXZ);
    }

    /**
     * 获取质心在世界坐标系下的位姿变换矩阵
     *
     * <p>在 {@code oldTransform} 与 {@code transform} 之间线性插值。客户端这两端由
     * {@link #advancePresentationPose()} 按客户端 tick 推进（见 {@link PoseDelayBuffer}），
     * 因此它们永远是一对相邻的网络样本，{@code number}（{@code partialTick}）的含义是
     * 「这一 tick 内的时间比例」。</p>
     *
     * @param number 插值系数(partialTick)，0~1
     * @return 质心在世界坐标系下的位姿变换矩阵
     */
    @NotNull
    public Matrix4f getWorldPositionMatrix(@NotNull Number number) {
        return SparkMathKt.toMatrix4f(SparkMathKt.lerp(oldTransform, transform, number.floatValue()).toTransformMatrix());
    }

    public float getDurability() {
        return this.syncedData.get(DATA_DURABILITY_ID);
    }

    public void setDurability(float durability) {
        this.syncedData.set(DATA_DURABILITY_ID, Math.clamp(durability, 0.0f, this.getMaxDurability()));
    }

    public int getDestroyTime() {
        return this.syncedData.get(DESTROY_TIME_ID);
    }

    public void setDestroyTime(int time) {
        this.syncedData.set(DESTROY_TIME_ID, time);
    }

    abstract public float getMaxDurability();

    abstract public PhysicsLevel getPhysicsLevel();
}
