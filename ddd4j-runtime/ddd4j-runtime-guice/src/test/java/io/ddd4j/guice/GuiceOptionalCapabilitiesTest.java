package io.ddd4j.guice;

import cn.hutool.core.lang.Snowflake;
import com.google.common.base.Stopwatch;
import com.google.inject.Guice;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.data.logs.ApiOperationLogProvider;
import io.ddd4j.data.logs.DefaultApiOperationLogProvider;
import io.ddd4j.data.logs.aspect.ApiOperationLogAspect;
import io.swagger.v3.oas.annotations.Operation;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证显式日志装配及没有 WebMVC/Servlet 的真实 JVM 日志调用。
 */
class GuiceOptionalCapabilitiesTest {
    /**
     * \u6309 <code>("getMethod".equals(invoked.getName()))</code> \u7b49\u5b9e\u9645\u6761\u4ef6\u9009\u62e9\u5f53\u524d\u7528\u4f8b\u8fd4\u56de\u7684\u7ed3\u679c\u3002
     * @param result \u4f20\u7ed9 <code>Proxy.newProxyInstance()</code> \u7684 <code>Object</code> \u8f93\u5165
     * @param failure \u89e6\u53d1\u5f53\u524d\u6d4b\u8bd5\u9519\u8bef\u5904\u7406\u8def\u5f84\u7684\u5931\u8d25
     * @return \u6839\u636e\u6d4b\u8bd5\u6761\u4ef6\u8fd4\u56de\u7684\u53ef\u80fd\u503c\uff0c\u5305\u62ec <code>method</code>\u3001<code>method.getName()</code>
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
     */
    static ProceedingJoinPoint point(Object result, Throwable failure) throws Exception {
        Method method = Endpoint.class.getMethod("handle", String.class);
        MethodSignature signature = (MethodSignature) Proxy.newProxyInstance(MethodSignature.class.getClassLoader(),
                new Class<?>[]{MethodSignature.class}, (proxy, invoked, args) -> {
                    if ("getMethod".equals(invoked.getName())) {
                        return method;
                    }
                    if ("getName".equals(invoked.getName())) {
                        return method.getName();
                    }
                    throw new IllegalStateException(invoked.getName());
                });
        return (ProceedingJoinPoint) Proxy.newProxyInstance(ProceedingJoinPoint.class.getClassLoader(),
                new Class<?>[]{ProceedingJoinPoint.class}, (proxy, invoked, args) -> {
                    if ("getSignature".equals(invoked.getName())) {
                        return signature;
                    }
                    if ("getArgs".equals(invoked.getName())) {
                        return new Object[]{"probe-input"};
                    }
                    if ("proceed".equals(invoked.getName())) {
                        if (Objects.nonNull(failure)) {
                            throw failure;
                        }
                        return result;
                    }
                    throw new IllegalStateException(invoked.getName());
                });
    }

