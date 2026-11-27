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
package io.ddd4j.quarkus.command;

import io.ddd4j.core.api.R;
import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 通过 <code>commandBuses.isResolvable()</code>、<code>commandBuses.get()</code>、<code>Executors.newSingleThreadExecutor()</code> 验证 <code>QuarkusCommandBus</code> 的运行行为。
 */
@QuarkusTest
class QuarkusCommandBusIT {

    /**
     * 保存测试需要比较的命令总线实例。
     */
    @Inject
    Instance<CommandBus> commandBuses;

    /**
     * 验证<code>commandBuses.isResolvable()</code>为真，<code>commandBuses.get() instanceof QuarkusCommandBus</code>为真。
     */
    @Test
    void runtimeBusIsTheUniqueDefaultCdiCommandBus() {
        assertTrue(commandBuses.isResolvable());
        assertTrue(commandBuses.get() instanceof QuarkusCommandBus);
    }

    /**
     * 验证<code>requestContext.isActive()</code>为假，<code>bus.execute(new SampleCommand()).isOk()</code>为真，<code>((QuarkusCommandBus) bus).executeVoid(new SampleCommand()).isOk()</code>为真。
     *
     * @throws Exception 等待并发操作时失败
     */
    @Test
    void runtimeBusActivatesAndRestoresRequestContext() throws Exception {
        ExecutorService dispatcher = Executors.newSingleThreadExecutor();
        try {
            dispatcher.submit(() -> {
                ManagedContext requestContext = Arc.container().requestContext();
                assertFalse(requestContext.isActive());
                CommandBus bus = commandBuses.get();
                assertTrue(bus.execute(new SampleCommand()).isOk());
                assertFalse(requestContext.isActive());
                assertTrue(((QuarkusCommandBus) bus).executeVoid(new SampleCommand()).isOk());
                assertFalse(requestContext.isActive());
            }).get(10, TimeUnit.SECONDS);
        } finally {
            dispatcher.shutdownNow();
        }
    }

    /**
     * 验证<code>result.isOk()</code>为真，<code>result.getData()</code> 等于 <code>SampleCommandHandler.HANDLED</code>。
     */
    @Test
    void routesDiscoveredCommandExecutor() {
        R<String> result = commandBuses.get().execute(new SampleCommand());

        assertTrue(result.isOk());
        assertEquals(SampleCommandHandler.HANDLED, result.getData());
    }

    /**
     * 验证<code>commandBuses.get().execute(new UnknownCommand())</code> 抛出 <code>IllegalStateException.class</code>，<code>failure.getMessage().contains(UnknownCommand.class.getName())</code>为真。
     */
    @Test
    void rejectsUnknownCommand() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> commandBuses.get().execute(new UnknownCommand()));

        assertTrue(failure.getMessage().contains(UnknownCommand.class.getName()));
    }

    /**
     * 没有注册执行器的命令样本，用来验证命令分发边界。
     */
    static class UnknownCommand implements Command {
    }
}
