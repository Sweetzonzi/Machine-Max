package io.github.sweetzonzi.machine_max.common.mech.explosion;

import com.jme3.math.Vector3f;
import net.minecraft.world.level.Level;

/**
 * 客户端火光的登记入口：起爆包被客户端接受后，把“起爆点 + 该次爆炸的火光参数”交给客户端实现。
 *
 * <p>本接口属于共通代码，因此它的方法签名里只有 {@link Level} 与 {@link Vector3f} 这类双端类型，
 * 不含任何 {@code net.minecraft.client} 下的类型——专用服务器加载 {@link ExplosionManager} 时
 * 会解析字段与方法描述符里的类型，一旦出现客户端类就会以
 * {@code Attempted to load class ... for invalid dist DEDICATED_SERVER} 中止启动。</p>
 *
 * <p>默认实现为空：专用服务器与“客户端尚未注入实现”的情形下，起爆流程照常完成表现与粒子的登记，
 * 只是没有火光登记。实现由客户端入口 {@code MachineMaxClient} 在模组构造期注入一次。</p>
 *
 * <p>调用线程：主线程（起爆包由 {@code MainThreadPayloadHandler} 处理）。</p>
 */
@FunctionalInterface
public interface ExplosionLightSink {

    /**
     * 登记一次爆炸火光。
     *
     * @param level  收到起爆包的维度
     * @param origin 起爆点（世界坐标）；实现须自行复制，调用方不保证该实例的生命周期
     * @param params 该次爆炸的完整参数集，火光参数取 {@link ExplosionParams#light()}
     */
    void onDetonate(Level level, Vector3f origin, ExplosionParams params);
}
