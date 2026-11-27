package io.ddd4j.sample.javalin.satoken;

import com.google.common.collect.ImmutableList;
import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.servlet.model.SaRequestForServlet;
import cn.dev33.satoken.servlet.model.SaResponseForServlet;
import cn.dev33.satoken.servlet.model.SaStorageForServlet;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.subject.Subject;
import io.ddd4j.core.subject.SubjectProvider;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** \u9a8c\u8bc1 <code>NativeAuthLifecycle</code> \u5728<code>restoresCallerBindings</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
class NativeAuthLifecycleTest {
    /**
     * \u8c03\u7528 <code>type.cast()</code>\u3001<code>Proxy.newProxyInstance()</code>\u3001<code>type.getClassLoader()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002
     * @param <T> \u5f53\u524d\u5951\u7ea6\u6d4b\u8bd5\u6240\u4f7f\u7528\u7684 Java \u7c7b\u578b
     * @param type \u7528\u6765\u89e3\u6790\u3001\u68c0\u67e5\u6216\u6784\u9020\u6d4b\u8bd5\u5bf9\u8c61\u7684 Java \u7c7b\u578b
     * @return \u63d0\u4f9b\u7ed9\u540e\u7eed\u5951\u7ea6\u65ad\u8a00\u7684 <code>T</code> \u7ed3\u679c
     */
    private static <T> T servletProxy(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> fail("unexpected servlet call: " + method)));
    }

    /**
     * \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>IllegalStateException.class</code>\uff0c<code>actual</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>failure</code>\uff0c<code>observations</code> \u7b49\u4e8e <code>List.of(Optional.of("framework-context-present"))</code>\u3002
     * @throws Exception \u53cd\u5c04\u8bbf\u95ee\u88ab\u6d4b\u6210\u5458\u65f6\u5931\u8d25
     */
    @Test
    void restoresCallerBindingsWhenSubjectProviderThrows() throws Exception {
        List<Optional<String>> observations = new ArrayList<>();
        IllegalStateException failure = new IllegalStateException("subject provider failure");
        try (ThreadContext.Scope callerScope = ThreadContext.open();
             io.ddd4j.sample.javalin.satoken.NativeAuthLifecycle lifecycle = new NativeAuthLifecycle(observations::add)) {
            lifecycle.initializeSaToken();
            observations.clear();
            ThreadContext.put("caller", new Object());
            java.util.Map<java.lang.Object, java.lang.Object> callerResources = ThreadContext.getResources();

            cn.dev33.satoken.context.SaTokenContext context = SaManager.getSaTokenContext();
            try {
                HttpServletRequest callerServletRequest = servletProxy(HttpServletRequest.class);
                HttpServletResponse callerServletResponse = servletProxy(HttpServletResponse.class);
                context.setContext(new SaRequestForServlet(callerServletRequest),
                        new SaResponseForServlet(callerServletResponse), new SaStorageForServlet(callerServletRequest));
                cn.dev33.satoken.context.model.SaRequest callerRequest = context.getModelBox().getRequest();
                cn.dev33.satoken.context.model.SaResponse callerResponse = context.getModelBox().getResponse();
                cn.dev33.satoken.context.model.SaStorage callerStorage = context.getModelBox().getStorage();

                // 仅替换主体解析，避免为这个失败路径注册一套全局 Guice 运行时。
                java.lang.reflect.Field field = NativeAuthLifecycle.class.getDeclaredField("provider");
                field.setAccessible(true);
                field.set(lifecycle, new SubjectProvider() {
                    @Override
                    public Subject getSubject() {
                        ThreadContext.put("request", new Object());
                        throw failure;
                    }
                });

                IllegalStateException actual = assertThrows(IllegalStateException.class, () -> lifecycle.doFilter(
                        servletProxy(HttpServletRequest.class), servletProxy(HttpServletResponse.class),
                        (request, response) -> fail("request must stop when its subject cannot be resolved")));

                assertAll(
                        () -> assertSame(failure, actual),
                        () -> assertEquals(callerResources, ThreadContext.getResources()),
                        () -> assertSame(callerRequest, context.getModelBox().getRequest()),
                        () -> assertSame(callerResponse, context.getModelBox().getResponse()),
                        () -> assertSame(callerStorage, context.getModelBox().getStorage()),
                        () -> assertEquals(ImmutableList.of(Optional.of("framework-context-present")), observations));
            } finally {
                context.clearContext();
            }
        }
    }
}
