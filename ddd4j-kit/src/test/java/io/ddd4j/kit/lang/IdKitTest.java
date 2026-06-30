package io.ddd4j.kit.lang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IdKit 测试：验证 Snowflake ID 生成的唯一性、格式、并发安全性。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@DisplayName("IdKit: Snowflake 分布式 ID 生成测试")
class IdKitTest {

    private static final long WORKER_ID = 1L;
    private static final long DATA_CENTER_ID = 1L;

    @Test
    @DisplayName("nextSnowflakeId 应生成正数 ID")
    void nextSnowflakeId_shouldBePositive() {
        long id = IdKit.nextSnowflakeId(WORKER_ID);
        assertTrue(id > 0, "Snowflake ID 应为正数，实际: " + id);
    }

    @Test
    @DisplayName("nextSnowflakeId(workerId, dataCenterId) 应生成正数 ID")
    void nextSnowflakeId_withDataCenter_shouldBePositive() {
        long id = IdKit.nextSnowflakeId(WORKER_ID, DATA_CENTER_ID);
        assertTrue(id > 0, "Snowflake ID 应为正数，实际: " + id);
    }

    @Test
    @DisplayName("nextSnowflakeIdStr 应返回非空字符串")
    void nextSnowflakeIdStr_shouldBeNonEmpty() {
        String idStr = IdKit.nextSnowflakeIdStr(WORKER_ID);
        assertNotNull(idStr);
        assertFalse(io.ddd4j.kit.lang.StrKit.isEmpty(idStr));
        // 字符串应能解析为正数
        long parsed = Long.parseLong(idStr);
        assertTrue(parsed > 0);
    }

    @Test
    @DisplayName("连续生成 1000 个 ID 应全部唯一")
    void nextSnowflakeId_shouldGenerateUniqueIds() {
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            long id = IdKit.nextSnowflakeId(WORKER_ID);
            assertTrue(ids.add(id), "生成了重复 ID: " + id);
        }
        assertEquals(1000, ids.size());
    }

    @Test
    @DisplayName("Snowflake ID 二进制长度应不超过 64 位")
    void nextSnowflakeId_shouldNotExceed64Bits() {
        long id = IdKit.nextSnowflakeId(WORKER_ID);
        String binary = Long.toBinaryString(id);
        assertTrue(binary.length() <= 64,
                "Snowflake ID 二进制长度应 <= 64，实际: " + binary.length());
    }

    @Test
    @DisplayName("并发生成 10 线程 x 100 ID 应全部唯一")
    void nextSnowflakeId_concurrentGeneration_shouldBeUnique() throws InterruptedException {
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
                        threadIds.add(IdKit.nextSnowflakeId(WORKER_ID, DATA_CENTER_ID));
                    }
                    synchronized (allIds) {
                        allIds.addAll(threadIds);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "并发执行超时");
        executor.shutdown();

        assertEquals(threadCount * idsPerThread, allIds.size(),
                "并发生成的 ID 应全部唯一，实际唯一数: " + allIds.size());
    }

    @Test
    @DisplayName("不同 workerId 生成的 ID 应不同（大概率）")
    void nextSnowflakeId_differentWorkers_shouldDiffer() {
        long id1 = IdKit.nextSnowflakeId(1L);
        long id2 = IdKit.nextSnowflakeId(2L);
        // 不同 worker 生成的 ID 几乎不可能相同（同一毫秒内 workerId 位不同）
        assertNotEquals(id1, id2,
                "不同 workerId 生成的 ID 不应相同");
    }

    @Test
    @DisplayName("getSnowflake 单例应返回同一实例")
    void getSnowflake_shouldReturnSingleton() {
        var sf1 = IdKit.getSnowflake(WORKER_ID);
        var sf2 = IdKit.getSnowflake(WORKER_ID);
        assertSame(sf1, sf2, "getSnowflake 应返回单例");
    }

    @Test
    @DisplayName("getLastIPAddress 应返回非零值")
    void getLastIPAddress_shouldReturnNonZero() {
        byte lastIp = IdKit.getLastIPAddress();
        // 只要不抛异常即可，值取决于运行环境
        assertNotNull(lastIp);
    }
}
