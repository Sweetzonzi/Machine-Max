package io.github.sweetzonzi.machine_max.common.mech.part;

import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.IPartAssembly;
import net.minecraft.world.level.Level;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;

/**
 * 客户端网络回包使用的装配体查询入口。
 *
 * <p>先查询 MM 载具，再按注册顺序询问外部宿主适配器。外部机体可直接查询自己的注册表，
 * 无需作为载具加入 {@link ObjectManager}。本类只查找既有对象，不创建、注册或延长装配体寿命。</p>
 * <p>适配器表按进程保留，适配器应通过传入世界查询当前会话的数据，避免捕获已卸载的世界。</p>
 */
public final class PartAssemblyLookup {
    private static final CopyOnWriteArrayList<BiFunction<Level, UUID, IPartAssembly>> ADAPTERS = new CopyOnWriteArrayList<>();
    /**
     * 查询适配器按进程共享，禁止实例化。
     */
    private PartAssemblyLookup() {
    }

    /**
     * 登记客户端装配体解析器，相同适配器对象只保留一次。
     *
     * <p>建议在模组初始化时注册一次。适配器在客户端查询调用线程执行，应返回已存在的装配体或
     * {@code null}，不应产生注册、构造等副作用；适配器抛出的异常会向调用者传播。</p>
     */
    public static void register(BiFunction<Level, UUID, IPartAssembly> adapter) {
        ADAPTERS.addIfAbsent(adapter);
    }

    /**
     * 按世界与 UUID 查找客户端装配体。
     *
     * <p>载具优先；适配器返回值还会核对世界对象身份和装配体 UUID，避免维度切换后命中残留对象。</p>
     *
     * @param level 当前客户端世界
     * @param id 要查询的装配体 UUID
     * @return 匹配的装配体；全部查询未命中时返回 {@code null}
     * @throws IllegalArgumentException 传入服务端世界时抛出
     */
    public static IPartAssembly findClient(Level level, UUID id) {
        if (!level.isClientSide()) throw new IllegalArgumentException("Client lookup requires client level");
        IPartAssembly vehicle = ObjectManager.clientAllVehicles.get(id);
        if (vehicle != null && vehicle.getLevel() == level) return vehicle;
        for (var adapter : ADAPTERS) {
            IPartAssembly assembly = adapter.apply(level, id);
            if (assembly != null && assembly.getLevel() == level && assembly.getAssemblyId().equals(id)) return assembly;
        }
        return null;
    }
}
