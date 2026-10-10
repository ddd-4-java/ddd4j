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
package io.ddd4j.web.core.error;

/**
 * 将运行时异常转换为稳定的 HTTP 与响应体语义。
 */
@FunctionalInterface
public interface WebExceptionTranslator {

    /**
     * 将异常翻译为面向 Web 响应的错误描述。
     *
     * @param throwable 待翻译的异常
     * @return 翻译得到的错误描述
     */
    WebError translate(Throwable throwable);
}
