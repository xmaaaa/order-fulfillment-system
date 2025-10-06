package com.xm.annotation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 测试注解类，通过反射获取
 *
 * @author XM
 * @date 2025/10/2
 */
@MyAnnotation(value = "测试类", level = 1)
public class Demo {

    private String text;

    @MyAnnotation(value = "测试方法", level = 2)
    public void test() {
        System.out.println("执行 test()");
    }

    public static void main(String[] args) throws Exception {
        // 获取类
        Class<Demo> clazz = Demo.class;
        if (clazz.isAnnotationPresent(MyAnnotation.class)) {
            MyAnnotation anno = clazz.getAnnotation(MyAnnotation.class);
            // 读取注解的值
            if (anno != null) {
                System.out.println("注解 value = " + anno.value());
                System.out.println("注解 level = " + anno.level());
            }
        }

        // 反射获取方法, 字段
        Method method = clazz.getMethod("test");
        Field field = clazz.getDeclaredField("text");
        field.setAccessible(true);

        // 判断方法上是否有 @MyAnnotation
        if (method.isAnnotationPresent(MyAnnotation.class)) {
            MyAnnotation anno = method.getAnnotation(MyAnnotation.class);

            // 读取注解的值
            if (anno != null) {
                System.out.println("注解 value = " + anno.value());
                System.out.println("注解 level = " + anno.level());
            }

            // 可以执行对应逻辑，比如日志、校验
            method.invoke(clazz.getDeclaredConstructor().newInstance());
        }
    }
}
