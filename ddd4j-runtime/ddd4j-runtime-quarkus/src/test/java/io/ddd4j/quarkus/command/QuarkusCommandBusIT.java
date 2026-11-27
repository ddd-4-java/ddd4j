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

import io.ddd4j.core.cqrs.command.Command;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.Result;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** \u901a\u8fc7 <code>commandBuses.isResolvable()</code>\u3001<code>commandBuses.get()</code>\u3001<code>Executors.newSingleThreadExecutor()</code> \u9a8c\u8bc1 <code>QuarkusCommandBus</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
@QuarkusTest
class QuarkusCommandBusIT {

    /** \u4fdd\u5b58\u6d4b\u8bd5\u9700\u8981\u6bd4\u8f83\u7684\u547d\u4ee4\u603b\u7ebf\u5b9e\u4f8b\u3002 */
    @Inject
    Instance<CommandBus> commandBuses;

    /** \u9a8c\u8bc1<code>commandBuses.isResolvable()</code>\u4e3a\u771f\uff0c<code>commandBuses.get() instanceof QuarkusCommandBus</code>\u4e3a\u771f\u3002 */
    @Test
    void runtimeBusIsTheUniqueDefaultCdiCommandBus() {
        assertTrue(commandBuses.isResolvable());
        assertTrue(commandBuses.get() instanceof QuarkusCommandBus);
    }

    /**
     * \u9a8c\u8bc1<code>requestContext.isActive()</code>\u4e3a\u5047\uff0c<code>bus.execute(new SampleCommand()).isSuccess()</code>\u4e3a\u771f\uff0c<code>((QuarkusCommandBus) bus).executeVoid(new SampleCommand()).isSuccess()</code>\u4e3a\u771f\u3002
     * @throws Exception \u7b49\u5f85\u5e76\u53d1\u64cd\u4f5c\u65f6\u5931\u8d25
     */
    @Test
    void runtimeBusActivatesAndRestoresRequestContext() throws Exception {
        ExecutorService dispatcher = Executors.newSingleThreadExecutor();
        try {
            dispatcher.submit(() -> {
                ManagedContext requestContext = Arc.container().requestContext();
                assertFalse(requestContext.isActive());
                CommandBus bus = commandBuses.get();
                assertTrue(bus.execute(new SampleCommand()).isSuccess());
                assertFalse(requestContext.isActive());
                assertTrue(((QuarkusCommandBus) bus).executeVoid(new SampleCommand()).isSuccess());
                assertFalse(requestContext.isActive());
            }).get(10, TimeUnit.SECONDS);
        } finally {
            dispatcher.shutdownNow();
        }
    }

    /** \u9a8c\u8bc1<code>result.isSuccess()</code>\u4e3a\u771f\uff0c<code>result.getData()</code> \u7b49\u4e8e <code>SampleCommandHandler.HANDLED</code>\u3002 */
    @Test
    void routesDiscoveredCommandExecutor() {
        Result<String> result = commandBuses.get().execute(new SampleCommand());

        assertTrue(result.isSuccess());
        assertEquals(SampleCommandHandler.HANDLED, result.getData());
    }

    /** \u9a8c\u8bc1<code>commandBuses.get().execute(new UnknownCommand())</code> \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>failure.getMessage().contains(UnknownCommand.class.getName())</code>\u4e3a\u771f\u3002 */
    @Test
    void rejectsUnknownCommand() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> commandBuses.get().execute(new UnknownCommand()));

        assertTrue(failure.getMessage().contains(UnknownCommand.class.getName()));
    }

    /** \u6ca1\u6709\u6ce8\u518c\u6267\u884c\u5668\u7684\u547d\u4ee4\u6837\u672c\uff0c\u7528\u6765\u9a8c\u8bc1\u547d\u4ee4\u5206\u53d1\u8fb9\u754c\u3002 */
    static class UnknownCommand implements Command {
    }
}
