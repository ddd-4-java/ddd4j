/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.ddd4j.sample.javalin;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.sample.javalin.order.infrastructure.JavalinOrderAdapters;
import io.ddd4j.sample.order.jdbc.JdbcOrderSampleRuntime;
import io.ddd4j.sample.order.testkit.SampleHttpTransport;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import java.net.BindException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.SQLTransientConnectionException;
import java.time.Clock;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证订单入口的 HTTP 状态、端口占用回滚及 Outbox 所有权。 */
class JavalinEntryContractTest {

    @Test
    void basicEntryCreatesAndQueriesOrdersWithoutBearerToken() throws Exception {
        try (JavalinSample.JavalinApplication app = JavalinSample.startBasic(0)) {
            assertEquals("", app.token());
            String orders = "http://127.0.0.1:" + app.app().port() + "/api/orders";
            String orderNo = "basic-" + UUID.randomUUID();
            String body = "{\"orderNo\":\"" + orderNo + "\",\"buyerId\":\"buyer\",\"buyerName\":\"Alice\"}";
            assertEquals(201, SampleHttpTransport.request(orders, "POST",
                    Collections.emptyMap(), Collections.emptyMap(), body).statusCode());
            SampleHttpTransport.Response result = SampleHttpTransport.request(orders + "?buyerId=buyer", "GET",
                    Collections.emptyMap(), Collections.emptyMap(), null);
            assertEquals(200, result.statusCode());
            assertTrue(result.body().contains(orderNo));
        }
    }

    @Test
    void closingJdbcEntryStopsOwnedOutboxWorker() throws Exception {
        BlockedDataSource dataSource = new BlockedDataSource();
        try (JdbcOrderSampleRuntime runtime = jdbcRuntime(dataSource)) {
            dataSource.awaitWorkerStarted();
            try (JavalinSample.JavalinApplication app = JavalinSample.startJdbc(0, runtime)) {
                assertFalse(app.token().isEmpty());
                String orders = "http://127.0.0.1:" + app.app().port() + "/api/orders";
                assertEquals(401, SampleHttpTransport.request(orders, "GET",
                        Collections.emptyMap(), Collections.emptyMap(), null).statusCode());
            }
            dataSource.awaitWorkerStopped();
        }
    }

    @Test
    void failedBasicStartupPreservesCallerContextAndBindFailure() throws Exception {
        try (ThreadContext.Scope original = ThreadContext.open();
             ServerSocket occupied = new ServerSocket(0)) {
            ThreadContext.put(new Object(), new Object());
            ThreadContext.set("caller-value", "preserved");
            Map<Object, Object> expected = ThreadContext.getResources();
            RuntimeException failure = assertThrows(RuntimeException.class,
                    () -> {
                        JavalinSample.startBasic(occupied.getLocalPort()).close();
                    });
            assertBindFailure(failure);
            assertEquals(expected, ThreadContext.getResources());
            assertEquals(expected, ThreadContext.getValues());
        }
    }

    @Test
    void failedJdbcStartupStopsOwnedWorkerAndPreservesCallerContext() throws Exception {
        BlockedDataSource dataSource = new BlockedDataSource();
        try (ThreadContext.Scope original = ThreadContext.open();
             JdbcOrderSampleRuntime runtime = jdbcRuntime(dataSource);
             ServerSocket occupied = new ServerSocket(0)) {
            dataSource.awaitWorkerStarted();
            ThreadContext.put(new Object(), new Object());
            ThreadContext.set("caller-value", "preserved");
            Map<Object, Object> expected = ThreadContext.getResources();
            RuntimeException failure = assertThrows(RuntimeException.class,
                    () -> {
                        JavalinSample.startJdbc(occupied.getLocalPort(), runtime).close();
                    });
            assertBindFailure(failure);
            dataSource.awaitWorkerStopped();
            assertEquals(expected, ThreadContext.getResources());
            assertEquals(expected, ThreadContext.getValues());
        }
    }

    private static void assertBindFailure(RuntimeException failure) {
        Throwable cause = failure;
        while (Objects.nonNull(cause.getCause())) {
            cause = cause.getCause();
        }
        assertTrue(cause instanceof BindException, "Original occupied-port failure must propagate: " + failure);
    }

    private static JdbcOrderSampleRuntime jdbcRuntime(BlockedDataSource dataSource) {
        return JdbcOrderSampleRuntime.start(dataSource, new ObjectMapper(), new JavalinOrderAdapters(),
                message -> { }, "javalin-entry-contract", 1, Duration.ofMinutes(1), Clock.systemUTC());
    }

    /** 阻塞 Outbox 工作器获取连接的过程，无需连接数据库。 */
    private static final class BlockedDataSource extends PGSimpleDataSource {

        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch blocked = new CountDownLatch(1);
        private final CountDownLatch interrupted = new CountDownLatch(1);
        private Thread worker;

        @Override
        public Connection getConnection() throws SQLTransientConnectionException {
            worker = Thread.currentThread();
            entered.countDown();
            try {
                blocked.await();
                throw new SQLTransientConnectionException("Unexpected connection fixture release");
            } catch (InterruptedException exception) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
                throw new SQLTransientConnectionException("Outbox worker interrupted", exception);
            }
        }

        void awaitWorkerStarted() throws InterruptedException {
            assertTrue(entered.await(5, TimeUnit.SECONDS), "Outbox worker did not reach its datasource");
        }

        void awaitWorkerStopped() throws InterruptedException {
            assertTrue(interrupted.await(5, TimeUnit.SECONDS), "Outbox worker was not interrupted");
            worker.join(5000);
            assertFalse(worker.isAlive(), "Outbox worker did not terminate");
        }
    }
}
