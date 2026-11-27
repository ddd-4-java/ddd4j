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
package io.ddd4j.core.cqrs.command;

/**
 * 框架无关的 CQRS 命令总线。
 */
public interface CommandBus {
    /**
     * \u5c06\u547d\u4ee4\u4ea4\u7ed9\u5339\u914d\u7684\u6267\u884c\u5668\u5904\u7406\u3002
     * @param <R> \u547d\u4ee4\u7ed3\u679c\u6570\u636e\u7c7b\u578b
     * @param command \u5f85\u6267\u884c\u547d\u4ee4
     * @return \u547d\u4ee4\u6267\u884c\u7ed3\u679c
     */
    <R> Result<R> execute(Command command);
}
