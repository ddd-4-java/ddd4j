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

class NativeAuthLifecycleTest {
    private static <T> T servletProxy(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> fail("unexpected servlet call: " + method)));
    }

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
