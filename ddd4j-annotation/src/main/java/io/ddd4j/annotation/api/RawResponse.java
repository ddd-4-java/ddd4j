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
package io.ddd4j.annotation.api;

import io.ddd4j.annotation.Contract;

import java.lang.annotation.*;

/**
 * 标记控制器方法跳过统一的 {@code R} 响应包装。
 *
 * <p>由 Spring MVC 适配层的 {@code GlobalResponseRAdvice} 识别，仅跳过该处理器的响应包装；
 * 返回值仍由框架正常序列化，不影响其他响应处理器或异常处理逻辑。</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Contract
@Target(ElementType.METHOD)
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface RawResponse {
}
