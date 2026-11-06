package io.github.sweetzonzi.machine_max.client.debug;

import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyState;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import com.jme3.bullet.objects.PhysicsRigidBody;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.DriveTelemetry;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.IOException;
import java.util.Locale;

/**
 * 乘车链路的客户端录制：{@code /mmride record [start|stop]}。
 *
 * <p>只录本地玩家正在乘坐的那一个座位零件，把同一条链上的各级位姿逐 tick 打进行里，用来判断
 * “座位、乘客、模型、调试框”是不是同一份数据：</p>
 *
 * <ul>
 *   <li>{@code sub} —— 座位零件自己的位姿：{@code transform}/{@code oldTransform}（渲染用，由
 *       {@code DestroyableObject#clientSyncPose()} 每 tick 从同步数据刷新）与 {@code getPosition()}/
 *       {@code getRotation()}（同步数据原文，两者在客户端可能差一个 tick）。</li>
 *   <li>{@code entity} —— {@link MMPartEntity} 的实体位姿与 {@code xo/yo/zo}（原版 tick 内插值的起点）。
 *       它在 {@code baseTick} 里由 {@code subPart.getPosition()} 赋值。</li>
 *   <li>{@code box} —— {@code entity.getBoundingBox()}，即 F3+B 画出来的那个框；它用的刚体包围盒与质心
 *       是 {@code SubPart#postTick()} 在上一 tick 写进实体的缓存。</li>
 *   <li>{@code box_recalc} —— 同一 tick 现算的 {@code entity.makeBoundingBox()}；与 {@code box} 的差就是缓存滞后。</li>
 *   <li>{@code body} —— <b>刚体调试框</b>的采样源：{@code stateOf(body).lastTransform} 与 {@code stateOf(body).transform}，
 *       与 {@code ShapeRenderer} 画框时用的完全是同一对缓存（{@code lastTransform.lerp(transform, partialTicks)}）。
 *       这一对只在 {@code PhysicsLevel.requestStep()} 里刷新，是独立于网络 tick 的另一条时间轴，见
 *       {@link #emitBody}。</li>
 *   <li>{@code rider} —— 乘客（本地玩家）的实体位姿与 {@code xo/yo/zo}，由原版 {@code positionRider}
 *       每 tick 按 {@code entity} 的坐标放置。</li>
 *   <li>{@code rider_box} —— 乘客自己的 {@code getBoundingBox()}。</li>
 * </ul>
 *
 * <p><b>采样相位。</b>挂在 {@link ClientTickEvent.Post}：已经晚于 {@code LevelTickEvent.Pre} 的
 * {@code clientSyncPose}、实体 tick（{@code MMPartEntity.baseTick} 与乘客的 {@code rideTick}）、
 * {@code LevelTickEvent.Post} 的 {@code SubPart.postTick}，所以每行拿到的是该 tick 收尾时的最终值。</p>
 *
 * <p><b>仅在客户端。</b>不新增网络载荷，协议版本不动。文件落在 {@code <gamedir>/logs/machine_max/}，
 * 写盘复用 {@link DriveTelemetry} 的队列与主线程 flush。</p>
 *
 * <p>{@code tick} 是录制器自己的单调计数；{@code game_time} 是 {@code level.getGameTime()}，它在客户端
 * 每 20 tick 会被服务端的时间包覆盖一次，会重复或跳号，不能当索引。</p>
 *
 * <p>{@code size_*} 只对 {@code box} / {@code box_recalc} / {@code rider_box} 三类行有意义；不适用或无法
 * 取得的列一律写 {@code NaN}。</p>
 *
 * <p>行尾两列 {@code skipped} / {@code skips} 是 {@code PhysicsLevel} 的掉步计数（累计与连续），
 * 每 tick 都填同一份值：{@code body} 行的缓存对不前进时，用它们区分“这一 tick 就没收到新包”与
 * “这一 tick 的物理请求被整段跳过”。</p>
 *
 * @author Sweetzonzi
 */
@EventBusSubscriber(modid = MachineMax.MOD_ID, value = Dist.CLIENT)
@OnlyIn(Dist.CLIENT)
public final class RideTelemetry {

