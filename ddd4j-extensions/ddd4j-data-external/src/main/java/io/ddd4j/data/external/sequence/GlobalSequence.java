package io.ddd4j.data.external.sequence;

import io.ddd4j.core.sequence.Sequence;
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
public class GlobalSequence extends Sequence {

    private final RedisOperationTemplate redisOperation;
    private static final String ID_LIST_KEY = "global:sequence:ids";
    private static final int BATCH_SIZE = 100; // 每次预生成的ID数量
    private static final int MIN_THRESHOLD = 20; // 最小阈值，低于此值时触发预生成
    private static final Queue<Long> idPool = new ConcurrentLinkedQueue<>();


    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public GlobalSequence(RedisOperationTemplate redisOperation, long workerId) {
        super(workerId);
        this.redisOperation = redisOperation;
        initializePreGeneration();
    }

    public GlobalSequence(RedisOperationTemplate redisOperation, long workerId, long dataCenterId) {
        super(workerId, dataCenterId);
        this.redisOperation = redisOperation;
        initializePreGeneration();
    }

    public GlobalSequence(RedisOperationTemplate redisOperation, long workerId, long dataCenterId, boolean isUseSystemClock) {
        super(workerId, dataCenterId, isUseSystemClock);
        this.redisOperation = redisOperation;
        initializePreGeneration();
    }

    public GlobalSequence(RedisOperationTemplate redisOperation, long workerId, long dataCenterId, boolean isUseSystemClock, long timeOffset) {
        super(workerId, dataCenterId, isUseSystemClock, timeOffset);
        this.redisOperation = redisOperation;
        initializePreGeneration();
    }

    public GlobalSequence(RedisOperationTemplate redisOperation, long workerId, long dataCenterId, boolean isUseSystemClock, long timeOffset, long randomSequenceLimit) {
        super(workerId, dataCenterId, isUseSystemClock, timeOffset, randomSequenceLimit);
        this.redisOperation = redisOperation;
        initializePreGeneration();
    }

    /**
     * 初始化预生成机制
     */
    private void initializePreGeneration() {
        // 启动时预生成一批ID
        preGenerateIds();
        // 定时检查并预生成ID（每5秒检查一次）
        scheduler.scheduleWithFixedDelay(this::checkAndPreGenerate, 5, 5, TimeUnit.SECONDS);
    }

    /**
     * 检查Redis List中的ID数量，如果低于阈值则预生成
     */
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

    /**
     * 预生成ID并存储到Redis List
     */
    private void preGenerateIds() {
        try {
            Set<Long> idSet = new HashSet<>();
            for (int i = 0; i < BATCH_SIZE; i++) {
                long id = this.getSnowflake().nextId();
                idSet.add(id);
            }
            redisOperation.lRightPushAll(ID_LIST_KEY, idSet);
            log.debug("Pre-generated {} IDs and stored to Redis list", BATCH_SIZE);
        } catch (Exception e) {
            log.error("Error pre-generating IDs", e);
        }
    }

    @Override
    public synchronized Long nextId() {
        try {
            // 优先从Redis List中获取预生成的ID
            Object idStr = redisOperation.lLeftPop(ID_LIST_KEY);
            if (idStr != null) {
                // 获取ID成功后，检查剩余数量并提前生成
                checkAndTriggerPreGeneration();
                return Long.valueOf(idStr.toString());
            }
        } catch (Exception e) {
            log.warn("从Redis获取ID失败，使用Snowflake生成: {}", e.getMessage());
        }

        // Redis中没有可用ID，直接使用snowflake生成
        return this.getSnowflake().nextId();
    }

    /**
     * 检查剩余ID数量并触发预生成
     */
    private void checkAndTriggerPreGeneration() {
        try {
            long listSize = redisOperation.lSize(ID_LIST_KEY);
            if (listSize < MIN_THRESHOLD) {
                log.debug("剩余ID数量 {} 低于阈值 {}，触发预生成", listSize, MIN_THRESHOLD);
                // 异步触发预生成，避免阻塞当前请求
                scheduler.execute(this::preGenerateIds);
            }
        } catch (Exception e) {
            log.warn("检查剩余ID数量失败: {}", e.getMessage());
        }
    }

    /**
     * 关闭资源
     */
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


    public String nextIdStr() {
        return snowflake.nextIdStr();
    }
}
