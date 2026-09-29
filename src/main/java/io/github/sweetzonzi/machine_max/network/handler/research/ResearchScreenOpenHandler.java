package io.github.sweetzonzi.machine_max.network.handler.research;

import io.github.sweetzonzi.machine_max.client.network.ClientResearchHandler;
import io.github.sweetzonzi.machine_max.network.payload.research.ResearchScreenOpenPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 打开研究界面的载荷处理器。
 *
 * <p>载荷类型注册与处理器装配都在共通代码里完成，专用服务器同样会加载本类，因此界面类与
 * {@code Minecraft} 都不出现在本类的常量池中：打开界面的动作整体位于客户端类
 * {@link ClientResearchHandler}，只在载荷真正抵达客户端时才解析执行。</p>
 */
public class ResearchScreenOpenHandler {
    public static void handler(final ResearchScreenOpenPayload payload, final IPayloadContext context) {
        context.enqueueWork(ClientResearchHandler::openBlueprintResearchScreen);
    }
}
