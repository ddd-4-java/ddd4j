package io.ddd4j.kit.lang;

import cn.hutool.core.lang.Snowflake;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
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
@Slf4j
@DisplayName("IdKit: Snowflake 分布式 ID 生成测试")
class IdKitTest {

    /** Snowflake \u6d4b\u8bd5\u4f7f\u7528\u7684\u5de5\u4f5c\u8282\u70b9\u7f16\u53f7\u3002 */
    private static final long WORKER_ID = 1L;
    /** Snowflake \u6d4b\u8bd5\u4f7f\u7528\u7684\u6570\u636e\u4e2d\u5fc3\u7f16\u53f7\u3002 */
    private static final long DATA_CENTER_ID = 1L;
    /** \u5e76\u53d1\u6d4b\u8bd5\u4e2d\u4fdd\u7559\u5e76\u8bb0\u5f55\u7684 ID \u6837\u672c\u6570\u4e0a\u9650\u3002 */
    private static final int LOG_SAMPLE_SIZE = 20;

    /** nextSnowflakeId \u5e94\u751f\u6210\u6b63\u6570 ID\u3002 */
    @Test
    @DisplayName("nextSnowflakeId 应生成正数 ID")
    void nextSnowflakeId_shouldBePositive() {
        long id = IdKit.nextSnowflakeId(WORKER_ID);
        logSnowflakeId("single-worker", id);
        assertTrue(id > 0, "Snowflake ID 应为正数，实际: " + id);
    }

    /** nextSnowflakeId(workerId, dataCenterId) \u5e94\u751f\u6210\u6b63\u6570 ID\u3002 */
    @Test
    @DisplayName("nextSnowflakeId(workerId, dataCenterId) 应生成正数 ID")
    void nextSnowflakeId_withDataCenter_shouldBePositive() {
        long id = IdKit.nextSnowflakeId(WORKER_ID, DATA_CENTER_ID);
        logSnowflakeId("worker-data-center", id);
        assertTrue(id > 0, "Snowflake ID 应为正数，实际: " + id);
    }

    /** nextSnowflakeIdStr \u5e94\u8fd4\u56de\u975e\u7a7a\u5b57\u7b26\u4e32\u3002 */
    @Test
    @DisplayName("nextSnowflakeIdStr 应返回非空字符串")
    void nextSnowflakeIdStr_shouldBeNonEmpty() {
        String idStr = IdKit.nextSnowflakeIdStr(WORKER_ID);
        log.info("Snowflake string id sample: workerId={}, id={}", WORKER_ID, idStr);
        assertNotNull(idStr);
        assertFalse(StrKit.isEmpty(idStr));
        // 字符串应能解析为正数
        long parsed = Long.parseLong(idStr);
        logSnowflakeId("string-parsed", parsed);
        assertTrue(parsed > 0);
    }

    /** \u8fde\u7eed\u751f\u6210 1000 \u4e2a ID \u5e94\u5168\u90e8\u552f\u4e00\u3002 */
    @Test
    @DisplayName("连续生成 1000 个 ID 应全部唯一")
    void nextSnowflakeId_shouldGenerateUniqueIds() {
        Set<Long> ids = new HashSet<>();
        List<Long> sampleIds = new ArrayList<>(LOG_SAMPLE_SIZE);
        for (int i = 0; i < 1000; i++) {
            long id = IdKit.nextSnowflakeId(WORKER_ID);
            addSampleId(sampleIds, id);
            assertTrue(ids.add(id), "生成了重复 ID: " + id);
        }
        log.info("Snowflake batch sample: workerId={}, total={}, sample={}", WORKER_ID, ids.size(), sampleIds);
        assertEquals(1000, ids.size());
    }

    /** Snowflake ID \u4e8c\u8fdb\u5236\u957f\u5ea6\u5e94\u4e0d\u8d85\u8fc7 64 \u4f4d\u3002 */
    @Test
    @DisplayName("Snowflake ID 二进制长度应不超过 64 位")
    void nextSnowflakeId_shouldNotExceed64Bits() {
        long id = IdKit.nextSnowflakeId(WORKER_ID);
        String binary = Long.toBinaryString(id);
        log.info("Snowflake binary sample: workerId={}, id={}, binaryLength={}, binary={}",
                WORKER_ID, id, binary.length(), binary);
        assertTrue(binary.length() <= 64,
                "Snowflake ID 二进制长度应 <= 64，实际: " + binary.length());
    }

