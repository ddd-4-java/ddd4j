package io.ddd4j.auth.satoken.annotation;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 账号校验：在标注一个方法上时，要求前端必须提交相应的账号密码参数才能访问方法。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface SaMixCheckLogin {

    /**
     * 多账号体系下所属的账号体系标识，非多账号体系无需关注此值
     *
     * @return /
     */
    String type() default "";

    /**
     * 临时Token是否是一次性使用的；用完即弃的
     */
    boolean throwaway() default false;

    /**
     * 是否使用临时Token的信息进行登录，仅当 throwaway=false 时有效
     */
    boolean login() default true;

}