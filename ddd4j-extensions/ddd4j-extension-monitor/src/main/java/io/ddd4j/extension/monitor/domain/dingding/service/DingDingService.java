package io.ddd4j.extension.monitor.domain.dingding.service;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * 钉钉告警util
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j(topic = "### BASE-MONITOR : DingDingService ###")
public class DingDingService {

    /**
     * 钉钉机器人 Webhook 基础地址
     */
    public static final String BASE_URL = "https://oapi.dingtalk.com/robot/send?access_token=";
    /**
     * HTTP 客户端
     */
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 处理发送的钉钉消息
     *
     * @param accessToken 钉钉机器人 access_token
     * @param secret      钉钉机器人加签密钥
     * @param msg         json格式数据
     */
    public static void send(String accessToken, String secret, String msg) {
        try {
            Long timestamp = System.currentTimeMillis();
            String sign = getSign(timestamp, secret);
            String dingUrl = BASE_URL + accessToken + "&timestamp=" + timestamp + "&sign=" + sign;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(dingUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(msg, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            log.debug("【发送钉钉群消息】消息响应结果：{}", response.body());
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
