package io.github.sweetzonzi.machinemax.web;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public class WebSocketServer {

    private final int port;

    public WebSocketServer(int port) {
        this.port = port;
    }

    public void run() throws Exception {
        // 主事件循环组（处理连接请求）
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        // 工作事件循环组（处理 I/O 操作）
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class) // 使用 NIO 传输
             .handler(new LoggingHandler(LogLevel.INFO)) // 日志处理器（可选）
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 protected void initChannel(SocketChannel ch) {
                     // 构建处理链
                     ch.pipeline()
                      // 4. 自定义业务处理器（处理文本/二进制消息）
                      .addLast(new WebSocketServerHandler());
                 }
             });

            // 绑定端口并启动服务器
            ChannelFuture f = b.bind(port).sync();
            System.out.println("WebSocket listening on: " + port);

            // 等待服务器关闭
            f.channel().closeFuture().sync();
        } finally {
            // 优雅关闭事件循环组
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws Exception {
        new WebSocketServer(8194).run(); // 启动服务器（端口 8080）
    }
}