package com.xm.multithread;

/**
 * @author XM
 * @date 2025/5/18
 */
public class BasicThread extends Thread {

    volatile boolean stop = false;

    @Override
    public void run() {
        while (!this.isInterrupted()) {
            System.out.println(getName() + " is running");

        }
        System.out.println(getName() + " is exiting...");
    }


}

