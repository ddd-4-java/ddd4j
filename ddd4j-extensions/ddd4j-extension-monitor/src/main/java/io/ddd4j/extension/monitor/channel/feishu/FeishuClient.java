package io.ddd4j.extension.monitor.channel.feishu;

import io.ddd4j.kit.lang.JsonKit;
import io.ddd4j.kit.lang.StrKit;
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

/**
 * 飞书自定义机器人 Webhook 客户端。
 *
 * <p>与钉钉机器人同属"timestamp\n secret → HmacSHA256 → Base64"加签协议；
 * 差异在于：飞书的 webhook URL 本身就含 token（无需 access_token），
 * 且支持 {@code msg_type=text/post/share_chat/interactive} 多种消息类型。
 *
 * <p>本类只暴露 {@link #send(String)} —— 由 {@link FeishuRobotSender} 构造符合飞书协议的 message 后回调。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j(topic = "### DDD4j-MONITOR : FeishuClient ###")
public class FeishuClient {

    /**
     * 飞书默认 webhook 基础地址（占位。实际每个机器人 webhook URL 不同，
     * 由 {@link FeishuProperties#getWebhookUrl()} 注入）。
     */
    public static final String DEFAULT_BASE_URL = "https://open.feishu.cn/open-apis/bot/v2/hook/";

    /**
     * HTTP 客户端（连接超时 10s）
     */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 飞书机器人 webhook 完整地址（含 token）
     */
    private final String webhookUrl;
    /**
     * 加签密钥（飞书机器人在设置时由用户给定；非签模式可为空）
     */
    private final String secret;

    /**
     * 默认构造函数（典型用法）。
     *
     * @param webhookUrl 飞书机器人 webhook 完整地址，含 hook token
     * @param secret      加签密钥（"不勾选签名校验"时可为 null 或空字符串）
     */
    public FeishuClient(String webhookUrl, String secret) {
        this.webhookUrl = webhookUrl;
        this.secret = secret;
    }

    /** @return 配置的 webhook 完整 URL */
    public String webhookUrl() {
        return webhookUrl;
    }

    /** @return 配置的加签密钥（无则为空字符串） */
    public String secret() {
        return secret;
    }

    /**
     * 发送一条 JSON 消息。
     *
     * <p>若 {@code secret} 非空，会按飞书签名协议在 URL 上拼接 {@code timestamp} 与 {@code sign}：
     * <ul>
     *   <li>{@code stringToSign = "{timestamp}\n{secret}"}</li>
     *   <li>{@code sign = URLEncoder.encode(Base64(HmacSHA256(stringToSign)))}</li>
     * </ul>
     *
     * @param msg 已构好的 JSON payload
     */
    public void send(String msg) {
        try {
            String url = buildUrl();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(msg, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            log.debug("【发送飞书告警】响应：{}", response.body());
        } catch (Exception e) {
            log.error("【发送飞书告警】error: {}", e.getMessage(), e);
        }
    }

    /**
     * 拼接完整 URL：若设置了签名密钥，则追加 query；否则原样返回。
     */
    private String buildUrl() throws Exception {
        if (StrKit.isEmpty(secret)) {
            return webhookUrl;
        }
        long timestamp = System.currentTimeMillis() / 1000L;
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        String sign = URLEncoder.encode(
                java.util.Base64.getEncoder().encodeToString(signData), "UTF-8");
        return webhookUrl + "?timestamp=" + timestamp + "&sign=" + sign;
    }
}
