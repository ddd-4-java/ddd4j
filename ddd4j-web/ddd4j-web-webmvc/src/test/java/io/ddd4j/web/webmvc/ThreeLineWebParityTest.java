package io.ddd4j.web.webmvc;

import feign.Contract;
import feign.RequestLine;
import feign.RequestTemplate;
import feign.Target;
import io.ddd4j.core.ApiRestResponse;
import io.ddd4j.core.BaseCoreProperties;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.web.webmvc.annotation.FeignHeader;
import io.ddd4j.web.webmvc.config.BaseWebConfig;
import io.ddd4j.web.webmvc.config.ServerI18nProperties;
import io.ddd4j.web.webmvc.error.GlobalExceptionHandler;
import io.ddd4j.web.webmvc.interceptor.BaseWebInterceptor;
import io.ddd4j.web.webmvc.interceptor.FeignHeaderInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;

import java.util.Collections;
import java.util.List;

import static io.ddd4j.core.constant.ContextConstants.SYSTEM_ID;
import static org.junit.jupiter.api.Assertions.*;

/** 同输入验证跨版本 Web 注册与出站 header；不启动外部服务。 */
class ThreeLineWebParityTest {
    @AfterEach
    void clearContext() {
        ThreadContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void nonEmptyInterceptorsAreRegisteredWithTheirPaths() {
        BaseWebInterceptor interceptor = new BaseWebInterceptor() {
            @Override public int getOrder() { return 0; }
            @Override public String[] pathPatterns() { return new String[]{"/protected/**"}; }
        };
        BaseWebConfig config = new BaseWebConfig(Collections.singletonList(interceptor),
                new BaseCoreProperties(), Collections.emptyList());
        RecordingRegistry registry = new RecordingRegistry();
        config.addInterceptors(registry);
        assertEquals(1, registry.entries().size(), "non-empty interceptor list must be registered");
        MappedInterceptor mapped = (MappedInterceptor) registry.entries().get(0);
        assertSame(interceptor, mapped.getInterceptor());
        assertArrayEquals(new String[]{"/protected/**"}, mapped.getPathPatterns());
    }

    @Test
    void emptyAndNullInterceptorListsAreSafe() {
        RecordingRegistry registry = new RecordingRegistry();
        new BaseWebConfig(Collections.emptyList(), new BaseCoreProperties(), Collections.emptyList()).addInterceptors(registry);
        new BaseWebConfig(null, new BaseCoreProperties(), Collections.emptyList()).addInterceptors(registry);
        assertTrue(registry.entries().isEmpty());
    }

    @Test
    void systemIdDefaultsForNullAndEmptyButPreservesAValue() throws Exception {
        for (String value : new String[]{null, "", "tenant-system"}) {
            ThreadContext.clear();
            if (value != null) {
                ThreadContext.set(SYSTEM_ID, value);
            }
            RequestTemplate template = new RequestTemplate();
            template.methodMetadata(new Contract.Default().parseAndValidateMetadata(HeaderClient.class).get(0));
            template.feignTarget(new Target.HardCodedTarget<HeaderClient>(HeaderClient.class, "parity", "http://localhost"));
            new FeignHeaderInterceptor().apply(template);
            String expected = value == null || value.isEmpty() ? "0" : "tenant-system";
            for (String name : FeignHeaderInterceptor.HEADER_SYSTEM_IDS) {
                assertEquals(Collections.singletonList(expected), new java.util.ArrayList<String>(template.headers().get(name)), name);
            }
        }
    }

    @Test
    void missingHeaderResponsePreservesStatusAndMessageWithOrWithoutI18n() throws Exception {
        GlobalExceptionHandler handler = new GlobalExceptionHandler() {
            @Override protected void logException(Exception exception) { }
            @Override protected String getLocaleMessage(Exception ex, String key, String fallback) {
                return "localized header message";
            }
        };
        ServerI18nProperties properties = new ServerI18nProperties();
        ReflectionTestUtils.setField(handler, "serverI18NProperties", properties);
        MissingRequestHeaderException exception = new MissingRequestHeaderException("X-Parity",
                new MethodParameter(ThreeLineWebParityTest.class.getDeclaredMethod("headerEndpoint", String.class), 0));
        for (boolean enabled : new boolean[]{false, true}) {
            properties.setEnabled(enabled);
            ApiRestResponse<String> response = handler.missingRequestHeaderException(exception);
            assertEquals(400, response.getCode());
            assertEquals(enabled ? "localized header message" : "缺少请求头: [X-Parity].", response.getMessage());
        }
    }

    private void headerEndpoint(String value) { }

    interface HeaderClient {
        @RequestLine("GET /")
        @FeignHeader(autoFillTenantId = false, useWebRequestHeader = false)
        void request();
    }

    private static class RecordingRegistry extends InterceptorRegistry {
        List<Object> entries() { return getInterceptors(); }
    }
}