    /**
     * \u9a8c\u8bc1<code>finished</code>\u4e3a\u771f\uff0c<code>process.exitValue()</code> \u7b49\u4e8e <code>0</code>\uff0c<code>output.contains("R07_BASIC_OK")</code>\u4e3a\u771f\u3002
     * @throws Exception \u51c6\u5907\u6216\u8bfb\u53d6\u5939\u5177\u6587\u4ef6\u65f6\u5931\u8d25
     */
    @Test
    void basicStartsInJvmWithoutUnselectedCapabilities() throws Exception {
        String classpath = Arrays.stream(System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")).split(File.pathSeparator))
                .filter(entry -> !entry.contains("ddd4j-data-") && !entry.contains("ddd4j-cache")
                        && !entry.contains("ddd4j-web-") && !entry.contains("/org/springframework/")
                        && !entry.contains("/org/hibernate/") && !entry.contains("/org/mybatis/")
                        && !entry.contains("/com/baomidou/") && !entry.contains("/jakarta/persistence/")
                        && !entry.contains("/io/javalin/") && !entry.contains("/org/eclipse/jetty/"))
                .collect(Collectors.joining(File.pathSeparator));
        Process process = new ProcessBuilder(System.getProperty("java.home") + "/bin/java", "-cp", classpath,
                BasicProbe.class.getName()).redirectErrorStream(true).start();
        boolean finished = process.waitFor(40, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        assertTrue(finished, "basic probe timed out");
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        System.out.println("R07 basic classpath=" + classpath + "\n" + output);
        assertEquals(0, process.exitValue(), output);
        assertTrue(output.contains("R07_BASIC_OK"), output);
    }

    /**
     * \u9a8c\u8bc1<code>finished</code>\u4e3a\u771f\uff0c<code>process.exitValue()</code> \u7b49\u4e8e <code>0</code>\uff0c<code>output.contains("R05_NO_WEBMVC_LOGGING_OK")</code>\u4e3a\u771f\u3002
     * @throws Exception \u51c6\u5907\u6216\u8bfb\u53d6\u5939\u5177\u6587\u4ef6\u65f6\u5931\u8d25
     */
    @Test
    void logsExecuteInJvmWithoutWebMvcSpringOrServlet() throws Exception {
        String classpath = Arrays.stream(System.getProperty("surefire.test.class.path",
                        System.getProperty("java.class.path")).split(File.pathSeparator))
                .filter(entry -> !entry.contains("ddd4j-web-webmvc")
                        && !entry.contains("/org/springframework/")
                        && !entry.contains("/jakarta/servlet/")
                        && !entry.contains("/javax/servlet/"))
                .collect(Collectors.joining(File.pathSeparator));
        Process process = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                "-Dslf4j.provider=org.apache.logging.slf4j.SLF4JServiceProvider", "-cp", classpath,
                LoggingProbe.class.getName()).redirectErrorStream(true).start();
        boolean finished = process.waitFor(40, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        assertTrue(finished, "isolated logging probe timed out");
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        System.out.println("R05 isolated classpath=" + classpath + "\n" + output);
        assertEquals(0, process.exitValue(), output);
        assertTrue(output.contains("R05_NO_WEBMVC_LOGGING_OK"), output);
    }

    /**
     * \u9a8c\u8bc1<code>result</code> \u7b49\u4e8e <code>"result"</code>\uff0c<code>watch.isRunning()</code>\u4e3a\u5047\u3002
     * @throws Throwable \u5f53\u524d\u88ab\u6d4b\u8c03\u7528\u5931\u8d25
     */
    @Test
    void aspectRetainsResultsCallbacksWrappingAndMdcCleanup() throws Throwable {
        Operation operation = Endpoint.class.getMethod("handle", String.class).getAnnotation(Operation.class);
        ApiOperationLogProvider callbacks = new ApiOperationLogProvider() {
            @Override
            public void doBefore(org.aspectj.lang.JoinPoint point, Operation annotation) {
                throw new IllegalStateException("before-callback");
            }

            @Override
            public void afterReturing(org.aspectj.lang.JoinPoint point, Operation annotation, Object result, Stopwatch watch) {
                assertEquals("result", result);
                assertFalse(watch.isRunning());
                throw new IllegalStateException("returning-callback");
            }

            @Override
            public Object wrapThrowing(org.aspectj.lang.JoinPoint point, Operation annotation, Throwable error, Stopwatch watch) {
                assertEquals("business-failure", error.getMessage());
                assertFalse(watch.isRunning());
                return "wrapped";
            }

            @Override
            public void afterThrowing(org.aspectj.lang.JoinPoint point, Operation annotation, Throwable error, Stopwatch watch) {
                throw new IllegalStateException("throwing-callback");
            }
        };
        ApiOperationLogAspect aspect = new ApiOperationLogAspect(new Snowflake(1, 1), callbacks);
        MDC.put("r05", "cleanup");
        assertEquals("result", aspect.aroundMethod(point("result", null), operation));
        assertNull(MDC.get("r05"));
        MDC.put("r05", "cleanup");
        assertEquals("wrapped", aspect.aroundMethod(point(null, new IllegalStateException("business-failure")), operation));
        assertNull(MDC.get("r05"));
        ApiOperationLogAspect rethrow = new ApiOperationLogAspect(new Snowflake(1, 1), new ApiOperationLogProvider() {
        });
        IllegalArgumentException failure = new IllegalArgumentException("original");
        assertSame(failure, assertThrows(IllegalArgumentException.class,
                () -> rethrow.aroundMethod(point(null, failure), operation)));
        assertTrue(Objects.isNull(MDC.getCopyOfContextMap()) || MDC.getCopyOfContextMap().isEmpty());
    }

    /**
     * 验证实际剥离能力依赖后的核心运行时生命周期。
     */
    public static class BasicProbe {
        /**
         * \u8c03\u7528 <code>Class.forName()</code>\u3001<code>Guice.createInjector()</code>\u3001<code>Ddd4jGuiceModule.basic()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param args \u4ece\u547d\u4ee4\u884c\u4f20\u5165\u6d4b\u8bd5\u63a2\u9488\u7684\u53c2\u6570
         * @throws Exception \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u65f6\u5931\u8d25
         */
        public static void main(String[] args) throws Exception {
            for (String type : new String[]{"org.springframework.web.servlet.DispatcherServlet",
                    "org.apache.ibatis.session.SqlSession", "com.baomidou.mybatisplus.core.mapper.BaseMapper",
                    "org.hibernate.Session",
                    "io.javalin.Javalin", "io.ddd4j.data.event.store.jpa.JpaEventStore"}) {
                try {
                    Class.forName(type);
                    throw new AssertionError("Unexpected capability: " + type);
                } catch (ClassNotFoundException expected) {
                }
            }
            com.google.inject.Injector injector = Guice.createInjector(Ddd4jGuiceModule.basic());
            try (Ddd4jGuiceRuntime runtime = injector.getInstance(Ddd4jGuiceRuntime.class)) {
                if (Objects.nonNull(injector.getExistingBinding(com.google.inject.Key.get(
                        io.ddd4j.core.cqrs.readmodel.ViewManager.class)))) {
                    throw new AssertionError("Unselected projection binding");
                }
                System.out.println("R07_BASIC_OK");
            }
        }
    }

    /**
     * 独立进程入口，先证实缺少 HTTP 类型，再执行 Guice 默认日志和扩展回调。
     */
    public static class LoggingProbe {
        /**
         * \u8c03\u7528 <code>Class.forName()</code>\u3001<code>LogManager.getLogger()</code>\u3001<code>logger.getLevel()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
         * @param args \u4ece\u547d\u4ee4\u884c\u4f20\u5165\u6d4b\u8bd5\u63a2\u9488\u7684\u53c2\u6570
         * @throws Throwable \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u65f6\u5931\u8d25
         */
        public static void main(String[] args) throws Throwable {
            for (String type : new String[]{"org.springframework.web.servlet.DispatcherServlet",
                    "org.springframework.web.context.request.RequestContextHolder", "jakarta.servlet.ServletRequest",
                    "io.ddd4j.web.webmvc.util.WebUtils"}) {
                assertThrows(ClassNotFoundException.class, () -> Class.forName(type));
            }
            Logger logger = (Logger) LogManager.getLogger(DefaultApiOperationLogProvider.class);
            Level previous = logger.getLevel();
            List<LogEvent> events = new ArrayList<>();
            AbstractAppender appender = new AbstractAppender("r05-native", null,
                    PatternLayout.createDefaultLayout(), false, Property.EMPTY_ARRAY) {
                @Override
                public void append(LogEvent event) {
                    events.add(event.toImmutable());
                }
            };
            appender.start();
            logger.addAppender(appender);
            logger.setLevel(Level.INFO);
            try (ThreadContext.Scope scope = ThreadContext.open()) {
                ThreadContext.put("request-id", "native-request");
                ThreadContext.put("http-path", "/native/logs");
                ThreadContext.put("client-ip", "192.0.2.5");
                ApiOperationLogProvider provider = Guice.createInjector(new Ddd4jLogsGuiceModule())
                        .getInstance(ApiOperationLogProvider.class);
                ApiOperationLogAspect aspect = new ApiOperationLogAspect(new Snowflake(1, 1), provider);
                assertEquals("native-request", aspect.getRequestId());
                Operation operation = Endpoint.class.getMethod("handle", String.class).getAnnotation(Operation.class);
                assertEquals("native-result", aspect.aroundMethod(point("native-result", null), operation));
                IllegalStateException error = new IllegalStateException("isolated-failure");
                assertSame(error, assertThrows(IllegalStateException.class,
                        () -> aspect.aroundMethod(point(null, error), operation)));
                final Object[] captured = new Object[2];
                DefaultApiOperationLogProvider extension = new DefaultApiOperationLogProvider() {
                    @Override
                    protected void saveLog(org.aspectj.lang.JoinPoint point, Method method, Operation annotation,
                                           Object result, Throwable failure, Stopwatch watch) {
                        captured[0] = result;
                        captured[1] = failure;
                    }
                };
                extension.afterReturing(point(null, null), operation, "saved", Stopwatch.createStarted());
                assertEquals("saved", captured[0]);
                extension.afterThrowing(point(null, null), operation, error, Stopwatch.createStarted());
                assertSame(error, captured[1]);
                assertTrue(events.stream().anyMatch(event -> event.getMessage().getFormattedMessage()
                        .contains("URI /native/logs IP 192.0.2.5")));
                assertTrue(events.stream().anyMatch(event -> event.getMessage().getFormattedMessage()
                        .contains("probe-input")));
                assertTrue(events.stream().anyMatch(event -> event.getLevel() == Level.ERROR
                        && event.getMessage().getFormattedMessage().contains("isolated-failure")));
                new ThreeLineLogsParityTest().guiceDefaultProviderEmitsSuccessAndFailureAccessLogs();
                System.out.println("R05_NO_WEBMVC_LOGGING_OK default-provider,success,failure,protected-hook,native-request-id,uri,ip,args");
            } finally {
                logger.removeAppender(appender);
                logger.setLevel(previous);
                appender.stop();
            }
        }
    }

    /**
     * 日志目标方法。
     */
    public static class Endpoint {
        /**
         * \u8bfb\u53d6 <code>input</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
         * @param input \u7528\u4e8e\u6784\u9020\u672c\u6b21\u8fd4\u56de\u7ed3\u679c\u7684 <code>String</code> \u8f93\u5165
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>input</code>
         */
        @Operation(summary = "R05 logging")
        public String handle(String input) {
            return input;
        }
    }
}
