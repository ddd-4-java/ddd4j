/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.annotation.api;

import io.ddd4j.annotation.BusinessType;

import java.lang.annotation.*;

/**
 * 操作日志注解
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
@Inherited
public @interface ApiOperationLog {

    /**
     * \u64cd\u4f5c\u6a21\u5757
     *
     * @return \u64cd\u4f5c\u6a21\u5757
     */
    String module() default "";

    /**
     * \u4e1a\u52a1\u540d\u79f0
     *
     * @return \u4e1a\u52a1\u540d\u79f0
     */
    String business() default "";

    /**
     * \u64cd\u4f5c\u7c7b\u578b
     *
     * @return \u64cd\u4f5c\u7c7b\u578b
     */
    BusinessType opt();

    /**
     * \u662f\u5426\u9a6c\u4e0a\u5904\u7406
     *
     * @return \u662f\u5426\u7acb\u5373\u5904\u7406
     */
    boolean immediate() default false;

}
