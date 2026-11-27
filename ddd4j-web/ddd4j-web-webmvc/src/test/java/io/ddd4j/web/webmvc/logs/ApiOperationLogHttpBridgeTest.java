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
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 日志 HTTP 桥接保留旧请求行为，并兼容原生 WebContextScope。
 */
class ApiOperationLogHttpBridgeTest {
    /** \u5199\u5165\u5f53\u524d\u6d4b\u8bd5\u6216\u5939\u5177\u8bca\u65ad\u4fe1\u606f\u7684\u65e5\u5fd7\u8bb0\u5f55\u5668\u3002 */
    private final Logger logger = (Logger) LogManager.getLogger(DefaultApiOperationLogProvider.class);
    /** \u5f53\u524d\u6d4b\u8bd5\u671f\u95f4\u6536\u96c6\u7684\u9886\u57df\u4e8b\u4ef6\uff0c\u4f9b\u65ad\u8a00\u6bd4\u8f83\u53d1\u5e03\u5185\u5bb9\u6216\u987a\u5e8f\u3002 */
    private final List<LogEvent> events = new ArrayList<>();
    /** \u4fdd\u5b58\u88ab\u6d4b\u64cd\u4f5c\u4e4b\u524d\u7684\u7ec4\u4ef6\u6216\u7ebf\u7a0b\u72b6\u6001\u3002 */
    private Level previous;
    /** \u6355\u83b7\u6d4b\u8bd5\u671f\u95f4\u8f93\u51fa\u7684\u65e5\u5fd7\u8bb0\u5f55\u5668\u9644\u52a0\u7ec4\u4ef6\u3002 */
    private AbstractAppender appender;

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>logger.getLevel()</code>\u3001<code>PatternLayout.createDefaultLayout()</code>\u3001<code>appender.start()</code>\u3001<code>logger.addAppender()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
    @BeforeEach
    void captureLogs() {
        previous = logger.getLevel();
        appender = new AbstractAppender("r05-http", null, PatternLayout.createDefaultLayout(), false, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                events.add(event.toImmutable());
            }
        };
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
    }

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>RequestContextHolder.resetRequestAttributes()</code>\u3001<code>ThreadContext.clear()</code>\u3001<code>logger.removeAppender()</code>\u3001<code>logger.setLevel()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
        ThreadContext.clear();
        logger.removeAppender(appender);
        logger.setLevel(previous);
        appender.stop();
    }

    /** \u9a8c\u8bc1<code>aspect.getRequestId()</code> \u7b49\u4e8e <code>"parameter-id"</code>\uff0c<code>aspect.getRequestId()</code> \u7b49\u4e8e <code>"header-id"</code>\uff0c<code>aspect.getRequestId()</code> \u4e0d\u7b49\u4e8e <code>generated</code>\u3002 */
    @Test
    void legacyRequestContextHolderKeepsParameterThenHeaderThenSnowflake() {
        MockHttpServletRequest request = bindRequest();
        ApiOperationLogAspect aspect = new ApiOperationLogAspect(new Snowflake(1, 1), new ApiOperationLogProvider() {
        });
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

    /** \u9a8c\u8bc1<code>context.requestId()</code> \u7b49\u4e8e <code>"legacy-param"</code>\uff0c<code>context.requestUri()</code> \u7b49\u4e8e <code>"/legacy/logs"</code>\uff0c<code>context.requestId()</code>\u4e3a\u7a7a\u3002 */
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

    /** \u9a8c\u8bc1<code>context.requestId()</code> \u7b49\u4e8e <code>"native-id"</code>\uff0c<code>context.requestUri()</code> \u7b49\u4e8e <code>"/native/logs"</code>\uff0c<code>ApiOperationLogRequestContext.current().requestId()</code>\u4e3a\u7a7a\u3002 */
    @Test
    void bridgeFallsBackToExistingWebContextScopeWithoutServletRequest() {
        try (WebContextScope ignored = nativeScope()) {
            ApiOperationLogRequestContext context = ApiOperationLogRequestContext.current();
            assertInstanceOf(WebMvcApiOperationLogRequestContext.class, context);
            assertEquals("native-id", context.requestId());
            assertEquals("/native/logs", context.requestUri());
            assertEquals("192.0.2.10", context.clientIp());
            assertEquals("native-id", new ApiOperationLogAspect(new Snowflake(1, 1),
                    new ApiOperationLogProvider() {
                    }).getRequestId());
        }
        assertNull(ApiOperationLogRequestContext.current().requestId());
    }

    /** \u9a8c\u8bc1<code>context.clientIp()</code> \u7b49\u4e8e <code>"198.51.100.11"</code>\uff0c<code>context.clientIp()</code> \u7b49\u4e8e <code>"203.0.113.8"</code>\uff0c<code>context.clientIp()</code> \u7b49\u4e8e <code>"127.0.0.9"</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>context.includeArgument(both)</code>\u4e3a\u5047\uff0c<code>context.includeArgument(new MockHttpServletRequest())</code>\u4e3a\u771f\uff0c<code>context.includeArgument(null)</code>\u4e3a\u771f\u3002 */
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

    /**
     * \u9a8c\u8bc1<code>provider.result</code> \u7b49\u4e8e <code>"result"</code>\uff0c<code>provider.failure</code>\u4e3a\u7a7a\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
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

    /**
     * \u9a8c\u8bc1<code>finished</code>\u4e3a\u771f\uff0c<code>process.exitValue()</code> \u7b49\u4e8e <code>0</code>\uff0c<code>output.contains("R05_WEBMVC_WITHOUT_LOGS_OK")</code>\u4e3a\u771f\u3002
     * @throws Exception \u51c6\u5907\u6216\u8bfb\u53d6\u5939\u5177\u6587\u4ef6\u65f6\u5931\u8d25
     */
    @Test
    void webMvcStartsInJvmWithoutLogs() throws Exception {
        String classpath = Arrays.stream(System.getProperty("surefire.test.class.path",
                        System.getProperty("java.class.path")).split(File.pathSeparator))
                .filter(entry -> !entry.contains("ddd4j-data-logs"))
                .collect(Collectors.joining(File.pathSeparator));
        Process process = new ProcessBuilder(System.getProperty("java.home") + "/bin/java", "-cp", classpath,
                WebMvcWithoutLogsProbe.class.getName()).redirectErrorStream(true).start();
        boolean finished = process.waitFor(40, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        assertTrue(finished, "WebMVC startup probe timed out");
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        System.out.println("R05 WebMVC without logs classpath=" + classpath + "\n" + output);
        assertEquals(0, process.exitValue(), output);
        assertTrue(output.contains("R05_WEBMVC_WITHOUT_LOGS_OK"), output);
    }

    /**
     * \u8bfb\u53d6 <code>request</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>request</code>
     */
    private MockHttpServletRequest bindRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/legacy/logs");
        request.setRemoteAddr("127.0.0.9");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }

    /**
     * \u8c03\u7528 <code>WebContextScope.open()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>WebContextScope</code> \u7ed3\u679c
     */
    private WebContextScope nativeScope() {
        return WebContextScope.open(new WebRequestContext("native-id", "trace-id", null, null,
                Locale.ROOT, "192.0.2.10", "POST", "/native/logs"));
    }

    /**
     * 检查历史 protected saveLog 扩展入口。
     */
    static class RecordingProvider extends DefaultApiOperationLogProvider {
        /** \u6267\u884c\u88ab\u6d4b\u64cd\u4f5c\u540e\u4fdd\u5b58\u7684\u7ed3\u679c\u3002 */
        private Object result;
        /** \u5f53\u524d\u5931\u8d25\u8def\u5f84\u6240\u7528\u6216\u89c2\u5bdf\u5230\u7684\u539f\u59cb\u5f02\u5e38\u3002 */
        private Throwable failure;
        /** \u7528\u6765\u65ad\u8a00\u4ed3\u50a8\u5b9e\u9645\u4fdd\u5b58\u5185\u5bb9\u6216\u4fdd\u5b58\u72b6\u6001\u7684\u8bb0\u5f55\u3002 */
        private int saved;

        /** {@inheritDoc} */
        @Override
        protected void saveLog(JoinPoint point, Method method, Operation annotation,
                               Object returned, Throwable thrown, Stopwatch watch) {
            this.result = returned;
            this.failure = thrown;
            this.saved++;
        }
    }

    /**
     * 日志测试目标。
     */
    public static class Endpoint {
        /**
         * \u8bfb\u53d6 <code>input</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
         * @param input \u7528\u4e8e\u6784\u9020\u672c\u6b21\u8fd4\u56de\u7ed3\u679c\u7684 <code>String</code> \u8f93\u5165
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>input</code>
         */
        @Operation(summary = "http")
        public String handle(String input) {
            return input;
        }

        /**
         * \u4e3a\u5f53\u524d\u7528\u4f8b\u63d0\u4f9b\u56fa\u5b9a\u7684 <code>hidden</code> \u8fd4\u56de\u503c\u3002
         * @return <code>"hidden"</code>
         */
        @Hidden
        public String hidden() {
            return "hidden";
        }
    }
}
