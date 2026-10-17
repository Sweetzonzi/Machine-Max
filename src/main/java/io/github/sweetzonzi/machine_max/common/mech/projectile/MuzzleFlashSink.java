package io.github.sweetzonzi.machine_max.common.mech.projectile;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 枪口火光的登记入口：开火发生时，把"枪口位置 + 这一发的火光参数"交给客户端实现。
 *
 * <p>本接口属于共通代码，因此它的方法签名里只有 {@link Level}、{@link Vec3} 与
 * {@link MuzzleFlashLight.Params} 这类双端类型，不含任何 {@code net.minecraft.client} 下的类型——
 * 专用服务器加载 {@link MuzzleFlashLight} 时会解析方法描述符里的类型，一旦出现客户端类就会以
 * {@code Attempted to load class ... for invalid dist DEDICATED_SERVER} 中止启动。</p>
 *
 * <p>默认实现为空：专用服务器与"客户端尚未注入实现"的情形下，开火流程照常完成弹道与音效，
 * 只是没有火光登记。实现由客户端入口 {@code MachineMaxClient} 在模组构造期注入一次。</p>
 *
 * <p>调用线程：主线程（发射器 {@code onTick} 的客户端分支、调试物品的 {@code use}）。</p>
 */
@FunctionalInterface
public interface MuzzleFlashSink {

    /**
     * 登记一次枪口火光。
     *
     * @param level    开火所在的维度
     * @param position 枪口世界坐标；实现须自行复制，调用方不保证该实例的生命周期
     * @param params   这一发的火光参数（半径、颜色、强度、寿命），已按弹种配置与口径求值
     */
    void onMuzzleFlash(Level level, Vec3 position, MuzzleFlashLight.Params params);
}
