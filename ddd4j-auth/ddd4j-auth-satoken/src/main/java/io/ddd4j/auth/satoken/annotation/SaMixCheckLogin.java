/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.auth.satoken.annotation;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 账号校验：在标注一个方法上时，要求前端必须提交相应的账号密码参数才能访问方法。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
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
     * \u4e34\u65f6Token\u662f\u5426\u662f\u4e00\u6b21\u6027\u4f7f\u7528\u7684\uff1b\u7528\u5b8c\u5373\u5f03\u7684
     *
     * @return \u662f\u5426\u4e00\u6b21\u6027\u4f7f\u7528
     */
    boolean throwaway() default false;

    /**
     * \u662f\u5426\u4f7f\u7528\u4e34\u65f6Token\u7684\u4fe1\u606f\u8fdb\u884c\u767b\u5f55\uff0c\u4ec5\u5f53 throwaway=false \u65f6\u6709\u6548
     *
     * @return \u662f\u5426\u4f7f\u7528\u4e34\u65f6 Token \u767b\u5f55
     */
    boolean login() default true;

}
