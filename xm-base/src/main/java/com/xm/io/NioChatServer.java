package com.xm.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;

/**
 * 一个基于nio 的聊天服务器， 客户端可以使用 nc/telnet localhost 1234
 *
 * @author xm
 */
public class NioChatServer {

    private static final int PORT = 1234;
    private static final Map<SocketChannel, String> clientMap = new HashMap<>();

    public static void main(String[] args) throws IOException {
        Selector selector = Selector.open();
        ServerSocketChannel server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress(PORT));
        server.configureBlocking(false);
        server.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("Chat server started on port " + PORT);

        while (true) {
            selector.select();  // 阻塞直到有事件
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> it = keys.iterator();

            while (it.hasNext()) {
                SelectionKey key = it.next();
                it.remove();

                if (key.isAcceptable()) {
                    SocketChannel client = server.accept();
                    client.configureBlocking(false);
                    client.register(selector, SelectionKey.OP_READ);
                    clientMap.put(client, "User" + client.socket().getPort());
                    System.out.println(clientMap.get(client) + " joined.");

                } else if (key.isReadable()) {
                    SocketChannel client = (SocketChannel) key.channel();
                    ByteBuffer buffer = ByteBuffer.allocate(1024);
                    int read = client.read(buffer);
                    if (read > 0) {
                        buffer.flip();
                        String msg = new String(buffer.array(), 0, read);
                        System.out.println(clientMap.get(client) + ": " + msg.trim());

                        // 广播给其他客户端
                        broadcastMessage(client, clientMap.get(client) + ": " + msg);
                    } else {
                        // 客户端断开
                        System.out.println(clientMap.get(client) + " disconnected.");
                        clientMap.remove(client);
                        key.cancel();
                        client.close();
                    }
                }
            }
        }
    }

    private static void broadcastMessage(SocketChannel sender, String message) throws IOException {
        for (SocketChannel client : clientMap.keySet()) {
            if (!client.equals(sender)) {
                ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());
                client.write(buffer);
            }
        }
    }
}
