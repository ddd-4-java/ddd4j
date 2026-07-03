/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.data.external.weather;

import com.alibaba.fastjson2.JSONObject;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 免费天气查询模板
 * <p>通过 sojson 天气接口获取城市天气信息，支持本地缓存</p>
 * <p>接口文档：<a href="https://www.sojson.com/api/weather.html">https://www.sojson.com/api/weather.html</a></p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class WeatherTemplate {

    /** 天气查询请求地址 */
    private final static String SOJSON_WEATHER_URL = "http://t.weather.sojson.com/api/weather/city/%s";

    /** REST 客户端 */
    private final RestClient restClient;
    /** 天气数据本地缓存（1 小时过期） */
    private final LoadingCache<String, Optional<JSONObject>> WEATHER_DATA_CACHES = Caffeine.newBuilder()
            // 设置写缓存后1个小时过期
            .expireAfterWrite(1, TimeUnit.HOURS)
            // 设置缓存容器的初始容量为10
            .initialCapacity(10)
            // 设置缓存最大容量为100，超过100之后就会按照LRU最近虽少使用算法来移除缓存项
            .maximumSize(100)
            // 设置要统计缓存的命中率
            .recordStats()
            // 设置缓存的移除通知
            .removalListener((key, value, cause) -> log.info("{} was removed, cause is {}", key, cause))
            // build方法中可以指定CacheLoader，在缓存不存在时通过CacheLoader的实现自动加载缓存
            .build(new CacheLoader<>() {

                @Override
                public Optional<JSONObject> load(String city_code) throws Exception {

                    ResponseEntity<String> response = restClient.get()
                            .uri(String.format(SOJSON_WEATHER_URL, city_code))
                            .retrieve()
                            .toEntity(String.class);
                    if (response.getStatusCode().is2xxSuccessful()) {
                        String bodyString = response.getBody();
                        if (StringUtils.hasText(bodyString)) {
                            log.info("city_code {} >> weather :  {}", city_code, bodyString);
                            JSONObject jsonObject = JSONObject.parseObject(bodyString);
                            return Optional.ofNullable(jsonObject);
                        }
                    }
                    log.error("Weather Query Error. Response Code >> {}, Body >> {}", response.getStatusCode().value(), response.getBody());
                    return Optional.empty();
                }
            });

    /**
     * 构造函数
     *
     * @param restClient REST 客户端
     */
    public WeatherTemplate(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * 获取指定城市的天气信息
     *
     * @param city_code 城市代码
     * @return 天气 JSON 数据，未找到返回 null
     * @throws ExecutionException 执行异常
     */
    public JSONObject getWeather(String city_code) throws ExecutionException {
        Optional<JSONObject> opt = WEATHER_DATA_CACHES.get(city_code);
        return Objects.isNull(opt) ? null : opt.orElse(null);
    }

}
