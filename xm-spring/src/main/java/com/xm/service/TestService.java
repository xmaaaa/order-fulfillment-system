package com.xm.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;

/**
 * @author hongwan
 * @date 2023/1/17
 */
public class TestService {

    @SentinelResource(value = "test", blockHandler = "handleBlock")
    public String test() {
        System.out.println("test");
        return "test";
    }


    public String handleBlock(BlockException ex) {
        return "系统繁忙，请稍后再试";
    }
}