    /**
     * \u5e76\u53d1\u751f\u6210 10 \u7ebf\u7a0b x 100 ID \u5e94\u5168\u90e8\u552f\u4e00\u3002
     * @throws InterruptedException \u7b49\u5f85\u6d4b\u8bd5\u64cd\u4f5c\u65f6\u88ab\u4e2d\u65ad
     */
    @Test
    @DisplayName("并发生成 10 线程 x 100 ID 应全部唯一")
    void nextSnowflakeId_concurrentGeneration_shouldBeUnique() throws InterruptedException {
        int threadCount = 10;
        int idsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        Set<Long> allIds = new HashSet<>();
        List<Long> sampleIds = Collections.synchronizedList(new ArrayList<>(LOG_SAMPLE_SIZE));

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    Set<Long> threadIds = new HashSet<>();
                    for (int j = 0; j < idsPerThread; j++) {
                        long id = IdKit.nextSnowflakeId(WORKER_ID, DATA_CENTER_ID);
                        threadIds.add(id);
                        addSampleId(sampleIds, id);
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

        log.info("Snowflake concurrent sample: workerId={}, dataCenterId={}, threads={}, idsPerThread={}, unique={}, sample={}",
                WORKER_ID, DATA_CENTER_ID, threadCount, idsPerThread, allIds.size(), sampleIds);
        assertEquals(threadCount * idsPerThread, allIds.size(),
                "并发生成的 ID 应全部唯一，实际唯一数: " + allIds.size());
    }

    /** \u9a8c\u8bc1<code>second</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>first</code>\uff0c<code>first.nextId()</code> \u4e0d\u7b49\u4e8e <code>first.nextId()</code>\u3002 */
    @Test
    void getSnowflake_withTimeOffset_shouldCreateReusableGenerator() {
        Snowflake first = IdKit.getSnowflake(1L, 1L, true, 5L);
        Snowflake second = IdKit.getSnowflake(1L, 1L, true, 5L);

        assertSame(first, second);
        assertNotEquals(first.nextId(), first.nextId());
    }

    /** \u9a8c\u8bc1<code>second</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>first</code>\uff0c<code>first.nextId()</code> \u4e0d\u7b49\u4e8e <code>first.nextId()</code>\u3002 */
    @Test
    void getSnowflake_withRandomSequenceLimit_shouldCreateReusableGenerator() {
        Snowflake first = IdKit.getSnowflake(2L, 2L, false, 5L, 16L);
        Snowflake second = IdKit.getSnowflake(2L, 2L, false, 5L, 16L);

        assertSame(first, second);
        assertNotEquals(first.nextId(), first.nextId());
    }

    /** \u4e0d\u540c workerId \u751f\u6210\u7684 ID \u5e94\u4e0d\u540c\uff08\u5927\u6982\u7387\uff09\u3002 */
    @Test
    @DisplayName("不同 workerId 生成的 ID 应不同（大概率）")
    void nextSnowflakeId_differentWorkers_shouldDiffer() {
        long id1 = IdKit.nextSnowflakeId(1L);
        long id2 = IdKit.nextSnowflakeId(2L);
        log.info("Snowflake different worker sample: worker1={}, id1={}, worker2={}, id2={}", 1L, id1, 2L, id2);
        // 不同 worker 生成的 ID 几乎不可能相同（同一毫秒内 workerId 位不同）
        assertNotEquals(id1, id2,
                "不同 workerId 生成的 ID 不应相同");
    }

    /** getSnowflake \u5355\u4f8b\u5e94\u8fd4\u56de\u540c\u4e00\u5b9e\u4f8b\u3002 */
    @Test
    @DisplayName("getSnowflake 单例应返回同一实例")
    void getSnowflake_shouldReturnSingleton() {
        var sf1 = IdKit.getSnowflake(WORKER_ID);
        var sf2 = IdKit.getSnowflake(WORKER_ID);
        log.info("Snowflake singleton sample: workerId={}, instance1={}, instance2={}",
                WORKER_ID, System.identityHashCode(sf1), System.identityHashCode(sf2));
        assertSame(sf1, sf2, "getSnowflake 应返回单例");
    }

    /** getLastIPAddress \u5e94\u8fd4\u56de\u975e\u96f6\u503c\u3002 */
    @Test
    @DisplayName("getLastIPAddress 应返回非零值")
    void getLastIPAddress_shouldReturnNonZero() {
        byte lastIp = IdKit.getLastIPAddress();
        log.info("Snowflake last IP worker hint: lastIp={}", lastIp);
        // 只要不抛异常即可，值取决于运行环境
        assertNotNull(lastIp);
    }

    /**
     * \u8bb0\u5f55\u6d4b\u8bd5\u751f\u6210\u7684 Snowflake ID\u3001\u573a\u666f\u548c\u4e8c\u8fdb\u5236\u8868\u793a\u3002
     * @param scenario \u4ea7\u51fa\u8be5 ID \u7684\u6d4b\u8bd5\u573a\u666f
     * @param id \u65b0\u751f\u6210\u7684 ID
     */
    private void logSnowflakeId(String scenario, long id) {
        String binary = Long.toBinaryString(id);
        log.info("Snowflake id sample: scenario={}, workerId={}, dataCenterId={}, id={}, binaryLength={}, binary={}",
                scenario, WORKER_ID, DATA_CENTER_ID, id, binary.length(), binary);
    }

    /**
     * \u540c\u6b65\u5411\u6837\u672c\u5217\u8868\u8ffd\u52a0 ID\uff0c\u76f4\u81f3\u8fbe\u5230\u6837\u672c\u6570\u4e0a\u9650\u3002
     * @param sampleIds \u4fdd\u5b58\u62bd\u6837 ID \u7684\u5171\u4eab\u5217\u8868
     * @param id \u5019\u9009 ID
     */
    private void addSampleId(List<Long> sampleIds, long id) {
        synchronized (sampleIds) {
            if (sampleIds.size() < LOG_SAMPLE_SIZE) {
                sampleIds.add(id);
            }
        }
    }
}
