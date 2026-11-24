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
package io.ddd4j.sample.order.testkit;

import io.ddd4j.core.cqrs.readmodel.DefaultProjectionService;
import io.ddd4j.core.cqrs.readmodel.EventChunk;
import io.ddd4j.core.cqrs.readmodel.InMemoryProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.ProjectionRunner;
import io.ddd4j.core.cqrs.readmodel.ProjectionView;
import io.ddd4j.sample.order.application.AddOrderLineCommand;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.application.OrderReadModel;
import io.ddd4j.sample.order.application.OutboxMessage;
import io.ddd4j.sample.order.domain.Order;
import io.ddd4j.sample.order.domain.OrderQuery;
import io.ddd4j.sample.order.local.InMemoryOrderAdapters;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 控制夹具验证共用断言的检错能力；不是 HTTP、原生认证引擎或平台验收。 */
class SampleContractAssertionsTest {
    @Test
    void defaultUsesExistingBusinessWithoutOptionalRuntimeServices() {
        LocalDriver driver = new LocalDriver();
        SampleContractAssertions.defaultOrder(driver, "S00-DEFAULT");
        assertEquals(3, driver.scenario.adapters().pending(10).size());
    }

    @Test
    void defaultDetectsLostUpdate() {
        assertThrows(AssertionError.class, () -> SampleContractAssertions.defaultOrder(new LocalDriver() {
            @Override
            public void addLine(AddOrderLineCommand command) {
                // 故意丢弃更新，证明契约不只验证 create 的返回值。
            }
        }, "S00-LOST-UPDATE"));
    }

    @Test
    void defaultDetectsWrongQueryResults() {
        assertThrows(AssertionError.class, () -> SampleContractAssertions.defaultOrder(new LocalDriver() {
            @Override
            public List<OrderReadModel> query(OrderQuery query) {
                return Collections.emptyList();
            }
        }, "S00-BAD-QUERY"));
    }

    @Test
    void cqrsUsesSharedRunnerAndRecoversFromRealHandlerFailure() {
        CqrsDriver driver = new CqrsDriver();
        SampleContractAssertions.cqrsOrder(driver, "S00-CQRS");
        assertEquals(Arrays.asList(10L, 10L, 40L), driver.readPositions);
        assertEquals(1, driver.queryStore.query(new OrderQuery(null, null, 1, 100)).size()); // 一笔订单，重放不增加查询行。
    }

    @Test
    void cqrsDetectsCheckpointAdvancingOnFailure() {
        assertThrows(AssertionError.class, () -> SampleContractAssertions.cqrsOrder(new CqrsDriver() {
            @Override
            public void project() {
                try {
                    super.project();
                } catch (IllegalStateException failure) {
                    positions.updateProjectionPosition("orders", 40);
                    throw failure;
                }
            }
        }, "S00-LOST-EVENT"));
    }

    @Test
    void cqrsDetectsAbsentEvents() {
        assertThrows(AssertionError.class, () -> SampleContractAssertions.cqrsOrder(new CqrsDriver() {
            @Override
            public List<OutboxMessage> events(String orderId) {
                return Collections.emptyList();
            }
        }, "S00-NO-EVENT"));
    }

    @Test
    void cqrsDetectsMissingProjectionAfterRetry() {
        assertThrows(AssertionError.class, () -> SampleContractAssertions.cqrsOrder(new CqrsDriver() {
            @Override
            public Optional<OrderReadModel> projection(String orderId) {
                return Optional.empty();
            }
        }, "S00-NO-PROJECTION"));
    }

    @Test
    void rejectsUnknownAndMissingEngineBeforeCallingDriver() {
        for (String engine : Arrays.asList("unsupported", null)) {
            AuthDriver driver = new AuthDriver() {
                @Override
                public Optional<String> contextPrincipal() {
                    throw new AssertionError("driver touched before engine validation");
                }
            };
            AssertionError failure = assertThrows(AssertionError.class, () -> authenticate(engine, driver));
            assertEquals("unsupported auth scenario name", failure.getMessage());
            assertEquals(Collections.emptyList(), driver.requests);
        }
    }

