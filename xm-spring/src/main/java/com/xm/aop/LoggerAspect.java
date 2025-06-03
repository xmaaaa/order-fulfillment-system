package com.xm.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

/**
 * 日志切面Aspect，把一个或多个通知（Advice）+ 一个或多个切入点（Pointcut）组合起来
 * Weaving（织入）: 就是把切面代码插入到目标方法中的过程。默认使用 动态代理-- JDK Proxy（接口）或 CGLIB（无接口）
 *
 * @author XM
 * @date 2023/1/17
 */
@Component
@Aspect
public class LoggerAspect {

    /**
     * Pointcut 为切入点, 切入的方法叫join Point
     */
    @Pointcut("execution(* com.xm.service.*.*(..))")
    public void log() {
    }

    /**
     * Advice 通知， 包括before, After， @AfterReturning，@AfterThrowing, @Around
     *
     * @param joinPoint
     */
    @Before("log()")
    public void doBefore(JoinPoint joinPoint) {
        // .......
        // 辅助业务1
        // .......
        System.out.println("before");
    }

    @After("log()")
    public void doAfter() {
        // .......
        // 辅助业务2
        // .......
        System.out.println("after");
    }
}
