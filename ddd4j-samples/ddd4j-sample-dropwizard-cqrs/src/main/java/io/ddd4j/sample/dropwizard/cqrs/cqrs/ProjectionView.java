package io.ddd4j.sample.dropwizard.cqrs.cqrs;

import java.util.List;

/**
 * 投影视图接口（CQRS 读侧）。
 *
 * <p>订阅事件并维护读模型。
 */
public interface ProjectionView {

    String getName();

    void handleEvents(List<Object> events);
}
