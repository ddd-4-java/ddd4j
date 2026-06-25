/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 */
package io.ddd4j.data.external;

import com.github.hiwepy.ip2region.spring.boot.IP2regionTemplate;
import io.ddd4j.data.external.region.BaiduRegionTemplate;
import io.ddd4j.data.external.region.NestedRegionTemplate;
import io.ddd4j.data.external.region.PconlineRegionTemplate;
import io.ddd4j.data.external.weather.WeatherTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisOperationTemplate;
import org.springframework.web.client.RestClient;

/**
 *
 */
@Configuration
public class ExternalAutoConfiguration {

    @Bean
    public RestClient restClient() {
        return RestClient.builder().build();
    }

    @Bean
    public BaiduRegionTemplate baiduRegionTemplate(ExternalProperties properties, RestClient restClient) {
        return new BaiduRegionTemplate(properties.getBaiduAk(), restClient);
    }

    @Bean
    public PconlineRegionTemplate pconlineRegionTemplate(RestClient restClient) {
        return new PconlineRegionTemplate(restClient);
    }

    @Bean
    public NestedRegionTemplate nestedRegionTemplate(ObjectProvider<RedisOperationTemplate> redisOperationProvider,
                                                     ObjectProvider<IP2regionTemplate> ip2RegionTemplateProvider,
                                                     PconlineRegionTemplate pconlineRegionTemplate) {
        return new NestedRegionTemplate(redisOperationProvider.getIfAvailable(), ip2RegionTemplateProvider.getIfAvailable(), pconlineRegionTemplate);
    }

    @Bean
    public WeatherTemplate weatherTemplate(RestClient restClient) {
        return new WeatherTemplate(restClient);
    }
/*
	@Bean
	public Sequence sequence(RedisOperationTemplate redisOperation, SequenceProperties properties) {
        long workerId = Objects.isNull(properties.getWorkerId()) ? 0x000000FF & Sequence.getLastIPAddress() : properties.getWorkerId();
        long dataCenterId = Objects.isNull(properties.getDataCenterId()) ? 0L : properties.getDataCenterId();
        long timeOffset = Objects.isNull(properties.getTimeOffset()) ? 5L : properties.getTimeOffset();
        long randomSequenceLimit = Objects.isNull(properties.getRandomSequenceLimit()) ? 0L : properties.getRandomSequenceLimit();
        return new GlobalSequence(redisOperation, workerId, dataCenterId, properties.isUseSystemClock(), timeOffset, randomSequenceLimit);
    }*/

}
