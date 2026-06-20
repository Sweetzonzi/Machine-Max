package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import cn.solarmoon.spark_core.sound.SpreadingSoundHelper;
import cn.solarmoon.spark_core.util.PPhase;
import cn.solarmoon.spark_core.util.SparkMathKt;
import cn.solarmoon.spark_core.util.TaskSubmitOffice;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.energy.EnergyGrid;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.signal.EmptySignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalSender;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalResult;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.RegenLoaderSubsystemAttr;
import lombok.Getter;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 再生装弹机子系统。<br>
 * 统一能量武器（激光、爆能束）和街机风格再生武器（导弹冷却再生）的弹药供给模型。<br>
 * 实现 {@link IAmmoSupplier} 接口，不实现 {@link IAmmoConsumer}（不消耗外部弹药）。<br>
 * <p>
 * 两种再生模式：
 * <ul>
 *   <li>{@code regenRoundByRound=true} — 随打随产：每消耗一发立即开始再生下一发</li>
 *   <li>{@code regenRoundByRound=false} — 批量产出：弹仓全空后触发再生，一次性回满</li>
 * </ul>
 * <p>
 * 能量消耗通过 {@link EnergyGrid#consumeEnergy(float)} 瞬时抽取，不实现 {@code IEnergyConsumer} 接口。
 */
public class RegenLoaderSubsystem extends BasicSubsystem implements IAmmoSupplier {

    public final RegenLoaderSubsystemAttr attr;

    /** 当前弹药计数（网络同步） */
    private static final EntityDataAccessor<Integer> AMMO_COUNT_ID =
            SynchedEntityData.defineId(RegenLoaderSubsystem.class, EntityDataSerializers.INT);

    /** 再生进度（0.0 ~ 1.0+，累积超过 1.0 时产出一发） */
    private float regenProgress = 0f;

    /** 批量冷却剩余 tick（regenRoundByRound=false 时使用） */
    private int batchCooldownTicks = 0;

    /** 是否正在批量冷却中 */
    private boolean isBatchReloading = false;

    /** 再生延迟剩余 tick，上次输送弹药后需等待此时间才恢复再生 */
    private int regenDelayRemainingTicks = 0;

    // ——— 装填进度音效状态（仅客户端有效） ———

    /** 每个消费者上次已播放音效的进度阈值（0.0~1.0），避免同段重复触发 */
    private final Map<IAmmoConsumer, Float> lastPlayedProgressKeys = new HashMap<>();

    /** 批量冷却模式下上次已播放音效的进度阈值 */
    @Nullable
    private Float lastPlayedBatchProgressKey;

    /** 批量冷却的总tick数（用于计算进度百分比） */
    private int batchTotalTicks = 0;

    // ——— 多消费者支持 ———

    /** 每个消费者的输送计时器 */
    private final Map<IAmmoConsumer, Integer> deliveryTimers = new HashMap<>();

    /** 每个消费者的弹药是否已就绪 */
    private final Set<IAmmoConsumer> readyConsumers = new HashSet<>();

    public RegenLoaderSubsystem(ISubsystemHost owner, String name, RegenLoaderSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(AMMO_COUNT_ID, 0);
    }

    /** 获取当前弹药计数 */
    public int getAmmoCount() {
        return getSynchedData().get(AMMO_COUNT_ID);
    }

    /** 设置当前弹药计数 */
    private void setAmmoCount(int count) {
        getSynchedData().set(AMMO_COUNT_ID, count);
    }

    // ==================== IAmmoSupplier 实现 ====================

    @Override
    public boolean hasAmmo() {
        return getAmmoCount() > 0;
    }

    @Override
    public int getRemainingCount() {
        return getAmmoCount();
    }

    @Override
    @Nullable
    public ProjectileType getSuppliedType() {
        return ProjectileType.get(getLevel(), attr.staticAttribute.getProjectileType());
    }

    @Override
    public boolean isRoundByRound() {
        return attr.staticAttribute.getReloadTime() > 0;
    }

    @Override
    public int getReloadTimeTicks() {
        return (int) (attr.staticAttribute.getReloadTime() * 20f); // 秒 → tick
    }

    @Override
    public boolean canSupplyMultiple() {
        return attr.staticAttribute.isCanSupplyMultiple();
    }

    @Override
    public String getLabel() {
        return attr.staticAttribute.getProjectileType().toString();
    }

    @Override
    public boolean requestRound(IAmmoConsumer consumer) {
        if (deliveryTimers.containsKey(consumer) || readyConsumers.contains(consumer)) {
            return true; // 幂等
        }

        if (getAmmoCount() <= 0) return false;

        // 启动输送计时器（秒 → tick）
        int tickTime = (int) (attr.staticAttribute.getReloadTime() * 20f);
        if (tickTime > 0) {
            deliveryTimers.put(consumer, tickTime);
        } else {
            // 瞬时交付
            readyConsumers.add(consumer);
        }
        return true;
    }

    @Override
    public boolean isRoundReady(IAmmoConsumer consumer) {
        if (readyConsumers.contains(consumer)) {
            return true;
        } else if (deliveryTimers.containsKey(consumer)) {
            return false; // 未完成输送
        } else {
            requestRound(consumer);
            return false; // 未请求
        }
    }

    @Override
    @Nullable
    public ProjectileType consumeReadyRound(IAmmoConsumer consumer) {
        if (!readyConsumers.contains(consumer)) return null;
        readyConsumers.remove(consumer);
        lastPlayedProgressKeys.remove(consumer); // 弹药已消费，清理该消费者的音效追踪

        if (getAmmoCount() <= 0) return null;
        if (!getLevel().isClientSide()) // 仅服务端更新弹药计数
            setAmmoCount(getAmmoCount() - 1);

        // 弹药已输送，启动再生延迟计时器（若 regenDelay > 0）
        float delay = attr.staticAttribute.getRegenDelay();
        if (delay > 0f) {
            regenDelayRemainingTicks = (int) (delay * 20f); // 秒 → tick
            // 延迟刷新时重置再生进度；但弹药已为0时保留已有进度（避免空仓丢进度）
            if (getAmmoCount() > 0) {
                regenProgress = 0f;
            }
        }

        return ProjectileType.get(getLevel(), attr.staticAttribute.getProjectileType());
    }

    @Override
    public int getCapacity() {
        return attr.staticAttribute.getMagazineCapacity();
    }

    @Override
    public SupplierStatus getStatus(IAmmoConsumer consumer) {
        if (isBatchReloading || deliveryTimers.containsKey(consumer)) return SupplierStatus.RELOADING;
        if (getAmmoCount() <= 0) return SupplierStatus.EMPTY;
        return SupplierStatus.READY;
    }

    @Override
    public float getReloadProgress(IAmmoConsumer consumer) {
        int cap = attr.staticAttribute.getMagazineCapacity();
        if (cap <= 0) return 0f;
        if (isBatchReloading) {
            // 批量冷却模式：计算总冷却 tick 数，返回剩余比例
            float rpm = attr.staticAttribute.getRegenPerMinute();
            int totalTicks = rpm > 0 ? (int) (cap / rpm * 1200) : 1;
            return 1f - (float) batchCooldownTicks / totalTicks;
        }
        float reloadTicks = attr.staticAttribute.getReloadTime() * 20f;
        if (reloadTicks > 0) {
            if (deliveryTimers.containsKey(consumer))
                return 1f - (float) deliveryTimers.get(consumer) / reloadTicks;
        }
        // 非 batch 模式：当前余量 / 总容量
        return (float) getAmmoCount() / cap;
    }

    @Override
    public boolean canEject() {
        return getFreeCapacity() > 0;
    }

    @Override
    public void returnRound(ProjectileType type) {
        // 仅服务端更新弹药计数
        if (!getLevel().isClientSide() && getAmmoCount() < attr.staticAttribute.getMagazineCapacity()) {
            setAmmoCount(getAmmoCount() + 1);
        }
    }

    // ==================== 核心逻辑 ====================

    @Override
    public void onTick() {
        super.onTick();
        if (!isActive() || isDestroyed()) return;

        // ① 推进输送计时器
        Iterator<Map.Entry<IAmmoConsumer, Integer>> iter = deliveryTimers.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<IAmmoConsumer, Integer> entry = iter.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                iter.remove();
                readyConsumers.add(entry.getKey());
                lastPlayedProgressKeys.remove(entry.getKey()); // 输送完成，清理该消费者的音效追踪
            } else {
                entry.setValue(remaining);
            }
        }

        // ② 推进再生延迟计时器
        if (regenDelayRemainingTicks > 0) {
            regenDelayRemainingTicks--;
        }

        // ③ 弹药再生（延迟计时未结束时暂停再生）
        if (regenDelayRemainingTicks <= 0) {
            if (attr.staticAttribute.isRegenRoundByRound()) {
                // 随打随产模式
                tickRegenRoundByRound();
            } else {
                // 批量产出模式
                tickBatchReload();
            }
        }

        // ④ 装填进度分段音效（仅客户端）
        if (getLevel().isClientSide()) {
            tickProgressSounds();
        }
    }

    /**
     * 随打随产模式：每 tick 累积再生进度，达到 1.0 时产出一发。
     */
    private void tickRegenRoundByRound() {
        float regenPerMinute = attr.staticAttribute.getRegenPerMinute();
        if (regenPerMinute <= 0f) return;

        if (getAmmoCount() < attr.staticAttribute.getMagazineCapacity()) {
            // 20 tick/s × 60 s = 1200 tick/min
            regenProgress += regenPerMinute / 1200f;

            while (regenProgress >= 1.0f && getAmmoCount() < attr.staticAttribute.getMagazineCapacity()) {
                // 尝试消耗能量
                float energyCost = attr.staticAttribute.getEnergyCostPerRound();
                if (energyCost > 0f) {
                    if (!tryConsumeEnergy(energyCost)) {
                        break; // 能量不足，暂停再生
                    }
                }
                if (!getLevel().isClientSide()) // 仅服务端更新弹药计数
                    setAmmoCount(getAmmoCount() + 1);
                regenProgress -= 1.0f;
            }
        }
    }

    /**
     * 批量产出模式：弹仓全空后触发冷却，冷却结束后一次性回满。
     */
    private void tickBatchReload() {
        if (getAmmoCount() == 0 && !isBatchReloading) {
            // 触发批量再生
            float regenPerMinute = attr.staticAttribute.getRegenPerMinute();
            if (regenPerMinute <= 0f) return;

            // 扣除整批能量
            float totalEnergy = attr.staticAttribute.getMagazineCapacity() * attr.staticAttribute.getEnergyCostPerRound();
            if (totalEnergy > 0f) {
                if (!tryConsumeEnergy(totalEnergy)) {
                    return; // 能量不够整批，等下一 tick 再试
                }
            }

            // 计算冷却时间：弹仓容量 / 每分钟再生次数 × 1200 tick
            batchCooldownTicks = (int) (attr.staticAttribute.getMagazineCapacity() / regenPerMinute * 1200);
            if (batchCooldownTicks < 1) batchCooldownTicks = 1;
            batchTotalTicks = batchCooldownTicks;
            isBatchReloading = true;
            lastPlayedBatchProgressKey = null; // 新一批冷却开始，重置音效进度追踪
        }

        if (isBatchReloading) {
            batchCooldownTicks--;

            // 批量冷却进度音效（在isBatchReloading翻转为false之前检查，确保播放到"1.0"）
            if (getLevel().isClientSide()) {
                checkBatchProgressSound();
            }

            if (batchCooldownTicks <= 0) {
                isBatchReloading = false;
                setAmmoCount(attr.staticAttribute.getMagazineCapacity());
                lastPlayedBatchProgressKey = null;
            }
        }
    }

    @Override
    public Map<ProjectileType, Integer> getAmmoBreakdown() {
        if (getSuppliedType()!=null)
            return Map.of(getSuppliedType(), getAmmoCount());
        else return Map.of(); // 无弹药时返回空
    }

    // ==================== 装填进度音效 ====================

    /**
     * 处理每消费者的装填进度分段音效。<br>
     * 遍历 {@link #deliveryTimers} 中的每个消费者，按当前输送进度查找匹配的
     * 进度阈值key，跨越阈值时播放对应音效。
     * <p>
     * <b>调用线程：</b>主线程（{@link #onTick()}）。
     * </p>
     */
    private void tickProgressSounds() {
        Map<String, SoundEvent> sounds = attr.staticAttribute.getProgressSounds();
        if (sounds.isEmpty()) return;

        float totalTicks = attr.staticAttribute.getReloadTime() * 20f;
        if (totalTicks <= 0) return; // 瞬时交付无需进度音效

        for (Map.Entry<IAmmoConsumer, Integer> entry : deliveryTimers.entrySet()) {
            IAmmoConsumer consumer = entry.getKey();
            float progress = 1f - (float) entry.getValue() / totalTicks;
            Float lastKey = lastPlayedProgressKeys.get(consumer);
            Float matchedKey = findBestProgressKey(sounds, progress);
            if (matchedKey != null && !matchedKey.equals(lastKey)) {
                lastPlayedProgressKeys.put(consumer, matchedKey);
                playProgressSound(sounds.get(String.valueOf(matchedKey)));
            }
        }

        // 清理已不在输送中的消费者追踪记录
        lastPlayedProgressKeys.keySet().removeIf(c -> !deliveryTimers.containsKey(c));
    }

    /**
     * 检查批量冷却模式下的进度音效。<br>
     * 由 {@link #tickBatchReload()} 在 batchCooldownTicks 递减后、
     * isBatchReloading 翻转前调用，确保跨越"1.0"阈值时能播放完成音效。
     * <p>
     * <b>调用线程：</b>主线程（{@link #onTick()} → {@link #tickBatchReload()}）。
     * </p>
     */
    private void checkBatchProgressSound() {
        Map<String, SoundEvent> sounds = attr.staticAttribute.getProgressSounds();
        if (sounds.isEmpty() || batchTotalTicks <= 0) return;

        float progress = 1f - (float) batchCooldownTicks / batchTotalTicks;
        Float matchedKey = findBestProgressKey(sounds, progress);
        if (matchedKey != null && !matchedKey.equals(lastPlayedBatchProgressKey)) {
            lastPlayedBatchProgressKey = matchedKey;
            playProgressSound(sounds.get(String.valueOf(matchedKey)));
        }
    }

    /**
     * 在进度音效映射中查找 ≤ 当前进度的最大key。
     *
     * @param sounds          进度音效映射（key=进度浮点字符串）
     * @param currentProgress 当前进度（0.0~1.0）
     * @return 最佳匹配的进度阈值，无匹配时返回 null
     */
    @Nullable
    private static Float findBestProgressKey(Map<String, SoundEvent> sounds, float currentProgress) {
        return sounds.keySet().stream()
            .map(k -> {
                try { return Float.parseFloat(k); }
                catch (NumberFormatException e) { return Float.NaN; }
            })
            .filter(k -> !Float.isNaN(k) && k <= currentProgress)
            .max(Float::compareTo)
            .orElse(null);
    }

    /**
     * 播放单次进度音效。<br>
     * 使用 {@link SpreadingSoundHelper#playSpreadingSound} 在子系统位置播放，
     * 带多普勒速度传播。
     *
     * @param sound 要播放的音效，null 时静默跳过
     */
    private void playProgressSound(@Nullable SoundEvent sound) {
        if (sound == null || !getLevel().isClientSide()) return;
        ((TaskSubmitOffice) getLevel()).submitImmediateTask(
            PPhase.ALL,
            () -> {
                SpreadingSoundHelper.playSpreadingSound(
                    getLevel(), sound, SoundSource.NEUTRAL,
                    SparkMathKt.toVec3(getSubPart().getPosition()),
                    SparkMathKt.toVec3(getSubPart().getLinearVelocity()),
                    1.0f, 1.0f
                );
                return null;
            }
        );
    }

    /**
     * 尝试从 EnergyGrid 消耗能量。
     *
     * @param amount 请求消耗的能量 (J)
     * @return true 表示实际消耗到了指定能量
     */
    private boolean tryConsumeEnergy(float amount) {
        EnergyGrid grid = getOwner().getEnergyGrid();
        if (grid == null) return false;
        float consumed = grid.consumeEnergy(amount);
        return consumed >= amount - 0.001f;
    }

    /**
     * 获取剩余容量。
     */
    private int getFreeCapacity() {
        return attr.staticAttribute.getMagazineCapacity() - getAmmoCount();
    }

    // ==================== 握手机制 ====================

    @Override
    public void onAttach() {
        super.onAttach();
        handShake();
    }

    @Override
    public void onVehicleStructureChanged() {
        super.onVehicleStructureChanged();
        handShake();
    }

    /**
     * 弹药链握手：向发现频道发送空信号，通过回调发现同一载具内的 IAmmoConsumer。<br>
     * 下游消费者（Launcher 等）收到回调后通过 addSupplier() 注册此供给者。
     * 回调携带频道名，供 Launcher 按 ammo_inputs 频道分组。
     */
    protected void handShake() {
        for (String signalChannel : attr.discoveryOutputs.keySet()) {
            sendSignalToAllTargetsWithCallback(signalChannel, EmptySignal.INSTANCE, false);
        }
    }

    // ==================== 持久化 ====================

    @Override
    public void loadData(net.minecraft.nbt.CompoundTag data) {
        super.loadData(data);
        if (data.contains("ammo_count")) setAmmoCount(data.getInt("ammo_count"));
        if (data.contains("regen_progress")) regenProgress = data.getFloat("regen_progress");
        if (data.contains("batch_cooldown")) batchCooldownTicks = data.getInt("batch_cooldown");
        if (data.contains("is_batch_reloading")) isBatchReloading = data.getBoolean("is_batch_reloading");
        if (data.contains("regen_delay_remaining")) regenDelayRemainingTicks = data.getInt("regen_delay_remaining");
    }

    @Override
    public net.minecraft.nbt.CompoundTag saveData(net.minecraft.nbt.CompoundTag data) {
        super.saveData(data);
        data.putInt("ammo_count", getAmmoCount());
        data.putFloat("regen_progress", regenProgress);
        data.putInt("batch_cooldown", batchCooldownTicks);
        data.putBoolean("is_batch_reloading", isBatchReloading);
        data.putInt("regen_delay_remaining", regenDelayRemainingTicks);
        return data;
    }

    // ==================== 信号 ====================

    @Override
    public boolean acceptAllRoutingInput() {
        return true;
    }

    @Override
    public boolean acceptAllBroadcastInput() {
        return acceptAllRoutingInput();
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        Map<String, List<String>> result = new HashMap<>(attr.discoveryOutputs);
        return result;
    }

    @Override
    public SignalResult onSignalUpdated(String channelName, ISignalSender sender) {
        // 弹药发现频道回调：供给者发现下游消费者并注册自身，从回调值读取频道名
        if (channelName.equals("callback") && sender instanceof IAmmoConsumer consumer) {
            if (consumer instanceof AbstractSubsystem sub) {
                if (sub.getOwner().getSubPart().getPart().assembly
                        != this.getOwner().getSubPart().getPart().assembly) {
                    return SignalResult.PASS;
                }
            }
            // 从信号频道读取回调携带的频道名，若无法获取则使用 "unknown"
            Object callbackValue = getSignalChannel("callback").get(sender);
            String discoveryChannel = callbackValue instanceof String s ? s : "unknown";
            consumer.addSupplier(this, discoveryChannel);
            return SignalResult.CONSUME;
        }
        return super.onSignalUpdated(channelName, sender);
    }
}
