package com.xm.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * @author XM
 * @date 2025/7/6
 */
@Component
public class RunnerExtender implements ApplicationRunner, CommandLineRunner {

    @Override
    public void run(ApplicationArguments args) {
        System.out.println("test ApplicationRunner:" + Arrays.toString(args.getSourceArgs()));
    }

    @Override
    public void run(String... args) {
        // System.out.println("test CommandLineRunner:" + Arrays.toString(args));
    }
}
