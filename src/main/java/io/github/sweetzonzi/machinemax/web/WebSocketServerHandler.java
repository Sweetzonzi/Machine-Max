package io.github.sweetzonzi.machinemax.web;


import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.eclipse.jetty.websocket.common.WebSocketFrame;
import org.eclipse.jetty.websocket.common.frames.TextFrame;

// 继承 SimpleChannelInboundHandler，指定处理 TextWebSocketFrame 类型（文本消息）
public class WebSocketServerHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame msg) throws Exception {
        // 获取客户端发送的消息内容
        String clientMessage = msg.getPayloadAsUTF8();
        System.out.println("get Msg: " + clientMessage);

        // 构造响应消息（可自定义逻辑）
        String response = "I got Your Msg: " + clientMessage;
        TextFrame frame = new TextFrame();
        frame.setPayload(response);
        // 发送响应（使用 TextWebSocketFrame 表示文本帧）
        ctx.writeAndFlush(frame);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        System.out.println("client connected: " + ctx.channel().remoteAddress());
    }
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("client disconnected: : " + ctx.channel().remoteAddress());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
    }
}