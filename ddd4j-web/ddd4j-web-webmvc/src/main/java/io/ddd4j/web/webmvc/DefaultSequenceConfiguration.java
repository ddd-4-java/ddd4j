/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.web.webmvc;

import cn.hutool.core.util.IdUtil;
import io.ddd4j.web.webmvc.config.SequenceProperties;
import io.ddd4j.core.sequence.Sequence;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;

@Configuration(proxyBeanMethods = false)
/**
 * 雪花算法 ID 生成器默认配置。
 * <p>基于 Hutool {@link Snowflake} 实现分布式全局唯一 ID 生成。</p>
 */
public class DefaultSequenceConfiguration {

    @Bean
    public Sequence sequence(SequenceProperties properties) {
        long dataCenterId = IdUtil.getDataCenterId(31);
        long workerId = IdUtil.getWorkerId(dataCenterId, 31);
        long timeOffset = Objects.isNull(properties.getTimeOffset()) ? 5L : properties.getTimeOffset();
        long randomSequenceLimit = Objects.isNull(properties.getRandomSequenceLimit()) ? 0L : properties.getRandomSequenceLimit();
        return new Sequence(workerId, dataCenterId, properties.isUseSystemClock(), timeOffset, randomSequenceLimit);
    }

}
