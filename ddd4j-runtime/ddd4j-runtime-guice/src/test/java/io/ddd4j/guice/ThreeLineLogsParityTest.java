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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.common.base.Stopwatch;
import com.google.inject.Guice;
import io.ddd4j.core.constant.Constants;
import io.ddd4j.data.logs.ApiOperationLogProvider;
import io.swagger.v3.oas.annotations.Operation;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 检查实际 Guice 默认日志回调，不以非空 Bean 代替日志行为。 */
class ThreeLineLogsParityTest {
    @Test
    void guiceDefaultProviderEmitsSuccessAndFailureAccessLogs() throws Exception {
        ApiOperationLogProvider provider = Guice.createInjector(new Ddd4jLogsGuiceModule())
                .getInstance(ApiOperationLogProvider.class);
        Logger logger = (Logger) LoggerFactory.getLogger("io.ddd4j.data.logs.DefaultApiOperationLogProvider");
        Level previous = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        try {
            Method method = Endpoint.class.getMethod("handle", String.class);
            MethodSignature signature = (MethodSignature) Proxy.newProxyInstance(
                    MethodSignature.class.getClassLoader(), new Class<?>[]{MethodSignature.class},
                    (proxy, invoked, arguments) -> {
                        if ("getMethod".equals(invoked.getName())) { return method; }
                        if ("getName".equals(invoked.getName())) { return "handle"; }
                        throw new IllegalStateException("Unexpected signature call: " + invoked.getName());
                    });
            JoinPoint point = (JoinPoint) Proxy.newProxyInstance(
                    JoinPoint.class.getClassLoader(), new Class<?>[]{JoinPoint.class},
                    (proxy, invoked, arguments) -> {
                        if ("getSignature".equals(invoked.getName())) { return signature; }
                        if ("getArgs".equals(invoked.getName())) { return new Object[]{"parity-input"}; }
                        throw new IllegalStateException("Unexpected join point call: " + invoked.getName());
                    });
            Operation operation = method.getAnnotation(Operation.class);
            provider.afterReturing(point, operation, "ok", Stopwatch.createStarted());
            assertTrue(appender.list.stream().anyMatch(event -> event.getLevel() == Level.INFO
                    && Objects.nonNull(event.getMarker())
                    && Constants.ACCESS_MARKER.equals(event.getMarker().getName())
                    && event.getFormattedMessage().contains("Success")), "successful callback must log");
            appender.list.clear();
            provider.afterThrowing(point, operation, new IllegalStateException("parity-failure"), Stopwatch.createStarted());
            assertTrue(appender.list.stream().anyMatch(event -> event.getLevel() == Level.ERROR
                    && event.getFormattedMessage().contains("parity-failure")), "failure callback must log");
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previous);
            appender.stop();
        }
    }

    public static class Endpoint {
        @Operation(summary = "parity")
        public String handle(String value) { return value; }
    }
}
