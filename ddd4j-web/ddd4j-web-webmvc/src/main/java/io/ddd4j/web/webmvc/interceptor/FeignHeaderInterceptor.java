package io.ddd4j.web.webmvc.interceptor;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.web.webmvc.annotation.FeignHeader;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;

import static io.ddd4j.core.constant.ContextConstants.SYSTEM_ID;
import static io.ddd4j.core.constant.ContextConstants.TENANT_ID;

/**
 * Feign 请求头拦截器。
 * <p>自动向 Feign 请求中添加认证、租户、系统 ID 等请求头，支持通过 {@link FeignHeader} 注解精细控制。</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class FeignHeaderInterceptor implements RequestInterceptor, Ordered {
    /**
     * 租户 ID 请求头名称列表
     */
    public static final String[] HEADER_TENANT_IDS = new String[]{"tenant_id", "tenant-id", "tenantId"};
    /**
     * 系统 ID 请求头名称列表
     */
    public static final String[] HEADER_SYSTEM_IDS = new String[]{"system_id", "system-id", "systemId"};
    private static final String[] USE_WEB_HEADERS = new String[]{"tenant-id", "system-id", "third-session", "enterprise-id", "shop-id", "app-id", "switch-tenant-id", "Authorization", "client-type", "own-language"};
    private static final String[] REMOVE_AUTHORIZATION_HEADER_TARGETS = new String[]{"cloud-mall-api", "cloud-pay-api", "subscribe-service"};

    /**
     * 应用请求头到 Feign 请求模板。
     *
     * @param template Feign 请求模板
     */
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        template.header("from", "Y");
        FeignHeader feignHeader = template.methodMetadata().method().getAnnotation(FeignHeader.class);
        if (Objects.nonNull(feignHeader)) {
            String webTenantId = "";
            String webSystemId = "";
            if (feignHeader.useWebRequestHeader()) {
                if (Objects.nonNull(attributes)) {
                    HttpServletRequest request = attributes.getRequest();
                    for (String header : USE_WEB_HEADERS) {
                        String headerValue = request.getHeader(header);
                        if (Objects.nonNull(headerValue) && org.springframework.util.StringUtils.hasLength(headerValue)) {
                            template.header(header, headerValue);
                        }
                    }
                    webTenantId = request.getHeader(TENANT_ID);
                    webSystemId = request.getHeader(SYSTEM_ID);
                    if (Objects.isNull(webTenantId) || !org.springframework.util.StringUtils.hasLength(webTenantId)) {
                        webTenantId = request.getHeader("switch-tenant-id");
                        if (Objects.isNull(webTenantId) || !org.springframework.util.StringUtils.hasLength(webTenantId)) {
                            webTenantId = ThreadContext.get(TENANT_ID);
                        }
                    }
                    if (Objects.isNull(webSystemId) || !org.springframework.util.StringUtils.hasLength(webSystemId)) {
                        webSystemId = ThreadContext.get(SYSTEM_ID);
                    }
                    for (String headerTenantId : HEADER_TENANT_IDS) {
                        template.header(headerTenantId, webTenantId);
                    }
                }
            }

            if (feignHeader.autoFillTenantId() && (Objects.isNull(webTenantId) || !org.springframework.util.StringUtils.hasLength(webTenantId))) {
                String tenantId = ThreadContext.get(TENANT_ID);
                for (String headerTenantId : HEADER_TENANT_IDS) {
                    template.header(headerTenantId, tenantId);
                }
            }

            if (feignHeader.autoFillSystemId() && (Objects.isNull(webSystemId) || !org.springframework.util.StringUtils.hasLength(webSystemId))) {
                String systemId = (Objects.isNull(ThreadContext.get(SYSTEM_ID)) || ThreadContext.<String>get(SYSTEM_ID).isEmpty()) ? "0" : ThreadContext.get(SYSTEM_ID);
                for (String headerSystemId : HEADER_SYSTEM_IDS) {
                    template.header(headerSystemId, systemId);
                }
            }
        } else {
            if (Objects.nonNull(attributes)) {
                String[] headers = new String[]{"system-id", "client-type", "own-language"};
                for (String header : headers) {
                    String headerValue = attributes.getRequest().getHeader(header);
                    if (Objects.nonNull(headerValue) && org.springframework.util.StringUtils.hasLength(headerValue)) {
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
