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
package io.ddd4j.data.cqrs.quarkus;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import io.ddd4j.data.cqrs.CommandHandler;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;

/**
 * 样例命令处理器（集成测试专用）：模拟集成方业务中的执行器——
 * {@code @ApplicationScoped} 刻板（ArC 发现键）＋{@code @CommandHandler} 发现注解
 * （声明性元数据，见 {@code QuarkusCommandBus} javadoc 的发现等价性注记），
 * 由真实 ArC 容器在索引中发现装配。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@ApplicationScoped
@CommandHandler(SampleCommand.class)
class SampleCommandHandler implements CommandExecutor<SampleCommand> {

    /**
     * execute 返回的载荷标记（IT 断言「真实路由到本 Handler」用）。
     */
    static final String HANDLED = "handled-by-quarkus-handler";

    @Override
    public Set<Class<? extends Command>> supportedCommands() {
        return Set.of(SampleCommand.class);
    }

    @Override
    public Result execute(SampleCommand command) {
        return Result.ok(HANDLED);
    }
}