    @Test
    void bothAuthNamesExerciseAllRequestBoundaries() {
        for (String engine : Arrays.asList("satoken", "shiro")) {
            AuthDriver driver = new AuthDriver();
            authenticate(engine, driver);
            assertEquals(Arrays.asList("login", "authenticated", "allowed", "denied", "failure", "logout",
                    "authenticated"), driver.requests);
        }
    }

    @Test
    void bothAuthNamesDetectAuthorizationBypass() {
        for (String engine : Arrays.asList("satoken", "shiro")) {
            assertThrows(AssertionError.class, () -> authenticate(engine, new AuthDriver() {
                @Override
                public String authorizedResource(String token, String permission) {
                    return "orders-for-alice";
                }
            }));
        }
    }

    @Test
    void bothAuthNamesDetectLogoutTokenReuse() {
        for (String engine : Arrays.asList("satoken", "shiro")) {
            assertThrows(AssertionError.class, () -> authenticate(engine, new AuthDriver() {
                @Override
                public void logout(String token) {
                    requests.add("logout");
                }
            }));
        }
    }

    @Test
    void bothAuthNamesDetectExceptionalContextLeak() {
        for (String engine : Arrays.asList("satoken", "shiro")) {
            assertThrows(AssertionError.class, () -> authenticate(engine, new AuthDriver() {
                @Override
                public void failingRequest(String token) {
                    context = "alice";
                    throw new IllegalStateException("injected business failure");
                }
            }));
        }
    }

    @Test
    void bothAuthNamesDetectSuccessfulRequestContextLeak() {
        for (String engine : Arrays.asList("satoken", "shiro")) {
            assertThrows(AssertionError.class, () -> authenticate(engine, new AuthDriver() {
                @Override
                public String authenticatedPrincipal(String token) {
                    String principal = super.authenticatedPrincipal(token);
                    context = principal;
                    return principal;
                }
            }));
        }
    }

    @Test
    void bothAuthNamesDetectWrongPrincipal() {
        for (String engine : Arrays.asList("satoken", "shiro")) {
            assertThrows(AssertionError.class, () -> authenticate(engine, new AuthDriver() {
                @Override
                public String authenticatedPrincipal(String token) {
                    return "another-user";
                }
            }));
        }
    }

    @Test
    void bothAuthNamesDetectDeniedRequestContextLeak() {
        for (String engine : Arrays.asList("satoken", "shiro")) {
            assertThrows(AssertionError.class, () -> authenticate(engine, new AuthDriver() {
                @Override
                public String authorizedResource(String token, String permission) {
                    try {
                        return super.authorizedResource(token, permission);
                    } catch (SecurityException denied) {
                        context = "alice";
                        throw denied;
                    }
                }
            }));
        }
    }

    @Test
    void bothAuthNamesDetectEmptyLoginToken() {
        for (String engine : Arrays.asList("satoken", "shiro")) {
            assertThrows(AssertionError.class, () -> authenticate(engine, new AuthDriver() {
                @Override
                public String login(String username, String password) {
                    return " ";
                }
            }));
        }
    }

    private static void authenticate(String engine, AuthSampleDriver driver) {
        SampleContractAssertions.authentication(engine, driver, "alice", "fixture-password", "alice",
                "order:read", "order:delete", "orders-for-alice");
    }

    /** 默认夹具只组合已有 OrderScenario，不构造 bus/ES/后台投影/MQ/auth。 */
    private static class LocalDriver implements OrderSampleDriver {
        final OrderScenario scenario = new OrderScenario();

        @Override
        public String create(CreateOrderCommand command) {
            return scenario.application().create(command).id();
        }

        @Override
        public void addLine(AddOrderLineCommand command) {
            scenario.application().addLine(command);
        }

        @Override
        public void pay(String orderId, String paymentKey) {
            scenario.application().pay(orderId, paymentKey);
        }

        @Override
        public OrderReadModel find(String orderId) {
            return scenario.application().find(orderId);
        }

        @Override
        public List<OrderReadModel> query(OrderQuery query) {
            return scenario.application().query(query);
        }
    }

