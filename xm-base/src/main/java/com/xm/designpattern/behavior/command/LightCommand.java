package com.xm.designpattern.behavior.command;

/**
 * 命令实现类，可以自由替换
 *
 * @author XM
 * @date 2023/1/11
 */
public class LightCommand implements Command {

    private final Bubble bubble;
    private final Net net;

    public LightCommand(Bubble bubble, Net net) {
        this.bubble = bubble;
        this.net = net;
    }

    @Override
    public void execute() {
        net.on();
        bubble.on();
    }

    @Override
    public void undo() {
        bubble.off();
        net.off();
    }
}