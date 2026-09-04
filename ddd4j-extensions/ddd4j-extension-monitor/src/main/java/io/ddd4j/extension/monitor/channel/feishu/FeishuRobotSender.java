package io.ddd4j.extension.monitor.channel.feishu;

import io.ddd4j.extension.monitor.Sender;
import io.ddd4j.kit.lang.JsonKit;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 飞书群机器人 {@link Sender} 适配器。
 *
 * <p>{@link #send(String)} 默认走 {@code msg_type=post}（富文本，能近似渲染 markdown）；
 * 如需文本/消息卡片等更丰富的协议层，请调用 {@link #send(Map)} 直接传完整 JSON payload。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class FeishuRobotSender implements Sender {

    /**
     * 飞书 post 富文本消息 JSON 字符串构造辅助结构，仅本类使用。
     */
    @Data
    @NoArgsConstructor
    static class PostPayload {
        private Map<String, Object> post;
    }

    private final FeishuClient client;

    /**
     * @param webhookUrl 飞书机器人 webhook 完整地址（含 hook token）
     * @param secret      加签密钥（无则置 null 或空字符串）
     */
    public FeishuRobotSender(String webhookUrl, String secret) {
        this.client = new FeishuClient(webhookUrl, secret);
    }

    /**
     * 暴露底层 HTTP 客户端，便于高级场景直接复用。
     */
    public FeishuClient getClient() {
        return client;
    }

    @Override
    public void send(String msg) {
        // 入参 msg 是 markdown 文本，这里包一层为标准 post 富文本结构：
        //   post 协议要求 content[0] 是富文本二维数组 [[ {tag: text, text: msg} ]]。
        Map<String, Object> textPara = new HashMap<>();
        textPara.put("tag", "text");
        textPara.put("text", msg);
        List<List<Map<String, Object>>> paragraphs = List.of(List.of(textPara));

        Map<String, Object> zhCn = new HashMap<>();
        zhCn.put("title", "");
        zhCn.put("content", paragraphs);

        Map<String, Object> postNode = new HashMap<>();
        postNode.put("zh_cn", zhCn);

        Map<String, Object> root = new HashMap<>();
        root.put("msg_type", "post");
        root.put("content", postNode);

        client.send(JsonKit.toJson(root));
    }
}
