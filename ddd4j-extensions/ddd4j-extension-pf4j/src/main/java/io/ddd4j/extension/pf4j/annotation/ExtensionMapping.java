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
package io.ddd4j.extension.pf4j.annotation;

import java.lang.annotation.*;

/**
 * 扩展点注解：用于标注某个功能扩展点的信息
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Inherited
public @interface ExtensionMapping {

    /**
     * id() 方法。
     * @return 结果字符串。
     */
    public String id() default "";

    /**
     * title() 方法。
     * @return 结果字符串。
     */
    public String title() default "";

    /**
     * ver() 方法。
     * @return 结果字符串。
     */
    public String ver() default "1.0.0";

    /**
     * desc() 方法。
     * @return 结果字符串。
     */
    public String desc() default "";

}
