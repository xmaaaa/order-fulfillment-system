package com.xm.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;


/**
 * 一个基于nio 的回声服务器，返回输入值， 客户端可以使用 nc/telnet localhost 1234
 *
 * @author xm
 */
public class NioEchoServer {
    private static final int PORT = 1234;

    public static void main(String[] args) throws IOException {
        // 1. 打开 Selector 和 ServerSocketChannel
        Selector selector = Selector.open();
        ServerSocketChannel serverChannel = ServerSocketChannel.open();

        // 2. 绑定端口，设置非阻塞
        serverChannel.bind(new InetSocketAddress(PORT));
        serverChannel.configureBlocking(false);

        // 3. 注册 accept 事件
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("Echo Server started on port " + PORT);

        ByteBuffer buffer = ByteBuffer.allocate(1024);

        while (true) {
            // 4. 等待事件发生
            selector.select();

            // 5. 处理事件
            Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();

            while (keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();
                keyIterator.remove();

                // 6. 处理新连接
                if (key.isAcceptable()) {
                    ServerSocketChannel server = (ServerSocketChannel) key.channel();
                    SocketChannel client = server.accept();
                    client.configureBlocking(false);
                    client.register(selector, SelectionKey.OP_READ);
                    System.out.println("Connected to client: " + client.getRemoteAddress());
                }

                // 7. 读取客户端数据
                else if (key.isReadable()) {
                    SocketChannel client = (SocketChannel) key.channel();
                    buffer.clear();
                    int bytesRead = 0;
                    try {
                        bytesRead = client.read(buffer);
                    } catch (IOException e) {
                        // 读异常，关闭连接
                        key.cancel();
                        client.close();
                        System.out.println("Closed connection due to read error");
                        continue;
                    }

                    if (bytesRead == -1) {
                        // 客户端关闭连接
                        key.cancel();
                        client.close();
                        System.out.println("Client disconnected");
                        continue;
                    }

                    // 8. 回写客户端数据（Echo）
                    buffer.flip();
                    client.write(buffer);
                }
            }
        }
    }
}
