package io.ddd4j.data.external.sequence;

import cn.hutool.core.util.IdUtil;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;


public class Sequence {

    private final Queue<Long> idPool = new ConcurrentLinkedQueue<>();
    private final RedisTemplate<String, String> redis;
    private final DefaultRedisScript<List> script;
    private final Long datacenterId;
    private final Long epoch;
    private final Long batchSize;
    private final Long workerId;
    private boolean useLocalMode;

    public Sequence(RedisTemplate<String, String> redis, String scriptText, Long datacenterId, Long workerId, Long epoch, Long batchSize) {
        this.redis = redis;
        this.datacenterId = datacenterId;
        this.workerId = workerId;
        this.epoch = epoch;
        this.batchSize = batchSize;

        this.script = new DefaultRedisScript<>();
        script.setResultType(List.class);
        script.setScriptText(scriptText);
        try {
            refill();
            useLocalMode = false;
        } catch (Exception e) {
            useLocalMode = true;
        }

    }

    private final Object refillLock = new Object();

    public synchronized long nextId() {
        Long id = idPool.poll();

        if (id == null) {
            synchronized (refillLock) {
                // 双重检查
                // 双重检查
                if (idPool.isEmpty()) {
                    try {
                        refill();
                    } catch (Exception e) {
                        // 切换到本地降级模式
                        useLocalMode = true;
                        System.err.println("Redis ID 生成失败，切换至本地降级模式: " + e.getMessage());
                    }
                }
            }
            id = idPool.poll();
            if (id == null) {
                if (useLocalMode) {
                    return generateLocalId();
                }
                throw new IllegalStateException("ID 生成失败：Redis ID 池仍为空！");
            }
        }

        // 异步补充：低水位时提前 refill
        if (idPool.size() < batchSize / 4) {
            CompletableFuture.runAsync(() -> {
                synchronized (refillLock) {
                    if (idPool.size() < batchSize / 4) {
                        try {
                            refill();
                            useLocalMode = false; // 成功 refill 后切回 Redis 模式
                        } catch (Exception e) {
                            useLocalMode = true;
                            System.err.println("异步 refill Redis 失败，保持本地降级模式: " + e.getMessage());
                        }
                    }
                }
            });
        }
        return id;
    }

    /**
     * 生成时间戳
     *
     * @return 时间戳
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
    // 本地降级 ID 生成方法
    private long generateLocalId() {
        return IdUtil.getSnowflake(workerId, datacenterId).nextId();
    }

    private void refill() {
        String redisKey = "{snowflake}:seq";
        List<Object> result = redis.execute(
                script,
                Collections.singletonList(redisKey),
                String.valueOf(epoch),
                String.valueOf(batchSize)
        );
        if (result == null || result.size() != 3) {
            throw new IllegalStateException("Redis script execution failed: " + result);
        }

        long nowMs = Long.parseLong(result.get(0).toString());
        long seqStart = Long.parseLong(result.get(1).toString());
        long seqEnd = Long.parseLong(result.get(2).toString());
        long tsPart = (nowMs - epoch) & 0x1FFFFFFFFFFL;

        for (long seq = seqStart; seq <= seqEnd; seq++) {
            long id = (tsPart << 22) | (datacenterId << 17) | (workerId << 12) | (seq & 0xFFFL);
            idPool.offer(id);
        }
    }
}
