package com.xm.annotation;

import java.lang.annotation.*;

/**
 * @author XM
 * @date 2025/10/2
 */
@Retention(RetentionPolicy.RUNTIME)  // 运行时可见
@Target({ElementType.METHOD, ElementType.TYPE}) // 作用目标：方法、类
public @interface MyAnnotation {
    String value();   // 注解的参数（必填）
    int level() default 1; // 带默认值的参数（可选）
}
