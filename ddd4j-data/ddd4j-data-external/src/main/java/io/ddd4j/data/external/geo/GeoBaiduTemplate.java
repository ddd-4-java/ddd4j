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
package io.ddd4j.data.external.geo;

import com.alibaba.fastjson2.JSONObject;
import io.ddd4j.kit.lang.StrKit;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 地址获取经纬度： http://lbsyun.baidu.com/index.php?title=webapi/guide/webservice-geocoding
 * IP获取经纬度：   http://lbsyun.baidu.com/index.php?title=webapi/ip-api
 * https://blog.csdn.net/Li_Chunxiao_/article/details/107082921
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class GeoBaiduTemplate {

    /**
     * 地理编码请求地址模板
     */
    private static String geocoder = "http://api.map.baidu.com/geocoding/v3/?address=%s&output=json&ak=%s";
    /**
     * IP定位请求地址模板
     */
    private static String geocoder2 = "http://api.map.baidu.com/location/ip?ak=%s&ip=%s&coor=bd09ll";
    /**
     * 高精度IP定位请求地址模板
     */
    private static String highacciploc = "https://api.map.baidu.com/highacciploc/v1?qcip=220.181.38.113&qterm=pc&ak=%s&coord=bd09ll";

    /**
     * HTTP 客户端
     */
    private final HttpClient httpClient;
    /**
     * 百度地图AK密钥
     */
    private final String ak;

    /**
     * 构造函数
     *
     * @param httpClient HTTP 客户端
     * @param ak         百度地图AK密钥
     */
    public GeoBaiduTemplate(HttpClient httpClient, String ak) {
        super();
        this.httpClient = httpClient;
        this.ak = ak;
    }

    public static void main(String[] args) throws IOException {

        GeoBaiduTemplate template = new GeoBaiduTemplate(HttpClient.newHttpClient(), "");

        Map<String, BigDecimal> mapLL = template.getLatAndLngByAddress("浙江省杭州市西湖区"); // lng：116.86380647644208  lat：38.297615350325717
        mapLL.get("lat");
        mapLL.get("lng");
        log.debug("lng：" + mapLL.get("lng") + "  lat：" + mapLL.get("lat"));

        Optional<JSONObject> mapLL2 = template.getLocationByIp("115.204.225.154"); // lng：116.86380647644208  lat：38.297615350325717
        log.debug(mapLL2.get().toJSONString());
    }

    /**
     * 调用百度API
     *
     * @param addr
     * @return
     * @throws IOException
     */
    public Map<String, BigDecimal> getLatAndLngByAddress(String addr) throws IOException {

        // {"message":"APP Referer校验失败","status":220}
        Optional<JSONObject> json = this.getLocationByAddress(addr);
        JSONObject result = json.get().getJSONObject("result");
        JSONObject location = result.getJSONObject("location");

        Map<String, BigDecimal> map = new HashMap<String, BigDecimal>();
        map.put("lat", location.getBigDecimal("lat"));
        map.put("lng", location.getBigDecimal("lng"));
        return map;
    }

    /**
     * 调用百度API
     *
     * @param addr
     * @return
     * @throws IOException
     */
    public Optional<JSONObject> getLocationByAddress(String addr) throws IOException {
        String address = java.net.URLEncoder.encode(addr, StandardCharsets.UTF_8);
        String url = String.format(geocoder, address, this.ak);
        // {"message":"APP Referer校验失败","status":220}
        HttpResponse<String> response;
        try {
            response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .header("Accept", "application/json")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("调用百度API被中断", ie);
        }
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            String bodyString = response.body();
            log.info(" Addr : {} >> Location : {} ", addr, bodyString);
            if (StrKit.hasText(bodyString)) {
                JSONObject jsonObject = JSONObject.parseObject(bodyString);
                if (jsonObject.getInteger("status") != 0) {
                    throw new IOException(jsonObject.getString("message"));
                }
                return Optional.of(jsonObject);
            }
        }
        log.error("Addr Location Query Error. Response Code >> {}, Body >> {}", response.statusCode(), response.body());
        return Optional.empty();
    }

    /**
     * 获取指定IP对应的经纬度（为空返回当前机器经纬度）
     * /*
     * <p>
     * {
     * address: "CN|北京|北京|None|CHINANET|1|None",    #详细地址信息
     * content:    #结构信息
     * {
     * address: "北京市",    #简要地址信息
     * address_detail:    #结构化地址信息
     * {
     * city: "北京市",    #城市
     * city_code: 131,    #百度城市代码
     * district: "",    #区县
     * province: "北京市",    #省份
     * street: "",    #街道
     * street_number: ""    #门牌号
     * },
     * point:    #当前城市中心点
     * {
     * x: "116.39564504",    #当前城市中心点经度
     * y: "39.92998578"    #当前城市中心点纬度
     * }
     * },
     * status: 0    #结果状态返回码
     * }
     *
     * @param ip
     * @return
     */
    public Optional<JSONObject> getLocationByIp(String ip) {
        if (Objects.isNull(ip)) {
            throw new NullPointerException("ip can not empty");
        }
        try {
            String url = String.format(geocoder2, this.ak, ip);
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .header("Accept", "application/json")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String bodyString = response.body();
                log.info(" IP : {} >> Location : {} ", ip, bodyString);
                if (StrKit.hasText(bodyString)) {
                    JSONObject jsonObject = JSONObject.parseObject(bodyString);
                    if (jsonObject.getInteger("status") != 0) {
                        throw new IOException(jsonObject.getString("message"));
                    }
                    return Optional.of(jsonObject);
                }
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("IP : {} >> Location Query Error. {}", ip, e.getMessage());
        }
        return Optional.empty();
    }


}
