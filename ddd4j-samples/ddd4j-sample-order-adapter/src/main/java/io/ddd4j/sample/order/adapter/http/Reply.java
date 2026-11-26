package io.ddd4j.sample.order.adapter.http;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;

/** 框架适配层传输的 HTTP 状态码与 JSON。 */
@Value
public class Reply {
    private int status;
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
