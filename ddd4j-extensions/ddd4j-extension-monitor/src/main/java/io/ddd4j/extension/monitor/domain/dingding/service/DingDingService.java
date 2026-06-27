package io.ddd4j.extension.monitor.domain.dingding.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 钉钉告警util
 *
 * @author Loong Wan
 */
@Slf4j(topic = "### BASE-MONITOR : DingDingService ###")
public class DingDingService {

    private static final RestTemplate restTemplate = new RestTemplate();


    public static final String BASE_URL = "https://oapi.dingtalk.com/robot/send?access_token=";

    /**
     * 处理发送的钉钉消息
     *
     * @param msg json格式数据
     */
    public static void send(String accessToken, String secret, String msg) {
        try {
            Long timestamp = System.currentTimeMillis();
            String sign = getSign(timestamp, secret);
            String dingUrl = BASE_URL + accessToken + "&timestamp=" + timestamp + "&sign=" + sign;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.valueOf(MediaType.APPLICATION_JSON_VALUE));
            HttpEntity<String> httpEntity = new HttpEntity<>(msg, headers);

            String response = restTemplate.postForObject(dingUrl, httpEntity, String.class);
            log.debug("【发送钉钉群消息】消息响应结果：{}", response);
        } catch (Exception e) {
            log.error("【发送钉钉群消息】error：" + e.getMessage(), e);
        }
    }


    private static String getSign(Long timestamp, String secret) throws Exception {
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        return URLEncoder.encode(new String(Base64.getEncoder().encode(signData)), "UTF-8");
    }


}