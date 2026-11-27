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

class NativeAuthLifecycleTest {
    private static <T> T servletProxy(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> fail("unexpected servlet call: " + method)));
    }

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