    /** 每行一列；位置与四元数统一 6 位小数。 */
    private static final String HEADER = String.join(",",
            "wall_ms", "nanos", "tick", "game_time", "seq", "partial", "level", "name",
            "x", "y", "z",
            "qx", "qy", "qz", "qw",
            "ox", "oy", "oz",
            "oqx", "oqy", "oqz", "oqw",
            "yaw", "pitch", "oyaw", "opitch",
            "rawx", "rawy", "rawz",
            "size_x", "size_y", "size_z",
            "skipped", "skips");

    private static DriveTelemetry sink;
    private static long tick;
    private static long seq;
    /** 本 tick 采到的物理层掉步计数，由 {@link #onClientTick} 每 tick 刷新，[#emit] 统一附加在行尾。 */
    private static long skippedRequests;
    private static int consecutiveSkips;

    private RideTelemetry() {
    }

    public static boolean isRecording() {
        return sink != null;
    }

    /**
     * 开始录制。
     *
     * @return 文件路径，供命令回显
     * @throws IllegalStateException 已在录制，或文件无法创建
     */
    public static String start() {
        if (sink != null) {
            throw new IllegalStateException("已在录制：" + sink.path());
        }
        try {
            sink = DriveTelemetry.open("ride", HEADER);
        } catch (IOException error) {
            throw new IllegalStateException("无法创建录制文件：" + error.getMessage());
        }
        tick = 0;
        seq = 0;
        return sink.path().toString();
    }

    /**
     * 结束录制。
     *
     * @return 文件路径与行数；未在录制时返回 {@code null}
     */
    public static String stop() {
        if (sink == null) {
            return null;
        }
        DriveTelemetry closed = sink;
        sink = null;
        closed.close();
        return closed.path() + "（" + closed.samples() + " 行）";
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (sink == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.isPaused()) {
            return;
        }
        if (!(player.getVehicle() instanceof MMPartEntity part) || part.subPart == null) {
            return;
        }
        SubPart sub = part.subPart;
        tick++;

        PhysicsLevel physicsLevel = sub.getPhysicsLevel();
        skippedRequests = physicsLevel == null ? -1 : physicsLevel.getSkippedRequests();
        consecutiveSkips = physicsLevel == null ? -1 : physicsLevel.getConsecutiveSkips();

        long wall = System.currentTimeMillis();
        long nanos = System.nanoTime();
        long gameTime = minecraft.level.getGameTime();
        double partial = minecraft.getTimer().getGameTimeDeltaPartialTick(false);

        Transform transform = sub.transform;
        Transform oldTransform = sub.oldTransform;
        Vector3f translation = transform.getTranslation();
        Quaternion rotation = transform.getRotation();
        Vector3f oldTranslation = oldTransform.getTranslation();
        Quaternion oldRotation = oldTransform.getRotation();
        Vector3f raw = sub.getPosition();

        emit(wall, nanos, gameTime, partial, "sub", part.subPartName,
                translation.x, translation.y, translation.z,
                rotation.getX(), rotation.getY(), rotation.getZ(), rotation.getW(),
                oldTranslation.x, oldTranslation.y, oldTranslation.z,
                oldRotation.getX(), oldRotation.getY(), oldRotation.getZ(), oldRotation.getW(),
                Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                raw.x, raw.y, raw.z,
                Double.NaN, Double.NaN, Double.NaN);

        emitEntity(wall, nanos, gameTime, partial, "entity", part, part.subPartName);

        emitBox(wall, nanos, gameTime, partial, "box", part, part.subPartName, part.getBoundingBox());
        emitBox(wall, nanos, gameTime, partial, "box_recalc", part, part.subPartName, part.makeBoundingBox());

        emitBody(wall, nanos, gameTime, partial, sub, part.subPartName);

        emitEntity(wall, nanos, gameTime, partial, "rider", player, "player");
        emitBox(wall, nanos, gameTime, partial, "rider_box", player, "player", player.getBoundingBox());

        sink.flush();
    }

