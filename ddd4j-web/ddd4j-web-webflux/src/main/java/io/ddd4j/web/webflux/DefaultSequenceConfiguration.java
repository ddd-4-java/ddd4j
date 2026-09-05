/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
