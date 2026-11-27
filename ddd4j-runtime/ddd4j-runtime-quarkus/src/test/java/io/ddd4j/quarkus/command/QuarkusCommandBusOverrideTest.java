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
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** \u901a\u8fc7 <code>commandBuses.isResolvable()</code>\u3001<code>commandBuses.get()</code>\u3001<code>result.getMessage()</code> \u9a8c\u8bc1 <code>QuarkusCommandBusOverride</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
@QuarkusTest
@TestProfile(QuarkusCommandBusOverrideTest.BusinessProfile.class)
class QuarkusCommandBusOverrideTest {

    /** \u4fdd\u5b58\u6d4b\u8bd5\u9700\u8981\u6bd4\u8f83\u7684\u547d\u4ee4\u603b\u7ebf\u5b9e\u4f8b\u3002 */
    @Inject
    Instance<CommandBus> commandBuses;

    /** \u9a8c\u8bc1<code>commandBuses.isResolvable()</code>\u4e3a\u771f\uff0c<code>result.getMessage()</code> \u7b49\u4e8e <code>"Business command bus"</code>\u3002 */
    @Test
    void businessCommandBusOverridesRuntimeDefault() {
        assertTrue(commandBuses.isResolvable());
        Result<Void> result = commandBuses.get().execute(new SampleCommand());
        assertEquals("Business command bus", result.getMessage());
    }

    /** \u5b9e\u73b0 <code>QuarkusTestProfile</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>getConfigOverrides()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    public static class BusinessProfile implements QuarkusTestProfile {
        /**
         * \u901a\u8fc7 <code>Collections.singletonMap()</code> \u53d6\u5f97\u5f53\u524d\u6d4b\u8bd5\u9700\u8981\u7684\u7ed3\u679c\u3002
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>Collections.singletonMap("ddd4j.test.business-command-bus", "true")</code>
         */
        @Override
        public Map<String, String> getConfigOverrides() {
            return Collections.singletonMap("ddd4j.test.business-command-bus", "true");
        }
    }

    /** \u5411\u6d4b\u8bd5\u5bb9\u5668\u6ce8\u518c <code>execute()</code> \u7b49\u6d4b\u8bd5\u5165\u53e3\u6216\u7ec4\u4ef6\u3002 */
    @ApplicationScoped
    @IfBuildProperty(name = "ddd4j.test.business-command-bus", stringValue = "true")
    public static class BusinessCommandBus implements CommandBus {
        /** {@inheritDoc} */
        @Override
        public <R> Result<R> execute(Command command) {
            return Result.fail("Business command bus");
        }
    }
}
