package io.ddd4j.sample.javalin.satoken;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.servlet.model.SaRequestForServlet;
import cn.dev33.satoken.servlet.model.SaResponseForServlet;
import cn.dev33.satoken.servlet.model.SaStorageForServlet;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 <code>NativeAuthLifecycle</code> 在<code>restoresCallerBindings</code>等操作中的行为契约。
 */
class NativeAuthLifecycleTest {
    /**
     * 调用 <code>type.cast()</code>、<code>Proxy.newProxyInstance()</code>、<code>type.getClassLoader()</code> 执行当前夹具操作。
     *
     * @param <T>  当前契约测试所使用的 Java 类型
     * @param type 用来解析、检查或构造测试对象的 Java 类型
     * @return 提供给后续契约断言的 <code>T</code> 结果
     */
    private static <T> T servletProxy(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> fail("unexpected servlet call: " + method)));
    }

    /**
     * 验证被测调用 抛出 <code>IllegalStateException.class</code>，<code>actual</code> 与…为同一实例 <code>failure</code>，<code>observations</code> 等于 <code>List.of(Optional.of("framework-context-present"))</code>。
     *
     * @throws Exception 反射访问被测成员时失败
     */
    @Test
    void restoresCallerBindingsWhenSubjectProviderThrows() throws Exception {
        List<Optional<String>> observations = new ArrayList<>();
        var failure = new IllegalStateException("subject provider failure");
        try (var callerScope = ThreadContext.open();
             var lifecycle = new NativeAuthLifecycle(observations::add)) {
            lifecycle.initializeSaToken();
            observations.clear();
            ThreadContext.put("caller", new Object());
            var callerResources = ThreadContext.getResources();

            var context = SaManager.getSaTokenContext();
            try {
                var callerServletRequest = servletProxy(HttpServletRequest.class);
                var callerServletResponse = servletProxy(HttpServletResponse.class);
                context.setContext(new SaRequestForServlet(callerServletRequest),
                        new SaResponseForServlet(callerServletResponse), new SaStorageForServlet(callerServletRequest));
                var callerRequest = context.getModelBox().getRequest();
                var callerResponse = context.getModelBox().getResponse();
                var callerStorage = context.getModelBox().getStorage();

                // 仅替换主体解析，避免为这个失败路径注册一套全局 Guice 运行时。
                var field = NativeAuthLifecycle.class.getDeclaredField("provider");
                field.setAccessible(true);
                field.set(lifecycle, new SubjectProvider() {
                    @Override
                    public Subject getSubject() {
                        ThreadContext.put("request", new Object());
                        throw failure;
                    }
                });

                var actual = assertThrows(IllegalStateException.class, () -> lifecycle.doFilter(
                        servletProxy(HttpServletRequest.class), servletProxy(HttpServletResponse.class),
                        (request, response) -> fail("request must stop when its subject cannot be resolved")));

                assertAll(
                        () -> assertSame(failure, actual),
                        () -> assertEquals(callerResources, ThreadContext.getResources()),
                        () -> assertSame(callerRequest, context.getModelBox().getRequest()),
                        () -> assertSame(callerResponse, context.getModelBox().getResponse()),
                        () -> assertSame(callerStorage, context.getModelBox().getStorage()),
                        () -> assertEquals(List.of(Optional.of("framework-context-present")), observations));
            } finally {
                context.clearContext();
            }
        }
    }
}
