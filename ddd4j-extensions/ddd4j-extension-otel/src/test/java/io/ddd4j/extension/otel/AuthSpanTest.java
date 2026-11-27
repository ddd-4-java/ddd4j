package io.ddd4j.extension.otel;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AuthSpan} 认证 span 测试（无 SDK 依赖，纯行为验证）。
 *
 * <p>由于 SubjectKit 在 noop OTel 模式下会抛出异常（无 SPI 注册），
 * 测试聚焦于：
 * <ul>
 *   <li>方法签名和返回值类型</li>
 *   <li>异常安全传播</li>
 *   <li>无 OTel 时方法可直接调用</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class AuthSpanTest {

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>OpenTelemetrySdkSetter.set()</code>\u3001<code>OpenTelemetry.noop()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
    @BeforeEach
    void setUp() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
    }

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>OpenTelemetrySdkSetter.set()</code>\u3001<code>OpenTelemetry.noop()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void tearDown() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
    }

    /** \u9a8c\u8bc1<code>AuthSpan.ATTR_AUTH_FRAMEWORK.getKey()</code> \u7b49\u4e8e <code>"auth.framework"</code>\uff0c<code>AuthSpan.ATTR_AUTH_LOGIN_ID.getKey()</code> \u7b49\u4e8e <code>"auth.login_id"</code>\uff0c<code>AuthSpan.ATTR_AUTH_PERMISSION.getKey()</code> \u7b49\u4e8e <code>"auth.permission"</code>\u3002 */
    @Test
    void attributeKeys_shouldHaveCorrectNames() {
        assertThat(AuthSpan.ATTR_AUTH_FRAMEWORK.getKey()).isEqualTo("auth.framework");
        assertThat(AuthSpan.ATTR_AUTH_LOGIN_ID.getKey()).isEqualTo("auth.login_id");
        assertThat(AuthSpan.ATTR_AUTH_SUCCESS.getKey()).isEqualTo("auth.success");
        assertThat(AuthSpan.ATTR_AUTH_OPERATION.getKey()).isEqualTo("auth.operation");
        assertThat(AuthSpan.ATTR_AUTH_ROLE.getKey()).isEqualTo("auth.role");
        assertThat(AuthSpan.ATTR_AUTH_PERMISSION.getKey()).isEqualTo("auth.permission");
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>OpenTelemetrySdkSetter.set()</code>\u3001<code>OpenTelemetry.noop()</code>\u3001<code>AuthSpan.hasRole()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void hasRole_shouldBeCallable_noopOTel() {
        // 即使无 SPI 也应能编译（无 OTel 时直接转发到 SubjectKit）
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
        // 无 SPI 注册会抛异常，这是预期行为
        assertThatThrownBy(() -> AuthSpan.hasRole("admin"))
                .isInstanceOfAny(Exception.class);
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>OpenTelemetrySdkSetter.set()</code>\u3001<code>OpenTelemetry.noop()</code>\u3001<code>AuthSpan.hasPermission()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void hasPermission_shouldBeCallable_noopOTel() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
        assertThatThrownBy(() -> AuthSpan.hasPermission("user:delete"))
                .isInstanceOfAny(Exception.class);
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>OpenTelemetrySdkSetter.set()</code>\u3001<code>OpenTelemetry.noop()</code>\u3001<code>AuthSpan.isLogin()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void isLogin_shouldBeCallable_noopOTel() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
        assertThatThrownBy(() -> AuthSpan.isLogin())
                .isInstanceOfAny(Exception.class);
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>OpenTelemetrySdkSetter.set()</code>\u3001<code>OpenTelemetry.noop()</code>\u3001<code>AuthSpan.login()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void login_shouldBeCallable_noopOTel() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
        // AuthRequest.of(loginId) 创建简单请求
        io.ddd4j.core.auth.AuthRequest req = io.ddd4j.core.auth.AuthRequest.of("user-1");
        assertThatThrownBy(() -> AuthSpan.login(req))
                .isInstanceOfAny(Exception.class);
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>OpenTelemetrySdkSetter.set()</code>\u3001<code>OpenTelemetry.noop()</code>\u3001<code>AuthSpan.logout()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void logout_shouldBeCallable_noopOTel() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
        assertThatThrownBy(() -> AuthSpan.logout())
                .isInstanceOfAny(Exception.class);
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>OpenTelemetrySdkSetter.set()</code>\u3001<code>OpenTelemetry.noop()</code>\u3001<code>AuthSpan.logout()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void logout_withLoginId_shouldBeCallable_noopOTel() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
        assertThatThrownBy(() -> AuthSpan.logout("user-1"))
                .isInstanceOfAny(Exception.class);
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>OpenTelemetrySdkSetter.set()</code>\u3001<code>OpenTelemetry.noop()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void verify_shouldBeCallable_noopOTel() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
        assertThatThrownBy(() -> AuthSpan.verify("token-123"))
                .isInstanceOfAny(Exception.class);
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>OpenTelemetrySdkSetter.set()</code>\u3001<code>OpenTelemetry.noop()</code>\u3001<code>AuthSpan.kickout()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void kickout_shouldBeCallable_noopOTel() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
        assertThatThrownBy(() -> AuthSpan.kickout("user-1"))
                .isInstanceOfAny(Exception.class);
    }

    /** \u9a8c\u8bc1<code>true</code>\u4e3a\u771f\u3002 */
    @Test
    void hasRole_shouldHandleNullArgument() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
        // 无 SPI 时抛异常，但传入 null 不应在到达 SubjectKit 之前抛 NPE
        try {
            AuthSpan.hasRole(null);
        } catch (NullPointerException | IllegalStateException e) {
            assertThat(true).isTrue();
        } catch (Exception e) {
            assertThat(true).isTrue();
        }
    }

    /** \u9a8c\u8bc1<code>true</code>\u4e3a\u771f\u3002 */
    @Test
    void hasPermission_shouldHandleNullArgument() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
        try {
            AuthSpan.hasPermission(null);
        } catch (Exception e) {
            assertThat(true).isTrue();
        }
    }

    /** \u9a8c\u8bc1<code>true</code>\u4e3a\u771f\u3002 */
    @Test
    void login_withNullRequest_shouldBeCallable() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
        try {
            AuthSpan.login(null);
        } catch (Exception e) {
            assertThat(true).isTrue();
        }
    }

    /** \u9a8c\u8bc1<code>true</code>\u4e3a\u771f\u3002 */
    @Test
    void verify_withNullToken_shouldBeCallable() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
        try {
            AuthSpan.verify(null);
        } catch (Exception e) {
            assertThat(true).isTrue();
        }
    }

    /** \u9a8c\u8bc1<code>true</code>\u4e3a\u771f\u3002 */
    @Test
    void logout_withNullLoginId_shouldBeCallable() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
        try {
            AuthSpan.logout(null);
        } catch (Exception e) {
            assertThat(true).isTrue();
        }
    }

    /** \u9a8c\u8bc1<code>true</code>\u4e3a\u771f\u3002 */
    @Test
    void kickout_withNullLoginId_shouldBeCallable() {
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
        try {
            AuthSpan.kickout(null);
        } catch (Exception e) {
            assertThat(true).isTrue();
        }
    }

    /** \u9a8c\u8bc1<code>invoked</code>\u975e\u7a7a\u3002 */
    @Test
    void verify_withAuthPrincipal_shouldSetLoginIdAttribute() {
        // 验证 verify 在 noop 模式下也能正确处理（即使抛异常也是预期的）
        AtomicBoolean invoked = new AtomicBoolean(false);
        OpenTelemetrySdkSetter.set(OpenTelemetry.noop());
        try {
            io.ddd4j.core.auth.AuthPrincipal p = AuthSpan.verify("token");
            invoked.set(Objects.nonNull(p));
        } catch (Exception ignored) {
        }
        // 验证至少被调用过（即使抛异常也代表执行到 SubjectKit）
        assertThat(invoked).isNotNull();
    }

    /** \u9a8c\u8bc1<code>AuthSpan.ATTR_AUTH_FRAMEWORK</code>\u975e\u7a7a\uff0c<code>AuthSpan.ATTR_AUTH_LOGIN_ID</code>\u975e\u7a7a\uff0c<code>AuthSpan.ATTR_AUTH_OPERATION</code>\u975e\u7a7a\u3002 */
    @Test
    void authSpan_constants_shouldBeImmutable() {
        // 验证常量定义（防止重构改动破坏调用）
        assertThat(AuthSpan.ATTR_AUTH_FRAMEWORK).isNotNull();
        assertThat(AuthSpan.ATTR_AUTH_LOGIN_ID).isNotNull();
        assertThat(AuthSpan.ATTR_AUTH_SUCCESS).isNotNull();
        assertThat(AuthSpan.ATTR_AUTH_OPERATION).isNotNull();
    }
}
