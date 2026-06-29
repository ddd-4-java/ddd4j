package io.ddd4j.data.external.sequence;

import cn.hutool.core.lang.Snowflake;
import io.ddd4j.kit.lang.IdKit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisOperationTemplate;

import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
/**
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class GlobalSequence {

    private static final String ID_LIST_KEY = "global:sequence:ids";
    private static final int BATCH_SIZE = 100;
    private static final int MIN_THRESHOLD = 20;
    private final RedisOperationTemplate redisOperation;
    private final Snowflake snowflake;
    private final Queue<Long> idPool = new ConcurrentLinkedQueue<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public GlobalSequence(RedisOperationTemplate redisOperation, long workerId) {
        this.redisOperation = redisOperation;
        this.snowflake = IdKit.getSnowflake(workerId);
        initializePreGeneration();
    }

    public GlobalSequence(RedisOperationTemplate redisOperation, long workerId, long dataCenterId) {
        this.redisOperation = redisOperation;
        this.snowflake = IdKit.getSnowflake(workerId, dataCenterId);
        initializePreGeneration();
    }

    public GlobalSequence(RedisOperationTemplate redisOperation, long workerId, long dataCenterId, boolean isUseSystemClock) {
        this.redisOperation = redisOperation;
        this.snowflake = IdKit.getSnowflake(workerId, dataCenterId, isUseSystemClock);
        initializePreGeneration();
    }

    public GlobalSequence(RedisOperationTemplate redisOperation, long workerId, long dataCenterId, boolean isUseSystemClock, long timeOffset) {
        this.redisOperation = redisOperation;
        this.snowflake = IdKit.getSnowflake(workerId, dataCenterId, isUseSystemClock, timeOffset);
        initializePreGeneration();
    }

    public GlobalSequence(RedisOperationTemplate redisOperation, long workerId, long dataCenterId, boolean isUseSystemClock, long timeOffset, long randomSequenceLimit) {
        this.redisOperation = redisOperation;
        this.snowflake = IdKit.getSnowflake(workerId, dataCenterId, isUseSystemClock, timeOffset, randomSequenceLimit);
        initializePreGeneration();
    }

    private void initializePreGeneration() {
        preGenerateIds();
        scheduler.scheduleWithFixedDelay(this::checkAndPreGenerate, 5, 5, TimeUnit.SECONDS);
    }

    private void checkAndPreGenerate() {
        try {
            long listSize = redisOperation.lSize(ID_LIST_KEY);
            if (listSize < MIN_THRESHOLD) {
                log.debug("ID list size {} is below threshold {}, pre-generating more IDs", listSize, MIN_THRESHOLD);
                preGenerateIds();
            }
        } catch (Exception e) {
            log.error("Error checking ID list size", e);
        }
    }

    private void preGenerateIds() {
        try {
            Set<Long> idSet = new HashSet<>();
            for (int i = 0; i < BATCH_SIZE; i++) {
                long id = snowflake.nextId();
                idSet.add(id);
            }
            redisOperation.lRightPushAll(ID_LIST_KEY, idSet);
            log.debug("Pre-generated {} IDs and stored to Redis list", BATCH_SIZE);
        } catch (Exception e) {
            log.error("Error pre-generating IDs", e);
        }
    }

    public synchronized Long nextId() {
        try {
            Object idStr = redisOperation.lLeftPop(ID_LIST_KEY);
            if (idStr != null) {
                checkAndTriggerPreGeneration();
                return Long.valueOf(idStr.toString());
            }
        } catch (Exception e) {
            log.warn("从Redis获取ID失败，使用Snowflake生成: {}", e.getMessage());
        }
        return snowflake.nextId();
    }

    private void checkAndTriggerPreGeneration() {
        try {
            long listSize = redisOperation.lSize(ID_LIST_KEY);
            if (listSize < MIN_THRESHOLD) {
                log.debug("剩余ID数量 {} 低于阈值 {}，触发预生成", listSize, MIN_THRESHOLD);
                scheduler.execute(this::preGenerateIds);
            }
        } catch (Exception e) {
            log.warn("检查剩余ID数量失败: {}", e.getMessage());
        }
    }

    public void shutdown() {
        if (!scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
