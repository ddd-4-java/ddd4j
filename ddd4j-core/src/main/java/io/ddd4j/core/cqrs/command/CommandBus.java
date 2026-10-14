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
     * 将命令交给匹配的执行器处理。
     *
     * @param <R> 命令结果数据类型
     * @param command 待执行命令
     * @return 命令执行结果
     */
    <R> Result<R> execute(Command command);
}
