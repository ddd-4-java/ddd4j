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
package io.ddd4j.data.cqrs.dropwizard;

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.cqrs.command.Result;
import io.ddd4j.data.cqrs.CommandHandler;

import java.util.Set;

/**
 * 样例命令处理器（集成测试专用）：模拟集成方业务中的执行器——
 * 经 {@code META-INF/services/io.ddd4j.core.cqrs.command.CommandExecutor}
 * 注册（ServiceLoader 发现键，见 {@code DropwizardCommandBus} javadoc 的发现注记）
 * ＋{@code @CommandHandler} 发现注解（声明性元数据）。
 *
 * <p>注意：本类必须 {@code public} 且带公有构造（ServiceLoader 的实例化契约，
 * 与 -spring/-quarkus/-micronaut 模块样例的包私有写法不同——那三者的容器
 * 发现机制不要求 public）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@CommandHandler(SampleCommand.class)
public class SampleCommandHandler implements CommandExecutor<SampleCommand> {

    /**
     * execute 返回的载荷标记（IT 断言「真实路由到本 Handler」用）。
     */
    static final String HANDLED = "handled-by-dropwizard-handler";

    @Override
    public Set<Class<? extends Command>> supportedCommands() {
        return Set.of(SampleCommand.class);
    }

    @Override
    public Result execute(SampleCommand command) {
        return Result.ok(HANDLED);
    }
}
