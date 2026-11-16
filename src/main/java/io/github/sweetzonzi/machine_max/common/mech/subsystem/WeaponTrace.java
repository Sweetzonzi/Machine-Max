package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.MachineMax;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 武器链路的时序插桩：开火、装填与客户端表现的记录开关。
 * <p>
 * <b>默认关闭</b>，只在被显式打开时写日志。开关是进程内的一个静态量——单人游戏与开发环境里，客户端与
 * 服务端在同一个 JVM 里，因此一条 {@code /debug weaponlog start} 能同时打开两侧的记录；专用服务器接独立
 * 客户端时只打开执行命令的那一侧，另一侧需要各自打开。
 * <p>
 * <b>记录格式</b>固定为 {@code [WDBG][S|C][t=<游戏时间>] 事件 字段=值 ...}，端别与游戏时间由本类补上，
 * 事件文本由调用点给出。两端在同一游戏时间上的同一事件据此对齐，不必先比对日志时间戳。
 * <p>
 * <b>只描述事实，不参与判定</b>：插桩点写"发生了什么"，不读也不改任何状态；关闭时整条调用是
 * 一次 volatile 读，参数不会被写进日志。
 */
public final class WeaponTrace {

    /** 进程内的开关；客户端与服务端各有一份类（同一个 JVM 里就是同一个静态量） */
    private static volatile boolean enabled = false;

    private WeaponTrace() {
    }

    /** 当前是否记录。 */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * 打开或关闭插桩。
     *
     * @param value 目标状态
     * @return 写入后的状态
     */
    public static boolean setEnabled(boolean value) {
        enabled = value;
        MachineMax.LOGGER.info("[WDBG] weapon trace {}", value ? "ENABLED" : "DISABLED");
        return value;
    }

    /**
     * 写一条记录；未打开时直接返回。
     *
     * @param level  事件所在的维度，用来区分端别并取游戏时间
     * @param format 事件文本，SLF4J 占位符
     * @param args   事件字段
     */
    public static void log(Level level, String format, Object... args) {
        if (!enabled) return;
        Object[] filled = new Object[args.length + 2];
        filled[0] = level.isClientSide() ? "C" : "S";
        filled[1] = level.getGameTime();
        System.arraycopy(args, 0, filled, 2, args.length);
        MachineMax.LOGGER.info("[WDBG][{}][t={}] " + format, filled);
    }

    /**
     * 弹药端身份的短写法：{@code <宿主 SubPart ID>:<子系统序号>}；{@code NONE} 写作 {@code -}。
     * <p>
     * 这是两端都能读到的结构身份，日志里因此可以直接比对"这一端认为的运输者／提供者"是不是同一台。
     */
    public static String ref(@Nullable AmmoRef ref) {
        return ref == null || ref.isNone() ? "-" : ref.subPartId() + ":" + ref.subsystemIndex();
    }
}
