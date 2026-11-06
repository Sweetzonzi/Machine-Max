package io.github.sweetzonzi.machine_max.client.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import io.github.sweetzonzi.machine_max.MachineMax;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * 乘车录制的客户端开关：{@code /mmride record [start|stop]}。
 *
 * <p>录的是本机进程里才拿得到的那几份位姿（同步数据原文、渲染用的 {@code transform}、
 * {@code MMPartEntity} 与乘客实体的原始坐标），服务端命令无法判断哪个客户端正在看这辆载具，
 * 因此拆成客户端命令：两端各自在自己的终端里开关，不新增网络载荷，协议版本也不用动。</p>
 *
 * <p>客户端命令由 NeoForge 的 {@link RegisterClientCommandsEvent} 注册，只在客户端存在，
 * 不会发到服务端。</p>
 *
 * @author Sweetzonzi
 */
@EventBusSubscriber(modid = MachineMax.MOD_ID, value = Dist.CLIENT)
@OnlyIn(Dist.CLIENT)
public final class RideTelemetryCommand {

    private RideTelemetryCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("mmride")
                .then(Commands.literal("record")
                        .executes(ctx -> record(ctx, null))
                        .then(Commands.literal("start").executes(ctx -> record(ctx, true)))
                        .then(Commands.literal("stop").executes(ctx -> record(ctx, false)))));
    }

    /**
     * @param start {@code null} 表示切换
     */
    private static int record(CommandContext<CommandSourceStack> ctx, Boolean start) {
        CommandSourceStack source = ctx.getSource();
        boolean beginning = start != null ? start : !RideTelemetry.isRecording();
        if (beginning) {
            try {
                String description = RideTelemetry.start();
                source.sendSuccess(() -> Component.literal("乘车录制已开始：" + description), false);
                return 1;
            } catch (IllegalStateException error) {
                source.sendFailure(Component.literal(error.getMessage()));
                return 0;
            }
        }
        String description = RideTelemetry.stop();
        if (description == null) {
            source.sendFailure(Component.literal("乘车录制未在运行"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("乘车录制已停止：" + description), false);
        return 1;
    }
}
