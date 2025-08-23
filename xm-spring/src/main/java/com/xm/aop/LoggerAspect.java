package com.xm.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.aop.aspectj.annotation.AnnotationAwareAspectJAutoProxyCreator;
import org.springframework.aop.framework.ReflectiveMethodInvocation;
import org.springframework.stereotype.Component;

/**
 * 日志切面Aspect，把一个或多个通知（Advice）+ 一个或多个切入点（Pointcut）组合起来
 * Weaving（织入）: 就是把切面代码插入到目标方法中的过程。默认使用 动态代理-- JDK Proxy（接口）或 CGLIB（无接口）
 *
 * Spring AOP 的底层是通过 {@link AnnotationAwareAspectJAutoProxyCreator} 这个 BeanPostProcessor 实现的。
 * 在 bean 初始化完成后，它会判断是否匹配切点，如果匹配，就用 JDK 动态代理或 CGLIB 生成代理对象。
 * 方法调用时走到代理逻辑，通过 {@link ReflectiveMethodInvocation} 维护一个拦截器链，把各种通知（@Before、@Around、@After 等）按顺序织入，最后再调用目标方法。
 *
 * @author XM
 * @date 2023/1/17
 */
@Component
@Aspect
public class LoggerAspect {

    /**
     * Pointcut 为切入点, 切入的方法叫join Point连接点
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
