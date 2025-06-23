package io.github.sweetzonzi.machinemax.web;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import io.github.sweetzonzi.machinemax.external.MMDynamicRes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;


@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME)
public class MMWebApp {
    private static final Logger logger = LoggerFactory.getLogger(MMWebApp.class);
    public static MCEFBrowser browser = null;
    private static Server server = null; // 静态 Server 实例，用于跨线程访问

    public static void register() {
        // 启动 Jetty 服务器线程
        Thread jettyThread = new Thread(() -> {
            try {
                Path webappPath = MMDynamicRes.NAMESPACE.resolve("webapp").resolve("test");
                logger.info("Webapp 路径: {}", webappPath);

                server = new Server(8195); // 初始化 Jetty 服务器

                // 配置静态资源处理器
                ServletContextHandler context = new ServletContextHandler();
                context.setContextPath("/");
                context.setResourceBase(webappPath.toString());
                context.addServlet(DefaultServlet.class, "/")
                        .setInitParameter("cacheControl", "no-cache, no-store, must-revalidate"); // 强制不缓存

                server.setHandler(context);
                server.start(); // 启动服务器
                logger.info("Jetty 服务器启动成功，监听端口: 8195");
                server.join(); // 阻塞当前线程，等待服务器运行

            } catch (Exception e) {
                logger.error("Jetty 服务器启动失败", e);
            }
        }, "Jetty-Server-Thread");

        jettyThread.start(); // 启动线程
    }

    /**
     * 游戏关闭时触发，优雅停止 Jetty 服务器
     */
    @SubscribeEvent
    public static void onGameShuttingDown(GameShuttingDownEvent event) {
        if (server != null && server.isRunning()) {
            logger.info("游戏即将关闭，正在停止 Jetty 服务器...");
            try {
                MMWebApp.browser.close();// 关闭浏览器
                server.stop(); // 停止服务器（不再接受新请求）
                server.wait(5000); // 等待最多 5 秒让服务器完成当前请求处理
                server.destroy();
                logger.info("Jetty 服务器已停止");
            } catch (Exception e) {
                logger.error("Jetty 服务器停止失败", e);
            } finally {
                server = null; // 清空引用，避免内存泄漏
            }
            if (browser != null) browser.close();
        }
    }
}