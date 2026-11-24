package io.ddd4j.sample.order.testkit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.ddd4j.sample.order.application.AddOrderLineCommand;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.application.OrderReadModel;
import io.ddd4j.sample.order.domain.OrderQuery;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 适配已有 R 包装响应的真实 HTTP 订单协议；不包含业务、平台装配或上下文清理。 */
public class EnvelopeOrderHttpDriver implements OrderSampleDriver {
    private final String base;
    private final String orders;
    private final Map<String, String> headers;
    private final HttpClient client = HttpClient.newHttpClient();
    /** 真实 HTTP 响应的共享 JSON 映射器。 */
    protected final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    /**
     * @param base 服务器地址
     * @param orders 原订单路径
     * @param headers 原生认证请求头
     */
    public EnvelopeOrderHttpDriver(String base, String orders, Map<String, String> headers) {
        this.base = base;
        this.orders = orders;
        this.headers = Map.copyOf(headers);
    }
    @Override public String create(CreateOrderCommand command) {
        return request("POST", orders, command, Map.of()).path("id").asText();
    }
    @Override public void addLine(AddOrderLineCommand command) {
        ObjectNode body = mapper.valueToTree(command);
        body.remove("orderId");
        request("POST", orders + "/" + command.orderId() + "/lines", body, Map.of());
    }
    @Override public void pay(String id, String paymentKey) {
        request("POST", orders + "/" + id + "/pay", null, Map.of("Idempotency-Key", paymentKey));
    }
    @Override public OrderReadModel find(String id) {
        return decode(request("GET", orders + "/" + id, null, Map.of()));
    }
    @Override public List<OrderReadModel> query(OrderQuery query) {
        String path = orders + "?page=" + query.page() + "&size=" + query.size();
        if (Objects.nonNull(query.buyerId())) {
            path += "&buyerId=" + URLEncoder.encode(query.buyerId(), StandardCharsets.UTF_8);
        }
        if (Objects.nonNull(query.status())) {
            path += "&status=" + query.status().name();
        }
        JsonNode rows = request("GET", path, null, Map.of());
        List<OrderReadModel> result = new ArrayList<>();
        rows.forEach(row -> result.add(decode(row)));
        return result;
    }
    /**
     * @param value 实际响应
     * @return 公共读模型
     */
    protected OrderReadModel decode(JsonNode value) {
        ObjectNode row = value.deepCopy();
        if (row.path("totalAmount").isObject()) {
            row.set("totalAmount", row.path("totalAmount").path("amount"));
        }
        row.remove(List.of("currency", "lineCount", "lines", "createdAt", "updatedAt"));
        return mapper.convertValue(row, OrderReadModel.class);
    }
    /**
     * @param method HTTP 方法
     * @param path 路径
     * @param body 请求体
     * @param additional 附加头
     * @return 实际 data
     */
    public JsonNode request(String method, String path, Object body, Map<String, String> additional) {
        try {
            SampleHttpTransport.Response response = SampleHttpTransport.request(client, base + path, method,
                    headers, additional, Objects.isNull(body) ? null : mapper.writeValueAsString(body));
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new SecurityException("HTTP " + response.statusCode());
            }
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("HTTP " + response.statusCode() + ": " + response.body());
            }
            return mapper.readTree(response.body()).path("data");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failure);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
