package io.ddd4j.web.webmvc.logs;

import cn.hutool.core.lang.Snowflake;
import com.google.common.base.Stopwatch;
import io.ddd4j.core.constant.XHeaders;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.data.logs.ApiOperationLogProvider;
import io.ddd4j.data.logs.ApiOperationLogRequestContext;
import io.ddd4j.data.logs.DefaultApiOperationLogProvider;
import io.ddd4j.data.logs.aspect.ApiOperationLogAspect;
import io.ddd4j.web.core.context.WebContextScope;
import io.ddd4j.web.core.context.WebRequestContext;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 日志 HTTP 桥接保留旧请求行为，并兼容原生 WebContextScope。 */
class ApiOperationLogHttpBridgeTest {
    private final Logger logger = (Logger) LogManager.getLogger(DefaultApiOperationLogProvider.class);
    private final List<LogEvent> events = new ArrayList<>();
    private Level previous;
    private AbstractAppender appender;

    @BeforeEach
    void captureLogs() {
        previous = logger.getLevel();
        appender = new AbstractAppender("r05-http", null, PatternLayout.createDefaultLayout(), false, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) { events.add(event.toImmutable()); }
        };
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
        ThreadContext.clear();
        logger.removeAppender(appender);
        logger.setLevel(previous);
        appender.stop();
    }

    @Test
    void legacyRequestContextHolderKeepsParameterThenHeaderThenSnowflake() {
        MockHttpServletRequest request = bindRequest();
        ApiOperationLogAspect aspect = new ApiOperationLogAspect(new Snowflake(1, 1), new ApiOperationLogProvider() { });
        request.addHeader(XHeaders.X_REQUEST_ID, "header-id");
        request.setParameter("requestId", "parameter-id");
        assertEquals("parameter-id", aspect.getRequestId());
        request.setParameter("requestId", "  ");
        assertEquals("header-id", aspect.getRequestId());
        request.removeHeader(XHeaders.X_REQUEST_ID);
        String generated = aspect.getRequestId();
        assertTrue(generated.matches("[0-9]+"));
        assertNotEquals(generated, aspect.getRequestId());
    }

    @Test
    void httpRequestWinsOverCoexistingNativeContextAndKeepsUriIp() {
        try (WebContextScope ignored = nativeScope()) {
            MockHttpServletRequest request = bindRequest();
            request.setParameter("requestId", "legacy-param");
            request.addHeader(XHeaders.X_REQUEST_ID, "legacy-header");
            request.addHeader("X-Forwarded-For", " 198.51.100.9, 10.0.0.1");
            ApiOperationLogRequestContext context = ApiOperationLogRequestContext.current();
            assertInstanceOf(WebMvcApiOperationLogRequestContext.class, context);
            assertEquals("legacy-param", context.requestId());
            assertEquals("/legacy/logs", context.requestUri());
            assertEquals("198.51.100.9", context.clientIp());
            request.removeParameter("requestId");
            assertEquals("legacy-header", context.requestId());
            request.removeHeader(XHeaders.X_REQUEST_ID);
            assertNull(context.requestId(), "HTTP request without IDs preserves legacy Snowflake fallback");
        }
    }

    @Test
    void bridgeFallsBackToExistingWebContextScopeWithoutServletRequest() {
        try (WebContextScope ignored = nativeScope()) {
            ApiOperationLogRequestContext context = ApiOperationLogRequestContext.current();
            assertInstanceOf(WebMvcApiOperationLogRequestContext.class, context);
            assertEquals("native-id", context.requestId());
            assertEquals("/native/logs", context.requestUri());
            assertEquals("192.0.2.10", context.clientIp());
            assertEquals("native-id", new ApiOperationLogAspect(new Snowflake(1, 1),
                    new ApiOperationLogProvider() { }).getRequestId());
        }
        assertNull(ApiOperationLogRequestContext.current().requestId());
    }

    @Test
    void remoteAddressKeepsForwardedRealAndRemoteOrder() {
        MockHttpServletRequest request = bindRequest();
        ApiOperationLogRequestContext context = ApiOperationLogRequestContext.current();
        request.addHeader("X-Real-IP", "198.51.100.11");
        assertEquals("198.51.100.11", context.clientIp());
        request.addHeader("X-Forwarded-For", "203.0.113.8, 10.0.0.1");
        assertEquals("203.0.113.8", context.clientIp());
        request.removeHeader("X-Forwarded-For");
        request.removeHeader("X-Real-IP");
        assertEquals("127.0.0.9", context.clientIp());
    }

    @Test
    void servletArgumentFilterRetainsOriginalConjunction() {
        ApiOperationLogRequestContext context = ApiOperationLogRequestContext.current();
        Object both = Proxy.newProxyInstance(ServletRequest.class.getClassLoader(),
                new Class<?>[]{ServletRequest.class, ServletResponse.class}, (proxy, method, args) -> null);
        assertFalse(context.includeArgument(both));
        assertTrue(context.includeArgument(new MockHttpServletRequest()));
        assertTrue(context.includeArgument(new MockHttpServletResponse()));
        assertTrue(context.includeArgument("business-argument"));
        assertTrue(context.includeArgument(null));
    }

    @Test
    void providerKeepsProtectedSaveHookAndHiddenSemantics() throws Exception {
        bindRequest();
        RecordingProvider provider = new RecordingProvider();
        JoinPoint point = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(point.getSignature()).thenReturn(signature);
        when(point.getArgs()).thenReturn(new Object[]{"input", new MockHttpServletRequest()});
        Method method = Endpoint.class.getMethod("handle", String.class);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getName()).thenReturn("handle");
        provider.afterReturing(point, method.getAnnotation(Operation.class), "result", Stopwatch.createStarted());
        assertEquals("result", provider.result);
        assertTrue(events.stream().anyMatch(event -> event.getMessage().getFormattedMessage()
                .contains("URI /legacy/logs IP 127.0.0.9")));
        assertTrue(events.stream().anyMatch(event -> event.getMessage().getFormattedMessage()
                .contains("input")));
        assertNull(provider.failure);
        IllegalStateException failure = new IllegalStateException("http-failure");
        provider.afterThrowing(point, method.getAnnotation(Operation.class), failure, Stopwatch.createStarted());
        assertSame(failure, provider.failure);
        assertTrue(events.stream().anyMatch(event -> event.getLevel() == Level.ERROR
                && event.getMessage().getFormattedMessage().contains("http-failure")));
        int saved = provider.saved;
        when(signature.getMethod()).thenReturn(Endpoint.class.getMethod("hidden"));
        provider.afterReturing(point, method.getAnnotation(Operation.class), "hidden", Stopwatch.createStarted());
        assertEquals(saved, provider.saved);
    }

    @Test
    void webMvcStartsInJvmWithoutLogs() throws Exception {
        String classpath = Arrays.stream(System.getProperty("surefire.test.class.path",
                        System.getProperty("java.class.path")).split(File.pathSeparator))
                .filter(entry -> !entry.contains("ddd4j-data-logs"))
                .collect(Collectors.joining(File.pathSeparator));
        Process process = new ProcessBuilder(System.getProperty("java.home") + "/bin/java", "-cp", classpath,
                WebMvcWithoutLogsProbe.class.getName()).redirectErrorStream(true).start();
        boolean finished = process.waitFor(40, TimeUnit.SECONDS);
        if (!finished) { process.destroyForcibly(); }
        assertTrue(finished, "WebMVC startup probe timed out");
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        System.out.println("R05 WebMVC without logs classpath=" + classpath + "\n" + output);
        assertEquals(0, process.exitValue(), output);
        assertTrue(output.contains("R05_WEBMVC_WITHOUT_LOGS_OK"), output);
    }

    private MockHttpServletRequest bindRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/legacy/logs");
        request.setRemoteAddr("127.0.0.9");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }

    private WebContextScope nativeScope() {
        return WebContextScope.open(new WebRequestContext("native-id", "trace-id", null, null,
                Locale.ROOT, "192.0.2.10", "POST", "/native/logs"));
    }

    /** 检查历史 protected saveLog 扩展入口。 */
    static class RecordingProvider extends DefaultApiOperationLogProvider {
        private Object result;
        private Throwable failure;
        private int saved;
        @Override
        protected void saveLog(JoinPoint point, Method method, Operation annotation,
                               Object returned, Throwable thrown, Stopwatch watch) {
            this.result = returned;
            this.failure = thrown;
            this.saved++;
        }
    }

    /** 日志测试目标。 */
    public static class Endpoint {
        @Operation(summary = "http")
        public String handle(String input) { return input; }
        @Hidden
        public String hidden() { return "hidden"; }
    }
}
