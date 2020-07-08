package com.atguigu.gmall.common.cache;


import java.lang.annotation.*;

@Target(ElementType.METHOD)// 表示此注解在方法上使用
@Retention(RetentionPolicy.RUNTIME)// 注表示解的声明周期
@Documented
public @interface GmallCache {
    String prefix() default "cache";
}
