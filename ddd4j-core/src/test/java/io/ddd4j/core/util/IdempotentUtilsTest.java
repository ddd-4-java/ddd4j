package io.ddd4j.core.util;

import com.alibaba.fastjson2.JSONObject;
import io.ddd4j.annotation.ApiIdempotent;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.lang.reflect.SourceLocation;
import org.junit.jupiter.api.Test;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.PostMapping;
import springfox.documentation.annotations.ApiIgnore;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link IdempotentUtils} tests.
 *
 * <p>说明：ddd4j-core 测试 classpath 未提供 Mockito（且 core pom 不允许改动），
 * 因此这里使用手写 Stub 替代 {@code Mockito.mock(ProceedingJoinPoint.class)}，
 * {@link ApiIdempotent} 注解直接通过真实注解方法反射获取。</p>
 */
class IdempotentUtilsTest {

    private static final String[] PARAM_NAMES = new String[]{"name", "req", "ignored"};

    @Test
    void shouldReturnIdempotentValueWhenAnnotationValueProvided() throws Exception {
        Method method = sampleMethod("createOrder");
        ApiIdempotent idempotent = method.getAnnotation(ApiIdempotent.class);
        ProceedingJoinPoint joinPoint = joinPoint(method, new Object[]{"orderA", null, "anything"});

        String key = IdempotentUtils.getIdempotentKey(joinPoint, idempotent);

        assertEquals("fixed-idem-key", key);
    }

    @Test
    void shouldEvaluateSpELWhenSpelFlagEnabled() throws Exception {
        Method method = sampleMethod("createOrderSpel");
        ApiIdempotent idempotent = method.getAnnotation(ApiIdempotent.class);
        ProceedingJoinPoint joinPoint = joinPoint(method, new Object[]{"orderA", null, null});

        String key = IdempotentUtils.getIdempotentKey(joinPoint, idempotent);

        assertEquals("orderA:6", key);
    }

    @Test
    void shouldBuildKeyFromPostMappingPathWhenValueBlank() throws Exception {
        Method method = sampleMethod("createOrderBlank");
        ApiIdempotent idempotent = method.getAnnotation(ApiIdempotent.class);
        ProceedingJoinPoint joinPoint = joinPoint(method, new Object[]{"orderA", null, null});

        String key = IdempotentUtils.getIdempotentKey(joinPoint, idempotent);

        assertEquals(DigestUtils.md5DigestAsHex("/orders/{id}".getBytes()), key);
    }

    @Test
    void shouldAppendJsonArgsWhenWithArgsIsTrue() throws Exception {
        Method method = sampleMethod("createOrderWithArgs");
        ApiIdempotent idempotent = method.getAnnotation(ApiIdempotent.class);
        ProceedingJoinPoint joinPoint = joinPoint(method, new Object[]{"orderA", null, null});

        String key = IdempotentUtils.getIdempotentKey(joinPoint, idempotent);

        String expectedRaw = "/orders/{id}" + JSONObject.toJSONString("orderA");
        assertEquals(DigestUtils.md5DigestAsHex(expectedRaw.getBytes()), key);
        // 与不携带参数的 key 不同，证明参数 JSON 确实参与了 key 的构造
        String withoutArgs = DigestUtils.md5DigestAsHex("/orders/{id}".getBytes());
        assertNotEquals(withoutArgs, key);
    }

