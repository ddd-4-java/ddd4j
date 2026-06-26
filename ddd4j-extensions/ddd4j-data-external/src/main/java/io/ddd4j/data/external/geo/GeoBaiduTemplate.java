package io.ddd4j.data.external.geo;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 地址获取经纬度： http://lbsyun.baidu.com/index.php?title=webapi/guide/webservice-geocoding
 * IP获取经纬度：   http://lbsyun.baidu.com/index.php?title=webapi/ip-api
 * https://blog.csdn.net/Li_Chunxiao_/article/details/107082921
 */
@Slf4j
public class GeoBaiduTemplate {

    private static String geocoder = "http://api.map.baidu.com/geocoding/v3/?address=%s&output=json&ak=%s";
    private static String geocoder2 = "http://api.map.baidu.com/location/ip?ak=%s&ip=%s&coor=bd09ll";
    private static String highacciploc = "https://api.map.baidu.com/highacciploc/v1?qcip=220.181.38.113&qterm=pc&ak=%s&coord=bd09ll";

    private final RestClient restClient;
    private final String ak;

    public GeoBaiduTemplate(RestClient restClient, String ak) {
        super();
        this.restClient = restClient;
        this.ak = ak;
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
        ResponseEntity<String> response = restClient.get()
                .uri(url)
                .retrieve()
                .toEntity(String.class);
        if (response.getStatusCode().is2xxSuccessful()) {
            String bodyString = response.getBody();
            log.info(" Addr : {} >> Location : {} ", addr, bodyString);
            if (StringUtils.hasText(bodyString)) {
                JSONObject jsonObject = JSONObject.parseObject(bodyString);
                if (jsonObject.getInteger("status") != 0) {
                    throw new IOException(jsonObject.getString("message"));
                }
                return Optional.of(jsonObject);
            }
        }
        log.error("Addr Location Query Error. Response Code >> {}, Body >> {}", response.getStatusCode().value(), response.getBody());
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
            ResponseEntity<String> response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .toEntity(String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                String bodyString = response.getBody();
                log.info(" IP : {} >> Location : {} ", ip, bodyString);
                if (StringUtils.hasText(bodyString)) {
                    JSONObject jsonObject = JSONObject.parseObject(bodyString);
                    if (jsonObject.getInteger("status") != 0) {
                        throw new IOException(jsonObject.getString("message"));
                    }
                    return Optional.of(jsonObject);
                }
            }
        } catch (Exception e) {
            log.error("IP : {} >> Location Query Error. {}", ip, e.getMessage());
        }
        return Optional.empty();
    }

    public static void main(String[] args) throws IOException {

        GeoBaiduTemplate template = new GeoBaiduTemplate(RestClient.create(), "");

        Map<String, BigDecimal> mapLL = template.getLatAndLngByAddress("浙江省杭州市西湖区"); // lng：116.86380647644208  lat：38.297615350325717
        mapLL.get("lat");
        mapLL.get("lng");
        log.debug("lng：" + mapLL.get("lng") + "  lat：" + mapLL.get("lat"));

        Optional<JSONObject> mapLL2 = template.getLocationByIp("115.204.225.154"); // lng：116.86380647644208  lat：38.297615350325717
        log.debug(mapLL2.get().toJSONString());
    }


}
