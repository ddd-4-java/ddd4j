package io.ddd4j.sample.order.testkit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.order.application.AddOrderLineCommand;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.application.OrderReadModel;
import io.ddd4j.sample.order.domain.OrderQuery;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** 共享验收的真实 HTTP 驱动，仅负责传输和解码；不执行订单业务或清理平台上下文。 */
public class HttpOrderSampleDriver implements OrderSampleDriver {
    private final String baseUrl;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    /** @param baseUrl 当前实际监听的 HTTP 地址 */
    public HttpOrderSampleDriver(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public String create(CreateOrderCommand command) {
        return request("POST", "/orders", command, String.class);
    }

    @Override
    public void addLine(AddOrderLineCommand command) {
        request("POST", "/orders/lines", command, String.class);
    }

    @Override
    public void pay(String orderId, String paymentKey) {
        request("POST", "/orders/" + orderId + "/payment?key=" + paymentKey, null, String.class);
    }

    @Override
    public OrderReadModel find(String orderId) {
        return request("GET", "/orders/" + orderId, null, OrderReadModel.class);
    }

    @Override
    public List<OrderReadModel> query(OrderQuery query) {
        return Arrays.asList(request("POST", "/orders/query", query, OrderReadModel[].class));
    }

    /**
     * 发送实际请求并将 HTTP 失败转换为契约异常。
     * @param method HTTP 方法
     * @param path 相对路径
     * @param body JSON 输入或 null
     * @param type 响应类型
     * @param <T> 响应类型
     * @return 实际响应解码
     */
    public <T> T request(String method, String path, Object body, Class<T> type) {
        try {
            SampleHttpTransport.Response response = SampleHttpTransport.request(client, baseUrl + path, method,
                    Map.of(), Map.of(), mapper.writeValueAsString(body));
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new SecurityException(response.body());
            }
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("HTTP " + response.statusCode() + ": " + response.body());
            }
            if (type == String.class) {
                return type.cast(response.body().startsWith("\"")
                        ? mapper.readValue(response.body(), String.class) : response.body());
            }
            return mapper.readValue(response.body(), type);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
