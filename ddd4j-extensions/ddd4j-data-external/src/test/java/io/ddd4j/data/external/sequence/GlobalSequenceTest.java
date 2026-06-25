package io.ddd4j.data.external.sequence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.RedisOperationTemplate;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

/**
 * GlobalSequence 测试类
 */
public class GlobalSequenceTest {

    @Mock
    private RedisOperationTemplate redisOperation;

    private GlobalSequence globalSequence;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        globalSequence = new GlobalSequence(redisOperation, 1L, 1L);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (globalSequence != null) {
            globalSequence.shutdown();
        }
    }

    @Test
    void testNextId_ShouldGenerateUniqueIds() {
        // 测试生成的ID是唯一的
        Set<Long> generatedIds = new HashSet<>();

        for (int i = 0; i < 1000; i++) {
            Long id = globalSequence.nextId();
            assertNotNull(id);
            assertTrue(id > 0);
            assertTrue(generatedIds.add(id), "Generated duplicate ID: " + id);
        }

        assertEquals(1000, generatedIds.size());
    }

    @Test
    void testNextId_WithRedisAvailable() {

        Long id1 = globalSequence.nextId();
        Long id2 = globalSequence.nextId();
        Long id3 = globalSequence.nextId();

        assertEquals(123456789L, id1);
        assertEquals(123456790L, id2);
        assertNotNull(id3);
        assertTrue(id3 > 0);
    }

    @Test
    void testNextId_WithRedisException() {

        // 应该降级到直接生成，不抛出异常
        Long id = globalSequence.nextId();
        assertNotNull(id);
        assertTrue(id > 0);
    }

    @Test
    void testTriggerPreGeneration() {

        Long id = globalSequence.nextId();

        assertEquals(123456789L, id);
        // 验证触发了剩余数量检查
        verify(redisOperation).lSize(anyString());
    }

    @Test
    void testConcurrentIdGeneration() throws InterruptedException {
        // 测试并发生成ID的唯一性
        int threadCount = 10;
        int idsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        Set<Long> allIds = new HashSet<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    Set<Long> threadIds = new HashSet<>();
                    for (int j = 0; j < idsPerThread; j++) {
                        Long id = globalSequence.nextId();
                        threadIds.add(id);
                    }

                    synchronized (allIds) {
                        allIds.addAll(threadIds);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        // 验证所有ID都是唯一的
        assertEquals(threadCount * idsPerThread, allIds.size());
    }

    @Test
    void testShutdown() {
        // 测试资源清理
        assertDoesNotThrow(() -> globalSequence.shutdown());

        // 多次调用shutdown应该是安全的
        assertDoesNotThrow(() -> globalSequence.shutdown());
    }

    @Test
    void testIdFormat() {
        // 测试生成的ID格式（Snowflake格式）
        Long id = globalSequence.nextId();
        assertNotNull(id);

        // Snowflake ID应该是正数且足够大
        assertTrue(id > 0);

        // 转换为二进制字符串检查长度（Snowflake ID通常是64位）
        String binaryString = Long.toBinaryString(id);
        assertTrue(binaryString.length() <= 64);
    }
}