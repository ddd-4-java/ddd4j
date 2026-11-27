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
     * 按 <code>("getMethod".equals(invoked.getName()))</code> 等实际条件选择当前用例返回的结果。
     *
     * @param result  传给 <code>Proxy.newProxyInstance()</code> 的 <code>Object</code> 输入
     * @param failure 触发当前测试错误处理路径的失败
     * @return 根据测试条件返回的可能值，包括 <code>method</code>、<code>method.getName()</code>
     * @throws Exception 当前用例准备资源或调用测试入口失败
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
     * 验证<code>finished</code>为真，<code>process.exitValue()</code> 等于 <code>0</code>，<code>output.contains("R07_BASIC_OK")</code>为真。
     *
     * @throws Exception 准备或读取夹具文件时失败
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
        String output = new String(com.google.common.io.ByteStreams.toByteArray(process.getInputStream()), StandardCharsets.UTF_8);
        System.out.println("R07 basic classpath=" + classpath + "\n" + output);
        assertEquals(0, process.exitValue(), output);
        assertTrue(output.contains("R07_BASIC_OK"), output);
    }

    /**
     * 验证<code>finished</code>为真，<code>process.exitValue()</code> 等于 <code>0</code>，<code>output.contains("R05_NO_WEBMVC_LOGGING_OK")</code>为真。
     *
     * @throws Exception 准备或读取夹具文件时失败
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
        String output = new String(com.google.common.io.ByteStreams.toByteArray(process.getInputStream()), StandardCharsets.UTF_8);
        System.out.println("R05 isolated classpath=" + classpath + "\n" + output);
        assertEquals(0, process.exitValue(), output);
        assertTrue(output.contains("R05_NO_WEBMVC_LOGGING_OK"), output);
    }

    /**
     * 验证<code>result</code> 等于 <code>"result"</code>，<code>watch.isRunning()</code>为假。
     *
     * @throws Throwable 当前被测调用失败
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
         * 调用 <code>Class.forName()</code>、<code>Guice.createInjector()</code>、<code>Ddd4jGuiceModule.basic()</code> 执行当前夹具操作。
         *
         * @param args 从命令行传入测试探针的参数
         * @throws Exception 反射访问被测成员时失败
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
         * 调用 <code>Class.forName()</code>、<code>LogManager.getLogger()</code>、<code>logger.getLevel()</code> 执行当前夹具操作。
         *
         * @param args 从命令行传入测试探针的参数
         * @throws Throwable 反射访问被测成员时失败
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
         * 读取 <code>input</code> 供当前测试使用。
         *
         * @param input 用于构造本次返回结果的 <code>String</code> 输入
         * @return 当前测试使用的结果，取自 <code>input</code>
         */
        @Operation(summary = "R05 logging")
        public String handle(String input) {
            return input;
        }
    }
}
