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

import com.google.inject.*;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.guice.command.GuiceCommandBus;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
@Slf4j
public class Ddd4jCommandGuiceModule extends AbstractModule {

    /** \u5728 Guice \u4e2d\u7ed1\u5b9a\u5171\u4eab\u547d\u4ee4\u6ce8\u518c\u53ca\u603b\u7ebf\u88c5\u914d\u3002 */
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

    /**
     * \u89e3\u6790 Guice \u7ed1\u5b9a\u4e0b\u7684\u547d\u4ee4\u6267\u884c\u5668\u3002
     * @param injector \u5f53\u524d Guice \u5e94\u7528\u6ce8\u5165\u5668
     * @param key \u5bb9\u5668\u7ed1\u5b9a\u952e\u6216\u5f85\u67e5\u627e\u7684\u6d88\u606f\u952e
     * @return \u5bb9\u5668\u89e3\u6790\u51fa\u7684\u547d\u4ee4\u6267\u884c\u5668
     */
    @SuppressWarnings("unchecked")
    private CommandExecutor<?> resolveExecutor(Injector injector, Key<?> key) {
        Object instance = injector.getInstance((Key<Object>) key);
        return instance instanceof CommandExecutor ? (CommandExecutor<?>) instance : null;
    }
}
