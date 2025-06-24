package io.github.sweetzonzi.machinemax.web;

import com.cinemamod.mcef.MCEFBrowser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sweetzonzi.machinemax.external.MMDynamicRes;
import io.github.sweetzonzi.machinemax.external.js.hook.Hook;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;


@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME)
public class MMWebApp {
    private static final Logger logger = LoggerFactory.getLogger(MMWebApp.class);
    public static MCEFBrowser browser = null;
    private static Server server = null; // 静态 Server 实例，用于跨线程访问
    private static WebSocket webSocket = null; // 静态 WebSocket 实例，用于跨线程访问，发送信息
    public static int WEB_APP_PORT = Hook.replace(8195,"web_app_port");
    public static int WS_PORT = Hook.replace(8194,"websocket_port");
    public static String URL = Hook.replace("http://localhost:%s/".formatted(WEB_APP_PORT), "web_app_running_url");

    public static void sendPacket(String tag, Object... args) {
        try {
            if (webSocket != null) {
                HashMap<String, Object[]> payload = new HashMap<>();
                payload.put(tag, args);
                ObjectMapper objectMapper = new ObjectMapper();
                byte[] jsonBytes = objectMapper.writeValueAsBytes(payload);
                webSocket.send(jsonBytes);
            }
        } catch (Exception e) {
            logger.error("WsEvent 执行失败 ", e);
        }
    }

    public static void register() {
        System.out.println("启动网络");
        // 启动 Jetty 服务器线程
        Thread jettyThread = new Thread(() -> {
            try {
                Path webappPath = MMDynamicRes.NAMESPACE.resolve("webapp").resolve("test");

                server = new Server(WEB_APP_PORT); // 初始化 Jetty 服务器

                // 配置静态资源处理器
                ServletContextHandler context = new ServletContextHandler();
                context.setContextPath("/");
                context.setResourceBase(webappPath.toString());
                context.addServlet(DefaultServlet.class, "/")
                        .setInitParameter("cacheControl", "no-cache, no-store, must-revalidate"); // 强制不缓存

                server.setHandler(context);
                server.start(); // 启动服务器
                logger.info("Jetty 服务器启动成功，监听端口: {}", WEB_APP_PORT);
                server.join(); // 阻塞当前线程，等待服务器运行

            } catch (Exception e) {
                logger.error("Jetty 服务器启动失败", e);
            }
        }, "MM-Jetty-Server-Thread");
        Thread wsThread = new Thread(() -> {
            try {
                new WebSocketServer(new InetSocketAddress(WS_PORT)) {
                    @Override
                    public void onOpen(WebSocket conn, ClientHandshake handshake) {
                        webSocket = conn;
                        sendPacket("connected", true);
                    }

                    @Override
                    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                        webSocket = null;
                    }

                    @Override
                    public void onMessage(WebSocket conn, String message) {
                        webSocket = conn;
                    }

                    @Override
                    public void onError(WebSocket conn, Exception ex) {
                        webSocket = null;
                    }

                    @Override
                    public void onStart() {
                        logger.info("Websocket 服务器启动成功，监听端口: {}", WS_PORT);
                    }
                }.run();
            } catch (Exception e) {
                logger.error("Websocket 服务器启动失败", e);
            }
        }, "MM-WebSocket-Thread");

        jettyThread.start(); // 启动http线程
        wsThread.start(); // 启动ws线程
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