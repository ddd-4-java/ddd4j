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
package io.ddd4j.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.guice.command.GuiceCommandBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ddd4j Command Bus Guice module.
 * <p>
 * Provides a {@link CommandBus} binding that collects all {@link CommandExecutor} bindings
 * from the Guice Injector and routes commands to the appropriate executors.
 * <p>
 * Usage:
 * <pre>{@code
 * Injector injector = Guice.createInjector(
 *     new Ddd4jGuiceModule(),
 *     new Ddd4jCommandGuiceModule(),
 *     // bind your command executors here
 * );
 * CommandBus commandBus = injector.getInstance(CommandBus.class);
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
public class Ddd4jCommandGuiceModule extends AbstractModule {

    private static final Logger log = LoggerFactory.getLogger(Ddd4jCommandGuiceModule.class);

    @Override
    protected void configure() {
        // CommandBus binding is provided via @Provides method below
    }

    /**
     * Collect all CommandExecutor bindings from the Injector and build a GuiceCommandBus.
     *
     * @param injector the Guice Injector
     * @return the command bus instance
     */
    @Provides
    @Singleton
    public CommandBus commandBus(Injector injector) {
        List<CommandExecutor<?>> executors = new ArrayList<>();
        for (Binding<?> binding : injector.getAllBindings().values()) {
            Class<?> rawType = binding.getKey().getTypeLiteral().getRawType();
            // skip the CommandExecutor interface itself
            if (CommandExecutor.class.equals(rawType)) {
                continue;
            }
            if (CommandExecutor.class.isAssignableFrom(rawType)) {
                CommandExecutor<?> executor = resolveExecutor(injector, binding.getKey());
                if (Objects.nonNull(executor)) {
                    executors.add(executor);
                }
            }
        }
        log.info("Collected {} command executors from Guice Injector", executors.size());
        return new GuiceCommandBus(executors);
    }

    @SuppressWarnings("unchecked")
    private CommandExecutor<?> resolveExecutor(Injector injector, Key<?> key) {
        Object instance = injector.getInstance((Key<Object>) key);
        return instance instanceof CommandExecutor<?> executor ? executor : null;
    }
}
