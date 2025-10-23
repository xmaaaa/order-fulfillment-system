package com.xm.multithread;


/**
 * 协程，用户态，不是真正的cpu支持
 *
 * @author XM
 * @date 2025/9/28
 */
public class VirtualThreadDemo {

    public static void main(String[] args) throws InterruptedException {
        Thread virtualThread = Thread.ofVirtual().start(() -> {
            System.out.println("This is a virtual thread!");
        });
        virtualThread.join();  // 等待虚拟线程结束
    }
}