    /** 结束世界或断开连接时收尾，避免录制器挂在已经卸载的世界上。 */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        String description = stop();
        if (description != null) {
            MachineMax.LOGGER.info("乘车录制随连接关闭结束：{}", description);
        }
    }

    private static void emitEntity(long wall, long nanos, long gameTime, double partial,
                                   String level, Entity entity, String name) {
        emit(wall, nanos, gameTime, partial, level, name,
                entity.getX(), entity.getY(), entity.getZ(),
                Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                entity.xo, entity.yo, entity.zo,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                entity.getYRot(), entity.getXRot(), entity.yRotO, entity.xRotO,
                Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN);
    }

    private static void emitBox(long wall, long nanos, long gameTime, double partial,
                                String level, Entity entity, String name, AABB box) {
        emit(wall, nanos, gameTime, partial, level, name,
                (box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5, (box.minZ + box.maxZ) * 0.5,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                entity.getX(), entity.getY(), entity.getZ(),
                Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN,
                box.maxX - box.minX, box.maxY - box.minY, box.maxZ - box.minZ);
    }

    /**
     * 刚体调试框的采样源：{@code ShapeRenderer} 画的是
     * {@code stateOf(body).lastTransform.lerp(transform, partialTicks)}，这里原样记下同一对缓存。
     *
     * <p>它与 {@code sub} 行（网络 tick 的 {@code oldTransform}/{@code transform}）是两条独立的时间轴：
     * 缓存只在 {@code PhysicsLevel.requestStep()} 里更新，而那一步在物理线程仍处于 RUNNING 时会整段跳过。</p>
     *
     * <p>不在这里读 {@code body.getTransform()}：刚体归物理线程所有，缓存的存在就是为了避开那次跨线程读取。</p>
     */
    private static void emitBody(long wall, long nanos, long gameTime, double partial,
                                 SubPart sub, String name) {
        PhysicsRigidBody body = sub.getBody();
        if (body == null) {
            return;
        }
        PhysicsBodyState state = PhysicsBodyExtensionKt.stateOf(body);
        Transform current = state.getTransform();
        Transform last = state.getLastTransform();
        Vector3f translation = current.getTranslation();
        Quaternion rotation = current.getRotation();
        Vector3f lastTranslation = last.getTranslation();
        Quaternion lastRotation = last.getRotation();
        emit(wall, nanos, gameTime, partial, "body", name,
                translation.x, translation.y, translation.z,
                rotation.getX(), rotation.getY(), rotation.getZ(), rotation.getW(),
                lastTranslation.x, lastTranslation.y, lastTranslation.z,
                lastRotation.getX(), lastRotation.getY(), lastRotation.getZ(), lastRotation.getW(),
                Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN);
    }

    private static void emit(long wall, long nanos, long gameTime, double partial,
                             String level, String name,
                             double x, double y, double z,
                             double qx, double qy, double qz, double qw,
                             double ox, double oy, double oz,
                             double oqx, double oqy, double oqz, double oqw,
                             double yaw, double pitch, double oyaw, double opitch,
                             double rawx, double rawy, double rawz,
                             double sizeX, double sizeY, double sizeZ) {
        StringBuilder line = new StringBuilder(224);
        line.append(wall).append(',').append(nanos).append(',').append(tick).append(',').append(gameTime)
                .append(',').append(++seq).append(',');
        number(line, partial).append(',').append(level).append(',').append(safeName(name));
        for (double value : new double[]{x, y, z, qx, qy, qz, qw, ox, oy, oz, oqx, oqy, oqz, oqw,
                yaw, pitch, oyaw, opitch, rawx, rawy, rawz, sizeX, sizeY, sizeZ}) {
            line.append(',');
            number(line, value);
        }
        line.append(',').append(skippedRequests).append(',').append(consecutiveSkips);
        sink.offer(line.toString());
    }

    private static StringBuilder number(StringBuilder line, double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return line.append("NaN");
        }
        return line.append(String.format(Locale.ROOT, "%.6f", value));
    }

    /** 零件名来自内容包，含逗号会破坏列数；这里只做最小替换。 */
    private static String safeName(String name) {
        if (name == null || name.isEmpty()) {
            return "-";
        }
        return name.indexOf(',') >= 0 ? name.replace(',', '_') : name;
    }
}