    /** 事件是实际共享 Outbox 失效通知；独立查询仓储只由核心 Runner 的处理器写入。 */
    private static class CqrsDriver extends LocalDriver implements CqrsOrderSampleDriver {
        final DefaultProjectionService positions = new DefaultProjectionService(
                new InMemoryProjectionPositionRepository());
        final InMemoryOrderAdapters queryStore = new InMemoryOrderAdapters();
        final List<Long> readPositions = new ArrayList<>();
        private boolean fail;
        private final ProjectionRunner<OutboxMessage> runner;
        private final ProjectionView<OutboxMessage> view = new ProjectionView<OutboxMessage>() {
            @Override
            public String getName() {
                return "orders";
            }

            @Override
            public String getCron() {
                return "0/5 * * * * ?";
            }

            @Override
            public Collection<String> getEventTypes() {
                return Collections.emptyList();
            }

            @Override
            public void handleEvents(Collection<OutboxMessage> events) {
                if (fail) {
                    fail = false;
                    throw new IllegalStateException("injected projection write failure");
                }
                for (OutboxMessage event : events) {
                    queryStore.project(persisted(event.aggregateId()));
                }
            }
        };

        CqrsDriver() {
            positions.updateProjectionPosition("orders", 10);
            // 源位置预置 10，三个失效事件结束在 40；位置不等于筛选后的事件数量。
            runner = new ProjectionRunner<>(positions, (stream, from, size, types) -> {
                readPositions.add(from);
                return from < 40 ? new EventChunk<>(scenario.adapters().pending(100), 40)
                        : EventChunk.empty(from);
            });
        }

        @Override
        public OrderReadModel persisted(String orderId) {
            Order order = scenario.adapters().findById(orderId).orElseThrow(NoSuchElementException::new);
            return new OrderReadModel(order.id(), order.orderNo(), order.buyerId(), order.buyerName(),
                    order.status(), order.totalAmount().amount());
        }

        @Override
        public List<OutboxMessage> events(String orderId) {
            return scenario.adapters().pending(100).stream()
                    .filter(event -> orderId.equals(event.aggregateId()))
                    .collect(Collectors.toList());
        }

        @Override
        public Optional<OrderReadModel> projection(String orderId) {
            return queryStore.findProjectionById(orderId);
        }

        @Override
        public OrderReadModel find(String orderId) {
            return projection(orderId).orElseThrow(NoSuchElementException::new);
        }

        @Override
        public List<OrderReadModel> query(OrderQuery query) {
            return queryStore.query(query);
        }

        @Override
        public long position() {
            return positions.readProjectionPosition("orders");
        }

        @Override
        public void failNextProjection() {
            fail = true;
        }

        @Override
        public void project() {
            runner.runOnce(view);
        }
    }

    /** 仅验证断言，不模拟任何具体安全框架；请求 finally 清理可被故意破坏。 */
    private static class AuthDriver implements AuthSampleDriver {
        final List<String> requests = new ArrayList<>();
        final Map<String, String> sessions = new HashMap<>();
        String context;

        @Override
        public String login(String username, String password) {
            requests.add("login");
            sessions.put("fixture-token", username);
            return "fixture-token";
        }

        @Override
        public String authenticatedPrincipal(String token) {
            requests.add("authenticated");
            try {
                context = sessions.get(token);
                if (Objects.isNull(context)) {
                    throw new SecurityException("unauthenticated");
                }
                return context;
            } finally {
                context = null;
            }
        }

        @Override
        public String authorizedResource(String token, String permission) {
            requests.add("order:read".equals(permission) ? "allowed" : "denied");
            try {
                context = sessions.get(token);
                if (Objects.isNull(context) || !"order:read".equals(permission)) {
                    throw new SecurityException("forbidden");
                }
                return "orders-for-" + context;
            } finally {
                context = null;
            }
        }

        @Override
        public void logout(String token) {
            requests.add("logout");
            sessions.remove(token);
        }

        @Override
        public void failingRequest(String token) {
            requests.add("failure");
            try {
                context = sessions.get(token);
                throw new IllegalStateException("injected business failure");
            } finally {
                context = null;
            }
        }

        @Override
        public Optional<String> contextPrincipal() {
            return Optional.ofNullable(context);
        }
    }
}
