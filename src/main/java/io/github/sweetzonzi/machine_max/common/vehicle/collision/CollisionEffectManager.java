package io.github.sweetzonzi.machine_max.common.vehicle.collision;

import cn.solarmoon.spark_core.sound.ISoundSpreader;
import cn.solarmoon.spark_core.sound.SpreadingSoundHelper;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * 碰撞效果管理器
 * <p>统一管理碰撞产生的粒子与持续性音效，实现 ISoundSpreader 以利用 SparkCore 的
 * 音速传播音效系统（包含多普勒效应、淡入淡出、交叉过渡等）。</p>
 *
 * <p>线程安全模型：</p>
 * <ul>
 *   <li>物理线程调用 {@link #recordLatestEffect} 写入快照（volatile 保证可见性）</li>
 *   <li>主线程调用 {@link #tick} 消费快照并驱动粒子/音效</li>
 *   <li>{@link #resetLatestStates} 每物理tick清空，防止残留</li>
 * </ul>
 *
 * <p>音效分为两大类：</p>
 * <ul>
 *   <li><b>轮胎类</b>（isWheel=true）：轮胎与地面的摩擦/尖叫声</li>
 *   <li><b>车体类</b>（isWheel=false）：车体刮擦地面/障碍物的声音</li>
 * </ul>
 *
 * <p>每类音效持有各自的 UUID 和材质标识键。当地面材质切换时，
 * 通过 transitionSound 进行交叉淡入淡出。无碰撞时通过 fadeSound 淡出。</p>
 */
public class CollisionEffectManager implements ISoundSpreader {

    // ============ 车体刮擦音效（hard_*，非轮胎） ============
    public static final SoundEvent HARD_CONCRETE = create("part.collision.terrain.hard_concrete");
    public static final SoundEvent HARD_DIRT = create("part.collision.terrain.hard_dirt");
    public static final SoundEvent HARD_ICE = create("part.collision.terrain.hard_ice");
    public static final SoundEvent HARD_METAL = create("part.collision.terrain.hard_metal");
    public static final SoundEvent HARD_MUD = create("part.collision.terrain.hard_mud");
    public static final SoundEvent HARD_SNOW = create("part.collision.terrain.hard_snow");
    public static final SoundEvent HARD_WOOD = create("part.collision.terrain.hard_wood");

    // ============ 轮胎摩擦音效（tire_*） ============
    public static final SoundEvent TIRE_CONCRETE = create("part.collision.terrain.tire_concrete");
    public static final SoundEvent TIRE_DIRT = create("part.collision.terrain.tire_dirt");
    public static final SoundEvent TIRE_GRASS = create("part.collision.terrain.tire_grass");
    public static final SoundEvent TIRE_METAL = create("part.collision.terrain.tire_metal");
    public static final SoundEvent TIRE_SAND = create("part.collision.terrain.tire_sand");
    public static final SoundEvent TIRE_SANDSTONE = create("part.collision.terrain.tire_sandstone");
    public static final SoundEvent TIRE_SNOW = create("part.collision.terrain.tire_snow");
    public static final SoundEvent TIRE_WOOD = create("part.collision.terrain.tire_wood");
    public static final SoundEvent TIRE_EXTRA_SLIP = create("part.collision.terrain.tire_extra_slip");
    public static final SoundEvent TIRE_EXTRA_WET = create("part.collision.terrain.tire_extra_wet");

    private static SoundEvent create(String path) {
        return SoundEvent.createFixedRangeEvent(
                ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, path), 64f
        );
    }

    /**
     * 碰撞快照
     * <p>物理线程记录的不可变碰撞数据，每 tick 每类（wheel/body）只保留最新一次。</p>
     *
     * @param isWheel           碰撞部位是否为轮胎
     * @param blockState        碰撞的方块状态
     * @param contactVel        接触点相对速度
     * @param normal            碰撞法线
     * @param worldContactPoint 世界坐标接触点
     * @param slipRatio         滑移率（仅轮胎有意义）
     */
    public record CollisionSnapshot(
            boolean isWheel,
            BlockState blockState,
            Vector3f contactVel,
            Vector3f normal,
            Vector3f worldContactPoint,
            float slipRatio
    ) {
    }

    private final SubPart subPart;

    //物理线程写入的最新快照（每类各一），主线程消费后清空
    private volatile CollisionSnapshot latestWheelSnapshot;
    private volatile CollisionSnapshot latestBodySnapshot;

    //持续性音效的UUID与地面材质标识，用于检测切换和淡入淡出
    private UUID activeWheelSoundUuid;
    private UUID activeBodySoundUuid;
    private UUID activeSlipSoundUuid;
    private String activeWheelBlockKey;
    private String activeBodyBlockKey;
    private float currentWheelVolume;
    private float currentBodyVolume;
    private float currentSlipVolume;
    private float currentWheelPitch = 1f;
    private float currentBodyPitch = 1f;
    private float currentSlipPitch = 1f;

    public CollisionEffectManager(SubPart subPart) {
        this.subPart = subPart;
    }

    //向量缓存
    private final Vector3f tmpBodyTangential = new Vector3f();
    private final Vector3f tmpSlip = new Vector3f();

    /**
     * 清空上帧累积的快照，由 CollisionHandler 在物理 tick 开始时调用。
     * 避免上一帧的残留数据在本帧无新碰撞时仍被消费。
     */
    public void resetLatestStates() {
        latestWheelSnapshot = null;
        latestBodySnapshot = null;
    }

    /**
     * 记录最新一次碰撞效果快照。
     * 调用于物理线程（CollisionHandler 各碰撞处理方法中），
     * 每 tick 每类只保留最后一次接触的数据。
     *
     * @param isWheel           碰撞部位是否为轮胎
     * @param blockState        碰撞的方块状态
     * @param contactVel        接触点相对速度
     * @param normal            碰撞法线
     * @param worldContactPoint 世界坐标接触点
     * @param slipRatio         滑移率（轮胎滑移时有效）
     */
    public void recordLatestEffect(boolean isWheel, BlockState blockState,
                                   Vector3f contactVel, Vector3f normal,
                                   Vector3f worldContactPoint, float slipRatio) {
        var snapshot = new CollisionSnapshot(isWheel, blockState, contactVel, normal, worldContactPoint, slipRatio);
        if (isWheel) {
            latestWheelSnapshot = snapshot;
        } else {
            latestBodySnapshot = snapshot;
        }
    }

    /**
     * 主线程 tick 入口，由 SubPart.postTick() 调用。
     * <p>执行流程：</p>
     * <ol>
     *   <li>取走最新快照并清空（双缓冲保证线程安全）</li>
     *   <li>轮胎类/车体类各自处理：有碰撞则生成粒子并更新音效，无碰撞则淡出音效</li>
     * </ol>
     */
    public void tick() {
        Level level = subPart.getLevel();
        if (!level.isClientSide()) return;

        //取走最新快照，后续物理线程写入的数据留到下一 tick 消费
        CollisionSnapshot wheelSnap = latestWheelSnapshot;
        CollisionSnapshot bodySnap = latestBodySnapshot;
        latestWheelSnapshot = null;
        latestBodySnapshot = null;

        if (wheelSnap != null) {
            spawnCollisionParticles(wheelSnap);
            updateContinuousSound(wheelSnap, true);

            //滑移率超过0.4时叠加额外漂移音效
            if (wheelSnap.slipRatio() > 0.4f) {
                updateSlipSound(level, wheelSnap);
            } else {
                fadeOutSlipSoundIfNeeded(level);
            }
        } else {
            fadeOutWheelSoundIfNeeded(level);
            fadeOutSlipSoundIfNeeded(level);
        }

        if (bodySnap != null) {
            spawnCollisionParticles(bodySnap);
            updateContinuousSound(bodySnap, false);
        } else {
            fadeOutBodySoundIfNeeded(level);
        }
    }

    /**
     * 根据碰撞快照生成一次性粒子效果。
     * <p>目前保留原有的泥土/沙地/雪地飞溅逻辑。后续可在此扩展
     * 轮胎漂移烟雾、火花刮擦等效果。</p>
     */
    private void spawnCollisionParticles(CollisionSnapshot snap) {
        Level level = subPart.getLevel();
        if (!level.isClientSide()) return;

        BlockState blockState = snap.blockState();
        Vector3f contactVel = snap.contactVel();
        Vector3f worldPos = snap.worldContactPoint();
        float speed = contactVel.length();

        //速度足够或随机触发时生成飞溅粒子
        if (speed > 10 || Math.random() < 1 - Math.exp(-0.5 * speed)) {
            //仅对泥土/沙地/雪地生成方块碎屑
            if (blockState.is(BlockTags.DIRT) || blockState.is(BlockTags.SAND) || blockState.is(BlockTags.SNOW)) {
                if (Math.random() < Math.max(1f, 0.05f * speed)) {
                    level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, blockState),
                            worldPos.x, worldPos.y + 0.01f, worldPos.z,
                            contactVel.x * (1f + 0.2f * (Math.random() - 0.5f)),
                            contactVel.y * (1f + 0.2f * (Math.random() - 0.5f)),
                            contactVel.z * (1f + 0.2f * (Math.random() - 0.5f))
                    );
                }
            }
        }
    }

    /**
     * 更新持续性摩擦/刮擦音效。
     * <p>根据地面材质切换决策：</p>
     * <ul>
     *   <li>首次碰撞 → 使用 playSpreadingSound 启动新音效，指定淡入淡出时长</li>
     *   <li>材质更换 → 使用 transitionSound 交叉淡入淡出至新音效</li>
     *   <li>材质不变 → 仅调整音量，不重启音效</li>
     * </ul>
     */
    private void updateContinuousSound(CollisionSnapshot snap, boolean isWheel) {
        Level level = subPart.getLevel();
        if (!level.isClientSide()) return;

        //以方块注册名作为材质标识键，检测地面是否切换
        String blockKey = snap.blockState().getBlock().getDescriptionId();
        SoundEvent sound = getFrictionSound(isWheel, snap.blockState());

        UUID currentUuid = isWheel ? activeWheelSoundUuid : activeBodySoundUuid;
        String currentKey = isWheel ? activeWheelBlockKey : activeBodyBlockKey;

        boolean materialChanged = !blockKey.equals(currentKey);

        if (currentUuid == null) {
            //首次播放，淡入4tick，后续准备淡出8tick，循环播放
            UUID uuid = playSpreadingSound(level, sound, SoundSource.BLOCKS, 4, 8, true);
            setActiveSoundState(isWheel, uuid, blockKey);
        } else if (materialChanged) {
            //材质切换，旧音效淡出8tick + 新音效淡入4tick
            UUID uuid = transitionSound(level, currentUuid, sound, SoundSource.BLOCKS, 4, 8, true);
            setActiveSoundState(isWheel, uuid, blockKey);
        }

        updateSoundLevel(isWheel, snap);
    }

    /**
     * 根据碰撞速度与零件自身速度更新音效音量和音高。
     * <p>综合考虑两种速度来源，确保碾压声和摩擦声都能驱动音效：</p>
     * <ol>
     *   <li>零件本体相对接触平面的切向速度（反映碾压/刮擦强度）</li>
     *   <li>接触点相对速度在接触平面上的投影（反映摩擦滑动强度）</li>
     *   <li>取两者较大值映射到 0~1 音量，0.8~1.2 音高</li>
     * </ol>
     */
    private void updateSoundLevel(boolean isWheel, CollisionSnapshot snap) {
        Vector3f normal = snap.normal();
        Vector3f contactVel = snap.contactVel();

        //接触平面上的滑移速度 = contactVel 减去法线分量
        float normalContactVel = contactVel.dot(normal);
        tmpSlip.set(contactVel).subtractLocal(normal.mult(normalContactVel));
        float slipSpeed = tmpSlip.length();

        //零件本体相对接触平面的切向速度
        Vector3f bodyVel = subPart.getLinearVelocity();
        float normalBodyVel = bodyVel.dot(normal);
        tmpBodyTangential.set(bodyVel).subtractLocal(normal.mult(normalBodyVel));
        float bodyTangentialSpeed = tmpBodyTangential.length() * 0.1f;

        //取较大者作为有效速度
        float effectiveSpeed = Math.max(slipSpeed, bodyTangentialSpeed);

        //音量：0~20 m/s 映射到 0~0.8，低于 0.5 m/s 时静音
        float volume = effectiveSpeed < 0.5f ? 0f : Math.clamp(effectiveSpeed / 20f, 0f, 1f) * 0.5f;
        //音高：0~20 m/s 映射到 0.8~1.2
        float pitch = 0.8f + 0.4f * Math.clamp(effectiveSpeed / 20f, 0f, 1f);

        if (isWheel) {
            currentWheelVolume = volume;
            currentWheelPitch = pitch;
        } else {
            currentBodyVolume = volume;
            currentBodyPitch = pitch;
        }
    }

    /**
     * 本 tick 无轮胎类碰撞，淡出并清理轮胎音效状态。
     */
    private void fadeOutWheelSoundIfNeeded(Level level) {
        if (activeWheelSoundUuid != null) {
            SpreadingSoundHelper.fadeSound(level, activeWheelSoundUuid);
            activeWheelSoundUuid = null;
            activeWheelBlockKey = null;
            currentWheelVolume = 0f;
            currentWheelPitch = 1f;
        }
    }

    /**
     * 本 tick 无车体类碰撞，淡出并清理车体音效状态。
     */
    private void fadeOutBodySoundIfNeeded(Level level) {
        if (activeBodySoundUuid != null) {
            SpreadingSoundHelper.fadeSound(level, activeBodySoundUuid);
            activeBodySoundUuid = null;
            activeBodyBlockKey = null;
            currentBodyVolume = 0f;
            currentBodyPitch = 1f;
        }
    }

    private void setActiveSoundState(boolean isWheel, UUID uuid, String blockKey) {
        if (isWheel) {
            activeWheelSoundUuid = uuid;
            activeWheelBlockKey = blockKey;
        } else {
            activeBodySoundUuid = uuid;
            activeBodyBlockKey = blockKey;
        }
    }

    /**
     * 更新轮胎额外漂移音效（TIRE_EXTRA_SLIP）。
     * 仅在滑移率超过阈值时调用，作为主轮胎音效的叠加层。
     */
    private void updateSlipSound(Level level, CollisionSnapshot snap) {
        if (activeSlipSoundUuid == null) {
            UUID uuid = playSpreadingSound(level, TIRE_EXTRA_SLIP, SoundSource.BLOCKS, 4, 8, true);
            activeSlipSoundUuid = uuid;
        }
        updateSlipSoundLevel(snap);
    }

    /**
     * 根据滑移程度更新漂移音效的音量和音高。
     */
    private void updateSlipSoundLevel(CollisionSnapshot snap) {
        Vector3f normal = snap.normal();
        Vector3f contactVel = snap.contactVel();
        //接触平面上的滑移速度 = contactVel 减去法线分量
        float normalContactVel = contactVel.dot(normal);
        tmpSlip.set(contactVel).subtractLocal(normal.mult(normalContactVel));
        float slipSpeed = tmpSlip.length();
        float slipRatio = snap.slipRatio();
        //滑移率 0.3~1.0 映射到音量 0~1，仅一定接触速度时才有效
        float volume = Math.clamp((slipRatio - 0.3f) / 0.6f, 0f, 1f) * Math.clamp(slipSpeed - 3f, 0f, 1f) * 0.3f;
        //滑移率越高音调越高
        float pitch = 0.8f + 0.6f * Math.clamp((slipRatio - 0.3f) / 0.6f, 0f, 1f);
        currentSlipVolume = volume;
        currentSlipPitch = pitch;
    }

    /**
     * 本 tick 无滑移触发或轮胎未碰撞，淡出并清理漂移音效状态。
     */
    private void fadeOutSlipSoundIfNeeded(Level level) {
        if (activeSlipSoundUuid != null) {
            SpreadingSoundHelper.fadeSound(level, activeSlipSoundUuid);
            activeSlipSoundUuid = null;
            currentSlipVolume = 0f;
            currentSlipPitch = 1f;
        }
    }

    /**
     * 停止所有正在播放的音效（部件被销毁时调用）。
     */
    public void stopAll() {
        Level level = subPart.getLevel();
        if (!level.isClientSide()) return;
        fadeOutWheelSoundIfNeeded(level);
        fadeOutBodySoundIfNeeded(level);
        fadeOutSlipSoundIfNeeded(level);
    }

    /**
     * 判断方块是否被雪覆盖（如覆雪草方块、覆雪菌丝等）。
     */
    private static boolean isSnowCovered(BlockState state) {
        return state.hasProperty(BlockStateProperties.SNOWY) && state.getValue(BlockStateProperties.SNOWY);
    }

    /**
     * 根据方块状态获取车体刮擦音效。
     * <p>匹配顺序：</p>
     * <ol>
     *   <li>雪（含覆雪方块）→ HARD_SNOW</li>
     *   <li>特殊识别（金属→HARD_METAL，泥→HARD_MUD）</li>
     *   <li>BlockTags 匹配（DIRT, SAND, ICE, WOOL）</li>
     *   <li>挖掘工具回退（镐→混凝土，斧→木，铲/锄→泥土）</li>
     * </ol>
     */
    @NotNull
    public static SoundEvent getBodyScrapeSound(BlockState state) {
        // 1. 雪（含覆雪草方块）
        if (state.is(BlockTags.SNOW) || isSnowCovered(state)) return HARD_SNOW;

        String id = state.getBlock().getDescriptionId().toLowerCase();

        // 2. 特殊识别：金属和泥
        if (id.contains("metal") || id.contains("iron_block") || id.contains("gold_block")
                || id.contains("copper_block") || id.contains("netherite")) return HARD_METAL;
        if (id.contains("mud")) return HARD_MUD;

        // 3. BlockTags 匹配
        if (state.is(BlockTags.DIRT)) return HARD_DIRT;
        if (state.is(BlockTags.SAND)) return HARD_DIRT;
        if (state.is(BlockTags.ICE)) return HARD_ICE;
        if (state.is(BlockTags.WOOL)) return HARD_MUD;

        // 4. 挖掘工具回退
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) return HARD_CONCRETE;
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) return HARD_WOOD;
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) return HARD_DIRT;
        if (state.is(BlockTags.MINEABLE_WITH_HOE)) return HARD_DIRT;

        return HARD_CONCRETE;
    }

    /**
     * 根据方块状态获取轮胎摩擦音效。
     * <p>匹配顺序：</p>
     * <ol>
     *   <li>雪（含覆雪方块）→ TIRE_SNOW</li>
     *   <li>草方块/菌丝/灰化土→ TIRE_GRASS</li>
     *   <li>砂岩→ TIRE_SANDSTONE，沙→ TIRE_SAND</li>
     *   <li>金属→ TIRE_METAL</li>
     *   <li>BlockTags 匹配</li>
     *   <li>挖掘工具回退</li>
     * </ol>
     */
    @NotNull
    public static SoundEvent getTireRollSound(BlockState state) {
        // 1. 雪（含覆雪草方块）
        if (state.is(BlockTags.SNOW) || isSnowCovered(state)) return TIRE_SNOW;

        String id = state.getBlock().getDescriptionId().toLowerCase();

        // 2. 草/菌丝/灰化土（在 DIRT tag 匹配之前优先）
        if (id.contains("grass") || id.contains("mycelium") || id.contains("podzol")) return TIRE_GRASS;

        // 3. 沙/砂岩（在 pickaxe 回退之前匹配）
        if (id.contains("sandstone")) return TIRE_SANDSTONE;
        if (id.contains("sand")) return TIRE_SAND;

        // 4. 金属（在 pickaxe 回退之前匹配）
        if (id.contains("metal") || id.contains("iron_block") || id.contains("gold_block")
                || id.contains("copper_block") || id.contains("netherite")) return TIRE_METAL;

        // 5. BlockTags 匹配
        if (state.is(BlockTags.SAND)) return TIRE_SAND;
        if (state.is(BlockTags.DIRT)) return TIRE_DIRT;
        if (state.is(BlockTags.ICE)) return TIRE_CONCRETE;

        // 6. 挖掘工具回退
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) return TIRE_CONCRETE;
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) return TIRE_WOOD;
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) return TIRE_DIRT;
        if (state.is(BlockTags.MINEABLE_WITH_HOE)) return TIRE_DIRT;

        return TIRE_CONCRETE;
    }

    /**
     * 根据轮胎/车体和碰撞方块查询对应的持续性摩擦音效。
     */
    @NotNull
    private SoundEvent getFrictionSound(boolean isWheel, BlockState blockState) {
        return isWheel ? getTireRollSound(blockState) : getBodyScrapeSound(blockState);
    }

    /**
     * 返回声源的实时位置。
     * 优先使用碰撞接触点，回退至 SubPart 位置。
     * 区别对待 wheel/body 两类音效的 UUID。
     */
    @NotNull
    @Override
    public Vec3 getPosition(UUID uuid, SoundEvent event) {
        if (uuid.equals(activeWheelSoundUuid) || uuid.equals(activeSlipSoundUuid)) {
            var snap = latestWheelSnapshot;
            if (snap != null && snap.worldContactPoint() != null)
                return new Vec3(snap.worldContactPoint().x, snap.worldContactPoint().y, snap.worldContactPoint().z);
        } else if (uuid.equals(activeBodySoundUuid)) {
            var snap = latestBodySnapshot;
            if (snap != null && snap.worldContactPoint() != null)
                return new Vec3(snap.worldContactPoint().x, snap.worldContactPoint().y, snap.worldContactPoint().z);
        }
        Vector3f pos = subPart.getPosition();
        return new Vec3(pos.x, pos.y, pos.z);
    }

    /**
     * 返回声源的实时速度（用于多普勒效应计算）。
     */
    @NotNull
    @Override
    public Vec3 getSpeed(UUID uuid, SoundEvent event) {
        return Vec3.ZERO;
    }

    /**
     * 返回声源的实时音量，由 updateSoundLevel 每 tick 根据碰撞速度更新。
     */
    @Override
    public float getVolume(UUID uuid, SoundEvent event) {
        if (uuid.equals(activeWheelSoundUuid)) return currentWheelVolume;
        if (uuid.equals(activeBodySoundUuid)) return currentBodyVolume;
        if (uuid.equals(activeSlipSoundUuid)) return currentSlipVolume;
        return 0f;
    }

    /**
     * 返回声源的实时音高，由 updateSoundLevel 每 tick 根据有效速度动态计算。
     */
    @Override
    public float getPitch(UUID uuid, SoundEvent event) {
        if (uuid.equals(activeWheelSoundUuid)) return currentWheelPitch;
        if (uuid.equals(activeBodySoundUuid)) return currentBodyPitch;
        if (uuid.equals(activeSlipSoundUuid)) return currentSlipPitch;
        return 1.0f;
    }
}
