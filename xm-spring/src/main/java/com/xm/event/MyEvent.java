package com.xm.event;

import lombok.Getter;

/**
 * @author XM
 * @date 2025/9/11
 */
@Getter
public class MyEvent {

    private final String name;

    public MyEvent(String name) {
        this.name = name;
    }
}