    @Test
    void shouldIgnoreServletRequestAndApiIgnoreParameters() throws Exception {
        Method method = sampleMethod("createOrderWithArgs");
        ApiIdempotent idempotent = method.getAnnotation(ApiIdempotent.class);

        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                IdempotentUtilsTest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, m, args) -> null);
        // req 为 ServletRequest、ignored 标记了 @ApiIgnore，两者都不应参与 key 计算
        String keyWithExtras = IdempotentUtils.getIdempotentKey(
                joinPoint(method, new Object[]{"orderA", request, "must-be-ignored"}), idempotent);
        String keyWithoutExtras = IdempotentUtils.getIdempotentKey(
                joinPoint(method, new Object[]{"orderA", null, null}), idempotent);

        assertEquals(keyWithoutExtras, keyWithExtras);
    }

    @Test
    void shouldProduceStableMd5ForSameInput() throws Exception {
        Method method = sampleMethod("createOrderWithArgs");
        ApiIdempotent idempotent = method.getAnnotation(ApiIdempotent.class);

        String first = IdempotentUtils.getIdempotentKey(
                joinPoint(method, new Object[]{"orderA", null, null}), idempotent);
        String second = IdempotentUtils.getIdempotentKey(
                joinPoint(method, new Object[]{"orderA", null, null}), idempotent);

        assertEquals(first, second);
        assertTrue(first.matches("[0-9a-f]{32}"), "key should be a 32-char hex MD5");
    }

    // ========================= Fixtures =========================

    private static Method sampleMethod(String name) throws NoSuchMethodException {
        return SampleController.class.getMethod(name, String.class, HttpServletRequest.class, String.class);
    }

    private static ProceedingJoinPoint joinPoint(Method method, Object[] args) {
        return new StubProceedingJoinPoint(new StubMethodSignature(method, PARAM_NAMES), args);
    }

    static class SampleController {

        @ApiIdempotent("fixed-idem-key")
        @PostMapping("/orders/{id}")
        public void createOrder(String name, HttpServletRequest req, @ApiIgnore String ignored) {
        }

        @ApiIdempotent(value = "#name + ':' + #name.length()", spel = true)
        @PostMapping("/orders/{id}")
        public void createOrderSpel(String name, HttpServletRequest req, @ApiIgnore String ignored) {
        }

        @ApiIdempotent(withArgs = true)
        @PostMapping("/orders/{id}")
        public void createOrderWithArgs(String name, HttpServletRequest req, @ApiIgnore String ignored) {
        }

        @ApiIdempotent
        @PostMapping("/orders/{id}")
        public void createOrderBlank(String name, HttpServletRequest req, @ApiIgnore String ignored) {
        }
    }

    private static final class StubMethodSignature implements MethodSignature {
        private final Method method;
        private final String[] parameterNames;

        private StubMethodSignature(Method method, String[] parameterNames) {
            this.method = method;
            this.parameterNames = parameterNames;
        }

        @Override
        public Class[] getParameterTypes() {
            return method.getParameterTypes();
        }

        @Override
        public String[] getParameterNames() {
            return parameterNames;
        }

        @Override
        public Class[] getExceptionTypes() {
            return method.getExceptionTypes();
        }

        @Override
        public Class getReturnType() {
            return method.getReturnType();
        }

        @Override
        public Method getMethod() {
            return method;
        }

        @Override
        public String getName() {
            return method.getName();
        }

        @Override
        public int getModifiers() {
            return method.getModifiers();
        }

        @Override
        public Class getDeclaringType() {
            return method.getDeclaringClass();
        }

        @Override
        public String getDeclaringTypeName() {
            return method.getDeclaringClass().getName();
        }

        @Override
        public String toShortString() {
            return method.getName();
        }

        @Override
        public String toLongString() {
            return method.toString();
        }
    }

    private static final class StubProceedingJoinPoint implements ProceedingJoinPoint {
        private final Signature signature;
        private final Object[] args;

        private StubProceedingJoinPoint(Signature signature, Object[] args) {
            this.signature = signature;
            this.args = args;
        }

        @Override
        public Object proceed() {
            return null;
        }

        @Override
        public Object proceed(Object[] args) {
            return null;
        }

        @Override
        public void set$AroundClosure(org.aspectj.runtime.internal.AroundClosure arc) {
            // no-op
        }

        @Override
        public String toShortString() {
            return "stub";
        }

        @Override
        public String toLongString() {
            return "stub";
        }

        @Override
        public Object getThis() {
            return null;
        }

        @Override
        public Object getTarget() {
            return null;
        }

        @Override
        public Object[] getArgs() {
            return args;
        }

        @Override
        public Signature getSignature() {
            return signature;
        }

        @Override
        public SourceLocation getSourceLocation() {
            return null;
        }

        @Override
        public String getKind() {
            return "method-execution";
        }

        @Override
        public JoinPoint.StaticPart getStaticPart() {
            return null;
        }
    }
}
