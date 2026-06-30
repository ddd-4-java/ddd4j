package io.ddd4j.quarkus.core.context;

import jakarta.enterprise.context.RequestScoped;
import lombok.Getter;
import lombok.Setter;

/**
 * Quarkus 请求级上下文，对齐 ddd4j-boot-web 的线程上下文能力。
 */
@Getter
@Setter
@RequestScoped
public class RequestContext {

    private String tenantId;

    private String userId;
}
