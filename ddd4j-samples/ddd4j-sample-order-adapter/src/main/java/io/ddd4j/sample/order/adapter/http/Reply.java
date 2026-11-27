package io.ddd4j.sample.order.adapter.http;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;

/** 框架适配层传输的 HTTP 状态码与 JSON。 */
@Value
public class Reply {
    /** HTTP \u54cd\u5e94\u72b6\u6001\u7801\u3002 */
    private int status;
    /** \u5f85\u8fd4\u56de\u7684 JSON \u54cd\u5e94\u6b63\u6587\u3002 */
    private JsonNode payload;

    /**
     * 获取序列化后的响应正文。
     *
     * @return 响应 JSON 正文
     */
    public String getBody() {
        return payload.toString();
    }
}
