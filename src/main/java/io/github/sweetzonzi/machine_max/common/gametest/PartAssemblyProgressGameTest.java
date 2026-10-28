package io.github.sweetzonzi.machine_max.common.gametest;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.network.payload.assembly.PartAssemblyProgressSyncPayload;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;

/** 使用真实子零件与对象表验证接收端路由；物理刚体保持未入世。 */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PartAssemblyProgressGameTest {
    private PartAssemblyProgressGameTest() {
    }

    /**
     * 制造同步仅依赖子零件对象表，任意成员均能定位所属部件；注销后丢弃排队更新。
     *
     * <p>在服务端 GameTest 世界调用接收处理器，并用可控制队列代替网络上下文的主线程排队。
     * 这条用例验证查找与状态写入路径，不验证客户端网络传输或渲染。</p>
     */
    @GameTest(template = "part_foundation", batch = "mmPartAssemblyProgress", timeoutTicks = 100)
    public static void routesBySubPartIdWithoutVehicleRegistry(GameTestHelper helper) {
        var type = PartDefinitions.regular(false).values().stream()
                .filter(candidate -> candidate.variants.values().stream().anyMatch(variant -> variant.subParts.size() > 1))
                .findFirst().orElseThrow(() -> new IllegalStateException("需要具有多个子零件的普通部件定义"));
        String variant = type.variants.entrySet().stream().filter(entry -> entry.getValue().subParts.size() > 1)
                .findFirst().orElseThrow().getKey();
        RegularPart target = PartFactory.createRegular(type, variant, helper.getLevel());
        RegularPart other = null;
        try {
            other = PartFactory.createRegular(type, variant, helper.getLevel());
            target.subParts.values().forEach(ObjectManager::addDestroyableObject);
            other.subParts.values().forEach(ObjectManager::addDestroyableObject);
            var address = target.subParts.values().stream().filter(child -> child != target.rootSubPart)
                    .findFirst().orElseThrow();
            helper.assertTrue(target.assembly == null && other.assembly == null,
                    "目标只注册子零件对象，不提供装配体或载具查询入口");
            target.assemblingProgress = 0.8f;
            target.materialProgress = 7;
            target.assembling = false;
            ArrayDeque<Runnable> tasks = new ArrayDeque<>();
            IPayloadContext context = queuedContext(helper.makeMockPlayer(GameType.SURVIVAL), tasks);

            PartAssemblyProgressSyncPayload.handle(
                    new PartAssemblyProgressSyncPayload(address.getId(), 0.25f, 0, true), context);
            helper.assertTrue(target.assemblingProgress == 0.8f && tasks.size() == 1,
                    "查询和写入必须等待主线程任务执行");
            tasks.removeFirst().run();
            helper.assertTrue(target.assemblingProgress == 0.25f && target.materialProgress == 0 && target.assembling,
                    "非根子零件 ID 应更新所属部件的全部制造状态");
            helper.assertTrue(other.assemblingProgress == 1f && other.materialProgress == Integer.MAX_VALUE,
                    "其他已注册部件不应被更新");

            PartAssemblyProgressSyncPayload.handle(
                    new PartAssemblyProgressSyncPayload(address.getId(), 0.6f, 0, false), context);
            ObjectManager.removeDestroyableObject(address);
            tasks.removeFirst().run();
            helper.assertTrue(target.assemblingProgress == 0.25f && target.assembling,
                    "执行前已注销的子零件不得通过收包时缓存的部件引用继续更新");
            helper.succeed();
        } finally {
            target.discardUnattached();
            if (other != null) other.discardUnattached();
        }
    }

    /** 仅替代本处理器使用的玩家查询和任务排队；未使用的网络能力直接报错。 */
    private static IPayloadContext queuedContext(Player player, ArrayDeque<Runnable> tasks) {
        return (IPayloadContext) Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(),
                new Class<?>[]{IPayloadContext.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("player")) return player;
                    if (method.getName().equals("enqueueWork") && arguments[0] instanceof Runnable task) {
                        tasks.addLast(task);
                        return CompletableFuture.completedFuture(null);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
