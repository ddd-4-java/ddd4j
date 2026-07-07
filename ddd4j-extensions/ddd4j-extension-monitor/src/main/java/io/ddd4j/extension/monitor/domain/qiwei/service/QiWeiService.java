package io.ddd4j.extension.monitor.domain.qiwei.service;

import io.ddd4j.extension.monitor.domain.common.vo.MarkDownVO;
import io.ddd4j.extension.monitor.domain.common.vo.MsgVO;
import io.ddd4j.kit.lang.JsonKit;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 企微告警util
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j(topic = "### BASE-MONITOR : QiWeiService ###")
public class QiWeiService {

    /**
     * 企微机器人 Webhook 基础地址
     */
    public static final String BASE_URL = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=";
    /**
     * HTTP 客户端
     */
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 处理发送的企微消息
     *
     * @param key 企微机器人 Webhook Key
     * @param msg markdown 格式消息内容
     */
    public static void send(String key, String msg) {
        String dingUrl = BASE_URL + key;
        MsgVO content = new MsgVO();
        MarkDownVO markDown = new MarkDownVO();
        markDown.setContent(msg);
        content.setMarkdown(markDown);
        content.setMsgtype("markdown");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(dingUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(JsonKit.toJson(content), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            log.debug("【发送企微告警消息】消息响应结果：{}", response.body());
        } catch (Exception e) {
            log.error("【发送企微告警消息】error：" + e.getMessage(), e);
        }
    }


}
