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

/**
 * \u96ea\u82b1\u5e8f\u5217\u751f\u6210\u5668\u7684\u9ed8\u8ba4\u88c5\u914d\u914d\u7f6e\u3002
 *
 *  <p>\u6309 {@link SequenceProperties} \u63d0\u4f9b\u7684\u53c2\u6570\u6784\u5efa Hutool {@link Snowflake} Bean\u3002
 * @since 2.0.x
 */
@Configuration(proxyBeanMethods = false)
public class DefaultSequenceConfiguration {

    /**
     * \u6784\u5efa\u96ea\u82b1 ID \u751f\u6210\u5668 Bean\u3002
     * @param properties \u5e8f\u5217\u751f\u6210\u914d\u7f6e\uff08\u65f6\u95f4\u56de\u62e8\u3001\u968f\u673a\u4e0a\u9650\u3001\u7cfb\u7edf\u65f6\u949f\u5f00\u5173\uff09
     * @return \u96ea\u82b1\u5e8f\u5217\u751f\u6210\u5668
     */
    @Bean
    public Snowflake sequence(SequenceProperties properties) {
        long dataCenterId = IdUtil.getDataCenterId(31);
        long workerId = IdUtil.getWorkerId(dataCenterId, 31);
        long timeOffset = Objects.isNull(properties.getTimeOffset()) ? 5L : properties.getTimeOffset();
        long randomSequenceLimit = Objects.isNull(properties.getRandomSequenceLimit()) ? 0L : properties.getRandomSequenceLimit();
        return IdKit.getSnowflake(workerId, dataCenterId, properties.isUseSystemClock(), timeOffset, randomSequenceLimit);
    }

}
