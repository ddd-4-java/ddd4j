/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
package io.ddd4j.data.external.weather;

import com.alibaba.fastjson2.JSONObject;
import io.ddd4j.cache.CacheKit;
import io.ddd4j.kit.lang.StrKit;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.Optional;

/**
 * 免费天气查询模板
 * <p>通过 sojson 天气接口获取城市天气信息，支持本地缓存</p>
 * <p>接口文档：<a href="https://www.sojson.com/api/weather.html">https://www.sojson.com/api/weather.html</a></p>
 *
 * <p>缓存通过 {@link CacheKit} 统一管理，不再直接依赖 Caffeine。</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class WeatherTemplate {

    /** 天气查询请求地址 */
    private final static String SOJSON_WEATHER_URL = "http://t.weather.sojson.com/api/weather/city/%s";

    /** 缓存业务标识 */
    private static final String CACHE_BIZ = "weather";
    /** 缓存过期时间（1 小时） */
    private static final long CACHE_EXPIRE_SECONDS = 3600L;

    static {
        // 初始化天气缓存（Caffeine 本地缓存，1 小时过期，最大 100 条）
        CacheKit.build(CACHE_BIZ, config -> config
                .expireAfterWriteSeconds(CACHE_EXPIRE_SECONDS)
                .maximumSize(100)
                .initialCapacity(10)
                .recordStats(true)
                .removalListener(key -> log.info("天气缓存 {} was removed", key))
        );
    }

    /** HTTP 客户端 */
    private final HttpClient httpClient;

    /**
     * 构造函数
     *
     * @param httpClient HTTP 客户端
     */
    public WeatherTemplate(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * 获取指定城市的天气信息（带缓存，未命中时自动查询远程接口）。
     *
     * @param cityCode 城市代码
     * @return 天气 JSON 数据，未找到返回 null
     */
    public JSONObject getWeather(String cityCode) {
        // 先查缓存
        JSONObject cached = CacheKit.get(CACHE_BIZ, cityCode);
        if (cached != null) {
            return cached;
        }
        // 缓存未命中，查询远程接口
        JSONObject weather = fetchWeather(cityCode);
        if (weather != null) {
            CacheKit.put(CACHE_BIZ, cityCode, weather);
        }
        return weather;
    }

    /**
     * 查询远程天气接口。
     *
     * @param cityCode 城市代码
     * @return 天气 JSON 数据，查询失败返回 null
     */
    private JSONObject fetchWeather(String cityCode) {
        try {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(String.format(SOJSON_WEATHER_URL, cityCode)))
                            .header("Accept", "application/json")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String bodyString = response.body();
                if (StrKit.hasText(bodyString)) {
                    log.info("city_code {} >> weather :  {}", cityCode, bodyString);
                    return JSONObject.parseObject(bodyString);
                }
            }
            log.error("Weather Query Error. Response Code >> {}, Body >> {}", response.statusCode(), response.body());
        } catch (Exception e) {
            log.error("Weather Query Failed: city_code={}", cityCode, e);
        }
        return null;
    }

}
