package io.ddd4j.extension.monitor.domain.qiwei.service;

import io.ddd4j.extension.monitor.domain.common.vo.MarkDownVO;
import io.ddd4j.extension.monitor.domain.common.vo.MsgVO;
import io.ddd4j.kit.lang.JsonKit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/**
 * 企微告警util
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j(topic = "### BASE-MONITOR : QiWeiService ###")
public class QiWeiService {

    public static final String BASE_URL = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=";
    private static final RestTemplate restTemplate = new RestTemplate();
    private static final RestClient restClient = RestClient.builder().build();

    /**
     * 处理发送的钉钉消息
     *
     * @param msg json格式数据
     */
    public static void send(String key, String msg) {
        Long timestamp = System.currentTimeMillis();
        String dingUrl = BASE_URL + key;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf(MediaType.APPLICATION_JSON_VALUE));
        MsgVO content = new MsgVO();
        MarkDownVO markDown = new MarkDownVO();
        markDown.setContent(msg);
        content.setMarkdown(markDown);
        content.setMsgtype("markdown");
        HttpEntity<String> httpEntity = new HttpEntity<>(JsonKit.toJson(content), headers);
        try {
            String response = restTemplate.postForObject(dingUrl, httpEntity, String.class);
            log.debug("【发送企微告警消息】消息响应结果：{}", response);
        } catch (Exception e) {
            log.error("【发送企微告警消息】error：" + e.getMessage(), e);
        }
    }


}