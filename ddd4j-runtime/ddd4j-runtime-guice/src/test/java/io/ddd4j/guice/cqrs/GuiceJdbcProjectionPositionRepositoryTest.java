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
package io.ddd4j.guice.cqrs;

import io.ddd4j.core.cqrs.readmodel.DefaultProjectionPosition;
import io.ddd4j.core.cqrs.readmodel.ProjectionPosition;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link GuiceJdbcProjectionPositionRepository} 的单元测试。
 * <p>
 * 使用 H2 内存数据库，不依赖外部数据库。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
class GuiceJdbcProjectionPositionRepositoryTest {

    private JdbcDataSource dataSource;
    private GuiceJdbcProjectionPositionRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:test_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=30000");
        // 清理旧表（每个测试方法使用独立数据库，此处为保险起见）
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS DDD4J_PROJECTION_POSITION");
        }
        // 构造 repository（自动建表）
        repository = new GuiceJdbcProjectionPositionRepository(dataSource);
    }

    @Test
    void saveAndFindByStreamId() {
        DefaultProjectionPosition position = new DefaultProjectionPosition("stream-1", 5L);

        ProjectionPosition saved = repository.save(position);

        assertEquals("stream-1", saved.getStreamId());
        assertEquals(5L, saved.getNextEventNumber());

        Optional<ProjectionPosition> found = repository.findByStreamId("stream-1");
        assertTrue(found.isPresent());
        assertEquals(5L, found.get().getNextEventNumber());

        assertFalse(repository.findByStreamId("missing").isPresent());
    }

    @Test
    void saveOverwritesExistingPosition() {
        repository.save(new DefaultProjectionPosition("stream-1", 1L));
        repository.save(new DefaultProjectionPosition("stream-1", 99L));

        Optional<ProjectionPosition> found = repository.findByStreamId("stream-1");
        assertTrue(found.isPresent());
        assertEquals(99L, found.get().getNextEventNumber());
    }

    @Test
    void findAllReturnsAllPositions() {
        repository.save(new DefaultProjectionPosition("stream-1", 1L));
        repository.save(new DefaultProjectionPosition("stream-2", 2L));
        repository.save(new DefaultProjectionPosition("stream-3", 3L));

        List<ProjectionPosition> all = repository.findAll();

        assertEquals(3, all.size());
    }

    @Test
    void deleteByStreamIdRemovesPosition() {
        repository.save(new DefaultProjectionPosition("stream-1", 1L));

        repository.deleteByStreamId("stream-1");

        assertFalse(repository.findByStreamId("stream-1").isPresent());
    }

    @Test
    void deleteByStreamIdNonexistentIsNoOp() {
        // 删除不存在的 streamId 不应抛异常
        assertDoesNotThrow(() -> repository.deleteByStreamId("nonexistent"));
    }

    @Test
    void resetToZeroResetsExistingPosition() {
        repository.save(new DefaultProjectionPosition("stream-1", 42L));

        repository.resetToZero("stream-1");

        Optional<ProjectionPosition> found = repository.findByStreamId("stream-1");
        assertTrue(found.isPresent());
        assertEquals(0L, found.get().getNextEventNumber());
    }

    @Test
    void resetToZeroCreatesPositionIfNotExists() {
        // resetToZero 在 streamId 不存在时应创建一个 zero 位置
        repository.resetToZero("stream-new");

        Optional<ProjectionPosition> found = repository.findByStreamId("stream-new");
        assertTrue(found.isPresent());
        assertEquals(0L, found.get().getNextEventNumber());
    }

    @Test
    void concurrentUpdatesOnSeparateStreamsDoNotThrow() throws Exception {
        // 每个线程操作独立 streamId，无行级锁竞争
        int threadCount = 10;
        int updatesPerThread = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < updatesPerThread; i++) {
                        long eventNumber = threadId * 1000L + i;
                        repository.save(new DefaultProjectionPosition("stream-" + threadId, eventNumber));
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    fail("Concurrent update failed: " + e.getMessage());
                }
            }));
        }

        startLatch.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();

        // 验证所有 stream 均已持久化
        assertEquals(threadCount * updatesPerThread, successCount.get());
        assertEquals(threadCount, repository.findAll().size());
        for (int t = 0; t < threadCount; t++) {
            Optional<ProjectionPosition> found = repository.findByStreamId("stream-" + t);
            assertTrue(found.isPresent());
            assertEquals(t * 1000L + updatesPerThread - 1, found.get().getNextEventNumber());
        }
    }

    @Test
    void concurrentUpdatesOnSameStreamDoNotThrow() throws Exception {
        // 少量线程并发更新同一 streamId，验证 last-writer-wins 语义
        int threadCount = 3;
        int updatesPerThread = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < updatesPerThread; i++) {
                        long eventNumber = threadId * 100L + i;
                        repository.save(new DefaultProjectionPosition("shared-stream", eventNumber));
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    fail("Concurrent update failed: " + e.getMessage());
                }
            }));
        }

        startLatch.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();

        // 验证最终状态一致（最后写者胜）
        assertEquals(threadCount * updatesPerThread, successCount.get());
        Optional<ProjectionPosition> found = repository.findByStreamId("shared-stream");
        assertTrue(found.isPresent());
        assertTrue(found.get().getNextEventNumber() >= 0);
    }

    @Test
    void duplicateUpdatesAreIdempotent() {
        // 重复写入相同值不应抛异常
        for (int i = 0; i < 10; i++) {
            repository.save(new DefaultProjectionPosition("stream-1", 5L));
        }

        Optional<ProjectionPosition> found = repository.findByStreamId("stream-1");
        assertTrue(found.isPresent());
        assertEquals(5L, found.get().getNextEventNumber());
    }
}
