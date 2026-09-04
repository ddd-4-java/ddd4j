/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.web.webflux;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import io.ddd4j.kit.lang.IdKit;
import io.ddd4j.web.webflux.config.SequenceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;

@Configuration(proxyBeanMethods = false)
public class DefaultSequenceConfiguration {

    @Bean
    public Snowflake sequence(SequenceProperties properties) {
        long dataCenterId = IdUtil.getDataCenterId(31);
        long workerId = IdUtil.getWorkerId(dataCenterId, 31);
        long timeOffset = Objects.isNull(properties.getTimeOffset()) ? 5L : properties.getTimeOffset();
        long randomSequenceLimit = Objects.isNull(properties.getRandomSequenceLimit()) ? 0L : properties.getRandomSequenceLimit();
        return IdKit.getSnowflake(workerId, dataCenterId, properties.isUseSystemClock(), timeOffset, randomSequenceLimit);
    }

}
