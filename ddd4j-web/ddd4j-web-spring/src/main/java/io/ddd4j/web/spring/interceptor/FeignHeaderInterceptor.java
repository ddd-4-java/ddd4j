package io.ddd4j.web.spring.interceptor;

import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.contract.annotation.FeignHeader;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.core.Ordered;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;

import static io.ddd4j.core.contract.constant.ContextConstants.SYSTEM_ID;
import static io.ddd4j.core.contract.constant.ContextConstants.TENANT_ID;

/**
 * Feign请求头拦截器
 */
public class FeignHeaderInterceptor implements RequestInterceptor, Ordered {
    private static final String[] USE_WEB_HEADERS = new String[]{"tenant-id", "system-id", "third-session", "enterprise-id", "shop-id", "app-id", "switch-tenant-id", "Authorization", "client-type", "own-language"};
    private static final String[] REMOVE_AUTHORIZATION_HEADER_TARGETS = new String[]{"cloud-mall-api", "cloud-pay-api", "subscribe-service"};
    public static final String[] HEADER_TENANT_IDS = new String[]{"tenant_id", "tenant-id", "tenantId"};
    public static final String[] HEADER_SYSTEM_IDS = new String[]{"system_id", "system-id", "systemId"};

    public void apply(RequestTemplate template) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        template.header("from", "Y");
        FeignHeader feignHeader = template.methodMetadata().method().getAnnotation(FeignHeader.class);
        if (Objects.nonNull(feignHeader)) {
            String webTenantId = "";
            String webSystemId = "";
            if (feignHeader.useWebRequestHeader()) {
                if (attributes != null) {
                    HttpServletRequest request = attributes.getRequest();
                    for (String header : USE_WEB_HEADERS) {
                        String headerValue = request.getHeader(header);
                        if (headerValue != null && !headerValue.isEmpty()) {
                            template.header(header, headerValue);
                        }
                    }
                    webTenantId = request.getHeader(TENANT_ID);
                    webSystemId = request.getHeader(SYSTEM_ID);
                    if (webTenantId == null || webTenantId.isEmpty()) {
                        webTenantId = request.getHeader("switch-tenant-id");
                        if (webTenantId == null || webTenantId.isEmpty()) {
                            webTenantId = ThreadContext.get(TENANT_ID);
                        }
                    }
                    if (webSystemId == null || webSystemId.isEmpty()) {
                        webSystemId = ThreadContext.get(SYSTEM_ID);
                    }
                    for (String headerTenantId : HEADER_TENANT_IDS) {
                        template.header(headerTenantId, webTenantId);
                    }
                }
            }

            if (feignHeader.autoFillTenantId() && (webTenantId == null || webTenantId.isEmpty())) {
                String tenantId = ThreadContext.get(TENANT_ID);
                for (String headerTenantId : HEADER_TENANT_IDS) {
                    template.header(headerTenantId, tenantId);
                }
            }

            if (feignHeader.autoFillSystemId() && (webSystemId == null || webSystemId.isEmpty())) {
                String systemId = (ThreadContext.get(SYSTEM_ID) == null || ThreadContext.<String>get(SYSTEM_ID).isEmpty()) ? "0" : ThreadContext.get(SYSTEM_ID);
                for (String headerSystemId : HEADER_SYSTEM_IDS) {
                    template.header(headerSystemId, systemId);
                }
            }
        } else {
            if (attributes != null) {
                String[] headers = new String[]{"system-id", "client-type", "own-language"};
                for (String header : headers) {
                    String headerValue = attributes.getRequest().getHeader(header);
                    if (headerValue != null && !headerValue.isEmpty()) {
                        template.header(header, headerValue);
                    }
                }
            }
            template.header("tenantId", ThreadContext.<String>get(TENANT_ID));
            template.header("tenant-id", ThreadContext.<String>get(TENANT_ID));
            template.header("system-id", ThreadContext.<String>get(SYSTEM_ID));
            if (!template.headers().containsKey("tenant_id")) {
                template.header("tenant_id", ThreadContext.<String>get(TENANT_ID));
            }
            if (!template.headers().containsKey("system_id")) {
                template.header("system_id", ThreadContext.<String>get(SYSTEM_ID));
            }
        }

        for (String str : REMOVE_AUTHORIZATION_HEADER_TARGETS) {
            if (Objects.equals(template.feignTarget().name(), str)) {
                template.removeHeader("Authorization");
                break;
            }
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
