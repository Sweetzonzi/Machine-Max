package io.github.sweetzonzi.machine_max.client.target;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.item.prop.TargetDesignatorItem;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.part.AbstractPart;
import io.github.sweetzonzi.machine_max.common.mech.ray.RayQueryContext;
import io.github.sweetzonzi.machine_max.common.mech.ray.SubPartRayHit;
import io.github.sweetzonzi.machine_max.common.mech.target.Target;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.HitBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 指示器的客户端状态：手上拿着目标指示器时的候选集、以及当前锁定。
 *
 * <p>锁定是客户端状态：一个 {@link Target} 字段，没有服务端副本，也不产生协议
 * （见设计文档 §4.4）。每 tick 轮询手持物，手上不再是指示器时释放；目标自己不可锁时同样释放，
 * 不缓存布尔值。</p>
 *
 * <p>候选集每 tick 重建一次，只在手持指示器时建立。它是派生值：从 {@link ObjectManager#levelVehicles}
 * 现算，不接收增删通知，因此不会与真相不一致。碰撞箱之外的实体不进候选——原版区块索引已经做了
 * 空间查询，等实体现身时各消费者按自己的 AABB 现查。</p>
 *
 * <p>遮挡判定只处理地形：{@code level.clip} 打在目标点之前就丢弃这一项。载具互挡不在这里处理。</p>
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = MachineMax.MOD_ID, value = Dist.CLIENT)
public final class TargetDesignatorHandler {

    /** 右键锁定的射线长度，单位米。 */
    private static final double LOCK_RANGE = 512.0;
    /**
     * 标注的扫描半径，单位米。
     *
     * <p>取值范围同时是遮挡射线的开销上限：每 tick 每个候选各一条 {@code level.clip}，
     * 半径内的候选数量决定这条开销。再远的框只有几个像素，挡不挡都看不清。</p>
     */
    private static final double SCAN_RANGE = 256.0;

    @Nullable
    private static Target lock;
    private static final List<Target> CANDIDATES = new ArrayList<>();

    private TargetDesignatorHandler() {
    }

    /** 当前锁定，没有时为 {@code null}。 */
    @Nullable
    public static Target lock() {
        return lock;
    }

    /**
     * 本 tick 的候选目标：先每辆可锁载具一个框，再是该载具上声明了 {@code lockable} 的命中框。
     * 未手持指示器时为空。
     */
    public static List<Target> candidates() {
        return CANDIDATES;
    }

    /**
     * 把锁定点接到瞄准上行通道：有锁定且仍可锁时用目标真值点，否则原样返回准星点。
     *
     * <p>瞄准点是每 tick 上报一次的，因此取当 tick 末的状态（插值系数 1），不带插值。</p>
     *
     * <p>武器控制器与发射器不区分这个点来自准星还是锁定，因此不需要新载荷。</p>
     */
    public static Vec3 resolveAimPoint(Vec3 fallback) {
        Target current = lock;
        return current != null && current.isLockable() ? current.point(1) : fallback;
    }

    /**
     * 右键：准星命中的可锁碰撞箱成为锁定；命中的是没声明 {@code lockable} 的部位时改锁整辆载具；
     * 再次右键同一目标取消；射线没打到载具时锁定不变。
     */
    public static void onUse(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1f).scale(LOCK_RANGE));
        SubPartRayHit hit = RayQueryContext.begin(mc.level, player, eye, end, null)
                .nearestSubPartHitMatching(null);
        if (hit == null) return;
        if (blockedByTerrain(mc.level, player, eye, hit.point())) return;
        Target picked = pick(hit);
        if (picked == null) return;
        lock = lock == picked ? null : picked;
    }

    /** 命中框可锁就锁框，否则退回它所属的载具。 */
    @Nullable
    private static Target pick(SubPartRayHit hit) {
        if (hit.hitBox().isLockable()) return hit.hitBox();
        AbstractPart<?> part = hit.subPart().part;
        return part.assembly instanceof VehicleCore vehicle && vehicle.isLockable() ? vehicle : null;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        CANDIDATES.clear();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            lock = null;
            return;
        }
        if (!(player.getMainHandItem().getItem() instanceof TargetDesignatorItem)) {
            lock = null;
            return;
        }
        if (lock != null && !lock.isLockable()) lock = null;
        collect(mc, player);
    }

    private static void collect(Minecraft mc, LocalPlayer player) {
        Vec3 eye = player.getEyePosition();
        double rangeSqr = SCAN_RANGE * SCAN_RANGE;
        VehicleCore riding = ridingVehicle(player);
        for (VehicleCore vehicle : ObjectManager.levelVehicles.getOrDefault(mc.level, Map.of()).values()) {
            if (vehicle == riding || !vehicle.isLockable()) continue;
            Vec3 center = vehicle.point(1);
            if (center.distanceToSqr(eye) > rangeSqr) continue;
            if (blockedByTerrain(mc.level, player, eye, center)) continue;
            CANDIDATES.add(vehicle);
            for (RegularPart part : vehicle.partMap.values()) {
                for (SubPart subPart : part.subParts.values()) {
                    if (subPart.isRemoved || subPart.isDestroyed()) continue;
                    for (HitBox hitBox : subPart.hitBoxes.values()) {
                        if (!hitBox.isLockable()) continue;
                        Vec3 point = hitBox.point(1);
                        if (point.distanceToSqr(eye) > rangeSqr) continue;
                        if (blockedByTerrain(mc.level, player, eye, point)) continue;
                        CANDIDATES.add(hitBox);
                    }
                }
            }
        }
    }

    /** 玩家所骑乘载具的装配体，没有时返回 {@code null}；自己的载具不作为标注目标。 */
    @Nullable
    private static VehicleCore ridingVehicle(Player player) {
        return player.getVehicle() instanceof MMPartEntity part && part.subPart != null
                && part.subPart.part.assembly instanceof VehicleCore vehicle ? vehicle : null;
    }

    private static boolean blockedByTerrain(net.minecraft.world.level.Level level, Player player,
                                            Vec3 eye, Vec3 point) {
        BlockHitResult hit = level.clip(new ClipContext(eye, point,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        // 射线终点就是目标点本身，所以「打到点上方」等于「没被挡住」
        return hit.getType() != HitResult.Type.MISS
                && hit.getLocation().distanceToSqr(eye) < point.distanceToSqr(eye) - 1.0e-6;
    }
}
