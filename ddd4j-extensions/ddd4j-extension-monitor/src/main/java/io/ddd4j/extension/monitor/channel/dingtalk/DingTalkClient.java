package io.ddd4j.extension.monitor.channel.dingtalk;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * 钉钉群机器人 Webhook 客户端。
 *
 * <p>纯 Java，使用 JDK {@link HttpClient} 推送 JSON 消息。
 * 鉴权采用钉钉机器人加签（{@code timestamp \n secret} 经 HMAC-SHA256 处理后 Base64 + URL 编码）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j(topic = "### DDD4j-MONITOR : DingTalkClient ###")
public class DingTalkClient {

    /**
     * 钉钉机器人 Webhook 基础地址
     */
    public static final String BASE_URL = "https://oapi.dingtalk.com/robot/send?access_token=";

    /**
     * 钉钉机器人 access_token
     */
    private final String accessToken;
    /**
     * 钉钉机器人加签密钥
     */
    private final String secret;
    /**
     * 钉钉 webhook 基础地址（可被构造函数替换，常量 {@link #BASE_URL} 是默认值）
     */
    private final String baseUrl;

    /**
     * 默认构造函数，使用 {@link #BASE_URL}。
     */
    public DingTalkClient(String accessToken, String secret) {
        this(accessToken, secret, BASE_URL);
    }

    /**
     * 可注入 baseUrl 的构造函数（用于测试或在企业内部代理场景）。
     *
     * @param accessToken 钉钉机器人 access_token
     * @param secret      钉钉机器人加签密钥
     * @param baseUrl     webhook 基础地址，应包含 {@code ?access_token=}
     */
    public DingTalkClient(String accessToken, String secret, String baseUrl) {
        this.accessToken = accessToken;
        this.secret = secret;
        this.baseUrl = baseUrl;
    }

    /** @return 配置的 access_token */
    public String accessToken() {
        return accessToken;
    }

    /** @return 配置的加签密钥 */
    public String secret() {
        return secret;
    }

    /** @return 当前生效的 webhook 基础地址（含 {@code ?access_token=}） */
    public String baseUrl() {
        return baseUrl;
    }

    /**
     * 发送 markdown 格式消息。
     *
     * @param msg 完整的 JSON payload，可通过 {@code Sender.renderMessage(Message)} 构造
     */
    public void sendMarkdown(String msg) {
        try {
            long timestamp = System.currentTimeMillis();
            String sign = getSign(timestamp, secret);
            String url = baseUrl + accessToken + "&timestamp=" + timestamp + "&sign=" + sign;
            cn.hutool.http.HttpResponse response = cn.hutool.http.HttpRequest.post(url)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .body(msg)
                    .timeout(10_000)
                    .execute();
            log.debug("【发送钉钉群消息】响应：{}", response.body());
        } catch (Exception e) {
            log.error("【发送钉钉群消息】error: {}", e.getMessage(), e);
        }
    }

    /**
     * 计算钉钉机器人加签。
     */
    private static String getSign(long timestamp, String secret) throws Exception {
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        return URLEncoder.encode(new String(Base64.getEncoder().encode(signData)), "UTF-8");
    }
}
