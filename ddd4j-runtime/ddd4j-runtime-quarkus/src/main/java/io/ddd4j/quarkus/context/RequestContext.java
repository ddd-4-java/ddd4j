package io.ddd4j.quarkus.context;

import jakarta.enterprise.context.RequestScoped;
import lombok.Getter;
import lombok.Setter;

/**
 * Quarkus 请求级上下文，对齐 ddd4j-boot-web 的线程上下文能力。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Getter
@Setter
@RequestScoped
public class RequestContext {

    /**
     * 租户 ID
     */
    private String tenantId;

    /**
     * 用户 ID
     */
    private String userId;
}
