package io.github.sweetzonzi.machine_max.client;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 客户端呈现位姿的推进点：每客户端 tick 一次，把全部可毁对象的样对沿时间轴推进一步。
 *
 * <h3>为什么挂在 {@link ClientTickEvent.Pre} 而不是 {@code LevelTickEvent.Pre}</h3>
 * {@code Minecraft#tick} 里的相位顺序是「{@code ClientTickEvent.Pre} → {@code level.tickEntities()}
 * → {@code LevelTickEvent.Pre} → {@code level.tick()}」。实体自己的一对 {@code xo/x} 在
 * {@code tickEntities} 里由 {@code MMPartEntity#baseTick} 从呈现位姿写入，而相机、乘客座位与实体
 * 调试框读的都是实体位置。挂在 {@code LevelTickEvent.Pre} 会把推进挪到实体 tick 之后，
 * 实体因此吃到上一 tick 的呈现位姿，实体框与模型相差一个 tick。
 *
 * <h3>为什么暂停时要跳过</h3>
 * 暂停时游戏时间不前进（{@code partialTick} 冻结），但 {@code Minecraft#tick} 仍在跑。
 * 缓冲的余量是按「真实前进的 tick」消耗的，暂停期间照常推进会把余量吃光，
 * 恢复后第一次掉包就退化成停帧。
 *
 * @author Sweetzonzi
 */
@EventBusSubscriber(modid = MachineMax.MOD_ID, value = Dist.CLIENT)
@OnlyIn(Dist.CLIENT)
public final class PartPoseDelayTick {

    private PartPoseDelayTick() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.isPaused()) return;
        ObjectManager.advanceClientPresentation(minecraft.level);
    }
}
