package io.github.sweetzonzi.machine_max.common.mech.vehicle.collision;

import cn.solarmoon.spark_core.sound.ISoundSpreader;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackLink;
import jme3utilities.math.MyQuaternion;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * 地形接触的粒子与 FMOD 持续音效。
 *
 * <p>物理线程在 {@link #resetLatestStates()} 后收集接触，物理步完成时调用
 * {@link #publishLatestStates()} 发布快照。主线程 {@link #tick()} 只消费完成态，
 * 轮胎面和普通接触面分别维护声源。快照中的向量使用不可变的 {@link Vec3}，
 * 接触回调里的可变向量只在记录时读取。</p>
 *
 * <p>两类接触都使用 tire_noise；轮胎面以轮轴转速驱动 rpm，普通接触面的 rpm 为 0。
 * surface 按接触方块选择，contact 取滚动与滑动强度的较大值，轮胎材质纹理受独立上限约束。
 * 轮胎滑动另用 tire_screech。FMOD 不可用时静音。</p>
 *
 * <p>健康履带的轮面接触只向 {@link TrackLink} 上报，由履带维护自己的音效。
 * 断带或解散后，成员轮面由本类播放。</p>
 */
public class CollisionEffectManager {
    private static final ResourceLocation CONTACT_BANK = ResourceLocation.parse("machine_max:basic_vehicle_sounds");
    private static final String TIRE_NOISE = "event:/Default_Vehicles/Transmission/Tracks_and_Tires/tire_noise";
    private static final String TIRE_SCREECH = "event:/Default_Vehicles/Transmission/Tracks_and_Tires/tire_screech";

    /** 轮胎实际转速 1200 r/min 对应事件 rpm=100，超过此值钳位。 */
    private static final float FULL_WHEEL_RPM = 1200f;
    /** 速度均为 m/s：低于起声阈值静音，滚动与滑动强度分别归一化到 0～1。 */
    private static final float CONTACT_FLOOR = 0.5f;
    private static final float FULL_ROLL_SPEED = 20f;
    private static final float FULL_SLIP_SPEED = 8f;
    /** 轮胎材质纹理的 contact 上限；普通零件刮擦使用事件的完整强度范围。 */
    private static final float MAX_TIRE_CONTACT = 0.35f;
    /** 轮胎尖叫须有实际滑动，低速转向时的高滑移率不足以触发。 */
    private static final float SCREECH_FLOOR = 3f;
    /** 接触短暂中断保留两 tick；资源不可用时每秒至多尝试播放一次。 */
    private static final int QUIET_GRACE_TICKS = 2;
    private static final int RETRY_TICKS = 20;

    /**
     * 一个接触面的跨线程完成态，速度单位为 m/s，角速度单位为 rad/s。
     *
     * @param blockState        被接触的方块状态
     * @param contactVel        接触点相对对方的世界速度，包含轮面转动贡献
     * @param worldContactPoint 世界接触位置
     * @param sourceVelocity    零件平动速度，作为接触区域的多普勒速度
     * @param wheelAngularSpeed 轮轴角速度的绝对值；普通接触面为 0
     * @param wheelRadius       轮胎面半径；普通接触面为 0
     * @param slipSpeed         接触点相对速度在接触平面内的长度
     * @param slipRatio         轮胎滑移率，用于漂移烟雾
     */
    public record CollisionSnapshot(
            BlockState blockState,
            Vec3 contactVel,
            Vec3 worldContactPoint,
            Vec3 sourceVelocity,
            float wheelAngularSpeed,
            float wheelRadius,
            float slipSpeed,
            float slipRatio
    ) {
        /** 轮面绕轮轴旋转产生的线速度，普通接触面为 0。 */
        float rollingSpeed() {
            return wheelAngularSpeed * wheelRadius;
        }

        /** 实际转速按统一上限归一化至事件的 0～100。 */
        float rpm() {
            return Math.clamp(wheelAngularSpeed * (30f / (float) Math.PI) / FULL_WHEEL_RPM, 0f, 1f) * 100f;
        }

        /** 滚动与滑动共享材质纹理，取较大强度；轮胎面按独立上限钳位。 */
        float contact() {
            float roll = Math.clamp((rollingSpeed() - CONTACT_FLOOR) / (FULL_ROLL_SPEED - CONTACT_FLOOR), 0f, 1f);
            float slip = Math.clamp((slipSpeed - CONTACT_FLOOR) / (FULL_SLIP_SPEED - CONTACT_FLOOR), 0f, 1f);
            float strength = Math.max(roll, slip);
            return wheelRadius > 0f ? Math.min(strength, MAX_TIRE_CONTACT) : strength;
        }

        /** 尖叫强度由实际滑动速度归一化，低于阈值为 0。 */
        float screechAmount() {
            return Math.clamp((slipSpeed - SCREECH_FLOOR) / (FULL_SLIP_SPEED - SCREECH_FLOOR), 0f, 1f);
        }
    }

    private final SubPart subPart;
    private final ContactSound wheelSound = new ContactSound(TIRE_NOISE);
    private final ContactSound bodySound = new ContactSound(TIRE_NOISE);
    private final ContactSound slipSound = new ContactSound(TIRE_SCREECH);

    // 本步收集槽只由物理线程写；发布槽只在物理步完成时写，主线程不清空它们。
    private CollisionSnapshot wheelSnapshotThisStep;
    private CollisionSnapshot bodySnapshotThisStep;
    private volatile CollisionSnapshot latestWheelSnapshot;
    private volatile CollisionSnapshot latestBodySnapshot;
    private CollisionSnapshot consumedWheelSnapshot;
    private CollisionSnapshot consumedBodySnapshot;

    public CollisionEffectManager(SubPart subPart) {
        this.subPart = subPart;
    }

    /** 物理线程：开始收集本物理步的接触，不影响主线程可见的完成态。 */
    public void resetLatestStates() {
        wheelSnapshotThisStep = null;
        bodySnapshotThisStep = null;
    }

    /** 物理线程：发布本步两类接触的完成态，空值表示本步没有这类接触。 */
    public void publishLatestStates() {
        latestWheelSnapshot = wheelSnapshotThisStep;
        latestBodySnapshot = bodySnapshotThisStep;
    }

    /**
     * 物理线程：冻结一次接触，轮胎面和普通接触面各保留本步最后一次记录。
     * 轮轴沿零件局部 X 轴，与 {@link CollisionHandler} 的轮面摩擦计算口径一致。
     *
     * @param hitBoxIndex 接触子形状索引，供 SubPart 查询轮面与半径
     * @param blockState 接触方块
     * @param contactVel 接触点相对速度
     * @param normal 接触平面法线
     * @param worldContactPoint 世界接触点
     * @param slipRatio 轮胎滑移率
     */
    public void recordLatestEffect(int hitBoxIndex, BlockState blockState,
                                   Vector3f contactVel, Vector3f normal,
                                   Vector3f worldContactPoint, float slipRatio) {
        if (!subPart.getLevel().isClientSide()) return;
        boolean wheelSurface = subPart.isWheel(hitBoxIndex) && subPart.isWheelSurface(hitBoxIndex);
        float radius = wheelSurface ? subPart.getWheelRadius(hitBoxIndex) : 0f;
        float omega = 0f;
        if (wheelSurface) {
            Vector3f axis = new Vector3f();
            MyQuaternion.rotate(subPart.body.getPhysicsRotation(null), Vector3f.UNIT_X, axis);
            omega = Math.abs(subPart.body.getAngularVelocity(null).dot(axis));
        }
        float normalSpeed = contactVel.dot(normal);
        float slipSpeed = contactVel.subtract(normal.mult(normalSpeed)).length();
        Vec3 velocity = copy(contactVel);
        Vec3 point = copy(worldContactPoint);
        Vec3 sourceVelocity = copy(subPart.body.getLinearVelocity(null));
        // 接触来自 native 边界，非有限值不能进入粒子或原生音频参数。
        if (!finite(velocity) || !finite(point) || !finite(sourceVelocity)
                || !Float.isFinite(omega) || !Float.isFinite(radius) || !Float.isFinite(slipSpeed)) return;
        var snapshot = new CollisionSnapshot(blockState, velocity, point, sourceVelocity,
                omega, radius, slipSpeed, Float.isFinite(slipRatio) ? slipRatio : 0f);
        if (wheelSurface) wheelSnapshotThisStep = snapshot;
        else bodySnapshotThisStep = snapshot;
    }

    /** 主线程：消费完成态、生成粒子并更新三个持续声源。 */
    public void tick() {
        if (!subPart.getLevel().isClientSide()) return;
        CollisionSnapshot wheel = latestWheelSnapshot;
        CollisionSnapshot body = latestBodySnapshot;
        // 同一份物理完成态只消费一次，物理步暂停时由声源宽限计时释放循环音。
        CollisionSnapshot freshWheel = wheel == consumedWheelSnapshot ? null : wheel;
        CollisionSnapshot freshBody = body == consumedBodySnapshot ? null : body;
        consumedWheelSnapshot = wheel;
        consumedBodySnapshot = body;

        if (freshWheel != null) {
            spawnCollisionParticles(freshWheel);
            if (freshWheel.slipSpeed() >= CONTACT_FLOOR && freshWheel.slipRatio() > 0.4f) {
                spawnDriftSmoke(freshWheel);
            }
        }
        if (freshBody != null) spawnCollisionParticles(freshBody);

        if (isTrackOwned()) {
            if (freshWheel != null) {
                Vec3 velocity = freshWheel.sourceVelocity();
                subPart.trackLink.reportWheelContact((float) velocity.horizontalDistance(), toVector(freshWheel.worldContactPoint()));
            }
            wheelSound.stop();
            slipSound.stop();
        } else {
            wheelSound.tick(freshWheel);
            slipSound.tick(freshWheel);
        }
        bodySound.tick(freshBody);
    }

    /** 健康履带的轮面音由 TrackLink 管理，普通刮擦由本管理器处理。 */
    private boolean isTrackOwned() {
        TrackLink track = subPart.trackLink;
        return track != null && !track.isDisposed() && !track.isBroken();
    }

    /** 主线程：销毁或卸载区块时立即释放接触声源，恢复 tick 后允许重新播放。 */
    public void stopAll() {
        if (!subPart.getLevel().isClientSide()) return;
        wheelSound.stop();
        bodySound.stop();
        slipSound.stop();
        consumedWheelSnapshot = latestWheelSnapshot;
        consumedBodySnapshot = latestBodySnapshot;
    }

    /**
     * 将接触方块归入 Studio 的六种材质纹理，返回值可直接写入 surface。
     * 雪覆盖优先；泥浆按泥方块识别；木质按标签和斧采掘标签识别；
     * 土、草、沙、羊毛采用泥土纹理，石、金属及未知方块采用水泥纹理。
     */
    public static float getSurface(BlockState state) {
        if (state.is(BlockTags.SNOW) || state.hasProperty(BlockStateProperties.SNOWY)
                && state.getValue(BlockStateProperties.SNOWY)) return 0.4f;
        if (state.is(BlockTags.ICE)) return 1f;
        if (state.is(Blocks.MUD) || state.is(Blocks.MUDDY_MANGROVE_ROOTS)) return 0.6f;
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS) || state.is(BlockTags.MINEABLE_WITH_AXE)) return 0.8f;
        if (state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.WOOL)
                || state.is(BlockTags.MINEABLE_WITH_SHOVEL) || state.is(BlockTags.MINEABLE_WITH_HOE)) return 0.2f;
        return 0f;
    }

    /** 每个持续声源独立保存位置、速度和 UUID，播放首帧即可查询空间状态。 */
    private final class ContactSound implements ISoundSpreader {
        private final String event;
        private UUID voice;
        private UUID fadingVoice;
        private CollisionSnapshot snapshot;
        private int quietTicks;
        private int retryTicks;

        private ContactSound(String event) {
            this.event = event;
        }

        /** 主线程：持续接触更新同一实例，短暂中断保留实例，资源失效后限频重试。 */
        private void tick(@Nullable CollisionSnapshot contact) {
            if (retryTicks > 0) retryTicks--;
            boolean screech = event.equals(TIRE_SCREECH);
            float strength = contact == null ? 0f : screech ? contact.screechAmount() : contact.contact();
            if (contact != null) snapshot = contact;
            if (strength <= 0f) {
                if (++quietTicks > QUIET_GRACE_TICKS) fade();
                else if (voice != null) {
                    // 断触时立即压低参数；宽限只保留实例，防止接缝导致反复创建。
                    updateParameters(screech ? Map.of("amount", 0f) : Map.of("rpm", 0f, "contact", 0f));
                }
                return;
            }
            quietTicks = 0;
            Map<String, Float> parameters = screech ? Map.of("amount", strength)
                    : Map.of("rpm", contact.rpm(), "contact", strength, "surface", getSurface(contact.blockState()));
            if (voice == null) {
                if (retryTicks > 0) return;
                voice = tryPlayFmodSound(CONTACT_BANK, event, SoundSource.BLOCKS, parameters);
                if (voice == null) retryTicks = RETRY_TICKS;
            } else {
                updateParameters(parameters);
            }
        }

        /** 主线程：任一参数更新失败即释放失效实例，下一次播放按重试间隔进行。 */
        private void updateParameters(Map<String, Float> parameters) {
            for (var parameter : parameters.entrySet()) {
                if (!setFmodParameter(voice, parameter.getKey(), parameter.getValue())) {
                    stopFmodSound(voice);
                    voice = null;
                    retryTicks = RETRY_TICKS;
                    break;
                }
            }
        }

        /** 主线程：请求 Studio 停止过渡，播放器负责释放完成尾音的实例。 */
        private void fade() {
            if (voice != null) {
                if (fadingVoice != null) stopFmodSound(fadingVoice);
                if (fadeFmodSound(voice)) fadingVoice = voice;
                else stopFmodSound(voice);
                voice = null;
            }
        }

        /** 主线程：生命周期结束立即停止，避免离开区块后保留无限循环。 */
        private void stop() {
            if (voice != null) stopFmodSound(voice);
            if (fadingVoice != null) stopFmodSound(fadingVoice);
            voice = null;
            fadingVoice = null;
            quietTicks = 0;
            retryTicks = 0;
            // Studio 正在淡出的实例也会查询此声源，保留最后一次不可变空间状态。
        }

        @NotNull
        @Override
        public Level getLevel() {
            return subPart.getLevel();
        }

        /** 空间位置取最近接触点，音效启动前已由 tick 保存。 */
        @NotNull
        @Override
        public Vec3 getPosition(UUID uuid) {
            return snapshot.worldContactPoint();
        }

        /** 接触区域随零件平动，轮面自转只参与业务参数计算。 */
        @NotNull
        @Override
        public Vec3 getSpeed(UUID uuid) {
            return snapshot.sourceVelocity();
        }

        /** 客户端实时听者判定与发动机、发射器共用同一规则。 */
        @Override
        public boolean shouldApplyInteriorEffect(@Nullable Entity listener, boolean firstPerson) {
            return AbstractSubsystem.isInteriorListener(listener, firstPerson);
        }
    }

    /** 主线程：根据实际滑移生成轮胎漂移烟雾，每 tick 最多四个粒子。 */
    private void spawnDriftSmoke(CollisionSnapshot snap) {
        Vec3 position = snap.worldContactPoint();
        Vec3 velocity = snap.contactVel();
        float normalizedSlip = Math.clamp((snap.slipRatio() - 0.4f) / 0.6f, 0f, 1f);
        int count = Math.min((int) Math.ceil(velocity.length() * 0.3f * normalizedSlip), 4);
        for (int i = 0; i < count; i++) {
            subPart.getLevel().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    position.x + (Math.random() - 0.5) * 0.6,
                    position.y + 0.05,
                    position.z + (Math.random() - 0.5) * 0.6,
                    velocity.x * 0.5 * (0.8 + 0.4 * Math.random()) * 0.05,
                    0.1 * (0.8 + 0.4 * Math.random()) * 0.05,
                    velocity.z * 0.5 * (0.8 + 0.4 * Math.random()) * 0.05);
        }
    }

    /** 主线程：在土、沙、雪接触处按相对速度生成方块碎屑。 */
    private void spawnCollisionParticles(CollisionSnapshot snap) {
        BlockState state = snap.blockState();
        Vec3 velocity = snap.contactVel();
        Vec3 position = snap.worldContactPoint();
        double speed = velocity.length();
        if ((speed > 10 || Math.random() < 1 - Math.exp(-0.5 * speed))
                && (state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.SNOW))) {
            subPart.getLevel().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    position.x, position.y + 0.01, position.z,
                    velocity.x * (1f + 0.2f * (Math.random() - 0.5f)),
                    velocity.y * (1f + 0.2f * (Math.random() - 0.5f)),
                    velocity.z * (1f + 0.2f * (Math.random() - 0.5f)));
        }
    }

    /** 在物理线程把可变的 native 接触向量冻结为不可变快照。 */
    private static Vec3 copy(Vector3f value) {
        return new Vec3(value.x, value.y, value.z);
    }

    /** 履带上报接口使用 jme 向量，转换时不暴露快照状态。 */
    private static Vector3f toVector(Vec3 value) {
        return new Vector3f((float) value.x, (float) value.y, (float) value.z);
    }

    /** native 接触边界的有限性校验。 */
    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
