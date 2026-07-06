package io.ddd4j.core.ddd.event;

import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * 异常事件。
 *
 * <p>当请求处理发生未捕获异常时，由框架适配层（Spring / Quarkus / Guice）
 * 构造此事件并发布到 {@link DomainEventPublisher}，供业务侧监听做告警、
 * 审计、错误统计等横切处理。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @EventListener
 * public void onException(ExceptionEvent event) {
 *     alertService.notify(event.getSource(), event.getPayload());
 * }
 * }</pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Getter
public class ExceptionEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 请求来源（如 URL、方法名等上下文信息） */
    private final Object source;

    /** 异常载荷 */
    private final Exception payload;

    /**
     * 构造异常事件。
     *
     * @param source  请求来源（可为 null）
     * @param payload 异常对象（不可为 null）
     * @throws NullPointerException payload 为 null
     */
    public ExceptionEvent(Object source, Exception payload) {
        this.source = source;
        this.payload = Objects.requireNonNull(payload, "payload must not be null");
    }
}
