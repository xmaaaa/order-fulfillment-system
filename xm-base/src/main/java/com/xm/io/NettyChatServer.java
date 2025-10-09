package com.xm.io;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.GlobalEventExecutor;

/**
 * netty 的实现版本聊天室
 *
 * @author XM
 * @date 2025/10/1
 */
public class NettyChatServer {

    private static final int PORT = 8888;
    private static final DefaultChannelGroup CHANNELS = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    public static void main(String[] args) throws Exception {

        // 创建一个负责接收连接的线程组（通常称为 bossGroup）
        // 参数 1 表示只用一个线程来监听客户端连接
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);

        // 创建一个负责处理已接收连接的线程组（workerGroup）
        // 默认线程数为 CPU 核心数 * 2，用于处理 I/O 读写事件
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            // 创建服务器启动类 ServerBootstrap
            ServerBootstrap serverBootstrap = new ServerBootstrap();

            // boss 负责接收连接，worker 处理 I/O
            serverBootstrap.group(bossGroup, workerGroup)
                    // 指定服务端 Channel 类型为 NIO
                    .channel(NioServerSocketChannel.class)
                    // 每个客户端连接都会创建一个 SocketChannel
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            // 获取这个客户端 Channel 的管道（Pipeline）
                            ChannelPipeline pipeline = ch.pipeline();
                            // 添加解码器：将 ByteBuf 自动转为 String
                            pipeline.addLast(new StringDecoder());
                            // 添加编码器：将 String 自动转为 ByteBuf 发送
                            pipeline.addLast(new StringEncoder());
                            // 添加自定义 Handler，处理业务逻辑（聊天消息广播）
                            // 业务线程池: 负责处理耗时操作（数据库、RPC、计算），避免 Worker 阻塞
                            EventExecutorGroup businessGroup = new DefaultEventExecutorGroup(64);
                            pipeline.addLast(businessGroup, new ChatServerHandler());
                        }
                    });

            // 绑定端口并同步等待成功，启动服务器
            ChannelFuture channelFuture = serverBootstrap.bind(PORT).sync();
            System.out.println("Chat server started on port " + PORT);
            // 等待服务器 Channel 关闭（通常不会主动关闭，除非调用 channel.close()）
            channelFuture.channel().closeFuture().sync();
        } finally {
            // 优雅关闭线程组，释放资源
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    /**
     * 业务处理handler，注意一个handler对应一个ChannelHandlerContext
     * EventExecutorGroup 绑定这个handler
     */
    private static class ChatServerHandler extends SimpleChannelInboundHandler<String> {

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            CHANNELS.add(ctx.channel());
            System.out.println("Client connected: " + ctx.channel().remoteAddress());
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
            System.out.println("Received message: " + msg);
            for (Channel channel : CHANNELS) {
                if (channel != ctx.channel()) {
                    channel.writeAndFlush(msg);
                }
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            CHANNELS.remove(ctx.channel());
            System.out.println("Client disconnected: " + ctx.channel().remoteAddress());
        }
    }
}
