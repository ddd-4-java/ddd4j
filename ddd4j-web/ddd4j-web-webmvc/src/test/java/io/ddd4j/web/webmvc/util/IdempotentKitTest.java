package io.ddd4j.web.webmvc.util;

import io.ddd4j.annotation.api.ApiIdempotent;
import io.ddd4j.annotation.api.ApiIdempotentType;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * {@link IdempotentKit} 幂等键生成单元测试。
 *
 * <p>验证 {@link IdempotentKit#getIdempotentKey(ProceedingJoinPoint, ApiIdempotent)} 的三种模式：
 * <ul>
 *   <li>固定 value 模式：直接返回注解 value</li>
 *   <li>SpEL 模式：解析表达式返回值</li>
 *   <li>路由拼接模式：拼接 RequestMapping + PostMapping 路径后计算 MD5</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@ExtendWith(MockitoExtension.class)
class IdempotentKitTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    // =================== 固定 value 模式 ===================

    @Test
    void getIdempotentKey_withFixedValue_shouldReturnDirectly() throws Exception {
        Method method = SampleController.class.getMethod("fixedValueEndpoint", String.class);
        ApiIdempotent idempotent = method.getAnnotation(ApiIdempotent.class);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"name"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{"test"});

        String key = IdempotentKit.getIdempotentKey(joinPoint, idempotent);

        assertThat(key).isEqualTo("my-fixed-key");
    }

    // =================== SpEL 模式 ===================

    @Test
    void getIdempotentKey_withSpel_shouldEvaluateExpression() throws Exception {
        Method method = SampleController.class.getMethod("spelEndpoint", String.class, Integer.class);
        ApiIdempotent idempotent = method.getAnnotation(ApiIdempotent.class);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"name", "count"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{"hello", 42});

        String key = IdempotentKit.getIdempotentKey(joinPoint, idempotent);

        assertThat(key).isEqualTo("hello-42");
    }

    // =================== 路由拼接模式（默认） ===================

    @Test
    void getIdempotentKey_withRouteMapping_shouldGenerateMd5() throws Exception {
        Method method = SampleController.class.getMethod("defaultEndpoint", String.class);
        ApiIdempotent idempotent = method.getAnnotation(ApiIdempotent.class);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"data"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{"payload"});

        String key = IdempotentKit.getIdempotentKey(joinPoint, idempotent);

        // 应该返回 MD5 哈希（32 位十六进制）
        assertThat(key).hasSize(32);
        assertThat(key).matches("[a-f0-9]+");
    }

    @Test
    void getIdempotentKey_withRouteMapping_noArgs_shouldGenerateMd5() throws Exception {
        Method method = SampleController.class.getMethod("noArgsEndpoint");
        ApiIdempotent idempotent = method.getAnnotation(ApiIdempotent.class);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(methodSignature.getParameterNames()).thenReturn(new String[]{});
        when(joinPoint.getArgs()).thenReturn(new Object[]{});

        String key = IdempotentKit.getIdempotentKey(joinPoint, idempotent);

        assertThat(key).hasSize(32);
        assertThat(key).matches("[a-f0-9]+");
    }

    @Test
    void getIdempotentKey_withArgsIncluded_shouldVaryByArgs() throws Exception {
        Method method = SampleController.class.getMethod("withArgsEndpoint", String.class);
        ApiIdempotent idempotent = method.getAnnotation(ApiIdempotent.class);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"orderId"});

        // 不同参数应生成不同 key
        when(joinPoint.getArgs()).thenReturn(new Object[]{"ORD-001"});
        String key1 = IdempotentKit.getIdempotentKey(joinPoint, idempotent);

        when(joinPoint.getArgs()).thenReturn(new Object[]{"ORD-002"});
        String key2 = IdempotentKit.getIdempotentKey(joinPoint, idempotent);

        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    void getIdempotentKey_sameArgsSameRoute_shouldGenerateSameKey() throws Exception {
        Method method = SampleController.class.getMethod("defaultEndpoint", String.class);
        ApiIdempotent idempotent = method.getAnnotation(ApiIdempotent.class);

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"data"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{"same-data"});

        String key1 = IdempotentKit.getIdempotentKey(joinPoint, idempotent);
        String key2 = IdempotentKit.getIdempotentKey(joinPoint, idempotent);

        assertThat(key1).isEqualTo(key2);
    }

    // =================== 辅助：测试用 Controller ===================

    @RestController
    @RequestMapping("/api/sample")
    static class SampleController {

        @ApiIdempotent("my-fixed-key")
        @PostMapping("/fixed")
        public void fixedValueEndpoint(String name) {
        }

        @ApiIdempotent(value = "#name + '-' + #count", spel = true)
        @PostMapping("/spel")
        public void spelEndpoint(String name, Integer count) {
        }

        @ApiIdempotent
        @PostMapping("/default")
        public void defaultEndpoint(String data) {
        }

        @ApiIdempotent
        @PostMapping("/noargs")
        public void noArgsEndpoint() {
        }

        @ApiIdempotent(withArgs = true)
        @PostMapping("/withargs")
        public void withArgsEndpoint(String orderId) {
        }
    }
}
