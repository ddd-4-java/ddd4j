
package io.ddd4j.data.external.sequence;

import cn.hutool.core.util.IdUtil;
import io.ddd4j.data.external.SequenceProperties;
import io.ddd4j.kit.lang.SequenceKit;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.RedisOperationTemplate;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * GlobalSequence 自动配置类
 */
//@Configuration
public class GlobalSequenceConfiguration {

    private GlobalSequence globalSequence;

    @Bean
    public GlobalSequence globalSequence(ObjectProvider<RedisOperationTemplate> redisOperationProvider,
                                         SequenceProperties properties) {
        long workerId = Objects.isNull(properties.getWorkerId()) ? 0x000000FF & SequenceKit.getLastIPAddress() : properties.getWorkerId();
        long dataCenterId = Objects.isNull(properties.getDataCenterId()) ? 0L : properties.getDataCenterId();
        long timeOffset = Objects.isNull(properties.getTimeOffset()) ? 5L : properties.getTimeOffset();
        long randomSequenceLimit = Objects.isNull(properties.getRandomSequenceLimit()) ? 0L : properties.getRandomSequenceLimit();
        this.globalSequence = new GlobalSequence(
                redisOperationProvider.getIfAvailable(),
                workerId,
                dataCenterId,
                properties.isUseSystemClock(),
                timeOffset,
                randomSequenceLimit
        );
        return this.globalSequence;
    }

    @PreDestroy
    public void destroy() {
        if (globalSequence != null) {
            globalSequence.shutdown();
        }
    }

    @Bean
    public SequenceKit snowflakeIdGenerator() throws IOException {

        // 1. 读取 Lua 脚本内容
        Resource lua = new ClassPathResource("scripts/redis-snowflake-batch.lua");
        InputStream in = lua.getInputStream();
        String scriptText = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        // 3. 配置：数据中心ID, 节点ID, 起始时间戳, 批次大小
        long datacenterId = IdUtil.getDataCenterId(31);
        long workerId = IdUtil.getWorkerId(datacenterId, 31);
        long epoch = 1288834974657L;
        long batchSize = 1000L;
        System.out.println("datacenterId:" + datacenterId);
        System.out.println("workerId:" + workerId);
        // 4. 返回实例
        // return new SequenceKit(redisTemplate, scriptText, datacenterId, workerId, epoch, batchSize);
        return null;
    }

}
