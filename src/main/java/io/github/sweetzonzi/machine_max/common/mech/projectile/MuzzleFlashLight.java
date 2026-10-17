package io.github.sweetzonzi.machine_max.common.mech.projectile;

import io.github.sweetzonzi.machine_max.common.mech.projectile.type.BallisticProjectileType;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 枪口火光的共通侧入口：把"这一发开火"翻译成一组与渲染无关的数值，交给客户端注入的
 * {@link MuzzleFlashSink} 去登记。
 *
 * <p><b>为什么需要这一层。</b>开火点（{@code LauncherSubsystem#getMuzzleWorldPosition}）与
 * 调试物品都在共通代码里，而火光状态、年龄包络与提交进光源表全是客户端的事。共通侧只算出
 * "多大、多亮、多久、什么颜色"，客户端只负责"记下来并每帧提交"——这条分界让
 * {@code common/} 的类描述符里不出现任何 {@code client/} 类型，专用服务器照常加载开火路径。</p>
 *
 * <p><b>不经过网络。</b>弹种数据两端各自加载且必须一致，客户端已经持有 {@link ProjectileType#get}
 * 的同一条记录，因此火光参数由客户端按本机数据推导，不增加载荷、不提升 registrar 版本。
 * 这与开火音效、枪口粒子同属一条客户端表现链：服务端不"授权"这一帧的闪光。</p>
 *
 * <p><b>每 tick 至多登记一条。</b>同一 tick 的多发在同一点上视觉不可区分（与
 * {@code LauncherSubsystem#playFireAnimation} 同粒度），强度也不按发数累加——否则高射速武器的
 * 实际亮度会随射速漂移，把内容包的 {@code intensity} 变成一个无法调参的量。</p>
 *
 * <p>设计文档：{@code docs/plan/进行中/渲染-屏幕空间光照通用化设计.md} §2.4、§8.6。</p>
 */
public final class MuzzleFlashLight {

    // ===== 数值策略常量（改这些不需要重编着色器）=====

    /**
     * 按口径折算照射半径时的基准值（m）。
     *
     * <p>枪口焰的物理体只比炮口大一圈，照射半径取"火焰本身 + 一小段地面"：
     * 7.62mm → 1.38 m，20mm → 2.0 m，50mm → 3.5 m，120mm → 7.0 m。基准 1 m 保证最小的
     * 弹种也能照亮枪口周围的墙面与车体，每毫米 5 cm 让大口径按装药量自然拉开差距。</p>
     */
    private static final float RADIUS_BASE_M = 1.0f;

    /** 每毫米口径增加的照射半径（m）：5 cm/mm */
    private static final float RADIUS_PER_MM_M = 0.05f;

    /** 折算半径上限（m）：避免超大口径把枪口焰变成探照灯；显式 {@code radius} 不受它约束 */
    private static final float RADIUS_MAX_M = 8.0f;

    /** 客户端实现的登记入口；缺省空实现，由 {@code MachineMaxClient} 注入 */
    private static volatile MuzzleFlashSink sink = (level, position, params) -> {};

    private MuzzleFlashLight() {
    }

    /**
     * 一条枪口火光在提交前的最终数值：与渲染无关，只含客户端登记所需的标量。
     *
     * @param radius        照射半径（m），已按弹种口径折算或取显式值，保证 {@code > 0}
     * @param colorRgb      颜色，打包的 {@code 0xRRGGBB}；每个通道都在 {@code [0,255]}
     * @param intensity     枪口瞬间的峰值强度（无量纲），已由内容包给出
     * @param durationTicks 总寿命（游戏 tick），按 20 TPS 计
     */
    public record Params(float radius, int colorRgb, float intensity, int durationTicks) {

        /** @return 红色通道，{@code [0,1]} */
        public float red() {
            return ((colorRgb >> 16) & 0xFF) / 255f;
        }

        /** @return 绿色通道，{@code [0,1]} */
        public float green() {
            return ((colorRgb >> 8) & 0xFF) / 255f;
        }

        /** @return 蓝色通道，{@code [0,1]} */
        public float blue() {
            return (colorRgb & 0xFF) / 255f;
        }
    }

    /**
     * 注入客户端实现。由客户端入口在模组构造期调用一次；{@code null} 表示恢复空实现。
     *
     * @param newSink 客户端火光登记实现
     */
    public static void setSink(MuzzleFlashSink newSink) {
        sink = newSink != null ? newSink : (level, position, params) -> {};
    }

    /**
     * 登记一次开火的枪口火光。
     *
     * <p>仅在客户端产生任何作用：非客户端直接返回。弹种未启用、强度为 0、或者没有可折算的口径
     * （{@code caliber <= 0} 且未写显式半径）时也不登记——这几种情况都不会产生可见贡献。</p>
     *
     * @param level    开火所在的维度；必须为客户端维度
     * @param position 枪口世界坐标（与音效、枪口粒子取同一个 locator 位姿）
     * @param type     这一发使用的弹种
     */
    public static void emit(Level level, Vec3 position, BallisticProjectileType type) {
        if (!level.isClientSide()) return;
        ProjectileType.MuzzleFlash flash = type.getVisual().muzzleFlash();
        if (!flash.enabled()) return;

        float intensity = flash.intensity();
        if (intensity <= 0f) return;

        float radius = radiusFor(type.getCaliber(), flash.radius());
        if (radius <= 0f) return;

        sink.onMuzzleFlash(level, position,
            new Params(radius, packColor(flash.color()), intensity, flash.durationTicks()));
    }

    /**
     * 照射半径：显式值优先，否则 {@code R = 1 m + 5 cm × 口径(mm)}，上限 8 m。
     *
     * <p>口径是"装药量有多大"在数据里的唯一载体（{@code external.caliber}，单位 mm），因此也是
     * 枪口焰尺度的合理缺省：7.62mm → 1.38 m，20mm → 2.0 m，30mm → 2.5 m，50mm → 3.5 m，
     * 120mm → 7.0 m，155mm → 8.0 m（触顶）。内容包写 {@code muzzle_flash.radius} 时改用显式值，
     * 本方法与 8 m 上限只作用于缺省折算。</p>
     *
     * <p><b>次口径弹按弹芯口径折算</b>：{@code caliber} 对 120mm APFSDS 是 26 mm（弹芯），
     * 折算出的枪口焰会明显小于炮口本身的尺度。要按炮口而不是弹芯表达，就在该弹种写显式
     * {@code radius}——折算式不区分"口径"是炮口还是弹芯，这一点由内容作者决定。</p>
     *
     * @param caliberMm        弹种口径（mm）
     * @param configuredRadius 内容包半径：{@code > 0} 为显式值，{@link ProjectileType.MuzzleFlash#DERIVE_RADIUS}
     *                         或任何非正数表示按口径折算
     * @return 世界半径（m）；口径缺失且未配显式值时返回 0，表示不产生贡献
     */
    public static float radiusFor(float caliberMm, double configuredRadius) {
        if (configuredRadius > 0.0) return (float) configuredRadius;
        if (caliberMm <= 0f) return 0f;
        return Math.min(RADIUS_BASE_M + RADIUS_PER_MM_M * caliberMm, RADIUS_MAX_M);
    }

    /**
     * 把三个整数通道打包成 {@code 0xRRGGBB}，并收敛到 {@code [0,255]}。
     *
     * <p>{@link Vec3i} 的 codec 不限制取值范围（与 {@code tracer_color} 同一约定），越界值在这里
     * 收敛，避免移位把相邻通道串味。</p>
     */
    private static int packColor(Vec3i color) {
        int r = channel(color.getX());
        int g = channel(color.getY());
        int b = channel(color.getZ());
        return (r << 16) | (g << 8) | b;
    }

    private static int channel(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
