package com.xm.multithread;

/**
 *
 *
 * @author XM
 * @date 2025/5/18
 */
public class BasicThread extends Thread {


    @Override
    public void run() {
        System.out.println(getName() + " is exiting...");
    }


    public static void main(String[] args) throws InterruptedException {
        BasicThread basicThread = new BasicThread();
        basicThread.start();
        basicThread.wait();
    }
}

