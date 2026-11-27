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
    /**
     * 写入当前测试或夹具诊断信息的日志记录器。
     */
    private final Logger logger = (Logger) LogManager.getLogger(DefaultApiOperationLogProvider.class);
    /**
     * 当前测试期间收集的领域事件，供断言比较发布内容或顺序。
     */
    private final List<LogEvent> events = new ArrayList<>();
    /**
     * 保存被测操作之前的组件或线程状态。
     */
    private Level previous;
    /**
     * 捕获测试期间输出的日志记录器附加组件。
     */
    private AbstractAppender appender;

    /**
     * 测试执行前执行 <code>logger.getLevel()</code>、<code>PatternLayout.createDefaultLayout()</code>、<code>appender.start()</code>、<code>logger.addAppender()</code> 完成资源准备。
     */
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

    /**
     * 测试结束后执行 <code>RequestContextHolder.resetRequestAttributes()</code>、<code>ThreadContext.clear()</code>、<code>logger.removeAppender()</code>、<code>logger.setLevel()</code> 完成资源回收或状态恢复。
     */
    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
        ThreadContext.clear();
        logger.removeAppender(appender);
        logger.setLevel(previous);
        appender.stop();
    }

    /**
     * 验证<code>aspect.getRequestId()</code> 等于 <code>"parameter-id"</code>，<code>aspect.getRequestId()</code> 等于 <code>"header-id"</code>，<code>aspect.getRequestId()</code> 不等于 <code>generated</code>。
     */
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

    /**
     * 验证<code>context.requestId()</code> 等于 <code>"legacy-param"</code>，<code>context.requestUri()</code> 等于 <code>"/legacy/logs"</code>，<code>context.requestId()</code>为空。
     */
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

    /**
     * 验证<code>context.requestId()</code> 等于 <code>"native-id"</code>，<code>context.requestUri()</code> 等于 <code>"/native/logs"</code>，<code>ApiOperationLogRequestContext.current().requestId()</code>为空。
     */
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

    /**
     * 验证<code>context.clientIp()</code> 等于 <code>"198.51.100.11"</code>，<code>context.clientIp()</code> 等于 <code>"203.0.113.8"</code>，<code>context.clientIp()</code> 等于 <code>"127.0.0.9"</code>。
     */
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

    /**
     * 验证<code>context.includeArgument(both)</code>为假，<code>context.includeArgument(new MockHttpServletRequest())</code>为真，<code>context.includeArgument(null)</code>为真。
     */
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
     * 验证<code>provider.result</code> 等于 <code>"result"</code>，<code>provider.failure</code>为空。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
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
     * 验证<code>finished</code>为真，<code>process.exitValue()</code> 等于 <code>0</code>，<code>output.contains("R05_WEBMVC_WITHOUT_LOGS_OK")</code>为真。
     *
     * @throws Exception 准备或读取夹具文件时失败
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
     * 读取 <code>request</code> 供当前测试使用。
     *
     * @return 当前测试使用的结果，取自 <code>request</code>
     */
    private MockHttpServletRequest bindRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/legacy/logs");
        request.setRemoteAddr("127.0.0.9");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }

    /**
     * 调用 <code>WebContextScope.open()</code> 执行当前夹具操作。
     *
     * @return 提供给后续契约断言的 <code>WebContextScope</code> 结果
     */
    private WebContextScope nativeScope() {
        return WebContextScope.open(new WebRequestContext("native-id", "trace-id", null, null,
                Locale.ROOT, "192.0.2.10", "POST", "/native/logs"));
    }

    /**
     * 检查历史 protected saveLog 扩展入口。
     */
    static class RecordingProvider extends DefaultApiOperationLogProvider {
        /**
         * 执行被测操作后保存的结果。
         */
        private Object result;
        /**
         * 当前失败路径所用或观察到的原始异常。
         */
        private Throwable failure;
        /**
         * 用来断言仓储实际保存内容或保存状态的记录。
         */
        private int saved;

        /**
         * 保存日志（子类可重写此方法实现持久化）。
         *
         * <p>此实现更新<code>this.result = returned</code>。</p>
         *
         * @param point      用于此操作的 <code>point</code>（<code>JoinPoint</code>）
         * @param method     方法
         * @param annotation 用于此操作的 <code>annotation</code>（<code>Operation</code>）
         * @param returned   用于此操作的 <code>returned</code>（<code>Object</code>）
         * @param thrown     用于此操作的 <code>thrown</code>（<code>Throwable</code>）
         * @param watch      用于此操作的 <code>watch</code>（<code>Stopwatch</code>）
         */
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
         * 读取 <code>input</code> 供当前测试使用。
         *
         * @param input 用于构造本次返回结果的 <code>String</code> 输入
         * @return 当前测试使用的结果，取自 <code>input</code>
         */
        @Operation(summary = "http")
        public String handle(String input) {
            return input;
        }

        /**
         * 为当前用例提供固定的 <code>hidden</code> 返回值。
         *
         * @return <code>"hidden"</code>
         */
        @Hidden
        public String hidden() {
            return "hidden";
        }
    }
}
