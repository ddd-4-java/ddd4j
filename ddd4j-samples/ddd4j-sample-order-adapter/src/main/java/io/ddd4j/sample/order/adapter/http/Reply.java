package io.ddd4j.sample.order.adapter.http;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;

/** 框架适配层传输的 HTTP 状态码与 JSON。 */
@Value
public class Reply {
    private int status;
    private JsonNode payload;

    public String getBody() {
        return payload.toString();
    }
}
