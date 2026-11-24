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

@QuarkusTest
class QuarkusCommandBusIT {

    @Inject
    Instance<CommandBus> commandBuses;

    @Test
    void runtimeBusIsTheUniqueDefaultCdiCommandBus() {
        assertTrue(commandBuses.isResolvable());
        assertTrue(commandBuses.get() instanceof QuarkusCommandBus);
    }

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

    @Test
    void routesDiscoveredCommandExecutor() {
        Result<String> result = commandBuses.get().execute(new SampleCommand());

        assertTrue(result.isSuccess());
        assertEquals(SampleCommandHandler.HANDLED, result.getData());
    }

    @Test
    void rejectsUnknownCommand() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> commandBuses.get().execute(new UnknownCommand()));

        assertTrue(failure.getMessage().contains(UnknownCommand.class.getName()));
    }

    static class UnknownCommand implements Command {
    }
}
